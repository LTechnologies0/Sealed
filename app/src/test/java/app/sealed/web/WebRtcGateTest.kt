package app.sealed.web

import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcGateTest {
    @Test
    fun forcesRelayAndDropsLanCandidates() {
        val script = WebRtcGate.script()
        assertTrue(script.contains("iceTransportPolicy = \"relay\""))
        assertTrue(script.contains("typ host"))
        assertTrue(script.contains("typ srflx"))
        assertTrue(script.contains("onicecandidate"))
        assertTrue(script.contains("addIceCandidate"))
    }
}
