package app.sealed.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieLinesTest {
    @Test
    fun addsPathAndSecure() {
        assertEquals(
            listOf("session=abc; Path=/; Secure"),
            CookieLines.normalize("session=abc", "example.com"),
        )
    }

    @Test
    fun rejectsForeignDomain() {
        assertTrue(CookieLines.normalize("a=b; Domain=evil.test", "example.com").isEmpty())
    }

    @Test
    fun keepsMatchingDomain() {
        val lines = CookieLines.normalize("a=b; Domain=.example.com; Path=/; Secure", "www.example.com")
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("Domain=.example.com"))
    }
}
