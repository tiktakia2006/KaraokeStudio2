package com.example.karaokestudio.data.online

import com.example.karaokestudio.data.remote.ApiClient
import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.RecordingDto
import com.example.karaokestudio.data.remote.SessionStore
import com.example.karaokestudio.data.sync.SyncManager
import java.io.File

class RecordingUploader(
    private val api: ApiClient,
    private val syncManager: SyncManager,
    private val session: SessionStore
) {

    suspend fun upload(clientId: String, filePath: String): RecordingDto {
        if (!session.current().signedIn) {
            throw ApiException.Unauthorized("Увійдіть в обліковий запис, щоб надіслати запис.")
        }
        val file = File(filePath)
        if (!file.exists()) throw ApiException.Client(0, "no_file", "Файл запису не знайдено на пристрої.")
        syncManager.sync()
        return api.uploadRecording(clientId, file)
    }

    suspend fun remove(clientId: String) {
        api.deleteRecording(clientId)
    }
}
