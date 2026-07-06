package dev.flatradar.scraper

import kotlinx.serialization.Serializable

/**
 * Fills the four rent fields of [ApartmentAd] for ads where the structured attribute
 * list on the page carries no usable price information (e.g. the price is buried in
 * the free-text description). The parser only wants a single function — the
 * implementation may be the LLM-backed [RentExtractor], a stub in tests, or anything
 * else. The scraper package must not depend on a concrete LLM SDK.
 */
fun interface RentFallback {
    suspend fun extract(description: String): ParsedRents?
}

/**
 * Nullable-Int rents in the same shape as [ApartmentAd]'s price slots, so callers
 * can pluck them in directly.
 */
@Serializable
data class ParsedRents(
    val baseRent: Int? = null,
    val sideCosts: Int? = null,
    val heatingCosts: Int? = null,
    val totalRent: Int? = null
) {
    /** Returns true when every field is null — caller can treat that as "no information". */
    fun isEmpty(): Boolean = baseRent == null && sideCosts == null &&
                             heatingCosts == null && totalRent == null
}