package com.duc.objectlanguage.data.api

import com.duc.objectlanguage.BuildConfig
import com.duc.objectlanguage.data.local.ApiConfig
import com.duc.objectlanguage.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var tokenManager: TokenManager? = null
    private var _api: ApiService? = null
    private var _collectionApi: CollectionApiService? = null

    fun init(tm: TokenManager) {
        tokenManager = tm
        rebuild()
    }

    fun rebuild() {
        _api = null
        _collectionApi = null
    }

    val api: ApiService
        get() {
            return _api ?: buildRetrofit().create(ApiService::class.java).also {
                _api = it
            }
        }

    val collectionApi: CollectionApiService
        get() {
            return _collectionApi ?: buildRetrofit().create(CollectionApiService::class.java).also {
                _collectionApi = it
            }
        }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(validBaseUrl())
            .client(buildClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun validBaseUrl(): String {
        val baseUrl = ApiConfig.baseUrl
        if (baseUrl.isEmpty()) {
            throw IllegalStateException("Dia chi server chua duoc cau hinh trong local.properties.")
        }
        return baseUrl
    }

    private fun buildClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = tokenManager?.accessToken
            val request = if (!token.isNullOrEmpty()) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(refreshAuthenticator())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun refreshAuthenticator() = okhttp3.Authenticator { _, response ->
        if (response.request.header("Authorization") == null || responseCount(response) >= 2) {
            return@Authenticator null
        }

        try {
            val refreshToken = tokenManager?.refreshToken
            if (!refreshToken.isNullOrEmpty()) {
                val refreshApi = Retrofit.Builder()
                    .baseUrl(validBaseUrl())
                    .client(OkHttpClient.Builder().build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)

                val refreshResponse = refreshApi
                    .refreshTokenSync(com.duc.objectlanguage.data.model.RefreshRequest(refreshToken))
                    .execute()

                val newTokens = refreshResponse.body()
                if (refreshResponse.isSuccessful && newTokens != null) {
                    tokenManager?.accessToken = newTokens.accessToken
                    tokenManager?.refreshToken = newTokens.refreshToken

                    return@Authenticator response.request.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .build()
                }
                if (refreshResponse.code() == 401 || refreshResponse.code() == 403) {
                    tokenManager?.clear()
                }
                return@Authenticator null
            }
        } catch (_: Exception) {
            // Keep credentials on transient network/server errors.
            return@Authenticator null
        }

        null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
