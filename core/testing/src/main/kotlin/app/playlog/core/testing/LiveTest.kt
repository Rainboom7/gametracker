package app.playlog.core.testing

import org.junit.Assume.assumeTrue

/**
 * Reads a secret for a `*LiveTest` (real network call). Skips the test when the
 * variable is not set, so live tests never fail on machines without credentials.
 */
fun requireEnv(name: String): String {
    val value = System.getenv(name)
    assumeTrue("$name is not set, skipping live test", !value.isNullOrBlank())
    return value!!
}
