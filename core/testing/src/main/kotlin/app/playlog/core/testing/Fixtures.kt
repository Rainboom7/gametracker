package app.playlog.core.testing

/**
 * Loads anonymised API responses from `src/test/resources/fixtures/<platform>/<name>`.
 */
object Fixtures {
    fun read(path: String): String {
        val resource = "fixtures/$path"
        val url = Thread.currentThread().contextClassLoader?.getResource(resource)
            ?: Fixtures::class.java.classLoader.getResource(resource)
        return requireNotNull(url) { "Fixture not found on test classpath: $resource" }.readText()
    }
}
