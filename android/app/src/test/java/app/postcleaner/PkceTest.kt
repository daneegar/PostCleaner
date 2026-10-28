package app.postcleaner

import org.junit.Assert.*
import org.junit.Test

class PkceTest {
    @Test fun challengeMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun verifierIsUrlSafeAndRandom() {
        val values = (1..100).map { Pkce.random() }
        assertEquals(100, values.toSet().size)
        assertTrue(values.all { it.length == 43 && it.matches(Regex("[A-Za-z0-9_-]+")) })
    }
    @Test fun callbackRejectsMissingOrMismatchedState() {
        assertFalse(Pkce.validCallback(null, null, "v", 100, 200))
        assertFalse(Pkce.validCallback("a", "b", "v", 100, 200))
        assertFalse(Pkce.validCallback("a", "a", null, 100, 200))
    }
    @Test fun callbackExpiresAndRejectsClockRollback() {
        assertTrue(Pkce.validCallback("a", "a", "v", 100, 600100))
        assertFalse(Pkce.validCallback("a", "a", "v", 100, 600101))
        assertFalse(Pkce.validCallback("a", "a", "v", 100, 99))
    }
}
