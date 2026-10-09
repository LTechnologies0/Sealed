package app.sealed.web

object CookieLines {
    fun normalize(raw: String, host: String): List<String> {
        val expected = host.lowercase()
        return raw.lineSequence().mapNotNull { normalizeLine(it, expected) }.toList()
    }

    private fun normalizeLine(line: String, host: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { it == '\n' || it == '\r' || it.code < 0x20 || it.code == 0x7f }) return null
        val name = trimmed.substringBefore('=').trim()
        if (name.isEmpty() || name.any { it.isWhitespace() || it == ';' || it == ',' }) return null
        if (!trimmed.contains('=')) return null
        val domain = Regex("""(?i)(?:^|;)\s*Domain\s*=\s*([^;]*)""")
            .find(trimmed)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.trim('"')
            ?.lowercase()
            ?.trimStart('.')
        if (domain != null && domain != host && !host.endsWith(".$domain")) return null
        var cookie = trimmed
        if (!Regex("""(?i)(?:^|;)\s*Path\s*=""").containsMatchIn(cookie)) cookie += "; Path=/"
        if (!Regex("""(?i)(?:^|;)\s*Secure(?:\s*;|\s*$)""").containsMatchIn(cookie)) cookie += "; Secure"
        return cookie
    }
}
