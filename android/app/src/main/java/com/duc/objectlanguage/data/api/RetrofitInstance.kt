package com.duc.objectlanguage.data.api

import com.duc.objectlanguage.data.local.ApiConfig
import com.duc.objectlanguage.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit singleton instance for Collections API
 * URL được lấy từ ApiConfig (BuildConfig)
 */
object RetrofitInstance {

    private var tokenManager: TokenManager? = null
    private var _collectionApi: CollectionApiService? = null

    /**
     * Initialize with TokenManager (required)
     */
    fun init(tm: TokenManager) {
        tokenManager = tm
        _collectionApi = null
    }

    /**
     * Xây dựng lại Retrofit instance với URL mới.
     * Gọi sau khi cập nhật API URL.
     */
    fun rebuild() {
        _collectionApi = null
    }

    /**
     * Collection API service with auth
     */
    val collectionApi: CollectionApiService
        get() {
            if (_collectionApi == null) {
                val baseUrl = ApiConfig.baseUrl

                if (baseUrl.isEmpty()) {
                    throw IllegalStateException("Địa chỉ server chưa được cấu hình trong local.properties.")
                }

                val loggingInterceptor = HttpLoggingInterceptor().apply {
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

                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(loggingInterceptor)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                _collectionApi = retrofit.create(CollectionApiService::class.java)
            }
            return _collectionApi!!
        }
}
