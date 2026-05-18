package com.duc.objectlanguage.data.local

import com.duc.objectlanguage.BuildConfig

/**
 * Quản lý địa chỉ API server (Base URL).
 * Địa chỉ server được cấu hình trong file local.properties.
 */
object ApiConfig {

    /**
     * Địa chỉ IP của server (đọc từ BuildConfig).
     */
    val serverIp: String = BuildConfig.SERVER_IP

    /**
     * Port của server (đọc từ BuildConfig).
     */
    val serverPort: String = BuildConfig.SERVER_PORT

    val serverScheme: String = BuildConfig.SERVER_SCHEME

    /**
     * Base URL đầy đủ dùng cho Retrofit.
     * Ví dụ: "http://192.168.1.84:8000/"
     */
    val baseUrl: String
        get() = "$serverScheme://$serverIp:$serverPort/"

    /**
     * Trả về true nếu đã cấu hình địa chỉ server.
     */
    val isConfigured: Boolean
        get() = serverIp.isNotEmpty()
}
