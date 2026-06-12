package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*

class ExploreRepository {

    private val api get() = RetrofitClient.api

    suspend fun getCategories(): Result<List<CategoryData>> {
        return try {
            val response = api.getCategories()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception(RepositoryText.get(R.string.repo_explore_categories_load_code, response.code())))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_explore_categories_load_error))
        }
    }

    suspend fun getObjectsByCategory(categoryId: Int): Result<List<ObjectData>> {
        return try {
            val response = api.getObjectsByCategory(categoryId)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception(RepositoryText.get(R.string.repo_explore_vocab_load_code, response.code())))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_explore_vocab_load_error))
        }
    }

    suspend fun getAllObjects(): Result<List<ObjectData>> {
        return try {
            val response = api.getObjectsByCategory()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception(RepositoryText.get(R.string.repo_explore_objects_load_code, response.code())))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_explore_objects_load_error))
        }
    }
}
