package com.screenshotbutler.ui

import com.screenshotbutler.ai.Digester
import com.screenshotbutler.data.Shot
import org.json.JSONObject

/** A card line with one bold part: "Phoenix Live on **Friday**", "**$85.00** sent to Alex". */
data class Highlight(val before: String, val bold: String, val after: String = "")

/** Turns the newest screenshot in a folder into a one-line summary for its Home card. */
object HomeHighlights {

    private val WEEKDAY = Regex("""\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*\b""")
    private val DAYS = mapOf(
        "Mon" to "Monday", "Tue" to "Tuesday", "Wed" to "Wednesday", "Thu" to "Thursday",
        "Fri" to "Friday", "Sat" to "Saturday", "Sun" to "Sunday",
    )
    // "$85.00", "PHP 63.06", "₱1,300.00" (OCR reads ₱ as "P", so a bare P needs the cents).
    // Not when letters run on: "$2oo" is OCR for "$200", and showing "$2" would be wrong.
    private val AMOUNT = Regex("""(?:(?:[$€£₱]\s?|\bPHP\s?)\d[\d,]*(?:\.\d{2})?|\bP\d[\d,]*\.\d{2})(?![A-Za-z0-9])""")
    private val STARTS_WITH_TIME = Regex("""^\d{1,2}:\d{2}""")
    private val SENT_TO = Regex("""\bto ([A-Z][a-z]+)""")
    private val SENT = Regex("""\b(sent|transfer(red)?)\b""", RegexOption.IGNORE_CASE)
    private val TOTAL_LABEL = Regex("""\b(total|amount|paid)\b""", RegexOption.IGNORE_CASE)
    private val STATEMENT = Regex("""\bstatement of account|account statement|statement period\b""", RegexOption.IGNORE_CASE)

    /** Newest ticket, preferring events: "<event> on <weekday>". */
    fun ticket(tickets: List<Shot>): Highlight? {
        val shot = tickets.firstOrNull { "event" in it.tagList } ?: tickets.firstOrNull() ?: return null
        val name = (digestValue(shot, "name")?.split(' ')?.take(2)?.joinToString(" ") ?: firstLine(shot) ?: return null)
            .trimEnd(':', ',', '-', '.', ' ') // "Phoenix Live:" -> "Phoenix Live"
        val day = WEEKDAY.find(shot.text)?.groupValues?.get(1)?.let { DAYS[it] }
        return if (day != null) Highlight("$name on ", day) else Highlight("", name)
    }

    /**
     * Newest single receipt: "<amount> sent to <name>" or "<amount> at <store>". Bank statements are skipped:
     * a page of transactions has no one amount to show (the first one on a real statement was an exchange rate).
     */
    fun receipt(receipts: List<Shot>): Highlight? {
        val candidates = receipts.filter { AMOUNT.containsMatchIn(it.text) && !STATEMENT.containsMatchIn(it.text) }
        // A real receipt has a labelled total ("Total: P1,300.00"); a chat that mentions money doesn't.
        val shot = candidates.firstOrNull { labelledAmount(it.text.lines()) != null } ?: candidates.firstOrNull() ?: return null
        val lines = shot.text.lines()
        val at = labelledAmount(lines) ?: lines.indexOfFirst { AMOUNT.containsMatchIn(it) }
        val amount = AMOUNT.find(lines[at])!!.value
        // "sent to Alex" only when the transfer wording sits next to the amount.
        val near = lines.subList(maxOf(0, at - 2), minOf(lines.size, at + 3)).joinToString("\n")
        val to = if (SENT.containsMatchIn(near)) SENT_TO.find(near)?.groupValues?.get(1) else null
        return when {
            to != null -> Highlight("", amount, " sent to $to")
            else -> firstLine(shot)?.let { Highlight("", amount, " at $it") } ?: Highlight("", amount)
        }
    }

    private fun digestValue(shot: Shot, key: String): String? =
        shot.digest?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { Digester.values(it)?.optString(key) }
            ?.takeIf { it.isNotBlank() }

    /** First real line of the screenshot (skipping the status bar), e.g. the app or store name. */
    /** Line index of the paid amount: on, or right after, a "Total" / "Amount" / "Paid" line. */
    private fun labelledAmount(lines: List<String>): Int? =
        lines.indices.firstNotNullOfOrNull { i ->
            if (!TOTAL_LABEL.containsMatchIn(lines[i])) null
            else (i until minOf(i + 2, lines.size)).firstOrNull { AMOUNT.containsMatchIn(lines[it]) }
        }

    /** First real line of the screenshot (skipping the status bar, even OCR-garbled like "5:51 O"), e.g. the app or store name. */
    private fun firstLine(shot: Shot): String? =
        com.screenshotbutler.ai.Prompts.cleanText(shot).lines()
            .map { it.substringBefore(": ").trim() }
            .firstOrNull { it.length in 3..24 && it.count(Char::isLetter) >= 3 && !STARTS_WITH_TIME.containsMatchIn(it) }
}
