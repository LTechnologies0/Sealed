package app.sealed.web

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
    fun blocksCleartextAndIgnoresBlob() {
        assertEquals(
            "scheme",
            RequestGate.blockReason("http://example.com/", "example.com", lockToHost = false, blockTrackers = true),
        )
        assertNull(
            RequestGate.blockReason("blob:https://example.com/id", "example.com", lockToHost = true, blockTrackers = true),
        )
    }
}
