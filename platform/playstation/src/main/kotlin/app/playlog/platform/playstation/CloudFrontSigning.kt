package app.playlog.platform.playstation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * CloudFront access to the PS cloud media gallery. Media URLs come back bare and CloudFront answers
 * "Missing Key-Pair-Id query parameter or cookie value", so the policy, signature and key pair id
 * (sent as `CloudFront-*` cookies or JSON fields) are appended to each URL as query parameters,
 * which CloudFront accepts in place of the cookies.
 */
internal data class CloudFrontSigning(val policy: String?, val signature: String, val keyPairId: String) {

    fun sign(url: String): String {
        if (url.contains("Key-Pair-Id=", ignoreCase = true) || url.contains("Signature=")) return url
        val params = listOfNotNull(policy?.let { "Policy=$it" }, "Signature=$signature", "Key-Pair-Id=$keyPairId")
        return url + (if ('?' in url) "&" else "?") + params.joinToString("&")
    }

    companion object {
        private val NAMES = mapOf(
            "policy" to Part.POLICY,
            "signature" to Part.SIGNATURE,
            "keypairid" to Part.KEY_PAIR_ID,
        )

        private enum class Part { POLICY, SIGNATURE, KEY_PAIR_ID }

        /** From `Set-Cookie` headers and from any matching string fields in [json] outside the media list. */
        fun from(setCookieHeaders: List<String>, json: JsonObject?): CloudFrontSigning? {
            val found = mutableMapOf<Part, String>()
            for (header in setCookieHeaders) {
                val pair = header.substringBefore(';')
                val name = pair.substringBefore('=', "").trim()
                val value = pair.substringAfter('=', "").trim().trim('"')
                part(name)?.let { if (value.isNotEmpty()) found.putIfAbsent(it, value) }
            }
            json?.let { collect(it, found, depth = 0) }
            val signature = found[Part.SIGNATURE] ?: return null
            val keyPairId = found[Part.KEY_PAIR_ID] ?: return null
            return CloudFrontSigning(found[Part.POLICY], signature, keyPairId)
        }

        private fun part(name: String): Part? =
            NAMES[name.removePrefix("CloudFront-").removePrefix("cloudFront").lowercase().filter(Char::isLetter)]

        private fun collect(element: JsonElement, found: MutableMap<Part, String>, depth: Int) {
            if (depth > MAX_DEPTH) return
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    if (key == "ugcDocument") continue
                    val part = part(key)
                    if (part != null && value is JsonPrimitive && value.isString) {
                        found.putIfAbsent(part, value.content)
                    } else {
                        collect(value, found, depth + 1)
                    }
                }
                is JsonArray -> element.forEach { collect(it, found, depth + 1) }
                else -> Unit
            }
        }

        private const val MAX_DEPTH = 4
    }
}
