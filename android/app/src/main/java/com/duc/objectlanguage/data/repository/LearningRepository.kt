package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.api.RetrofitClient

class LearningRepository {

    private val api get() = RetrofitClient.api

    suspend fun addToLearning(translationId: Int): Result<String> {
        return try {
            val response = api.addToLearning(translationId)
            response.body()?.close()
            if (response.isSuccessful) Result.success("Đã thêm vào danh sách học!")
            else Result.failure(Exception("Lỗi ${response.code()}: ${response.message()}"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }
}
