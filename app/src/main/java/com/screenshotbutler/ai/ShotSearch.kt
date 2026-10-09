package com.screenshotbutler.ai

import com.screenshotbutler.data.Shot
import com.screenshotbutler.data.ShotDao
import kotlin.math.ln

/**
 * [score] ranks results. [coverage] (0..1) is how much of the question the screenshot covers, with rare words
 * counting more: low coverage means search found only loosely related screenshots.
 */
data class ScoredShot(val shot: Shot, val score: Double, val coverage: Double)

/**
 * Keyword search over the FTS index. Each question word is weighted by how rare it is among the user's
 * screenshots (IDF), so "passport" outweighs "score" or "level" that dozens of game and sports screens share.
 */
class ShotSearch(private val dao: ShotDao) {

    suspend fun find(query: String): List<Shot> = findScored(query).map { it.shot }

    suspend fun findScored(query: String): List<ScoredShot> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()

        // Hint words ("concert") usually aren't on the screenshot, so also fetch screenshots with the tag they point at.
        val match = (terms + terms.map(::stem) + terms.flatMap { ALIASES[it].orEmpty() } + terms.mapNotNull { TAG_HINTS[it]?.first }).distinct().joinToString(" OR ") { "$it*" }
        val found = dao.search(match)
        val total = dao.total().coerceAtLeast(1)

        val strengths = found.map { shot -> Haystack(shot).let { h -> terms.map { strength(h, it) } } }
        // Every screenshot matching any word is fetched, so the document frequency can be counted here.
        // A word no screenshot matches gets the highest weight: a question about it has no good answer.
        val weights = terms.indices.map { t ->
            val df = strengths.count { it[t] > 0 }.coerceAtLeast(1)
            ln(1.0 + total.toDouble() / df)
        }
        val weightSum = weights.sum()

        return found.mapIndexed { i, shot ->
            val st = strengths[i]
            ScoredShot(
                shot,
                score = terms.indices.sumOf { weights[it] * st[it] },
                coverage = terms.indices.sumOf { weights[it] * minOf(st[it], 1.0) } / weightSum,
            )
        }
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<ScoredShot> { it.score }.thenByDescending { it.shot.takenAt })
    }

    /** How well one screenshot matches one question word. Tags count a bit more than text, but only a bit: regex tags misfire on real screens. */
    private fun strength(h: Haystack, term: String): Double {
        val hint = TAG_HINTS[term]
        val stems = (listOf(term) + ALIASES[term].orEmpty()).map(::stem)
        return when {
            term in h.tags -> 1.5                                     // the word is a tag: "wifi", "flight"
            hint != null && hint.first in h.tags -> hint.second / 2.0 // "concert" points at event tickets
            // Same stem: "texted" finds "Texting". A word alone on its line is a UI label ("Address", "Password"),
            // stronger evidence than the same word inside a sentence.
            stems.any { it in h.stems } -> if (term in h.lines) 1.25 else 1.0
            h.text.contains(term) -> 0.5                              // part of a word: "open" in "OPENING"
            else -> 0.0
        }
    }

    /** A screenshot's searchable text, lowercased and split once: a search can score thousands of screenshots. */
    private class Haystack(shot: Shot) {
        // The digest summary adds words the screenshot never shows.
        val text = (shot.text + " " + shot.tags + " " + shot.labels + " " + shot.digest.orEmpty()).lowercase()
            .replace(WIFI_SPELLING, "wifi")
        val stems = text.split(NON_WORD).mapTo(HashSet(), ::stem)
        val lines = shot.text.lowercase().lines().mapTo(HashSet()) { it.trim() }
        val tags = shot.tagList
    }

    companion object {
        /**
         * Words people ask with that point at a tag even when the screenshot never uses them, with a weight.
         * Specific tags (event, flight) outweigh the generic ticket tag, so "which gate for the concert"
         * ranks the concert ticket above a flight booking that also has a gate.
         */
        private val TAG_HINTS = mapOf(
            "concert" to ("event" to 3), "gig" to ("event" to 3), "festival" to ("event" to 3), "event" to ("event" to 3),
            "flight" to ("flight" to 3), "boarding" to ("flight" to 3), "plane" to ("flight" to 3),
            "airline" to ("flight" to 3), "booking" to ("flight" to 2),
            "gate" to ("ticket" to 2), "seat" to ("ticket" to 2),
            "network" to ("wifi" to 2), "router" to ("wifi" to 2), "internet" to ("wifi" to 2),
            "spent" to ("receipt" to 2), "sent" to ("receipt" to 2), "send" to ("receipt" to 2), "paid" to ("receipt" to 2),
            "cost" to ("receipt" to 2), "price" to ("receipt" to 2), "bill" to ("receipt" to 2),
            "cook" to ("recipe" to 2), "bake" to ("recipe" to 2),
        )

        /** Other ways screenshots write a question word: "the pw is Sunset2024", "City Dental". */
        private val ALIASES = mapOf(
            "password" to listOf("pw", "pass", "passcode", "pin"),
            "dentist" to listOf("dental"),
            "doctor" to listOf("dr", "clinic"),
            "phone" to listOf("tel", "mobile"),
            "address" to listOf("street", "st", "avenue", "ave", "road"),
        )

        private val WIFI_SPELLING = Regex("""\bwi[- ]?fi\b""")

        private val STOPWORDS = setOf(
            "a", "an", "and", "are", "at", "be", "by", "can", "did", "do", "does", "for", "from", "get",
            "how", "i", "in", "is", "it", "me", "much", "my", "of", "on", "or", "show", "that", "the",
            "this", "to", "was", "what", "whats", "when", "where", "which", "who", "with", "you", "your",
            // Words that frame a question but are rarely on the screenshot that answers it ("what TIME do doors
            // open" matched a "...FOR THE 10TH TIME" meme).
            "about", "again", "any", "far", "find", "have", "know", "long", "many", "need", "remember", "tell",
            "there", "time", "up", "will",
        )

        private val NON_WORD = Regex("[^a-z0-9]+")
        private val SUFFIXES = listOf("ings", "ing", "ers", "er", "ed", "es", "s")

        /** Crude English stemming, enough for keyword search: "parked" / "parking" -> "park", "followers" -> "follow". */
        fun stem(word: String): String {
            for (suffix in SUFFIXES) {
                if (word.endsWith(suffix) && word.length - suffix.length >= 3 && !(suffix == "s" && word.endsWith("ss"))) {
                    return word.dropLast(suffix.length)
                }
            }
            return word
        }

        /** "what's the wifi password at mom's?" -> [wifi, password, mom] */
        fun terms(query: String): List<String> =
            query.lowercase()
                .split(NON_WORD)
                .filter { it.length >= 2 && it !in STOPWORDS }
                .map { if (it == "wi" || it == "fi") "wifi" else it }
                .distinct()
    }
}
