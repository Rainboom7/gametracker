package app.playlog.platform.playstation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudFrontSigningTest {
    @Test
    fun `signing cookies are appended to bare media urls`() {
        val signing = CloudFrontSigning.from(
            listOf(
                "CloudFront-Policy=POL; Path=/; Secure; HttpOnly",
                "CloudFront-Signature=SIG~x; Path=/",
                "CloudFront-Key-Pair-Id=KID; Path=/",
                "other=1",
            ),
            json = null,
        )!!
        assertEquals("https://cdn.test/a.jpg?Policy=POL&Signature=SIG~x&Key-Pair-Id=KID", signing.sign("https://cdn.test/a.jpg"))
        assertEquals("https://cdn.test/a.jpg?w=1&Policy=POL&Signature=SIG~x&Key-Pair-Id=KID", signing.sign("https://cdn.test/a.jpg?w=1"))
        val alreadySigned = "https://cdn.test/a.jpg?Expires=1&Signature=S&Key-Pair-Id=K"
        assertEquals(alreadySigned, signing.sign(alreadySigned))
    }

    @Test
    fun `signing fields in the json body are found outside the media list`() {
        val json = Json.parseToJsonElement(
            """{"ugcDocument":[{"signature":"ignored"}],"cloudfront":{"CloudFront-Signature":"S","CloudFront-Key-Pair-Id":"K"}}""",
        ).jsonObject
        assertEquals(CloudFrontSigning(policy = null, signature = "S", keyPairId = "K"), CloudFrontSigning.from(emptyList(), json))
    }

    @Test
    fun `no signing without both signature and key pair id`() {
        assertNull(CloudFrontSigning.from(listOf("CloudFront-Signature=S"), json = null))
        assertNull(CloudFrontSigning.from(emptyList(), json = null))
    }
}
