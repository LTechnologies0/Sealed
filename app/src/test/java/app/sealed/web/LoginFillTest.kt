package app.sealed.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginFillTest {
    @Test
    fun fillsOnlyTopFrameVisibleFieldAfterTap() {
        val script = LoginFill.script("user@example.com", "secret")
        assertTrue(script.contains("if (window.top !== window) return;"))
        assertTrue(script.contains("function visible(el)"))
        assertTrue(script.contains("pointerdown"))
        assertTrue(script.contains("focusin"))
        assertTrue(script.contains("click"))
        assertTrue(script.contains("target !== document.activeElement"))
        assertFalse(script.contains("MutationObserver"))
        assertFalse(script.contains("pointerup"))
        assertTrue(script.contains("user@example.com"))
    }
}
