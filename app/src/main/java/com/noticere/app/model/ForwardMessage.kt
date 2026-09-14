package com.noticere.app.model

import com.noticere.app.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一条待转发的消息（短信 / 未接来电 / 通知）。
 */
data class ForwardMessage(
    val type: String,       // sms | missed_call | notification | test
    val source: String,     // 来源标识：号码 / 应用名
    val title: String,      // 展示标题
    val body: String,       // 展示正文
    val packageName: String?,
    val sim: String?,       // 接收短信/来电的 SIM 手机号（通知为 null）
    val timestamp: Long
) {
    companion object {
        fun fromSms(r: SmsRecord, simNumber: String?): ForwardMessage {
            val who = r.address?.ifBlank { "未知号码" } ?: "未知号码"
            val values = baseValues(
                type = "短信",
                number = who,
                content = r.body ?: "",
                time = fmt(r.date),
                sim = simNumber
            )
            return ForwardMessage(
                type = "sms",
                source = who,
                title = TemplateRenderer.render(Config.smsTitleTemplate, values),
                body = TemplateRenderer.render(Config.smsBodyTemplate, values),
                packageName = null,
                sim = simNumber,
                timestamp = r.date
            )
        }

        fun fromCall(r: CallRecord, simNumber: String?): ForwardMessage {
            val who = r.number?.ifBlank { "未知号码" } ?: "未知号码"
            val label = when (r.type) {
                5 -> "拒接来电"
                6 -> "拦截来电"
                else -> "未接来电"
            }
            val values = baseValues(
                type = label,
                number = who,
                content = "",
                time = fmt(r.date),
                sim = simNumber
            )
            return ForwardMessage(
                type = "missed_call",
                source = who,
                title = TemplateRenderer.render(Config.callTitleTemplate, values),
                body = TemplateRenderer.render(Config.callBodyTemplate, values),
                packageName = null,
                sim = simNumber,
                timestamp = r.date
            )
        }

        fun fromNotification(pkg: String, appName: String, title: String?, text: String?): ForwardMessage {
            val content = buildString {
                if (!title.isNullOrBlank()) append(title)
                if (!text.isNullOrBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(text)
                }
            }
            val values = baseValues(
                type = "通知",
                number = "",
                content = content,
                time = fmt(System.currentTimeMillis()),
                sim = null
            ).plus(
                mapOf("app" to appName, "package" to pkg)
            )
            return ForwardMessage(
                type = "notification",
                source = appName,
                title = TemplateRenderer.render(Config.notifTitleTemplate, values),
                body = TemplateRenderer.render(Config.notifBodyTemplate, values),
                packageName = pkg,
                sim = null,
                timestamp = System.currentTimeMillis()
            )
        }

        private fun baseValues(
            type: String,
            number: String,
            content: String,
            time: String,
            sim: String?
        ): Map<String, String> = mapOf(
            "type" to type,
            "number" to number,
            "content" to content,
            "time" to time,
            "sim" to (sim ?: ""),
            "app" to "",
            "package" to ""
        )

        private fun fmt(ms: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))
    }
}

/** 短信记录（从 mmssms.db 读取）。type == 1 表示收到的短信，simId 表示接收订阅ID。 */
data class SmsRecord(
    val id: Long,
    val address: String?,
    val body: String?,
    val date: Long,
    val type: Int,
    val simId: Int
)

/** 通话记录（从 calllog.db / contacts2.db 读取）。type == 3 表示未接来电，simId 表示所在订阅ID。 */
data class CallRecord(
    val id: Long,
    val number: String?,
    val date: Long,
    val duration: Long,
    val type: Int,
    val simId: Int
)
