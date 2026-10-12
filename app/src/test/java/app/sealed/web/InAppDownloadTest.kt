package app.sealed.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InAppDownloadTest {
    @Test
    fun stripsPathTraversalFromFileName() {
        assertEquals("download", InAppDownload.safeFileName(".."))
        assertEquals("download", InAppDownload.safeFileName("."))
        assertEquals("download", InAppDownload.safeFileName("../.."))
        assertEquals("secret", InAppDownload.safeFileName("../../secret"))
        assertEquals("report.pdf", InAppDownload.safeFileName("report.pdf"))
        assertFalse(InAppDownload.safeFileName("..\\..\\passwd").contains(".."))
    }

    @Test
    fun downloadAllowlistIsHttpsOnly() {
        assertEquals(
            "scheme",
            RequestGate.blockReason("http://example.com/file", "", lockToHost = false, blockTrackers = false),
        )
        assertEquals(
            "scheme",
            RequestGate.blockReason("blob:https://example.com/id", "", lockToHost = false, blockTrackers = false),
        )
        assertEquals(
            null,
            RequestGate.blockReason("https://example.com/file.bin", "", lockToHost = false, blockTrackers = false),
        )
    }
}
