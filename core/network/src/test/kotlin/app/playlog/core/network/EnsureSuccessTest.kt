package app.playlog.core.network

import app.playlog.core.model.Platform
import app.playlog.platform.api.PlatformException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration

class EnsureSuccessTest {
    @Serializable
    private data class Payload(val value: Int)

    private fun clientAnswering(status: HttpStatusCode, body: String = "", headers: Headers = Headers.Empty) =
        HttpClientFactory.create(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = Headers.build {
                        appendAll(headers)
                        append(HttpHeaders.ContentType, "application/json")
                    },
                )
            },
        )

    private suspend inline fun <reified E : PlatformException> expectFailure(
        status: HttpStatusCode,
        body: String = "",
        headers: Headers = Headers.Empty,
    ): E {
        val client = clientAnswering(status, body, headers)
        try {
            client.get("https://example.test/x").ensureSuccess(Platform.STEAM)
        } catch (e: PlatformException) {
            assertTrue("Expected ${E::class.simpleName}, got $e", e is E)
            assertEquals(Platform.STEAM, e.platform)
            return e as E
        }
        fail("Expected ${E::class.simpleName} for HTTP ${status.value}")
        error("unreachable")
    }

    @Test
    fun `2xx passes and successBody decodes`() = runTest {
        val client = clientAnswering(HttpStatusCode.OK, """{"value":7,"extra":"ignored"}""")
        val payload: Payload = client.get("https://example.test/x").successBody(Platform.STEAM)
        assertEquals(Payload(7), payload)
    }

    @Test
    fun `401 and 403 mean the credentials are dead`() = runTest {
        expectFailure<PlatformException.AuthExpired>(HttpStatusCode.Unauthorized)
        expectFailure<PlatformException.AuthExpired>(HttpStatusCode.Forbidden)
    }

    @Test
    fun `429 carries Retry-After in seconds`() = runTest {
        val e = expectFailure<PlatformException.RateLimited>(
            HttpStatusCode.TooManyRequests,
            headers = headersOf(HttpHeaders.RetryAfter, "120"),
        )
        assertEquals(Duration.ofSeconds(120), e.retryAfter)
    }

    @Test
    fun `429 without Retry-After has no delay hint`() = runTest {
        val e = expectFailure<PlatformException.RateLimited>(HttpStatusCode.TooManyRequests)
        assertNull(e.retryAfter)
    }

    @Test
    fun `429 with an unparseable Retry-After still reports rate limiting`() = runTest {
        // Retry-After may also be an HTTP date; it must never turn into a Protocol error or a crash.
        val e = expectFailure<PlatformException.RateLimited>(
            HttpStatusCode.TooManyRequests,
            headers = headersOf(HttpHeaders.RetryAfter, "Wed, 21 Oct 2026 07:28:00 GMT"),
        )
        assertTrue(e.retryAfter == null || !e.retryAfter!!.isNegative)
    }

    @Test
    fun `other errors are protocol errors with a body snippet`() = runTest {
        val e = expectFailure<PlatformException.Protocol>(HttpStatusCode.InternalServerError, "x".repeat(1_000))
        assertTrue(e.message!!.contains("HTTP 500"))
        assertTrue("snippet must be capped", e.message!!.length < 400)
    }

    @Test
    fun `redirects are not followed and count as errors`() = runTest {
        expectFailure<PlatformException.Protocol>(HttpStatusCode.Found, headers = headersOf(HttpHeaders.Location, "https://login"))
    }
}
