package app.sealed.web

/**
 * Hosts used by common analytics and ad pixels.
 * Match is the host itself or a subdomain, never a substring of another name.
 */
object TrackerList {
    val hosts: Set<String> = setOf(
        "google-analytics.com",
        "googletagmanager.com",
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "facebook.net",
        "connect.facebook.net",
        "analytics.tiktok.com",
        "hotjar.com",
        "segment.io",
        "segment.com",
        "mixpanel.com",
        "scorecardresearch.com",
        "quantserve.com",
        "adnxs.com",
        "ads-twitter.com",
        "bat.bing.com",
        "clarity.ms",
    )

    fun blocks(host: String?): Boolean {
        val name = host?.lowercase()?.trimEnd('.') ?: return false
        if (name.isEmpty()) return false
        return hosts.any { listed -> name == listed || name.endsWith(".$listed") }
    }
}
