package io.github.luannamorim.myfinance.core.format

import java.text.Normalizer

object SearchText {
    private val marks = Regex("\\p{M}+")

    /** Lowercase without diacritics: "Abdômen" -> "abdomen". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase().trim()

    /** True when every whitespace-separated term of [query] occurs in [text] (accent/case-insensitive). */
    fun matches(text: String, query: String): Boolean {
        val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        val haystack = normalize(text)
        return terms.all { it in haystack }
    }
}
