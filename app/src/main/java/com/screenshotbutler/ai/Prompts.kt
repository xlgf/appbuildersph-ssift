package com.screenshotbutler.ai

import com.screenshotbutler.data.Shot
import com.screenshotbutler.index.Tagger

/**
 * All prompts in one place. Tune them on a laptop first with `ollama run gemma3:1b`,
 * pasting real OCR text from the demo screenshots, then copy the final wording here.
 *
 * Speed matters more than anything here: on a phone CPU, reading the prompt and writing each reply token
 * dominate the cost, so prompts carry as little text as possible and replies are kept short.
 */
object Prompts {

    /** Gemma chat template. Set to false if the .task bundle already applies it and answers look odd. */
    private const val WRAP_GEMMA_TEMPLATE = true
    private const val ASK_MAX_CHARS_PER_SHOT = 900
    private const val DIGEST_MAX_CHARS = 600
    private const val MAX_FACTS_CHARS = 250

    /**
     * The model's reply is started for it ("prefilled"): a 1B model follows a format far more reliably
     * when the first token is already written. Gemma sometimes repeats the prefix, so callers add it back
     * only when the reply doesn't already start with it.
     */
    const val ASK_PREFILL = "ANSWER:"
    const val DIGEST_PREFILL = "{"

    private fun wrap(user: String, prefill: String): String =
        if (WRAP_GEMMA_TEMPLATE) "<start_of_turn>user\n$user<end_of_turn>\n<start_of_turn>model\n$prefill"
        else "$user\n$prefill"

    // Instructions go AFTER the screenshots and the question: small models follow what they read last.
    private val ASK_INSTRUCTIONS = """
        Answer the QUESTION with only the exact value it asks for (a password, amount, date, code, name or place), copied from the screenshots above.
        If the answer has several parts (like a garage, level and spot), give all of them, separated by commas.
        Do not include times, explanations or other words.
        If the answer is not in the screenshots, write NOT FOUND.
        Reply in exactly this format:
        ANSWER: <value or NOT FOUND>
        SOURCE: <screenshot number>
    """.trimIndent()
    // No worked example here: on-device tests showed Gemma 3 1B copies the example's value into every answer.

    // Keep this JSON-shaped: Gemma 3 1B copies the SHAPE of the instructions. A "key = description" list
    // made it answer with a key list (and pick the first type every time); this template yields valid JSON.
    private val DIGEST_INSTRUCTIONS = """
        Describe the screenshot text above. Reply with ONLY a JSON object, no other text:
        {"summary": "one short sentence",
         "values": {"name": "value"} with up to 3 key values such as password, amount, date, code, place,
         "sensitive": true if it shows a password, card number, ID number or login codes, else false}
    """.trimIndent()
    // No "type" field: the 1B model answered "ticket" (the first option listed) for almost everything,
    // which polluted the Tickets folder. Folder rules already cover type.

    /** Hard caps on reply length: they bound the time per call and stop rare runaway repetition ("7777…"). */
    const val ASK_MAX_REPLY_CHARS = 160
    const val DIGEST_MAX_REPLY_CHARS = 360

    private val CLOCK_LINE = Regex("""^\s*\d{1,2}:\d{2}\s*$""")                      // status bar "9:41"
    private val MESSAGE_TIME_LINE = Regex("""^\s*\d{1,2}:\d{2}\s?([AaPp][Mm])?\s*$""") // chat "6:02 PM"
    private val NOISE_LINE = Regex(
        """^\s*(?:(?:5G|4G|LTE|Wi-?Fi)?\s*\d{1,3}%|<|Message|iMessage|Liked by .*|\d+ likes|\S)\s*$""", // battery, UI chrome, single chars
        RegexOption.IGNORE_CASE,
    )

