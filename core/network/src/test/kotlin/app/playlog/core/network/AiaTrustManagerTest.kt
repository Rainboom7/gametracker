package app.playlog.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class AiaTrustManagerTest {
    private fun pem(name: String) = javaClass.getResource("/fixtures/tls/$name")!!.readBytes()
    private fun cert(name: String) = CertificateFactory.getInstance("X.509").generateCertificate(pem(name).inputStream()) as X509Certificate

    private val root = cert("root.pem")
    private val intermediate = cert("int.pem")
    private val leaf = cert("leaf.pem")

    /** Trusts only the test root, like a device trusting a public root but never seeing the intermediate. */
    private val rootOnly: X509TrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
        init(KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("root", root) })
        trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private val requested = mutableListOf<String>()

    @Test
    fun `caIssuers url is read from the AIA extension`() {
        assertEquals("http://ca.example.test/int.crt", AiaTrustManager.caIssuersUrl(leaf))
        assertNull(AiaTrustManager.caIssuersUrl(root))
    }

    @Test
    fun `missing intermediate is downloaded and the chain validates against the system roots`() {
        val tm = AiaTrustManager(rootOnly) { url -> requested += url; intermediate.encoded }
        tm.checkServerTrusted(arrayOf(leaf), "ECDHE_ECDSA")
        tm.checkServerTrusted(arrayOf(leaf), "ECDHE_ECDSA")
        assertEquals("downloaded once, then cached", listOf("http://ca.example.test/int.crt"), requested)
    }

    @Test
    fun `PEM issuer certificates are accepted too`() {
        AiaTrustManager(rootOnly) { pem("int.pem") }.checkServerTrusted(arrayOf(leaf), "ECDHE_ECDSA")
    }

    @Test
    fun `chain still fails when the download fails or brings an untrusted issuer`() {
        assertRejected(AiaTrustManager(rootOnly) { null })
        assertRejected(AiaTrustManager(rootOnly) { "not a certificate".toByteArray() })
        // A certificate that did not issue the leaf must not help, even a trusted one.
        assertRejected(AiaTrustManager(rootOnly) { root.encoded })
    }

    @Test
    fun `complete chains never trigger a download`() {
        AiaTrustManager(rootOnly) { fail("unexpected download $it"); null }.checkServerTrusted(arrayOf(leaf, intermediate), "ECDHE_ECDSA")
    }

    private fun assertRejected(tm: AiaTrustManager) {
        try {
            tm.checkServerTrusted(arrayOf(leaf), "ECDHE_ECDSA")
            fail("expected CertificateException")
        } catch (_: CertificateException) {
        }
    }
}
