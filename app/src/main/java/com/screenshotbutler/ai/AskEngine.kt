package com.screenshotbutler.ai

import android.os.SystemClock
import android.util.Log
import com.screenshotbutler.data.Shot
import com.screenshotbutler.index.Tagger

sealed interface AskResult {
    data class Answer(val value: String, val source: Shot?, val matches: List<Shot>, val millis: Long, val read: Int) : AskResult
    data class NotSure(val matches: List<Shot>) : AskResult
    data class ModelUnavailable(val matches: List<Shot>) : AskResult
    data object NoMatches : AskResult
}

/** Search first (instant), then let the LLM read only the top matches. */
class AskEngine(private val search: ShotSearch, private val llm: LocalLlm) {

    suspend fun ask(question: String): AskResult {
        val found = search.findScored(question)
        if (found.isEmpty()) return AskResult.NoMatches
        // Answer only from info screenshots: game and comic screens share common words with every question
        // ("where did I park" matched a manga page). They still show in the list of matches, after the info ones.
        val (scored, mediaOnly) = found.partition { Tagger.answerable(it.shot) }
        val matches = (scored + mediaOnly).map { it.shot }
        if (scored.isEmpty()) {
            Log.d("AskEngine", "only game/comic screenshots matched, not asking the model")
            return AskResult.NotSure(matches)
        }
        // Search only found loosely related screenshots (a meme sharing one common word): say so instead of
        // letting the model answer from the wrong screenshot.
        if (scored[0].coverage < MIN_COVERAGE) {
            Log.d("AskEngine", "weak match (coverage ${"%.2f".format(scored[0].coverage)}), not asking the model")
            return AskResult.NotSure(matches)
        }
        if (llm.state.value != LocalLlm.State.Ready) return AskResult.ModelUnavailable(matches)

        // A clear winner goes to the model alone: similar screenshots (a flight and a concert ticket both
        // have a "gate") confuse a 1B model, and one screenshot is also a shorter, faster prompt.
        val clearWinner = scored.size == 1 || scored[0].score >= scored[1].score * CLEAR_LEAD
        // Otherwise only screenshots close to the best one: "when is the concert" also matched a bank transfer
        // with reference "Concert tickets", and the 1B model answered with the transfer's date.
        val top = if (clearWinner) matches.take(1)
        else scored.filter { it.score >= scored[0].score * NEAR_TOP }.take(TOP_K).map { it.shot }
        Log.d("AskEngine", "scores ${scored.take(4).map { "%.1f".format(it.score) }}, coverage ${"%.2f".format(scored[0].coverage)}, sending ${top.size} screenshot(s)")
        val started = SystemClock.elapsedRealtime()

        // When the question names a label on the best screenshot ("booking code"), read the value straight off
        // the screenshot: exact and instant, and a 1B model can't pick a neighbouring line by mistake.
        lookup(question, matches.first())?.let { value ->
            Log.d("AskEngine", "answered by label lookup: $value")
            return AskResult.Answer(respace(value), matches.first(), matches, SystemClock.elapsedRealtime() - started, read = 1)
        }

        val raw = try {
            llm.generate(Prompts.ask(question, top), maxChars = Prompts.ASK_MAX_REPLY_CHARS)
                // Occasionally the model stops right after the prefilled "ANSWER:"; retry once without it.
                .ifBlank { llm.generate(Prompts.ask(question, top, prefill = false), maxChars = Prompts.ASK_MAX_REPLY_CHARS) }
        } catch (e: Exception) {
            Log.e("AskEngine", "Generation failed", e)
            return AskResult.NotSure(matches)
        }
        val millis = SystemClock.elapsedRealtime() - started
        Log.d("AskEngine", "model reply in ${millis}ms: $raw")

        // The prompt started the reply with "ANSWER:"; add it back unless the model repeated it.
        val reply = if (raw.trimStart().startsWith(Prompts.ASK_PREFILL, ignoreCase = true)) raw else Prompts.ASK_PREFILL + raw
        val answer = (ANSWER.find(reply)?.groupValues?.get(1)?.trim()).takeUnless { it.isNullOrBlank() || TIME_ONLY.matches(it) }
            ?: lastUsefulLine(raw)
        if (answer.isNullOrBlank() || answer.contains("NOT FOUND", ignoreCase = true)) {
            return AskResult.NotSure(matches)
        }
        val snapped = snapToSource(answer, top)
        if (!grounded(snapped, top)) {
            Log.d("AskEngine", "answer not found in the screenshots, dropping it: $snapped")
            return AskResult.NotSure(matches)
        }
        val sourceIndex = SOURCE.find(reply)?.groupValues?.get(1)?.toIntOrNull()?.minus(1)
        val source = sourceIndex?.let { top.getOrNull(it) } ?: top.first()
        return AskResult.Answer(respace(snapped), source, matches, millis, read = top.size)
    }

    /**
     * Whether the answer actually comes from the screenshots. Every number must appear as written (a made-up
     * code or amount is the worst failure); words may be reworded ("October" for "Oct"), so half of them
     * matching on their first letters is enough.
     */
    private fun grounded(answer: String, shots: List<Shot>): Boolean {
        val text = shots.joinToString(" ") { it.text + " " + it.digest.orEmpty() }.lowercase()
        val tokens = answer.lowercase().split(NON_ALNUM).filter { it.length >= 2 }
        if (tokens.isEmpty()) return true
        val (numbers, words) = tokens.partition { t -> t.any { it.isDigit() } }
        if (numbers.any { it !in text }) return false
        return words.isEmpty() || words.count { it.take(3) in text } * 2 >= words.size
    }

