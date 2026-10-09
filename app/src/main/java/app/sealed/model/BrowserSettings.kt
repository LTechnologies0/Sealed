package app.sealed.model

import org.json.JSONObject

data class BrowserSettings(
    val pixelPersona: Boolean = true,
    val customUserAgent: String = "",
    val language: String = "en-US",
    val timeZone: String = "America/Los_Angeles",
    val blockThirdPartyCookies: Boolean = true,
    val acceptCookies: Boolean = true,
    val doNotTrack: Boolean = true,
    val globalPrivacyControl: Boolean = true,
    val safeBrowsing: Boolean = true,
    val javascript: Boolean = true,
    val images: Boolean = true,
    val popups: Boolean = false,
    val autoplay: Boolean = false,
    val geolocation: Boolean = false,
    val camera: String = "ask",
    val microphone: String = "ask",
    val locationMode: String = "ask",
    val protectedMedia: String = "ask",
    val midiSysex: String = "ask",
    val spoofLatitude: Double = 0.0,
    val spoofLongitude: Double = 0.0,
    val spoofAccuracyMeters: Double = 100.0,
    val domStorage: Boolean = true,
    val textZoomPercent: Int = 100,
    val colorScheme: String = "system",
    val zoomEnabled: Boolean = true,
    val lockToHost: Boolean = true,
    val blockWebRtcLan: Boolean = true,
    val clearOnExit: Boolean = false,
    val blockTrackers: Boolean = true,
    val referrerPolicy: String = "no-referrer",
) {
    fun resolvedUserAgent(): String =
        if (pixelPersona) PixelPersona.USER_AGENT
        else customUserAgent.ifBlank { PixelPersona.USER_AGENT }

    fun sensorMode(kind: String): String = when (kind) {
        "camera" -> camera
        "microphone" -> microphone
        "location" -> locationMode
        "media" -> protectedMedia
        "midi" -> midiSysex
        else -> "deny"
    }

    fun withSensor(kind: String, mode: String): BrowserSettings {
        val safe = if (mode == "deny" || mode == "allow" || mode == "spoof" || mode == "ask") mode else "ask"
        val stored = if (kind == "media" || kind == "midi") {
            if (safe == "spoof") "deny" else safe
        } else {
            safe
        }
        return when (kind) {
            "camera" -> copy(camera = stored)
            "microphone" -> copy(microphone = stored)
            "location" -> copy(locationMode = stored, geolocation = stored == "allow")
            "media" -> copy(protectedMedia = stored)
            "midi" -> copy(midiSysex = stored)
            else -> this
        }
    }

    fun usesSensorSpoof(): Boolean =
        camera == "spoof" || microphone == "spoof" || locationMode == "spoof"

    fun toJson(): JSONObject = JSONObject()
        .put("pixelPersona", pixelPersona)
        .put("customUserAgent", customUserAgent)
        .put("language", language)
        .put("timeZone", timeZone)
        .put("blockThirdPartyCookies", blockThirdPartyCookies)
        .put("acceptCookies", acceptCookies)
        .put("doNotTrack", doNotTrack)
        .put("globalPrivacyControl", globalPrivacyControl)
        .put("safeBrowsing", safeBrowsing)
        .put("javascript", javascript)
        .put("images", images)
        .put("popups", popups)
        .put("autoplay", autoplay)
        .put("geolocation", locationMode == "allow")
        .put("camera", camera)
        .put("microphone", microphone)
        .put("locationMode", locationMode)
        .put("protectedMedia", protectedMedia)
        .put("midiSysex", midiSysex)
        .put("spoofLatitude", spoofLatitude)
        .put("spoofLongitude", spoofLongitude)
        .put("spoofAccuracyMeters", spoofAccuracyMeters)
        .put("domStorage", domStorage)
        .put("textZoomPercent", textZoomPercent)
        .put("colorScheme", colorScheme)
        .put("zoomEnabled", zoomEnabled)
        .put("lockToHost", lockToHost)
        .put("blockWebRtcLan", blockWebRtcLan)
        .put("clearOnExit", clearOnExit)
        .put("blockTrackers", blockTrackers)
        .put("referrerPolicy", referrerPolicy)

    companion object {
        private fun sensorOr(json: JSONObject, key: String, fallback: String): String {
            val raw = json.optString(key, fallback)
            return if (raw == "ask" || raw == "deny" || raw == "allow" || raw == "spoof") raw else fallback
        }

        fun fromJson(json: JSONObject?): BrowserSettings {
            if (json == null) return BrowserSettings()
            val base = BrowserSettings()
            return base.copy(
                pixelPersona = json.optBoolean("pixelPersona", base.pixelPersona),
                customUserAgent = json.optString("customUserAgent", base.customUserAgent),
                language = json.optString("language", base.language).ifBlank { base.language },
                timeZone = json.optString("timeZone", base.timeZone).ifBlank { base.timeZone },
                blockThirdPartyCookies = json.optBoolean("blockThirdPartyCookies", base.blockThirdPartyCookies),
                acceptCookies = json.optBoolean("acceptCookies", base.acceptCookies),
                doNotTrack = json.optBoolean("doNotTrack", base.doNotTrack),
                globalPrivacyControl = json.optBoolean("globalPrivacyControl", base.globalPrivacyControl),
                safeBrowsing = json.optBoolean("safeBrowsing", base.safeBrowsing),
                javascript = json.optBoolean("javascript", base.javascript),
                images = json.optBoolean("images", base.images),
                popups = json.optBoolean("popups", base.popups),
                autoplay = json.optBoolean("autoplay", base.autoplay),
                geolocation = json.optBoolean("geolocation", base.geolocation),
                camera = sensorOr(json, "camera", base.camera),
                microphone = sensorOr(json, "microphone", base.microphone),
                locationMode = if (json.has("locationMode")) {
                    sensorOr(json, "locationMode", base.locationMode)
                } else if (json.optBoolean("geolocation", false)) {
                    "allow"
                } else {
                    "deny"
                },
                protectedMedia = sensorOr(json, "protectedMedia", base.protectedMedia),
                midiSysex = sensorOr(json, "midiSysex", base.midiSysex),
                spoofLatitude = json.optDouble("spoofLatitude", base.spoofLatitude).coerceIn(-90.0, 90.0),
                spoofLongitude = json.optDouble("spoofLongitude", base.spoofLongitude).coerceIn(-180.0, 180.0),
                spoofAccuracyMeters = json.optDouble("spoofAccuracyMeters", base.spoofAccuracyMeters).coerceIn(1.0, 10000.0),
                domStorage = json.optBoolean("domStorage", base.domStorage),
                textZoomPercent = json.optInt("textZoomPercent", base.textZoomPercent).coerceIn(50, 200),
                colorScheme = json.optString("colorScheme", base.colorScheme).ifBlank { base.colorScheme },
                zoomEnabled = json.optBoolean("zoomEnabled", base.zoomEnabled),
                lockToHost = json.optBoolean("lockToHost", base.lockToHost),
                blockWebRtcLan = json.optBoolean("blockWebRtcLan", base.blockWebRtcLan),
                clearOnExit = json.optBoolean("clearOnExit", base.clearOnExit),
                blockTrackers = json.optBoolean("blockTrackers", base.blockTrackers),
                referrerPolicy = json.optString("referrerPolicy", base.referrerPolicy).ifBlank { base.referrerPolicy },
            )
        }
    }
}

object PixelPersona {
    const val CHROME_MAJOR = "140"
    const val CHROME_FULL = "140.0.7339.155"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16; Pixel 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_FULL Mobile Safari/537.36"
    const val MODEL = "Pixel 10"
    const val PLATFORM_VERSION = "16.0.0"
}
