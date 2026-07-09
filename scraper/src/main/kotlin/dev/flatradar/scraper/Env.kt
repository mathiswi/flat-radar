package dev.flatradar.scraper

import io.github.cdimascio.dotenv.Dotenv
import java.io.File

/**
 * Single entry point for environment configuration. [get] checks the real
 * process environment first (shell `export`, docker -e, k8s env), then a
 * `.env` file found by walking up from the cwd, then returns `null`.
 *
 * Use this instead of `System.getenv(...)` directly: dotenv-java's loaded
 * `.env` values live in its own map, not in `System.getenv()`, so a direct
 * call would silently miss anything set only via `.env`.
 */
object Env {

    private val dotenv: Dotenv by lazy { load() }

    /** Returns the value for [key], or `null` if not set in `.env` or the real environment. */
    fun get(key: String): String? = dotenv[key]

    private fun load(): Dotenv {
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