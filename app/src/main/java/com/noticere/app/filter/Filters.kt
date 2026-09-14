package com.noticere.app.filter

/**
 * 白名单过滤工具。规则统一为「逗号分隔、留空 = 全部放行」。
 */
object Filters {

    fun split(s: String): List<String> =
        s.split(',', '，', ';', '；').map { it.trim() }.filter { it.isNotEmpty() }

    fun containsAny(text: String?, list: List<String>): Boolean =
        text != null && list.any { text.contains(it, ignoreCase = true) }

    /** 号码白名单：空列表 = 全部放行；否则号码需包含任一（支持前缀匹配）。 */
    fun numberAllowed(number: String?, filter: String): Boolean {
        val list = split(filter)
        if (list.isEmpty()) return true
        return containsAny(number, list)
    }

    /** 关键词白名单：空列表 = 全部放行；否则文本需包含任一。 */
    fun keywordAllowed(text: String?, filter: String): Boolean {
        val list = split(filter)
        if (list.isEmpty()) return true
        return containsAny(text, list)
    }
}
