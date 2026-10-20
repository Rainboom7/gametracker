package app.playlog.core.network

import app.playlog.core.model.Platform
import app.playlog.platform.api.PlatformException
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class PlatformCallTest {
    @Serializable
    private data class Payload(val required: Int)

    private suspend inline fun <reified E : Throwable> failureOf(crossinline block: suspend () -> Unit): E {
        try {
            platformCall(Platform.NINTENDO) { block() }
        } catch (e: Throwable) {
            assertTrue("Expected ${E::class.simpleName}, got $e", e is E)
            return e as E
        }
        fail("Expected ${E::class.simpleName}")
        error("unreachable")
    }

    @Test
    fun `returns the block result`() = runTest {
        assertSame(Unit, platformCall(Platform.NINTENDO) { })
    }

    @Test
    fun `platform exceptions pass through unchanged`() = runTest {
        val original = PlatformException.PrivateProfile(Platform.NINTENDO)
        assertSame(original, failureOf<PlatformException.PrivateProfile> { throw original })
    }

    @Test
    fun `cancellation is never swallowed`() = runTest {
        failureOf<CancellationException> { throw CancellationException("stop") }
    }

    @Test
    fun `serialization failures become Protocol`() = runTest {
        val e = failureOf<PlatformException.Protocol> { throw SerializationException("bad json") }
        assertTrue(e.cause is SerializationException)
    }

    @Test
    fun `a response missing a required field becomes Protocol`() = runTest {
        val client = HttpClientFactory.create(
            MockEngine { respond("""{"other":1}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
        )
        failureOf<PlatformException.Protocol> { client.get("https://example.test/x").body<Payload>() }
    }

    @Test
    fun `IO failures become Network`() = runTest {
        val e = failureOf<PlatformException.Network> { throw IOException("connection reset") }
        assertTrue(e.cause is IOException)
    }

    @Test
    fun `a dropped connection from the engine becomes Network`() = runTest {
        val client = HttpClientFactory.create(MockEngine { throw IOException("socket closed") })
        failureOf<PlatformException.Network> { client.get("https://example.test/x") }
    }

    @Test
    fun `request timeout becomes Network`() = runTest {
        failureOf<PlatformException.Network> { throw HttpRequestTimeoutException("https://example.test/x", 60_000) }
    }

    @Test
    fun `programming errors are not disguised as platform errors`() = runTest {
        failureOf<IllegalStateException> { error("bug") }
    }
}
