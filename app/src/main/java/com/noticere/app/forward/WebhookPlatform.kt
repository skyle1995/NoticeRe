package com.noticere.app.forward

/** Webhook 平台预设。 */
enum class WebhookPlatform(val key: String) {
    GENERIC("generic"),
    DINGTALK("dingtalk"),
    FEISHU("feishu"),
    SERVERCHAN("serverchan");

    companion object {
        fun from(key: String): WebhookPlatform =
            entries.firstOrNull { it.key == key } ?: GENERIC
    }
}
