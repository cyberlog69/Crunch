package com.kappi.crunch.util

import java.util.Locale

/**
 * Pure formatting and parsing helpers shared by the download pipeline and the UI.
 */
object FormatUtils {

    fun formatFilesize(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1000) {
            String.format(Locale.US, "%.1f GB", mb / 1024)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    /**
     * yt-dlp failures arrive as a full Python traceback. The UI only needs the
     * final line (the exception itself); the complete text still goes to logcat.
     */
    fun conciseError(message: String?): String {
        val raw = message?.trim().orEmpty()
        if (raw.isEmpty()) return "Unknown error"
        val result = if (!raw.contains("Traceback (most recent call last)")) {
            raw
        } else {
            val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val last = lines.lastOrNull { it.startsWith("ERROR:") }
                ?: lines.lastOrNull { EXCEPTION_LINE.containsMatchIn(it) }
                ?: lines.lastOrNull().orEmpty()
            last.removePrefix("ERROR:").trim()
        }
        return result.ifEmpty { "Unknown error" }.take(400)
    }

    private val EXCEPTION_LINE = Regex("""^[\w.]*(Error|Exception|Interrupt|Exit)\b""")

    fun mimeTypeFor(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
            "mkv" -> "video/x-matroska"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/opus"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> "*/*"
        }
    }

    /**
     * Splits a raw command-line string into tokens, honouring single and double quotes.
     */
    fun splitArgs(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '
        for (c in input) {
            when {
                !inQuote && (c == '"' || c == '\'') -> {
                    inQuote = true
                    quoteChar = c
                }
                inQuote && c == quoteChar -> inQuote = false
                !inQuote && c.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
