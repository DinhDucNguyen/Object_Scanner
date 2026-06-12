package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*

class StatsRepository {

    private val api get() = RetrofitClient.api

    suspend fun getStats(): Result<StatsResponse> {
        return try {
            val response = api.getStats()
            ApiErrorParser.bodyResult(response, R.string.repo_stats_load_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_stats_load_error))
        }
    }

    suspend fun getAnalytics(): Result<AnalyticsResponse> {
        return try {
            val response = api.getAnalytics()
            ApiErrorParser.bodyResult(response, R.string.repo_stats_load_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_stats_load_error))
        }
    }
}
