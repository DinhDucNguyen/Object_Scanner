package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*

class StatsRepository {

    private val api get() = RetrofitClient.api

    suspend fun getStats(): Result<StatsResponse> {
        return try {
            val response = api.getStats()
            ApiErrorParser.bodyResult(response, "Không tải được thống kê")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được thống kê"))
        }
    }

    suspend fun getAnalytics(): Result<AnalyticsResponse> {
        return try {
            val response = api.getAnalytics()
            ApiErrorParser.bodyResult(response, "Không tải được thống kê")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được thống kê"))
        }
    }
}
