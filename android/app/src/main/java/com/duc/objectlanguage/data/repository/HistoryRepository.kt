package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*

class HistoryRepository {

    private val api get() = RetrofitClient.api

    suspend fun getHistory(
        limit: Int = 50,
        offset: Int = 0,
        keyword: String? = null,
        period: String? = "all",
        fromDate: String? = null,
        toDate: String? = null,
    ): Result<List<HistoryItem>> {
        return try {
            val response = api.getHistory(limit, offset, keyword, period, fromDate, toDate)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Lỗi tải lịch sử"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được lịch sử"))
        }
    }

    suspend fun getHistoryDetail(scanId: Int): Result<HistoryDetail> {
        return try {
            val response = api.getHistoryDetail(scanId)
            ApiErrorParser.bodyResult(response, "Không tìm thấy lịch sử")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được chi tiết lịch sử"))
        }
    }

    suspend fun deleteHistory(scanId: Int): Result<String> {
        return try {
            val response = api.deleteHistory(scanId)
            response.body()?.close()
            if (response.isSuccessful) Result.success("Đã xoá lịch sử quét")
            else Result.failure(Exception("Xoá lịch sử thất bại"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không xoá được lịch sử"))
        }
    }

    suspend fun getTranslationsByCode(objectCode: String): Result<List<TranslationResponse>> {
        return try {
            val response = api.getTranslations(objectCode)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Không tìm thấy từ vựng"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được từ vựng"))
        }
    }
}
