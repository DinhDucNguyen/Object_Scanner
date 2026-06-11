package com.duc.objectlanguage.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import retrofit2.Response

internal object ApiErrorParser {

    fun fromResponse(response: Response<*>, fallback: String): String {
        val raw = response.errorBody()?.string()
        val detail = parseDetail(raw)
        return if (detail.isNullOrBlank()) "$fallback (${response.code()})" else detail
    }

    fun userFacing(e: Exception, fallback: String): Exception {
        if (e is CancellationException) throw e
        return Exception(fallback)
    }

    fun <T> bodyResult(response: Response<T>, fallback: String): Result<T> {
        val body = response.body()
        return if (response.isSuccessful && body != null) Result.success(body)
        else Result.failure(Exception(fromResponse(response, fallback)))
    }

    private fun parseDetail(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JsonParser.parseString(raw)
            if (!json.isJsonObject) return raw
            val obj = json.asJsonObject
            val detail = obj.get("detail")
            when {
                detail == null -> obj.get("message")?.asString ?: raw
                detail.isJsonPrimitive -> detail.asString
                detail.isJsonArray && detail.asJsonArray.size() > 0 -> {
                    val first = detail.asJsonArray[0]
                    if (first.isJsonObject) first.asJsonObject.get("msg")?.asString ?: first.toString()
                    else first.toString()
                }
                else -> detail.toString()
            }
        } catch (_: Exception) { raw }
    }
}
