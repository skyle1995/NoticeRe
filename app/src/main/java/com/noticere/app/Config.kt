package com.noticere.app

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用配置，基于 SharedPreferences。
 * 首次使用前必须调用 [init]。
 */
object Config {

    private const val PREFS = "noticere_prefs"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // ---- Bark ----
    var barkEnabled: Boolean
        get() = sp.getBoolean("bark_enabled", false)
        set(v) = sp.edit().putBoolean("bark_enabled", v).apply()

    var barkServer: String
        get() = sp.getString("bark_server", "https://api.day.app") ?: "https://api.day.app"
        set(v) = sp.edit().putString("bark_server", v.trim().trimEnd('/')).apply()

    var barkKey: String
        get() = sp.getString("bark_key", "") ?: ""
        set(v) = sp.edit().putString("bark_key", v.trim()).apply()

    /** Bark 推送级别：active / timeSensitive / passive */
    var barkLevel: String
        get() = sp.getString("bark_level", "active") ?: "active"
        set(v) = sp.edit().putString("bark_level", v.trim()).apply()

    /** Bark 提示音（如 alarm / payattention，留空 = 默认） */
    var barkSound: String
        get() = sp.getString("bark_sound", "") ?: ""
        set(v) = sp.edit().putString("bark_sound", v.trim()).apply()

    // ---- Webhook ----
    var webhookEnabled: Boolean
        get() = sp.getBoolean("webhook_enabled", false)
        set(v) = sp.edit().putBoolean("webhook_enabled", v).apply()

    var webhookUrl: String
        get() = sp.getString("webhook_url", "") ?: ""
        set(v) = sp.edit().putString("webhook_url", v.trim()).apply()

    /** Webhook 平台：generic / dingtalk / feishu / serverchan */
    var webhookPlatform: String
        get() = sp.getString("webhook_platform", "generic") ?: "generic"
        set(v) = sp.edit().putString("webhook_platform", v.trim()).apply()

    /** 钉钉/飞书机器人加签密钥（留空 = 不加签） */
    var webhookSecret: String
        get() = sp.getString("webhook_secret", "") ?: ""
        set(v) = sp.edit().putString("webhook_secret", v.trim()).apply()

    /** 通用 Webhook 自定义 Header（每行 "Key: Value"，仅 generic 平台生效） */
    var webhookHeaders: String
        get() = sp.getString("webhook_headers", "") ?: ""
        set(v) = sp.edit().putString("webhook_headers", v.trim()).apply()

    // ---- 转发内容开关 ----
    var smsEnabled: Boolean
        get() = sp.getBoolean("sms_enabled", false)
        set(v) = sp.edit().putBoolean("sms_enabled", v).apply()

    var callEnabled: Boolean
        get() = sp.getBoolean("call_enabled", false)
        set(v) = sp.edit().putBoolean("call_enabled", v).apply()

    var notifEnabled: Boolean
        get() = sp.getBoolean("notif_enabled", false)
        set(v) = sp.edit().putBoolean("notif_enabled", v).apply()

    /** 通知包名过滤（逗号分隔，空 = 全部应用）。 */
    var notifPackageFilter: String
        get() = sp.getString("notif_pkg_filter", "") ?: ""
        set(v) = sp.edit().putString("notif_pkg_filter", v.trim()).apply()

    /** 通知关键词白名单（逗号分隔，匹配标题/正文，空 = 全部）。 */
    var notifKeywordFilter: String
        get() = sp.getString("notif_keyword_filter", "") ?: ""
        set(v) = sp.edit().putString("notif_keyword_filter", v.trim()).apply()

    // ---- 过滤（白名单，逗号分隔，空 = 全部放行） ----
    var smsNumberFilter: String
        get() = sp.getString("sms_number_filter", "") ?: ""
        set(v) = sp.edit().putString("sms_number_filter", v.trim()).apply()

    var smsKeywordFilter: String
        get() = sp.getString("sms_keyword_filter", "") ?: ""
        set(v) = sp.edit().putString("sms_keyword_filter", v.trim()).apply()

    var callNumberFilter: String
        get() = sp.getString("call_number_filter", "") ?: ""
        set(v) = sp.edit().putString("call_number_filter", v.trim()).apply()

    // ---- 消息模板 ----
    var smsTitleTemplate: String
        get() = sp.getString("sms_title_tpl", Defaults.SMS_TITLE) ?: Defaults.SMS_TITLE
        set(v) = sp.edit().putString("sms_title_tpl", v).apply()

    var smsBodyTemplate: String
        get() = sp.getString("sms_body_tpl", Defaults.SMS_BODY) ?: Defaults.SMS_BODY
        set(v) = sp.edit().putString("sms_body_tpl", v).apply()

    var callTitleTemplate: String
        get() = sp.getString("call_title_tpl", Defaults.CALL_TITLE) ?: Defaults.CALL_TITLE
        set(v) = sp.edit().putString("call_title_tpl", v).apply()

    var callBodyTemplate: String
        get() = sp.getString("call_body_tpl", Defaults.CALL_BODY) ?: Defaults.CALL_BODY
        set(v) = sp.edit().putString("call_body_tpl", v).apply()

    var notifTitleTemplate: String
        get() = sp.getString("notif_title_tpl", Defaults.NOTIF_TITLE) ?: Defaults.NOTIF_TITLE
        set(v) = sp.edit().putString("notif_title_tpl", v).apply()

    var notifBodyTemplate: String
        get() = sp.getString("notif_body_tpl", Defaults.NOTIF_BODY) ?: Defaults.NOTIF_BODY
        set(v) = sp.edit().putString("notif_body_tpl", v).apply()

    /** 把消息模板恢复为默认值。 */
    fun resetTemplates() {
        smsTitleTemplate = Defaults.SMS_TITLE
        smsBodyTemplate = Defaults.SMS_BODY
        callTitleTemplate = Defaults.CALL_TITLE
        callBodyTemplate = Defaults.CALL_BODY
        notifTitleTemplate = Defaults.NOTIF_TITLE
        notifBodyTemplate = Defaults.NOTIF_BODY
    }

    private object Defaults {
        const val SMS_TITLE = "{type} • {sim}"
        const val SMS_BODY = "{content}\n\n来自号码：{number}"
        const val CALL_TITLE = "{type} • {sim}"
        const val CALL_BODY = "时间：{time}\n来自号码：{number}"
        const val NOTIF_TITLE = "{type} • {app}"
        const val NOTIF_BODY = "{content}"
    }

    // ---- 去重游标（记录上次已处理的最大 id） ----
    var smsBaselineDone: Boolean
        get() = sp.getBoolean("sms_baseline_done", false)
        set(v) = sp.edit().putBoolean("sms_baseline_done", v).apply()

    var lastSmsId: Long
        get() = sp.getLong("last_sms_id", 0L)
        set(v) = sp.edit().putLong("last_sms_id", v).apply()

    var callBaselineDone: Boolean
        get() = sp.getBoolean("call_baseline_done", false)
        set(v) = sp.edit().putBoolean("call_baseline_done", v).apply()

    var lastCallId: Long
        get() = sp.getLong("last_call_id", 0L)
        set(v) = sp.edit().putLong("last_call_id", v).apply()
}
