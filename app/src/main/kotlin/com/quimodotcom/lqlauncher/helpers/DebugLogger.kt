package com.quimodotcom.lqlauncher.helpers

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object DebugLogger {
    private const val MAX_LOGS = 50
    private val logs = ConcurrentLinkedDeque<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        val timestamp = synchronized(dateFormat) {
            dateFormat.format(Date())
        }
        val logEntry = "$timestamp [$tag] $message"

        logs.add(logEntry)
        while (logs.size > MAX_LOGS) {
            logs.pollFirst()
        }
    }

    fun getLogs(): List<String> {
        return logs.toList()
    }

    fun clear() {
        logs.clear()
    }
}
