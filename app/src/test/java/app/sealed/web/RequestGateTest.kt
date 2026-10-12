package app.sealed.web

import app.sealed.model.BrowserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestGateTest {
    @Test
    fun blocksKnownTrackerHostAndSubdomain() {
        assertTrue(TrackerList.blocks("google-analytics.com"))
        assertTrue(TrackerList.blocks("www.google-analytics.com"))
        assertTrue(TrackerList.blocks("region1.analytics.google-analytics.com"))
    }

    @Test
    fun doesNotBlockLookalikeHost() {
        assertFalse(TrackerList.blocks("notgoogle-analytics.com"))
        assertFalse(TrackerList.blocks("google-analytics.com.evil.test"))
        assertFalse(TrackerList.blocks("example.com"))
    }

    @Test
    fun blocksTrackerRequestWithoutBlockingSiteCdn() {
        assertEquals(
            "tracker",
            RequestGate.blockReason(
                "https://www.google-analytics.com/g/collect",
                "example.com",
                lockToHost = true,
                blockTrackers = true,
            ),
        )
        assertNull(
            RequestGate.blockReason(
                "https://cdn.example.com/app.js",
                "example.com",
                lockToHost = false,
                blockTrackers = true,
            ),
        )
    }

    @Test
    fun domainLockAppliesOnlyWhenAsked() {
        assertEquals(
            "domain",
            RequestGate.blockReason(
                "https://other.test/path",
                "example.com",
                lockToHost = true,
                blockTrackers = true,
            ),
        )
        assertNull(
            RequestGate.blockReason(
                "https://cdn.jsdelivr.net/pkg.js",
                "example.com",
                lockToHost = false,
                blockTrackers = true,
            ),
        )
    }

    @Test
    fun rejectsNonHttpsSchemes() {
        val host = "example.com"
        assertEquals("scheme", RequestGate.blockReason("http://example.com/", host, lockToHost = false, blockTrackers = true))
        assertEquals("scheme", RequestGate.blockReason("javascript:alert(1)", host, lockToHost = false, blockTrackers = true))
        assertEquals("scheme", RequestGate.blockReason("intent://scan/#Intent;end", host, lockToHost = false, blockTrackers = true))
        assertEquals("scheme", RequestGate.blockReason("file:///sdcard/secret", host, lockToHost = false, blockTrackers = true))
        assertEquals("scheme", RequestGate.blockReason("content://media/external/file", host, lockToHost = false, blockTrackers = true))
        assertEquals("scheme", RequestGate.blockReason("data:text/html,hi", host, lockToHost = false, blockTrackers = true))
        assertEquals("scheme", RequestGate.blockReason("blob:https://example.com/id", host, lockToHost = false, blockTrackers = true))
        assertNull(RequestGate.blockReason("https://example.com/", host, lockToHost = true, blockTrackers = true))
    }

    @Test
    fun popupTargetUsesTheSameHttpsAllowlist() {
        val locked = BrowserSettings(lockToHost = true, blockTrackers = true)
        assertEquals(
            "https://example.com/ok",
            RequestGate.popupTarget("https://example.com/ok", "example.com", locked),
        )
        assertNull(RequestGate.popupTarget("http://example.com/", "example.com", locked))
        assertNull(RequestGate.popupTarget("javascript:alert(1)", "example.com", locked))
        assertNull(RequestGate.popupTarget("intent://scan/#Intent;end", "example.com", locked))
        assertNull(RequestGate.popupTarget("file:///sdcard/x", "example.com", locked))
        assertNull(RequestGate.popupTarget("content://media/x", "example.com", locked))
        assertNull(RequestGate.popupTarget("data:text/html,hi", "example.com", locked))
        assertNull(RequestGate.popupTarget("blob:https://example.com/id", "example.com", locked))
        assertNull(RequestGate.popupTarget("https://evil.test/", "example.com", locked))
        val open = BrowserSettings(lockToHost = false, blockTrackers = false)
        assertEquals(
            "https://cdn.example.net/a",
            RequestGate.popupTarget("https://cdn.example.net/a", "example.com", open),
        )
    }
}
