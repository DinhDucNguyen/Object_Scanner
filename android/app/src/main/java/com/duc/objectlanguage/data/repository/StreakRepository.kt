package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*

class StreakRepository {

    private val api get() = RetrofitClient.api

    suspend fun getStreak(): Result<StreakResponse> {
        return try {
            val response = api.getStreak()
            ApiErrorParser.bodyResult(response, R.string.repo_streak_load_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_streak_load_error))
        }
    }

    suspend fun recordStreak(): Result<StreakResponse> {
        return try {
            val response = api.recordStreak()
            ApiErrorParser.bodyResult(response, R.string.repo_streak_record_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_streak_record_error))
        }
    }

    suspend fun getStreakCalendar(days: Int = 30): Result<StreakCalendarResponse> {
        return try {
            val response = api.getStreakCalendar(days)
            ApiErrorParser.bodyResult(response, R.string.repo_streak_calendar_load_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_streak_calendar_load_error))
        }
    }

    suspend fun syncStreak(req: StreakSyncRequest): Result<StreakResponse> {
        return try {
            val response = api.syncStreak(req)
            ApiErrorParser.bodyResult(response, R.string.repo_streak_sync_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_streak_sync_error))
        }
    }
}
