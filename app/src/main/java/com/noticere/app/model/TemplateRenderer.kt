package com.noticere.app.model

/**
 * 模板渲染：把 {xxx} 占位符替换为实际值，并清理空值导致的残留分隔符/首尾空白。
 *
 * 可用占位符：
 *   {type}     类型（短信 / 未接来电 / 拒接来电 / 拦截来电 / 通知）
 *   {number}   对方号码（短信发送方 / 来电号码，通知为空）
 *   {content}  内容（短信正文 / 通知正文）
 *   {time}     时间 yyyy-MM-dd HH:mm:ss
 *   {sim}      接收短信/来电的 SIM 手机号（通知为空）
 *   {app}      应用名（通知）
 *   {package}  包名（通知）
 */
object TemplateRenderer {

    private val KEYS = listOf("type", "number", "content", "time", "sim", "app", "package")

    fun render(template: String, values: Map<String, String>): String {
        var result = template
        for (key in KEYS) {
            result = result.replace("{$key}", values[key] ?: "")
        }
        // 逐行去掉首尾空白与行尾残留分隔符（如空 sim 产生的 "通知 • "），保留模板中的空行
        return result.lineSequence()
            .map { it.trim().trimEnd('•', '：', ':', '-').trimEnd() }
            .joinToString("\n")
            .trim()
    }
}
