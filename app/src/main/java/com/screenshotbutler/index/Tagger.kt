package com.screenshotbutler.index

import com.screenshotbutler.data.Shot

/** Fast regex tagging that runs on every screenshot right after OCR. The charging digest refines it later. */
object Tagger {

    /** Bump when the rules below change, so screenshots indexed earlier are re-tagged. */
    const val VERSION = 5

    /** [media]: games, comics and photos are for browsing; Ask doesn't answer from them. */
    data class Folder(val tag: String, val label: String, val media: Boolean = false)

    val folders = listOf(
        Folder("ticket", "Tickets"),
        Folder("receipt", "Receipts"),
        Folder("schedule", "Schedule"),
        Folder("account", "Accounts"),
        Folder("wifi", "Wi-Fi"),
        Folder("code", "Codes"),
        Folder("address", "Addresses"),
        Folder("chat", "Chats"),
        Folder("recipe", "Recipes"),
        Folder("social", "Social", media = true),
        Folder("game", "Games", media = true),
        Folder("comic", "Comics & memes", media = true),
        Folder("image", "Images", media = true), // set by the Indexer from image labels, not by a rule below
    )

    private val rules: Map<String, Regex> = mapOf(
        // "concert" and "venue" are left out: a bank transfer with reference "Concert tickets" is not a ticket.
        "ticket" to Regex(
            """\b(ticket|boarding pass|gate \w+|seat \w+|admit one|row \w+|flight|departs?|e-?ticket)\b""",
            RegexOption.IGNORE_CASE,
        ),
        // Internal tags (not folders on Home) that let search tell an event ticket from a flight booking.
        "event" to Regex(
            """\b(venue|arena|stadium|doors|admit one|world tour|festival|entrance|row \w+ - seat)\b""",
            RegexOption.IGNORE_CASE,
        ),
        // Words only: bare flight-number shapes ("KL 1023") also match pharmacy codes and game stats ("UB 1565").
        "flight" to Regex(
            """\b(flight|airline|boarding|departs?|booking code|gate\s*/\s*seat)\b""",
            RegexOption.IGNORE_CASE,
        ),
        // A payment word; [tag] also requires a money amount, because "total" alone tagged stats screens
        // ("1,057 hours total") and box scores as receipts.
        "receipt" to Regex(
            """\b(total|subtotal|receipt|paid|payment|amount|invoice|transfer(red)?|sent|order|price|purchase|subscription)\b""",
            RegexOption.IGNORE_CASE,
        ),
        // Wi-Fi details, not just the word: "wifi" plus a password/network word, or an SSID/WPA mention.
        "wifi" to Regex(
            """\b(ssid|network name|wlan|wpa2?)\b|\bwi-?fi\b.{0,60}\b(password|pw|pass|network|code)\b|\b(password|pw|network)\b.{0,60}\bwi-?fi\b""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ),
        "code" to Regex(
            """\b(verification code|security code|one-time|otp|2fa|backup codes?|recovery codes?|promo code|coupon|redeem( code)?)\b""",
            RegexOption.IGNORE_CASE,
        ),
        // A house number (not part of a time like "10:42 AM") + 1-3 words + a street word. "Dr" is left out
        // because it matches "Dr. Kim"; "way" needs a capital ("Ring Way"), so "on my way" doesn't count.
        "address" to Regex(
            """(?<![:\d])\b\d{1,5}\s+(?!(?i:am|pm)\b)(?:[A-Za-z]+\s){1,3}(?:(?i:st|street|ave|avenue|rd|road|blvd|boulevard|ln|lane|drive|ct|court|pl|place)\b|Way\b)""",
        ),
        "recipe" to Regex("""\b(tbsp|tsp|cups?|ingredients|preheat|oven|simmer|tablespoons?)\b""", RegexOption.IGNORE_CASE),
        // Sign-in screens, password resets, bank account details.
        "account" to Regex(
            """\b(username|password|passcode|log ?in|sign ?in|account (number|no\.?)|create (an )?account)\b""",
            RegexOption.IGNORE_CASE,
        ),
        // Interviews, appointments, school advisories: a schedule word, and (checked in [tag]) a date or time.
        "schedule" to Regex(
            """\b(interview|appointment|meeting|scheduled?|deadline|due date|advisory|suspended|exam|webinar|reservation|rsvp|orientation)\b""",
            RegexOption.IGNORE_CASE,
        ),
    )

    // Pesos too: "PHP 63.06", "₱1,300.00", which OCR often reads as "P1,300.00" (so a bare P needs the cents).
    private val money = Regex("""[${'$'}€£₱]\s?\d|\bPHP\s?\d|\bP\d[\d,]*\.\d{2}\b""")

    private val timestamp = Regex("""\b\d{1,2}:\d{2}\s?(am|pm)?\b""", RegexOption.IGNORE_CASE)
    private val date = Regex(
        """\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.? \d{1,2}\b|\b\d{1,2} (jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)|\b(mon|tues?|wed|thu|fri|sat|sun)[a-z]*\b|\b\d{1,2}[/-]\d{1,2}([/-]\d{2,4})?\b|\b\d{1,2}:\d{2}\s?(am|pm)\b|\b(tomorrow|today)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Tags needing two different matching words: one "Cup" (a race name in a game) isn't a recipe. */
    private val needsTwoWords = setOf("recipe")

    /**
     * On a game or comic screen most info rules misfire ("$2.3M" payroll is not a receipt, "CHAMPIONS MEETING"
     * is not a schedule). Only these still count there: a redeem code or a game login is real info.
     */
    private val keepOnMedia = setOf("code", "wifi", "account")

    fun tag(text: String): List<String> {
        var tags = rules.filter { (tag, regex) ->
            when (tag) {
                in needsTwoWords -> regex.findAll(text).map { it.value.lowercase() }.toSet().size >= 2
                "schedule" -> regex.containsMatchIn(text) && date.containsMatchIn(text)
                "receipt" -> regex.containsMatchIn(text) && money.containsMatchIn(text)
                else -> regex.containsMatchIn(text)
            }
        }.keys.toList()
        // Chat screenshots: several message timestamps on one screen.
        if (timestamp.findAll(text).count() >= 3) tags = tags + "chat"
        MediaClassifier.classify(text)?.let { media -> tags = tags.filter { it in keepOnMedia } + media }
        return tags
    }

    private val mediaTags = setOf("game", "comic")

    /**
     * Whether Ask may answer from this screenshot. Games and comics share common words with every question
     * ("park", "code"), so they only count when they also carry real info (a redeem code, a login) or private details.
     */
    fun answerable(shot: Shot): Boolean {
        val tags = shot.tagList
        if (tags.none { it in mediaTags }) return true
        return tags.any { it !in mediaTags && it != "image" && it != "social" } || shot.sensitive.isNotBlank()
    }
}
