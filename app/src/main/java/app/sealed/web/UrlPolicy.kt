package app.sealed.web

import android.net.Uri
import java.net.IDN
import java.net.URI

object UrlPolicy {
    fun normalizeStartUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host.isEmpty() || host == "localhost" || isIpLiteral(host)) return null
        val ascii = runCatching { IDN.toASCII(host) }.getOrNull() ?: return null
        val path = uri.rawPath?.ifEmpty { "/" } ?: "/"
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "https://$ascii$path$query"
    }

    fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    fun sameSite(lockedHost: String, target: Uri?): Boolean {
        val host = target?.host?.lowercase() ?: return false
        if (target.scheme != "https") return false
        return host == lockedHost || host.endsWith(".$lockedHost")
    }

    private fun isIpLiteral(host: String): Boolean {
        if (host.startsWith("[") || host.count { it == '.' } == 3 && host.all { it.isDigit() || it == '.' }) {
            return true
        }
        return host.contains(':')
    }
}
