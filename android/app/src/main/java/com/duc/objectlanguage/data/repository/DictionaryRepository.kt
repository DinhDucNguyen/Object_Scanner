package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*
import kotlinx.coroutines.CancellationException

class DictionaryRepository {

    private val api get() = RetrofitClient.api

    suspend fun lookupWord(word: String, fromLang: String = "en", toLang: String = "en"): Result<DictionaryResponse> {
        return try {
            val response = api.lookupWord(word, fromLang, toLang)
            ApiErrorParser.bodyResult(response, R.string.repo_dictionary_word_not_found)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_dictionary_lookup_error))
        }
    }

    suspend fun translate(text: String, fromLang: String, toLang: String): Result<TranslateResponse> {
        return try {
            val response = api.translate(TranslateRequest(text, fromLang, toLang))
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else {
                val message = ApiErrorParser.fromResponse(response, R.string.repo_dictionary_translate_error)
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(Exception(RepositoryText.get(R.string.repo_dictionary_connect_error)))
        }
    }

    suspend fun getDictionaryHistory(limit: Int = 30): Result<List<DictionaryHistoryItem>> {
        return try {
            val response = api.getDictionaryHistory(limit)
            ApiErrorParser.bodyResult(response, R.string.repo_dictionary_history_load_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_dictionary_history_load_error))
        }
    }

    suspend fun deleteHistoryItem(id: Int): Result<Unit> {
        return try {
            val response = api.deleteHistoryItem(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(RepositoryText.get(R.string.repo_dictionary_history_delete_error)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_dictionary_history_delete_unavailable))
        }
    }
}
