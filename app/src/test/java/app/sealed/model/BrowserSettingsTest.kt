package app.sealed.model

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSettingsTest {
    @Test
    fun safeBrowsingDefaultsOff() {
        assertFalse(BrowserSettings().safeBrowsing)
        assertFalse(BrowserSettings.fromJson(null).safeBrowsing)
        assertFalse(BrowserSettings.fromJson(JSONObject()).safeBrowsing)
    }

    @Test
    fun webRtcLanBlockDefaultsOn() {
        assertTrue(BrowserSettings().blockWebRtcLan)
        assertTrue(BrowserSettings.fromJson(JSONObject()).blockWebRtcLan)
    }

    @Test
    fun popupsDefaultOff() {
        assertFalse(BrowserSettings().popups)
    }

    @Test
    fun jsonCanEnableSafeBrowsingExplicitly() {
        val json = JSONObject().put("safeBrowsing", true)
        assertTrue(BrowserSettings.fromJson(json).safeBrowsing)
    }
}
