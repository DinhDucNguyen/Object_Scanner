package com.duc.objectlanguage.data.api

import com.duc.objectlanguage.data.local.ApiConfig
import com.duc.objectlanguage.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var tokenManager: TokenManager? = null
    private var _api: ApiService? = null

    fun init(tm: TokenManager) {
        tokenManager = tm
        _api = null // rebuild
    }

    /**
     * Xây dựng lại Retrofit instance với URL mới.
     * Gọi sau khi cập nhật API URL.
     */
    fun rebuild() {
        _api = null
    }

    val api: ApiService
        get() {
            if (_api == null) {
                val baseUrl = ApiConfig.baseUrl

                if (baseUrl.isEmpty()) {
                    throw IllegalStateException("Địa chỉ server chưa được cấu hình trong local.properties.")
                }

                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }

                val authInterceptor = Interceptor { chain ->
                    val original = chain.request()
                    val token = tokenManager?.accessToken
                    val request = if (!token.isNullOrEmpty()) {
                        original.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    } else original
                    chain.proceed(request)
                }

                val authenticator = okhttp3.Authenticator { _, response ->
                    if (response.request.header("Authorization") != null) {
                        try {
                            val refreshToken = tokenManager?.refreshToken
                            if (!refreshToken.isNullOrEmpty()) {
                                val tempClient = OkHttpClient.Builder().build()
                                val tempRetrofit = Retrofit.Builder()
                                    .baseUrl(baseUrl)
                                    .client(tempClient)
                                    .addConverterFactory(GsonConverterFactory.create())
                                    .build()
                                    .create(ApiService::class.java)

                                val refreshResponse = kotlinx.coroutines.runBlocking {
                                    tempRetrofit.refreshToken(com.duc.objectlanguage.data.model.RefreshRequest(refreshToken))
                                }

                                if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                                    val newTokens = refreshResponse.body()!!
                                    tokenManager?.accessToken = newTokens.accessToken
                                    tokenManager?.refreshToken = newTokens.refreshToken

                                    return@Authenticator response.request.newBuilder()
                                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                                        .build()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    tokenManager?.clear()
                    null
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .authenticator(authenticator)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                _api = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
            }
            return _api!!
        }
}