    /**
     * A 1B model sometimes drops a character from a code ("7K2PQ" for "X7K2PQ"). If the answer is a single
     * token found inside a longer token of the screenshot text, return the full token from the screenshot.
     */
    private fun snapToSource(answer: String, shots: List<Shot>): String {
        val clean = answer.trim().trimEnd('-', ',', '.', ':').trim()
        if (clean.length < 3) return clean
        // The model sometimes drops spaces ("Gate14-RowF" for "Gate 14 - Row F"): match ignoring whitespace
        // and return the screenshot's own spelling.
        val loose = Regex(clean.filterNot { it.isWhitespace() }.map { Regex.escape(it.toString()) }.joinToString("""\s*"""), RegexOption.IGNORE_CASE)
        shots.firstNotNullOfOrNull { shot -> loose.find(shot.text)?.let { shot.text to it } }?.let { (text, m) ->
            // It also stops early in spaced codes ("1Z 84F 93R" of "1Z 84F 93R 0412 6650"): if the line
            // continues with more code-like groups, include them.
            val lineEnd = text.indexOf('\n', m.range.last + 1).let { if (it < 0) text.length else it }
            val rest = text.substring(m.range.last + 1, lineEnd)
            // Same for a place cut at a comma ("Westfield Central" of "Westfield Central, Level 3").
            val full = if (CODE_CONTINUATION.matches(rest) || DETAIL_CONTINUATION.matches(rest)) m.value + rest else m.value
            return full.trimEnd('-', ' ').trim()
        }
        if (clean.contains(' ')) return clean
        // It sometimes drops a character from a code ("7K2PQ" for "X7K2PQ"): use the full source token.
        val tokens = shots.flatMap { TOKEN.findAll(it.text).map { m -> m.value } }
        return tokens.filter { it.contains(clean, ignoreCase = true) }.minByOrNull { it.length } ?: clean
    }

    /**
     * Direct answer from the screenshot's label/value pairs, or null to let the model answer:
     * - a label of two or more words that appears in the question ("booking code", "tracking number");
     * - "where did I park": the location labels joined ("Westfield Central, Level 3, Spot C14").
     */
    private fun lookup(question: String, shot: Shot): String? {
        val q = question.lowercase()
        val pairs = Prompts.labelValues(shot)
        pairs.filter { (label, _) -> label.contains(' ') && label.lowercase() in q }
            .maxByOrNull { it.first.length }
            ?.let { return it.second }
        if (PARKING_QUESTION.containsMatchIn(q)) {
            val parts = pairs.filter { (label, _) -> PARKING_LABEL.matches(label) }
            if (parts.isNotEmpty()) {
                return parts.joinToString(", ") { (label, value) ->
                    if (label.equals("garage", true) || label.equals("location", true)) value
                    else "${label.replaceFirstChar { it.uppercase() }} $value"
                }
            }
        }
        return null
    }

    /** OCR drops the space after place words ("Level3", "RowF"); put it back for display. */
    private fun respace(answer: String): String = PLACE_WORD_GLUED.replace(answer) { "${it.groupValues[1]} ${it.groupValues[2]}" }

    /** When the model ignores the format, the last line that isn't a bare timestamp is usually the answer. */
    private fun lastUsefulLine(raw: String): String? =
        raw.lines().map { it.trim() }
            .filter { it.isNotBlank() && !TIME_ONLY.matches(it) && !it.startsWith("SOURCE", ignoreCase = true) }
            .lastOrNull()
            ?.removePrefix("ANSWER:")?.trim()

    private companion object {
        const val TOP_K = 3
        const val NEAR_TOP = 0.75       // runners-up below 75% of the best score aren't sent to the model
        const val CLEAR_LEAD = 1.5      // the top match needs 1.5x the runner-up's score to be read alone
        const val MIN_COVERAGE = 0.55   // below this, search found nothing that covers the question
        val NON_ALNUM = Regex("[^a-z0-9]+")
        val ANSWER = Regex("""ANSWER:\s*(.+)""", RegexOption.IGNORE_CASE)
        val SOURCE = Regex("""SOURCE:\s*(\d+)""", RegexOption.IGNORE_CASE)
        val TIME_ONLY = Regex("""\d{1,2}:\d{2}\s?([AaPp][Mm])?""")
        val TOKEN = Regex("""[A-Za-z0-9$._\-]+""")
        val CODE_CONTINUATION = Regex("""(?:\s[A-Z0-9]{2,6})+\s*""") // " 0412 6650"
        val DETAIL_CONTINUATION = Regex(""",\s[^,\n]{1,25}\s*""")     // ", Level 3"
        val PARKING_QUESTION = Regex("""\b(park|parked|parking|my car)\b""")
        val PARKING_LABEL = Regex("""(?i)garage|location|level|floor|spot|bay|row|section|zone""")
        // Place word glued to an uppercase letter or digit: "Level3" -> "Level 3", "RowF" -> "Row F".
        val PLACE_WORD_GLUED = Regex("""\b((?i:level|floor|gate|row|seat|spot|platform|terminal|bay))([A-Z0-9])""")
    }
}
