package com.example.karaokestudio.data.remote

interface TokenStore {
    suspend fun serverUrl(): String
    suspend fun accessToken(): String?
    suspend fun refreshToken(): String?
    suspend fun saveSession(session: SessionDto)
    suspend fun clearSession()
}
