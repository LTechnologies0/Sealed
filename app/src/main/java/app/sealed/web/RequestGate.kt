package app.sealed.web

import java.net.URI

enum class ReferrerMode { NO_REFERRER, STRICT_ORIGIN, OFF }

object RequestGate {
    fun referrerMode(raw: String): ReferrerMode = when (raw) {
        "strict-origin-when-cross-origin" -> ReferrerMode.STRICT_ORIGIN
        "off" -> ReferrerMode.OFF
        else -> ReferrerMode.NO_REFERRER
    }

    fun referrerToken(mode: ReferrerMode): String? = when (mode) {
        ReferrerMode.NO_REFERRER -> "no-referrer"
        ReferrerMode.STRICT_ORIGIN -> "strict-origin-when-cross-origin"
        ReferrerMode.OFF -> null
    }

    fun blockReason(url: String, lockHost: String, lockToHost: Boolean, blockTrackers: Boolean): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return "url"
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http") return "scheme"
        if (scheme != "https") return null
        val host = uri.host?.lowercase() ?: return "host"
        if (blockTrackers && TrackerList.blocks(host)) return "tracker"
        if (lockToHost && host != lockHost && !host.endsWith(".$lockHost")) return "domain"
        return null
    }
}
