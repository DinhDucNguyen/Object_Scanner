package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ScanRepository {

    private val api get() = RetrofitClient.api

    suspend fun scanByCode(objectCode: String, confidence: Float): Result<ScanResponse> {
        return try {
            val response = api.scanByCode(ScanRequest(objectCode, confidence))
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else {
                val errorStr = ApiErrorParser.fromResponse(response, R.string.repo_server_no_error_detail)
                Result.failure(Exception(RepositoryText.get(R.string.repo_scan_object_not_found, errorStr, response.code())))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_scan_unavailable))
        }
    }

    suspend fun scanByImage(imageBytes: ByteArray): Result<ScanResponse> {
        return try {
            val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "capture.jpg", body)
            val response = api.scanByImage(part)
            val responseBody = response.body()
            if (response.isSuccessful && responseBody != null) Result.success(responseBody)
            else {
                val errorStr = ApiErrorParser.fromResponse(response, R.string.repo_server_no_error_detail)
                Result.failure(Exception(RepositoryText.get(R.string.repo_scan_object_not_recognized, errorStr, response.code())))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_scan_unavailable))
        }
    }

    suspend fun saveLichSuQuet(
        objectCode: String,
        confidence: Float,
        imageBytes: ByteArray?,
        trainingSource: String = "yolo",
    ): Result<LichSuQuetResponse> {
        return try {
            val codePart = objectCode.toRequestBody("text/plain".toMediaTypeOrNull())
            val confidencePart = confidence.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val trainingSourcePart = trainingSource.toRequestBody("text/plain".toMediaTypeOrNull())
            val imagePart = imageBytes?.let {
                val body = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("image", "scan.jpg", body)
            }
            val response = api.saveLichSuQuet(codePart, confidencePart, trainingSourcePart, imagePart)
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else Result.failure(Exception(RepositoryText.get(R.string.repo_scan_save_history_failed, response.code())))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_scan_save_history_unavailable))
        }
    }

    suspend fun getTtsAudio(word: String, lang: String = "en"): ByteArray? {
        return try {
            val response = api.getTts(word, lang)
            if (response.isSuccessful) withContext(Dispatchers.IO) { response.body()?.bytes() } else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun getAudioByUrl(audioUrl: String): ByteArray? {
        return try {
            val response = api.getAudioByUrl(normalizeAudioUrl(audioUrl))
            if (response.isSuccessful) withContext(Dispatchers.IO) { response.body()?.bytes() } else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun getExamples(word: String, lang: String = "en"): List<String> {
        return try {
            val response = api.getExamples(word, lang)
            if (response.isSuccessful) response.body()?.sentences?.take(3) ?: emptyList()
            else emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun normalizeAudioUrl(audioUrl: String): String {
        val trimmed = audioUrl.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else trimmed.trimStart('/')
    }
}
