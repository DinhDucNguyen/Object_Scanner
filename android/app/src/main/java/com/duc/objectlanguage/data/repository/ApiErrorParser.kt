package com.duc.objectlanguage.data.repository

import androidx.annotation.StringRes
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import retrofit2.Response

internal object ApiErrorParser {

    fun fromResponse(response: Response<*>, @StringRes fallbackRes: Int, vararg args: Any): String {
        return fromResponse(response, RepositoryText.get(fallbackRes, *args))
    }

    fun fromResponse(response: Response<*>, fallback: String): String {
        val raw = response.errorBody()?.string()
        val detail = parseDetail(raw)
        if (detail.isNullOrBlank()) return "$fallback (${response.code()})"
        return if (RepositoryText.language() == "vi") detail else "$fallback (${response.code()})"
    }

    fun userFacing(e: Exception, @StringRes fallbackRes: Int, vararg args: Any): Exception {
        return userFacing(e, RepositoryText.get(fallbackRes, *args))
    }

    fun userFacing(e: Exception, fallback: String): Exception {
        if (e is CancellationException) throw e
        return Exception(fallback)
    }

    fun <T> bodyResult(response: Response<T>, @StringRes fallbackRes: Int, vararg args: Any): Result<T> {
        return bodyResult(response, RepositoryText.get(fallbackRes, *args))
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
