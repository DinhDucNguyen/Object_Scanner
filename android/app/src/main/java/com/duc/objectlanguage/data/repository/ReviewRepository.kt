package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*
import kotlinx.coroutines.CancellationException

class ReviewRepository {

    private val api get() = RetrofitClient.api

    suspend fun getDueCount(): Result<Int> {
        return try {
            val response = api.getDueCount()
            if (response.isSuccessful) Result.success(response.body()?.count ?: 0)
            else Result.success(0)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.success(0)
        }
    }

    suspend fun getDueReviews(): Result<List<ReviewCardResponse>> {
        return try {
            val response = api.getDueReviews()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Không tải được dữ liệu ôn tập"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được dữ liệu ôn tập"))
        }
    }

    suspend fun submitReview(progressId: Int, quality: Int): Result<ReviewResult> {
        return try {
            val response = api.submitReview(progressId, ReviewRequest(quality))
            ApiErrorParser.bodyResult(response, "Không gửi được kết quả ôn tập")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không gửi được kết quả ôn tập"))
        }
    }

    suspend fun getMasteredWords(): Result<List<ReviewCardResponse>> {
        return try {
            val response = api.getMasteredWords()
            ApiErrorParser.bodyResult(response, "Không tải được danh sách từ thành thạo")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được danh sách từ thành thạo"))
        }
    }
}
