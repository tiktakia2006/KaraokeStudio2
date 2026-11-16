package com.example.karaokestudio.data

import com.example.karaokestudio.data.remote.ApiClient
import com.example.karaokestudio.data.remote.ApiErrorMessages
import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.ChangeDto
import com.example.karaokestudio.data.remote.SessionDto
import com.example.karaokestudio.data.remote.TokenStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

private class MemoryTokens(var url: String) : TokenStore {
    var access: String? = null
    var refresh: String? = null
    var cleared = 0

    override suspend fun serverUrl(): String = url
    override suspend fun accessToken(): String? = access
    override suspend fun refreshToken(): String? = refresh
    override suspend fun saveSession(session: SessionDto) {
        access = session.token
        refresh = session.refreshToken
    }

    override suspend fun clearSession() {
        access = null
        refresh = null
        cleared++
    }
}

class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var tokens: MemoryTokens
    private lateinit var client: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokens = MemoryTokens(server.url("/").toString().trimEnd('/'))
        client = ApiClient(ApiClient.defaultHttpClient(), tokens)
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (e: IllegalArgumentException) {
            return
        }
    }

    private fun json(code: Int, body: String, vararg headers: Pair<String, String>): MockResponse {
        val response = MockResponse().setResponseCode(code).setBody(body).addHeader("Content-Type", "application/json")
        headers.forEach { response.addHeader(it.first, it.second) }
        return response
    }

    private val sessionJson = """{"token":"access-1","refreshToken":"refresh-1","expiresIn":3600,"user":{"id":7,"username":"taras","nickname":"Тарас","role":"user"}}"""

    private fun takeRequest() = server.takeRequest(2, TimeUnit.SECONDS)!!

    @Test
    fun loginStoresTokens() = runTest {
        server.enqueue(json(200, sessionJson))

        val session = client.login("taras", "secret-password")

        assertEquals("Тарас", session.user.nickname)
        assertEquals("access-1", tokens.access)
        assertEquals("refresh-1", tokens.refresh)
        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/login", request.path)
        assertNull(request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"username\":\"taras\""))
    }

    @Test
    fun wrongPasswordIsUnauthorizedWithServerMessage() = runTest {
        server.enqueue(json(401, """{"error":{"code":"unauthorized","message":"Невірне ім’я користувача або пароль"},"requestId":"r"}"""))

        try {
            client.login("taras", "bad")
            fail("очікувався виняток")
        } catch (e: ApiException.Unauthorized) {
            assertEquals("Невірне ім’я користувача або пароль", e.message)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun expiredAccessTokenIsRenewedOnceAndRequestRepeated() = runTest {
        tokens.access = "old-access"
        tokens.refresh = "refresh-0"
        server.enqueue(json(401, """{"error":{"code":"unauthorized","message":"x"}}"""))
        server.enqueue(json(200, sessionJson))
        server.enqueue(json(200, """{"changes":[],"hasMore":false,"nextSince":5}"""))

        val page = client.pull(0)

        assertEquals(5L, page.nextSince)
        assertEquals("access-1", tokens.access)
        val first = takeRequest()
        val refresh = takeRequest()
        val retry = takeRequest()
        assertEquals("Bearer old-access", first.getHeader("Authorization"))
        assertEquals("/api/v1/auth/refresh", refresh.path)
        assertTrue(refresh.body.readUtf8().contains("refresh-0"))
        assertEquals("Bearer access-1", retry.getHeader("Authorization"))
        assertEquals("/api/v1/sync/pull?since=0", retry.path)
    }

    @Test
    fun failedRefreshClearsSessionAndReportsUnauthorized() = runTest {
        tokens.access = "old-access"
        tokens.refresh = "refresh-0"
        server.enqueue(json(401, """{"error":{"code":"unauthorized","message":"x"}}"""))
        server.enqueue(json(401, """{"error":{"code":"unauthorized","message":"Сесія недійсна"}}"""))

        try {
            client.pull(0)
            fail("очікувався виняток")
        } catch (e: ApiException.Unauthorized) {
            assertTrue(e.message!!.isNotBlank())
        }
        assertNull(tokens.access)
        assertNull(tokens.refresh)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun withoutRefreshTokenUnauthorizedIsReportedImmediately() = runTest {
        tokens.access = "old-access"
        server.enqueue(json(401, """{"error":{"code":"unauthorized","message":"Потрібна авторизація"}}"""))

        try {
            client.pull(0)
            fail("очікувався виняток")
        } catch (e: ApiException.Unauthorized) {
            assertEquals("Потрібна авторизація", e.message)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rateLimitCarriesRetryAfter() = runTest {
        server.enqueue(json(429, """{"error":{"code":"rate_limited","message":"Забагато запитів"}}""", "Retry-After" to "17"))

        try {
            client.fetchSongs(0, 50)
            fail("очікувався виняток")
        } catch (e: ApiException.RateLimited) {
            assertEquals(17L, e.retryAfterSeconds)
            assertTrue(e.isRetryable)
        }
    }

    @Test
    fun clientErrorKeepsStatusAndCode() = runTest {
        server.enqueue(json(409, """{"error":{"code":"username_taken","message":"Таке ім’я вже зайняте"}}"""))

        try {
            client.register("taras", "secret-password", null)
            fail("очікувався виняток")
        } catch (e: ApiException.Client) {
            assertEquals(409, e.status)
            assertEquals("username_taken", e.code)
            assertFalse(e.isRetryable)
            assertEquals("Таке ім'я користувача вже зайняте.", ApiErrorMessages.describe(e))
        }
    }

    @Test
    fun serverErrorIsRetryable() = runTest {
        server.enqueue(json(503, "<html>unavailable</html>"))

        try {
            client.fetchSongs(0, 50)
            fail("очікувався виняток")
        } catch (e: ApiException.Server) {
            assertEquals(503, e.status)
            assertTrue(e.isRetryable)
        }
    }

    @Test
    fun connectionFailureBecomesNoNetwork() = runTest {
        server.shutdown()

        try {
            client.fetchSongs(0, 50)
            fail("очікувався виняток")
        } catch (e: ApiException.NoNetwork) {
            assertTrue(e.isRetryable)
        }
    }

    @Test
    fun garbageBodyIsMalformed() = runTest {
        server.enqueue(json(200, "not json at all"))

        try {
            client.fetchSongs(0, 50)
            fail("очікувався виняток")
        } catch (e: ApiException.Malformed) {
            assertFalse(e.isRetryable)
        }
    }

    @Test
    fun catalogRequestIsPublicAndParsesPage() = runTest {
        tokens.access = "token"
        server.enqueue(
            json(
                200,
                """{"items":[{"slug":"morning-wave","title":"Ранок","artist":"Hurt","durationMs":48000,"genre":"Поп","lyrics":"","audioSource":"demo:morning-wave","coverUrl":null,"deleted":false,"updatedAt":11},{"slug":"gone","deleted":true,"updatedAt":12}],"hasMore":true,"nextSince":12}"""
            )
        )

        val page = client.fetchSongs(3, 200)

        assertTrue(page.hasMore)
        assertEquals(12L, page.nextSince)
        assertEquals("Ранок", page.items[0].title)
        assertTrue(page.items[1].deleted)
        val request = takeRequest()
        assertEquals("/api/v1/songs?since=3&limit=200", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun pushSendsOnlyFilledFields() = runTest {
        tokens.access = "token"
        server.enqueue(json(200, """{"results":[{"index":0,"status":"applied"},{"index":1,"status":"rejected","reason":"unknown_song"}],"applied":1,"serverTime":99}"""))

        val response = client.push(
            listOf(
                ChangeDto(type = "favorite", op = "upsert", at = 5, songSlug = "neon-dance"),
                ChangeDto(type = "performance", op = "upsert", at = 6, songSlug = "x", clientId = "c-1", performedAt = 1, durationMs = 2, score = null)
            )
        )

        assertEquals(listOf("applied", "rejected"), response.results.map { it.status })
        assertEquals("unknown_song", response.results[1].reason)
        val body = takeRequest().body.readUtf8()
        assertTrue(body.contains("\"songSlug\":\"neon-dance\""))
        assertFalse(body.contains("\"score\""))
        assertFalse(body.contains("null"))
    }

    @Test
    fun lyricsQueryIsEncoded() = runTest {
        tokens.access = "token"
        server.enqueue(json(200, """{"found":false,"attribution":{"name":"LRCLIB","url":"https://lrclib.net"},"cached":false}"""))

        val result = client.lyrics("Не пали", "Океан Ельзи & друзі", 215)

        assertFalse(result.found)
        val path = takeRequest().path!!
        assertTrue(path.startsWith("/api/v1/integrations/lyrics?title="))
        assertTrue(path.contains("%D0%9D%D0%B5"))
        assertTrue(path.contains("%26"))
        assertTrue(path.endsWith("&durationSec=215"))
    }

    @Test
    fun logoutAlwaysClearsLocalSession() = runTest {
        tokens.access = "a"
        tokens.refresh = "r"
        server.enqueue(MockResponse().setResponseCode(204))

        client.logout()

        assertNull(tokens.access)
        assertNull(tokens.refresh)
        assertEquals("/api/v1/auth/logout", takeRequest().path)
    }

    @Test
    fun logoutWorksOffline() = runTest {
        tokens.access = "a"
        tokens.refresh = "r"
        server.shutdown()

        client.logout()

        assertNull(tokens.access)
    }

    @Test
    fun describesErrorsInUkrainian() {
        assertTrue(ApiErrorMessages.describe(ApiException.NoNetwork()).contains("з'єднання"))
        assertTrue(ApiErrorMessages.describe(ApiException.RateLimited(12, "x")).contains("12"))
        assertTrue(ApiErrorMessages.describe(ApiException.Server(500, "x")).contains("500"))
        assertTrue(ApiErrorMessages.describe(ApiException.Client(423, "account_locked", "x")).contains("заблоковано"))
    }

    @Test
    fun globalLeaderboardParsesPlayersAndMe() = runTest {
        server.enqueue(
            json(
                200,
                """{"items":[{"rank":1,"nickname":"Анна","totalScore":170,"songsCount":2,"bestScore":90,"attempts":3}],"total":4,"me":{"rank":3,"nickname":"Я","totalScore":100,"songsCount":1,"bestScore":100,"attempts":1}}"""
            )
        )

        val board = client.globalLeaderboard(50)

        assertEquals(4, board.total)
        assertEquals("Анна", board.items[0].nickname)
        assertEquals(170, board.items[0].totalScore)
        assertEquals(3, board.me!!.rank)
        val request = takeRequest()
        assertEquals("/api/v1/leaderboard?limit=50", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun uploadRecordingSendsFileBodyAndExplainsRefusal() = runTest {
        tokens.access = "token"
        val file = java.io.File.createTempFile("rec", ".m4a")
        file.writeBytes(byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 1, 2, 3, 4))
        server.enqueue(json(201, """{"clientId":"c-1","mime":"audio/mp4","sizeBytes":12,"sha256":"abc","createdAt":5}"""))
        server.enqueue(json(409, """{"error":{"code":"performance_not_synced","message":"Спочатку синхронізуйте виконання"}}"""))

        val saved = client.uploadRecording("c-1", file)

        assertEquals(12L, saved.sizeBytes)
        val first = takeRequest()
        assertEquals("PUT", first.method)
        assertEquals("/api/v1/recordings/c-1", first.path)
        assertEquals("audio/mp4", first.getHeader("Content-Type"))
        assertEquals(12L, first.bodySize)
        try {
            client.uploadRecording("c-2", file)
            fail("очікувався виняток")
        } catch (e: ApiException.Client) {
            assertEquals("performance_not_synced", e.code)
            assertTrue(ApiErrorMessages.describe(e).contains("синхронізовано"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun deleteAccountSendsPasswordAndClearsSession() = runTest {
        tokens.access = "a"
        tokens.refresh = "r"
        server.enqueue(MockResponse().setResponseCode(204))

        client.deleteAccount("my-password")

        val request = takeRequest()
        assertEquals("DELETE", request.method)
        assertTrue(request.body.readUtf8().contains("my-password"))
        assertNull(tokens.access)
    }
}
