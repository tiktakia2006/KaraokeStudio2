package com.example.karaokestudio.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface RemoteApi {
    suspend fun fetchSongs(since: Long, limit: Int): SongPageDto
    suspend fun push(changes: List<ChangeDto>): PushResponse
    suspend fun pull(since: Long): PullResponse
}

class ApiClient(
    private val http: OkHttpClient,
    private val tokens: TokenStore,
    private val json: Json = ApiJson
) : RemoteApi {

    private val refreshLock = Mutex()

    suspend fun register(username: String, password: String, nickname: String?): SessionDto {
        val body = json.encodeToString(CredentialsRequest.serializer(), CredentialsRequest(username, password, nickname))
        val session = post("/api/v1/auth/register", body, SessionDto.serializer(), authorized = false)
        tokens.saveSession(session)
        return session
    }

    suspend fun login(username: String, password: String): SessionDto {
        val body = json.encodeToString(CredentialsRequest.serializer(), CredentialsRequest(username, password))
        val session = post("/api/v1/auth/login", body, SessionDto.serializer(), authorized = false)
        tokens.saveSession(session)
        return session
    }

    suspend fun logout() {
        val refresh = tokens.refreshToken()
        if (refresh != null) {
            try {
                val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
                execute(Request.Builder().post(body.toJsonBody()), "/api/v1/auth/logout", authorized = false)
            } catch (e: ApiException) {
                tokens.clearSession()
                return
            }
        }
        tokens.clearSession()
    }

    suspend fun me(): UserDto = get("/api/v1/auth/me", UserEnvelope.serializer()).user

    suspend fun updateNickname(nickname: String): UserDto {
        val body = json.encodeToString(NicknameRequest.serializer(), NicknameRequest(nickname))
        return send("PATCH", "/api/v1/auth/me", body, UserEnvelope.serializer()).user
    }

    suspend fun changePassword(current: String, new: String): SessionDto {
        val body = json.encodeToString(PasswordChangeRequest.serializer(), PasswordChangeRequest(current, new))
        val session = post("/api/v1/auth/password", body, SessionDto.serializer())
        tokens.saveSession(session)
        return session
    }

    suspend fun deleteAccount(password: String) {
        val body = json.encodeToString(PasswordConfirmRequest.serializer(), PasswordConfirmRequest(password))
        execute(Request.Builder().delete(body.toJsonBody()), "/api/v1/auth/me", authorized = true)
        tokens.clearSession()
    }

    override suspend fun fetchSongs(since: Long, limit: Int): SongPageDto =
        get("/api/v1/songs?since=$since&limit=$limit", SongPageDto.serializer(), authorized = false)

    override suspend fun push(changes: List<ChangeDto>): PushResponse {
        val body = json.encodeToString(PushRequest.serializer(), PushRequest(changes))
        return post("/api/v1/sync/push", body, PushResponse.serializer())
    }

    override suspend fun pull(since: Long): PullResponse =
        get("/api/v1/sync/pull?since=$since", PullResponse.serializer())

    suspend fun leaderboard(slug: String, limit: Int): LeaderboardDto =
        get("/api/v1/songs/${encodeSegment(slug)}/leaderboard?limit=$limit", LeaderboardDto.serializer(), authorized = tokens.accessToken() != null)

    suspend fun globalLeaderboard(limit: Int): GlobalLeaderboardDto =
        get("/api/v1/leaderboard?limit=$limit", GlobalLeaderboardDto.serializer(), authorized = tokens.accessToken() != null)

    suspend fun lyrics(title: String, artist: String?, durationSec: Int?): LyricsDto {
        val query = StringBuilder("title=").append(encodeQuery(title))
        if (!artist.isNullOrBlank()) query.append("&artist=").append(encodeQuery(artist))
        if (durationSec != null && durationSec > 0) query.append("&durationSec=").append(durationSec)
        return get("/api/v1/integrations/lyrics?$query", LyricsDto.serializer())
    }

    suspend fun metadata(query: String, limit: Int): MetadataDto =
        get("/api/v1/integrations/metadata?q=${encodeQuery(query)}&limit=$limit", MetadataDto.serializer())

    suspend fun uploadRecording(clientId: String, file: File): RecordingDto {
        val body = file.asRequestBody("audio/mp4".toMediaType())
        val text = execute(Request.Builder().put(body), "/api/v1/recordings/${encodeSegment(clientId)}", authorized = true)
        return decode(text, RecordingDto.serializer())
    }

    suspend fun deleteRecording(clientId: String) {
        execute(Request.Builder().delete(), "/api/v1/recordings/${encodeSegment(clientId)}", authorized = true)
    }

    private suspend fun <T> get(path: String, serializer: KSerializer<T>, authorized: Boolean = true): T =
        decode(execute(Request.Builder().get(), path, authorized), serializer)

    private suspend fun <T> post(path: String, body: String, serializer: KSerializer<T>, authorized: Boolean = true): T =
        decode(execute(Request.Builder().post(body.toJsonBody()), path, authorized), serializer)

    private suspend fun <T> send(method: String, path: String, body: String, serializer: KSerializer<T>): T =
        decode(execute(Request.Builder().method(method, body.toJsonBody()), path, true), serializer)

    private fun <T> decode(text: String, serializer: KSerializer<T>): T = try {
        json.decodeFromString(serializer, text)
    } catch (e: Exception) {
        throw ApiException.Malformed("Не вдалося розібрати відповідь сервера", e)
    }

    private suspend fun execute(builder: Request.Builder, path: String, authorized: Boolean): String {
        val url = (tokens.serverUrl() + path).toHttpUrlOrNull()
            ?: throw ApiException.Client(0, "bad_url", "Некоректна адреса сервера")
        var usedToken = if (authorized) tokens.accessToken() else null
        var refreshed = false
        while (true) {
            builder.url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
            if (usedToken != null) builder.header("Authorization", "Bearer $usedToken") else builder.removeHeader("Authorization")
            val reply = readReply(http.newCall(builder.build()).await())
            if (reply.code in 200..299) return reply.text
            if (reply.code == 401 && authorized && !refreshed && tokens.refreshToken() != null) {
                refreshed = true
                usedToken = renewAccessToken(usedToken)
                continue
            }
            throw failure(reply.code, reply.retryAfter, reply.text)
        }
    }

    private suspend fun readReply(response: Response): Reply = response.use { result ->
        Reply(result.code, result.header("Retry-After"), withContext(Dispatchers.IO) { result.body?.string().orEmpty() })
    }

    private suspend fun renewAccessToken(failedToken: String?): String = refreshLock.withLock {
        val current = tokens.accessToken()
        if (current != null && current != failedToken) current else requestNewSession().token
    }

    private suspend fun requestNewSession(): SessionDto {
        val refresh = tokens.refreshToken() ?: throw ApiException.Unauthorized("Потрібно увійти в обліковий запис.")
        val url = (tokens.serverUrl() + "/api/v1/auth/refresh").toHttpUrlOrNull()
            ?: throw ApiException.Client(0, "bad_url", "Некоректна адреса сервера")
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
        val request = Request.Builder().url(url).post(body.toJsonBody())
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        val reply = readReply(http.newCall(request).await())
        if (reply.code in 200..299) {
            val session = decode(reply.text, SessionDto.serializer())
            tokens.saveSession(session)
            return session
        }
        if (reply.code == 401 || reply.code == 400) {
            tokens.clearSession()
            throw ApiException.Unauthorized("Сесію завершено. Увійдіть знову.")
        }
        throw failure(reply.code, reply.retryAfter, reply.text)
    }

    private class Reply(val code: Int, val retryAfter: String?, val text: String)

    private fun failure(status: Int, retryAfter: String?, text: String): ApiException {
        val envelope = try {
            json.decodeFromString(ErrorEnvelope.serializer(), text)
        } catch (e: Exception) {
            ErrorEnvelope()
        }
        val message = envelope.error.message.ifBlank { "Помилка $status" }
        return when {
            status == 401 -> ApiException.Unauthorized(message)
            status == 429 -> ApiException.RateLimited(retryAfter?.toLongOrNull() ?: 1L, message)
            status in 400..499 -> ApiException.Client(status, envelope.error.code, message)
            else -> ApiException.Server(status, message)
        }
    }

    private fun String.toJsonBody(): RequestBody = toRequestBody(JSON_MEDIA_TYPE)

    private fun encodeSegment(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun encodeQuery(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(ApiException.NoNetwork(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val USER_AGENT = "KaraokeStudio-Android/1.0"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build()
    }
}
