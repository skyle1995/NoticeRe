package com.noticere.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 进程内运行日志（环形缓冲，保留最近若干条），供「运行状态」页展示。
 */
object RunLog {

    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(msg: String) {
        val line = "[${fmt.format(Date())}] $msg"
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun clear() = synchronized(lines) { lines.clear() }
}
