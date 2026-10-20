package app.playlog.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val TrackerJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

object HttpClientFactory {
    /**
     * Shared client configuration. Pass OkHttp's engine on Android and `MockEngine` in tests.
     * `expectSuccess` stays off: each platform maps HTTP errors to [app.playlog.platform.api.PlatformException] itself.
     */
    fun create(engine: HttpClientEngine, enableLogging: Boolean = false, logger: Logger = Logger.SIMPLE): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            followRedirects = false
            install(ContentNegotiation) {
                json(TrackerJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
            if (enableLogging) {
                install(Logging) {
                    this.logger = logger
                    level = LogLevel.INFO
                    sanitizeHeader { it.equals("Authorization", ignoreCase = true) || it.equals("Cookie", ignoreCase = true) }
                }
            }
        }
}
