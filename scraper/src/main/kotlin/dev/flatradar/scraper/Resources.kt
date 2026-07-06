package dev.flatradar.scraper

/**
 * Loads a classpath resource as UTF-8 text. Used by [Fetcher]'s mock scheme and by
 * tests that need to read fixture HTML. Centralised so the classloader story and the
 * "missing resource" error message live in one place.
 *
 * Uses the current thread's context classloader when available (works the same on
 * Gradle's test runner as at runtime) and falls back to this class's own loader.
 *
 * @throws IllegalStateException when [path] does not resolve to a resource, with the
 *         missing path in the message so the caller can spot the typo immediately.
 */
internal fun loadResource(path: String): String {
    val classLoader = Thread.currentThread().contextClassLoader
        ?: Resources::class.java.classLoader
    val normalized = path.removePrefix("/")
    return classLoader.getResourceAsStream(normalized)?.bufferedReader()?.use { it.readText() }
        ?: error("resource not found: $path")
}

private object Resources