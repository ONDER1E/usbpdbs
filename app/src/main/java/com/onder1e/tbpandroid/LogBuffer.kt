package com.onder1e.tbpandroid

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object LogBuffer {
    private val entries = CopyOnWriteArrayList<String>()
    private val MAX_ENTRIES = 500
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = fmt.format(Date())
        entries.add("$timestamp | $message")
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }

    fun getLogs(): String = entries.joinToString("\n")

    fun clear() { entries.clear() }
}
