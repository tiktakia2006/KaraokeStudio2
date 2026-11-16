package com.example.karaokestudio.data.remote

object ServerUrl {

    fun normalize(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        val schemeLength = when {
            lower.startsWith("https://") -> 8
            lower.startsWith("http://") -> 7
            else -> return null
        }
        val host = trimmed.substring(schemeLength)
        if (host.isEmpty() || host.any { it.isWhitespace() }) return null
        if (host.startsWith("/") || host.startsWith(":")) return null
        return trimmed
    }

    fun isSecure(url: String): Boolean = url.lowercase().startsWith("https://")
}
