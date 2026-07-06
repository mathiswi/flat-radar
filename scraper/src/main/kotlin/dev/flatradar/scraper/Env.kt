package dev.flatradar.scraper

import io.github.cdimascio.dotenv.Dotenv
import java.io.File

/**
 * Single entry point for environment configuration.
 *
 * Resolution order for [get]:
 *   1. Real process environment (shell `export`, docker -e, k8s env, ...) - dotenv-java
 *      checks this on every lookup, regardless of any `.env` file.
 *   2. The first `.env` file found by walking from the current working directory up
 *      a few parent directories. Covers `./gradlew :scraper:run` which sets cwd to
 *      the subproject directory while the user's `.env` lives at the repo root.
 *   3. `null` if neither has the key.
 *
 * Why a helper instead of calling `System.getenv(...)` directly:
 *   - dotenv-java's loaded values live in its own map, not in the JVM's `System.getenv()`.
 *     Calling `System.getenv("GEMINI_API_KEY")` would NOT see a value from `.env`.
 *   - Centralising the lookup means every config read goes through the same path.
 */
object Env {

    private val dotenv: Dotenv by lazy { load() }

    /**
     * Returns the value for [key], or `null` if not set in `.env` or the real environment.
     * Use `.takeIf { it.isNotBlank() }` if you treat whitespace as "not set".
     */
    fun get(key: String): String? = dotenv[key]

    private fun load(): Dotenv {
        // Walk up looking for a .env file. If found, point dotenv at its directory.
        // If not found, dotenv still picks up real env vars (k8s, docker, shell export).
        val envDir = findUpward(".env")?.let { File(it).parentFile }
            ?: return Dotenv.configure()
                .ignoreIfMissing()
                .ignoreIfMalformed()
                .load()

        return Dotenv.configure()
            .directory(envDir.path)
            .ignoreIfMalformed()
            .load()
    }
}