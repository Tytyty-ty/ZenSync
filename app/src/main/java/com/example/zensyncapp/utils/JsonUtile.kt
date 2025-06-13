
package com.example.zensyncapp.utils

import kotlinx.serialization.json.Json

object JsonUtils {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
}