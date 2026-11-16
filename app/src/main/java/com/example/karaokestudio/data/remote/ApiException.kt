package com.example.karaokestudio.data.remote

sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class NoNetwork(cause: Throwable? = null) : ApiException("Немає з'єднання з сервером", cause)

    class Unauthorized(message: String) : ApiException(message)

    class Client(val status: Int, val code: String, message: String) : ApiException(message)

    class RateLimited(val retryAfterSeconds: Long, message: String) : ApiException(message)

    class Server(val status: Int, message: String) : ApiException(message)

    class Malformed(message: String, cause: Throwable? = null) : ApiException(message, cause)

    val isRetryable: Boolean
        get() = this is NoNetwork || this is Server || this is RateLimited
}
