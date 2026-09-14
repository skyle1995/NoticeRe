package com.noticere.app.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.noticere.app.Config
import com.noticere.app.RunLog
import com.noticere.app.filter.Filters
import com.noticere.app.forward.Forwarder
import com.noticere.app.model.ForwardMessage

/**
 * 通知监听服务：系统唯一正规的通知捕获渠道，需用户在「通知使用权」中授权。
 */
class NotificationForwardService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifForward"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (!Config.notifEnabled) return
        // 跳过本应用自己的通知（例如前台服务通知），避免自循环
        if (sbn.packageName == packageName) return
        if (!packageAllowed(sbn.packageName)) return

        val msg = buildMessage(sbn) ?: return
        if (!Filters.keywordAllowed(msg.title + "\n" + msg.body, Config.notifKeywordFilter)) return
        Forwarder.send(msg)
        RunLog.log("转发通知 ${msg.source}")
    }

    private fun packageAllowed(pkg: String): Boolean {
        val filter = Config.notifPackageFilter.trim()
        if (filter.isEmpty()) return true
        val list = filter.split(',', '，', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (list.isEmpty()) return true
        return list.any { pkg == it || pkg.startsWith(it) || pkg.contains(it) }
    }

    private fun buildMessage(sbn: StatusBarNotification): ForwardMessage? {
        val n = sbn.notification ?: return null
        val extras = n.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)

        // 聊天类应用：优先取大文本或全部行
        val body = when {
            !bigText.isNullOrBlank() -> bigText
            lines != null && lines.isNotEmpty() ->
                lines.joinToString("\n") { it?.toString() ?: "" }.trim()
            !text.isNullOrBlank() -> text
            !subText.isNullOrBlank() -> subText
            else -> null
        }

        val displayTitle = title ?: subText ?: appName(sbn.packageName)
        if (displayTitle.isBlank() && body.isNullOrBlank()) return null

        return ForwardMessage.fromNotification(
            pkg = sbn.packageName,
            appName = appName(sbn.packageName),
            title = displayTitle,
            text = body
        )
    }

    private fun appName(pkg: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
