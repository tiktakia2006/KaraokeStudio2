package com.example.karaokestudio.data.online

import com.example.karaokestudio.data.remote.ApiClient
import com.example.karaokestudio.data.remote.GlobalLeaderboardDto

class RankingRepository(private val api: ApiClient) {

    suspend fun global(): GlobalLeaderboardDto = api.globalLeaderboard(LIMIT)

    private companion object {
        const val LIMIT = 50
    }
}
