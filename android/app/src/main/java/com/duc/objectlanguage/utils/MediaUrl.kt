package com.duc.objectlanguage.utils

import com.duc.objectlanguage.data.local.ApiConfig

fun resolveMediaUrl(path: String): String {
    val trimmed = path.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return "${ApiConfig.baseUrl.trimEnd('/')}/${trimmed.trimStart('/')}"
}
