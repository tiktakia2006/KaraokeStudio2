package com.example.karaokestudio.data

import com.example.karaokestudio.data.online.AccountValidator
import com.example.karaokestudio.data.remote.ServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {

    @Test
    fun trimsSpacesAndTrailingSlashes() {
        assertEquals("https://karaoke.example.com", ServerUrl.normalize("  https://karaoke.example.com/// "))
        assertEquals("http://10.0.2.2:8080", ServerUrl.normalize("http://10.0.2.2:8080/"))
    }

    @Test
    fun keepsPathPrefix() {
        assertEquals("https://example.com/karaoke", ServerUrl.normalize("https://example.com/karaoke/"))
    }

    @Test
    fun rejectsMissingSchemeAndBlankHost() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("   "))
        assertNull(ServerUrl.normalize("example.com"))
        assertNull(ServerUrl.normalize("ftp://example.com"))
        assertNull(ServerUrl.normalize("https://"))
        assertNull(ServerUrl.normalize("https:///path"))
        assertNull(ServerUrl.normalize("https://exa mple.com"))
    }

    @Test
    fun acceptsUppercaseScheme() {
        assertNotNull(ServerUrl.normalize("HTTPS://example.com"))
    }

    @Test
    fun detectsSecureScheme() {
        assertTrue(ServerUrl.isSecure("https://example.com"))
        assertFalse(ServerUrl.isSecure("http://10.0.2.2:8080"))
    }
}

class AccountValidatorTest {

    @Test
    fun acceptsValidUsernames() {
        assertNull(AccountValidator.username("taras"))
        assertNull(AccountValidator.username("Taras_01"))
        assertNull(AccountValidator.username("a.b-c"))
    }

    @Test
    fun rejectsBadUsernames() {
        assertNotNull(AccountValidator.username("ab"))
        assertNotNull(AccountValidator.username("a".repeat(25)))
        assertNotNull(AccountValidator.username("_start"))
        assertNotNull(AccountValidator.username("тарас"))
        assertNotNull(AccountValidator.username("with space"))
    }

    @Test
    fun checksPasswordLength() {
        assertNotNull(AccountValidator.password("1234567"))
        assertNull(AccountValidator.password("12345678"))
        assertNotNull(AccountValidator.password("x".repeat(129)))
    }

    @Test
    fun checksNicknameLength() {
        assertNotNull(AccountValidator.nickname("я"))
        assertNull(AccountValidator.nickname("Тарас"))
        assertNotNull(AccountValidator.nickname("н".repeat(25)))
    }
}
