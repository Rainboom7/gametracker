package app.playlog.platform.api

import app.playlog.core.model.Platform
import java.time.Duration

sealed class PlatformException(
    val platform: Platform,
    message: String,
    cause: Throwable? = null,
) : Exception("[${platform.displayName}] $message", cause) {

    /** No credentials saved: the account was never linked or was unlinked. */
    class NotLinked(platform: Platform) : PlatformException(platform, "Account is not linked")

    /** Credentials were rejected; the user has to link again. */
    class AuthExpired(platform: Platform, message: String = "Credentials expired or were revoked", cause: Throwable? = null) :
        PlatformException(platform, message, cause)

    /** Input given while linking is malformed or was rejected. */
    class InvalidInput(platform: Platform, val field: LinkFieldType?, message: String) :
        PlatformException(platform, message)

    /** Profile or game details are private, so the API returns nothing useful. */
    class PrivateProfile(platform: Platform) : PlatformException(platform, "Profile or game details are private")

    class RateLimited(platform: Platform, val retryAfter: Duration?) : PlatformException(platform, "Rate limited")

    class Network(platform: Platform, cause: Throwable) : PlatformException(platform, cause.message ?: "Network error", cause)

    /** The service answered something we do not understand: an API change or a bug. */
    class Protocol(platform: Platform, message: String, cause: Throwable? = null) : PlatformException(platform, message, cause)
}
