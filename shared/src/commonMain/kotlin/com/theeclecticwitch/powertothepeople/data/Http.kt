package com.theeclecticwitch.powertothepeople.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * The one HTTP client, and the one JSON reader. Every source this app reads is public and free,
 * and none needs a key yet. Each platform's engine is found on the classpath: OkHttp on Android,
 * Darwin on iOS, Java's own client on the desktops.
 */
object Http {
    val client = HttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 20_000
        }
    }

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun getText(url: String, block: HttpRequestBuilder.() -> Unit = {}): String =
        client.get(url) {
            header("User-Agent", "PowerToThePeople/0.1 (civic information app)")
            block()
        }.bodyAsText()
}
