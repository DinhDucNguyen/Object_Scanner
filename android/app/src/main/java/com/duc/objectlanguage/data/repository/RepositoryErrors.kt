package com.duc.objectlanguage.data.repository

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal fun userFacingException(
    throwable: Throwable,
    fallback: String = "Có lỗi xảy ra, vui lòng thử lại"
): Exception {
    val message = when (throwable) {
        is UnknownHostException ->
            "Không tìm thấy máy chủ. Kiểm tra Wi-Fi và địa chỉ backend."
        is ConnectException ->
            "Không kết nối được máy chủ. Kiểm tra backend đã chạy và điện thoại cùng mạng với laptop."
        is SocketTimeoutException ->
            "Máy chủ phản hồi quá lâu. Vui lòng thử lại sau vài giây."
        is SSLException ->
            "Không kết nối được máy chủ. Kiểm tra app đang dùng đúng địa chỉ backend."
        is IOException ->
            "Kết nối mạng không ổn định. Vui lòng thử lại."
        is HttpException -> when (throwable.code()) {
            401 -> "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại."
            403 -> "Bạn không có quyền thực hiện thao tác này."
            in 500..599 -> "Máy chủ đang gặp lỗi, vui lòng thử lại sau."
            else -> throwable.message().takeIf { it.isNotBlank() } ?: fallback
        }
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: fallback
    }
    return Exception(message, throwable)
}
