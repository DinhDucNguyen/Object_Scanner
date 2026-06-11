package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*

class ExploreRepository {

    private val api get() = RetrofitClient.api

    suspend fun getCategories(): Result<List<CategoryData>> {
        return try {
            val response = api.getCategories()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Không tải được chủ đề (${response.code()})"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được chủ đề"))
        }
    }

    suspend fun getObjectsByCategory(categoryId: Int): Result<List<ObjectData>> {
        return try {
            val response = api.getObjectsByCategory(categoryId)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Không tải được từ vựng (${response.code()})"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được từ vựng"))
        }
    }

    suspend fun getAllObjects(): Result<List<ObjectData>> {
        return try {
            val response = api.getObjectsByCategory()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Không tải được danh sách vật thể (${response.code()})"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được danh sách vật thể"))
        }
    }
}
