package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.api.RetrofitClient

class LearningRepository {

    private val api get() = RetrofitClient.api

    suspend fun addToLearning(translationId: Int): Result<String> {
        return try {
            val response = api.addToLearning(translationId)
            response.body()?.close()
            if (response.isSuccessful) Result.success(RepositoryText.get(R.string.repo_learning_added))
            else Result.failure(Exception(RepositoryText.get(R.string.repo_error_code, response.code(), response.message())))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }
}
