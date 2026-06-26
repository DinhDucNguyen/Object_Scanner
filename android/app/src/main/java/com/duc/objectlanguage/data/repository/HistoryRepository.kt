package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*

class HistoryRepository {

    private val api get() = RetrofitClient.api

    suspend fun getHistory(
        limit: Int = 50,
        offset: Int = 0,
        keyword: String? = null,
        period: String = "all",
        fromDate: String? = null,
        toDate: String? = null
    ): Result<HistoryPaginatedResponse> {
        return try {
            val response = api.getHistory(limit, offset, keyword, period, fromDate, toDate)
            if (response.isSuccessful) Result.success(response.body() ?: HistoryPaginatedResponse(0, emptyList()))
            else Result.failure(Exception(RepositoryText.get(R.string.repo_history_load_error)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_dictionary_history_load_error))
        }
    }

    suspend fun getHistoryDetail(scanId: Int): Result<HistoryDetail> {
        return try {
            val response = api.getHistoryDetail(scanId)
            ApiErrorParser.bodyResult(response, R.string.repo_history_not_found)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_history_detail_load_error))
        }
    }

    suspend fun deleteHistory(scanId: Int): Result<String> {
        return try {
            val response = api.deleteHistory(scanId)
            response.body()?.close()
            if (response.isSuccessful) Result.success(RepositoryText.get(R.string.repo_history_delete_success))
            else Result.failure(Exception(RepositoryText.get(R.string.repo_history_delete_failed)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_dictionary_history_delete_unavailable))
        }
    }

    suspend fun getTranslationsByCode(objectCode: String): Result<List<TranslationResponse>> {
        return try {
            val response = api.getTranslations(objectCode)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception(RepositoryText.get(R.string.repo_history_vocab_not_found)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_history_vocab_load_error))
        }
    }
}
