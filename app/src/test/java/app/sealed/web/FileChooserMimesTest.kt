package app.sealed.web

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class FileChooserMimesTest {
    @Test
    fun keepsExplicitMimeTypes() {
        assertArrayEquals(
            arrayOf("image/png", "application/pdf"),
            FileChooserMimes.filters(arrayOf("image/png", "application/pdf")),
        )
    }

    @Test
    fun emptyAcceptFallsBackToAny() {
        assertArrayEquals(arrayOf("*/*"), FileChooserMimes.filters(null))
        assertArrayEquals(arrayOf("*/*"), FileChooserMimes.filters(emptyArray()))
    }
}
