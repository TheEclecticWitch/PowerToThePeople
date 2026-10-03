package com.theeclecticwitch.powertothepeople.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * The one HTTP client, and the one JSON reader. Every source this app reads is public and free,
 * and none needs a key yet. Each platform's engine is found on the classpath: OkHttp on Android,
 * Darwin on iOS, Java's own client on the desktops.
 */
object Http {
    /** Who is asking, with a way to reach the project: Wikimedia and others turn away requests that don't say. */
    const val USER_AGENT = "PowerToThePeople/0.1 (civic information app; https://github.com/TheEclecticWitch/PowerToThePeople)"

    val client = HttpClient {
        expectSuccess = true
        // Every request, the image loader's included.
        install(UserAgent) { agent = USER_AGENT }
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
            block()
        }.bodyAsText()
}
