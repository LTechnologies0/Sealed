package app.sealed.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorGateTest {
    @Test
    fun sameSiteAcceptsTheInstalledHostAndItsSubdomains() {
        assertTrue(SensorGate.sameSite("https://example.com", "example.com"))
        assertTrue(SensorGate.sameSite("https://login.example.com/path", "example.com"))
        assertFalse(SensorGate.sameSite("https://evil.com", "example.com"))
        assertFalse(SensorGate.sameSite("https://example.com.evil", "example.com"))
        assertFalse(SensorGate.sameSite("http://example.com", "example.com"))
        assertFalse(SensorGate.sameSite(null, "example.com"))
    }
}