    /**
     * OCR text without noise the model doesn't need. A 1B model latches onto times, so bare clock lines are
     * dropped everywhere and per-message times are dropped from chats; times with meaning stay ("DOORS 7:30 PM").
     */
    fun cleanText(shot: Shot): String {
        val timeLine = if ("chat" in shot.tagList) MESSAGE_TIME_LINE else CLOCK_LINE
        val lines = shot.text.lines().filterNot { timeLine.matches(it) || NOISE_LINE.matches(it) || it.isBlank() }
        return pairLabels(lines).joinToString("\n")
    }

    private val LABEL_LINE = Regex("""^[A-Z][A-Za-z /]{1,18}$""") // "Spot", "Booking code", "DATE", "Gate / Seat"

    /** The screenshot's label/value pairs ("Booking code" to "X7K2PQ"), for answering by direct lookup. */
    fun labelValues(shot: Shot): List<Pair<String, String>> =
        cleanText(shot).lines().mapNotNull { line ->
            val i = line.indexOf(": ")
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 2).trim()
        }.filter { (label, value) -> LABEL_LINE.matches(label) && value.isNotBlank() }

    /**
     * App screens put a label on one line and its value on the next ("Spot" / "C14"), and OCR keeps them apart,
     * so the model can't tell "C14" is the spot. Rejoin them as "Spot: C14".
     */
    private fun pairLabels(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            val next = lines.getOrNull(i + 1)?.trim()
            if (next != null && LABEL_LINE.matches(line) && !LABEL_LINE.matches(next)) {
                out += "$line: $next"
                i += 2
            } else {
                out += line
                i += 1
            }
        }
        return out
    }

    private val VALUABLE_FOLDERS = setOf("ticket", "receipt", "wifi", "code", "address", "recipe", "account", "schedule")
    // The "code" folder already catches verification and backup codes, so the bare word "code" isn't listed
    // (it pulled in memes like "WHEN THE CODE WORKS").
    private val VALUABLE_TEXT = Regex("""\d{3,}|\$\s?\d|@|\bpw\b|password|address|booking|order""", RegexOption.IGNORE_CASE)

    /**
     * Whether the LLM digest has anything to add. Memes, photos and casual chats with no numbers, codes or
     * addresses are skipped: rules and image labels already describe them, and skipping them is the
     * biggest single speed-up of the digest.
     */
    fun needsDigest(shot: Shot): Boolean =
        // Game and comic screens are full of numbers, but Ask never answers from them: reading them is wasted time.
        Tagger.answerable(shot) && (
            shot.tagList.any { it in VALUABLE_FOLDERS } ||
                shot.sensitive.isNotBlank() ||
                VALUABLE_TEXT.containsMatchIn(cleanText(shot))
            )

    /** [prefill] = false is the retry when the model returned nothing after a prefilled "ANSWER:". */
    fun ask(question: String, shots: List<Shot>, prefill: Boolean = true): String {
        val context = shots.mapIndexed { i, shot ->
            buildString {
                append("SCREENSHOT ${i + 1}:\n")
                append(cleanText(shot).take(ASK_MAX_CHARS_PER_SHOT))
                if (shot.labels.isNotBlank()) append("\nIMAGE SHOWS: ${shot.labels}")
                // Facts make answers better but every character slows the prompt read, so they're capped.
                shot.digest?.takeIf { it.length > 2 && !it.contains("\"skipped\"") }?.let { append("\nFACTS: ${it.take(MAX_FACTS_CHARS)}") }
            }
        }.joinToString("\n\n")

        return wrap(
            "Text read from the user's phone screenshots:\n\n$context\n\nQUESTION: $question\n\n$ASK_INSTRUCTIONS",
            if (prefill) ASK_PREFILL else "",
        )
    }

    fun digest(shot: Shot): String {
        val image = if (shot.labels.isNotBlank()) "\nIMAGE SHOWS: ${shot.labels}" else ""
        return wrap(
            "SCREENSHOT TEXT:\n${cleanText(shot).take(DIGEST_MAX_CHARS)}$image\n\n$DIGEST_INSTRUCTIONS",
            DIGEST_PREFILL,
        )
    }
}
