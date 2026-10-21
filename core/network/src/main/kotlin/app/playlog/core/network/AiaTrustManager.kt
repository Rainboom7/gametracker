package app.playlog.core.network

import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Wraps the system trust manager and, when a server sends an incomplete chain (no intermediate:
 * Nintendo's `mypage-api.entry.nintendo.co.jp` fails on Android with "Trust anchor for certification
 * path not found"), downloads the missing issuers from the certificates' Authority Information Access
 * `caIssuers` URLs, as desktop browsers do. The completed chain must still validate against the
 * system roots, so this never trusts anything the platform would not.
 */
class AiaTrustManager(
    private val system: X509TrustManager = systemTrustManager(),
    private val download: (String) -> ByteArray? = ::httpGet,
) : X509TrustManager {
    private val issuers = ConcurrentHashMap<String, X509Certificate>()

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            system.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            val completed = complete(chain) ?: throw e
            system.checkServerTrusted(completed, authType)
        }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    private fun complete(chain: Array<X509Certificate>): Array<X509Certificate>? {
        if (chain.isEmpty()) return null
        val result = chain.toMutableList()
        repeat(MAX_FETCHED) {
            val last = result.last()
            if (last.subjectX500Principal == last.issuerX500Principal) return@repeat
            val url = caIssuersUrl(last) ?: return@repeat
            val issuer = issuers[url] ?: fetch(url)?.also { issuers[url] = it } ?: return@repeat
            if (result.any { it == issuer }) return@repeat
            result += issuer
        }
        return result.takeIf { it.size > chain.size }?.toTypedArray()
    }

    private fun fetch(url: String): X509Certificate? = runCatching {
        val bytes = download(url) ?: return null
        CertificateFactory.getInstance("X.509").generateCertificates(ByteArrayInputStream(bytes))
            .filterIsInstance<X509Certificate>().firstOrNull()
    }.getOrNull()

    companion object {
        private const val MAX_FETCHED = 3
        private const val AIA_OID = "1.3.6.1.5.5.7.1.1"

        /** DER of the caIssuers access method OID 1.3.6.1.5.5.7.48.2. */
        private val CA_ISSUERS = byteArrayOf(0x06, 0x08, 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x02)
        private const val URI_TAG = 0x86
        private const val LONG_LENGTH_1 = 0x81
        private const val BYTE_MASK = 0xFF
        private const val LONG_FORM = 0x80

        fun systemTrustManager(): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }

        /** First `caIssuers` URI in the AIA extension; a full ASN.1 parser is not worth it for one field. */
        internal fun caIssuersUrl(cert: X509Certificate): String? {
            val ext = cert.getExtensionValue(AIA_OID) ?: return null
            var i = indexOf(ext, CA_ISSUERS, 0)
            while (i >= 0) {
                var p = i + CA_ISSUERS.size
                if (p < ext.size && (ext[p].toInt() and BYTE_MASK) == URI_TAG) {
                    p++
                    var len = ext[p++].toInt() and BYTE_MASK
                    if (len == LONG_LENGTH_1) len = ext[p++].toInt() and BYTE_MASK else if (len >= LONG_FORM) return null
                    if (p + len <= ext.size) {
                        val uri = String(ext, p, len, Charsets.US_ASCII)
                        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
                    }
                }
                i = indexOf(ext, CA_ISSUERS, i + 1)
            }
            return null
        }

        private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
            for (i in from..data.size - pattern.size) {
                if (pattern.indices.all { data[i + it] == pattern[it] }) return i
            }
            return -1
        }

        /**
         * caIssuers URLs are plain HTTP. A raw socket instead of HttpURLConnection, because Android's
         * cleartext policy would refuse the request; the certificate is verified afterwards anyway.
         */
        private fun httpGet(url: String): ByteArray? {
            val uri = URI(url)
            if (uri.scheme != "http") return null
            val port = if (uri.port > 0) uri.port else HTTP_PORT
            Socket().use { socket ->
                socket.connect(InetSocketAddress(uri.host, port), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                val path = uri.rawPath.ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
                socket.getOutputStream().apply {
                    write("GET $path HTTP/1.0\r\nHost: ${uri.host}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    flush()
                }
                val response = socket.getInputStream().readBytes()
                val headerEnd = indexOf(response, "\r\n\r\n".toByteArray(), 0)
                if (headerEnd < 0) return null
                val status = String(response, 0, headerEnd, Charsets.US_ASCII).lineSequence().first()
                if (!status.contains(" 200")) return null
                return response.copyOfRange(headerEnd + HEADER_END_LENGTH, response.size)
            }
        }

        private const val HTTP_PORT = 80
        private const val TIMEOUT_MS = 10_000
        private const val HEADER_END_LENGTH = 4
    }
}
