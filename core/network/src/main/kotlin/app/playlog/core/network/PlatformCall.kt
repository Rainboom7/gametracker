package app.playlog.core.network

import app.playlog.core.model.Platform
import app.playlog.platform.api.PlatformException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.time.Duration

/**
 * Runs a request and translates transport and parsing failures into [PlatformException], so
 * callers above the platform modules only ever deal with one error type.
 */
suspend inline fun <T> platformCall(platform: Platform, block: () -> T): T =
    try {
        block()
    } catch (e: PlatformException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: SerializationException) {
        throw PlatformException.Protocol(platform, "Unexpected response format: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization reports missing fields as IllegalArgumentException subclasses.
        throw PlatformException.Protocol(platform, "Unexpected response: ${e.message}", e)
    } catch (e: IOException) {
        throw PlatformException.Network(platform, e)
    } catch (e: Exception) {
        if (e::class.qualifiedName?.startsWith("io.ktor") == true) throw PlatformException.Network(platform, e)
        throw e
    }

/** Throws a [PlatformException] for non-2xx statuses; 401/403 become [PlatformException.AuthExpired]. */
suspend fun HttpResponse.ensureSuccess(platform: Platform) {
    if (status.isSuccess()) return
    when (status) {
        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
            throw PlatformException.AuthExpired(platform, "HTTP ${status.value}: ${bodySnippet()}")
        HttpStatusCode.TooManyRequests ->
            throw PlatformException.RateLimited(platform, headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.let(Duration::ofSeconds))
        else -> throw PlatformException.Protocol(platform, "HTTP ${status.value}: ${bodySnippet()}")
    }
}

suspend inline fun <reified T> HttpResponse.successBody(platform: Platform): T {
    ensureSuccess(platform)
    return body()
}

suspend fun HttpResponse.bodySnippet(max: Int = 300): String =
    runCatching { bodyAsText().take(max) }.getOrDefault("")
