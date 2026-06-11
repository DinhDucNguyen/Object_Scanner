package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*
import kotlinx.coroutines.CancellationException

class DictionaryRepository {

    private val api get() = RetrofitClient.api

    suspend fun lookupWord(word: String, fromLang: String = "en", toLang: String = "en"): Result<DictionaryResponse> {
        return try {
            val response = api.lookupWord(word, fromLang, toLang)
            ApiErrorParser.bodyResult(response, "Không tìm thấy từ")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tra được từ lúc này"))
        }
    }

    suspend fun translate(text: String, fromLang: String, toLang: String): Result<TranslateResponse> {
        return try {
            val response = api.translate(TranslateRequest(text, fromLang, toLang))
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else {
                val rawError = response.errorBody()?.string()
                val message = runCatching {
                    org.json.JSONObject(rawError ?: "").optString("detail").takeIf { it.isNotBlank() }
                }.getOrNull() ?: rawError ?: "Không thể dịch lúc này"
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(Exception("Không thể kết nối tới server"))
        }
    }

    suspend fun getDictionaryHistory(limit: Int = 30): Result<List<DictionaryHistoryItem>> {
        return try {
            val response = api.getDictionaryHistory(limit)
            ApiErrorParser.bodyResult(response, "Không tải được lịch sử")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được lịch sử"))
        }
    }

    suspend fun deleteHistoryItem(id: Int): Result<Unit> {
        return try {
            val response = api.deleteHistoryItem(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Lỗi xóa lịch sử"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không xoá được lịch sử"))
        }
    }
}
