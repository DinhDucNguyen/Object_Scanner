package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*

class StreakRepository {

    private val api get() = RetrofitClient.api

    suspend fun getStreak(): Result<StreakResponse> {
        return try {
            val response = api.getStreak()
            ApiErrorParser.bodyResult(response, "Không tải được chuỗi ngày học")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được streak"))
        }
    }

    suspend fun recordStreak(): Result<StreakResponse> {
        return try {
            val response = api.recordStreak()
            ApiErrorParser.bodyResult(response, "Không ghi được chuỗi ngày học")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không ghi được streak"))
        }
    }

    suspend fun getStreakCalendar(days: Int = 30): Result<StreakCalendarResponse> {
        return try {
            val response = api.getStreakCalendar(days)
            ApiErrorParser.bodyResult(response, "Không tải được lịch chuỗi ngày học")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được lịch streak"))
        }
    }

    suspend fun syncStreak(req: StreakSyncRequest): Result<StreakResponse> {
        return try {
            val response = api.syncStreak(req)
            ApiErrorParser.bodyResult(response, "Không đồng bộ được chuỗi ngày học")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không đồng bộ được streak"))
        }
    }
}
