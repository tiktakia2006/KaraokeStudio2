package com.example.karaokestudio.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.karaokestudio.KaraokeApp
import com.example.karaokestudio.data.remote.ApiException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as KaraokeApp
        return try {
            app.container.syncManager.sync()
            Result.success()
        } catch (e: ApiException) {
            if (e.isRetryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
