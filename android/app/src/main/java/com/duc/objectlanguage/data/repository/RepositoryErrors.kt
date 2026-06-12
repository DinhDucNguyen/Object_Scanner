package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal fun userFacingException(
    throwable: Throwable,
    fallback: String = RepositoryText.get(R.string.repo_error_generic)
): Exception {
    if (throwable is CancellationException) throw throwable
    val message = when (throwable) {
        is UnknownHostException ->
            RepositoryText.get(R.string.repo_error_unknown_host)
        is ConnectException ->
            RepositoryText.get(R.string.repo_error_connect)
        is SocketTimeoutException ->
            RepositoryText.get(R.string.repo_error_timeout)
        is SSLException ->
            RepositoryText.get(R.string.repo_error_ssl)
        is IOException ->
            RepositoryText.get(R.string.repo_error_io)
        is HttpException -> when (throwable.code()) {
            401 -> RepositoryText.get(R.string.repo_error_session_expired)
            403 -> RepositoryText.get(R.string.repo_error_forbidden)
            in 500..599 -> RepositoryText.get(R.string.repo_error_server)
            else -> throwable.message().takeIf { it.isNotBlank() } ?: fallback
        }
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: fallback
    }
    return Exception(message, throwable)
}
