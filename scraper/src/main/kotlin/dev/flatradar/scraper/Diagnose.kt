package dev.flatradar.scraper

import io.ktor.client.HttpClient
import org.jsoup.Jsoup

/**
 * Diagnostic subcommand: fetch a single URL through the same code path as a
 * normal scrape run, then classify the response (detail page / search page /
 * bot challenge / unknown). Use to triage "skip (null)" lines in the scraper
 * log. Does not touch the backend.
 *
 * Usage: `./gradlew :scraper:run --args="diagnose <url>"`
 */
object Diagnose {

    suspend fun run(client: HttpClient, url: String) {
        println("[diagnose] $url")

        val html = try {
            fetch(client, url)
        } catch (e: Exception) {
            println("  fetch failed: ${e.message}")
            return
        }

        val doc = Jsoup.parse(html)
        val length = html.length
        val lower = html.lowercase()
        val markers = mapOf(
            "#viewad-title" to (doc.selectFirst("#viewad-title") != null),
            "article.aditem" to (doc.selectFirst("article.aditem") != null),
            "input[name=adId]" to (doc.selectFirst("input[name=adId]") != null),
            "captcha" to (lower.contains("captcha") || lower.contains("verifizieren") || lower.contains("ich bin kein")),
            "robots-meta" to (doc.selectFirst("meta[name=robots]") != null),
            "cloudflare" to lower.contains("cloudflare"),
            "datadome" to lower.contains("datadome"),
            "perimeterx" to (lower.contains("perimeterx") || lower.contains("px-captcha")),
        )

        println("  length:   $length bytes")
        println("  preview:  ${html.take(300).replace("\n", " ").replace("\r", "")}")
        println("  markers:")
        for ((name, present) in markers) {
            println("    %-22s : %s".format(name, if (present) "PRESENT" else "absent"))
        }

        val verdict = when {
            markers.getValue("captcha") || markers.getValue("cloudflare") ||
                markers.getValue("datadome") || markers.getValue("perimeterx") ->
                "BLOCKED - bot protection served a challenge page"
            markers.getValue("#viewad-title") && markers.getValue("input[name=adId]") ->
                "OK - detail page looks parseable"
            markers.getValue("article.aditem") ->
                "OK - search-results page (call this URL through SearchPageParser instead)"
            length < 2000 ->
                "UNKNOWN - response is suspiciously short ($length bytes); possibly a redirect or block"
            else ->
                "UNKNOWN - unexpected shape; neither detail page nor search page"
        }
        println("  verdict:  $verdict")
    }
}