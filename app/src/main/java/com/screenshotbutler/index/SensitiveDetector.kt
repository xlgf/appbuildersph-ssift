package com.screenshotbutler.index

/** Finds screenshots that expose private details. Runs entirely on-device. */
object SensitiveDetector {

    val labels = mapOf(
        "card" to "Card number",
        "password" to "Password",
        "backupcodes" to "Backup codes",
        "id" to "ID number",
        "ai" to "Private details", // flagged by the charging digest
    )

    private val cardCandidate = Regex("""\b(?:\d[ -]?){13,19}\b""")
    private val password = Regex("""\b(password|passcode|passwd|pwd|pin)\s*[:=]\s*\S+""", RegexOption.IGNORE_CASE)
    private val backupContext = Regex("""\b(backup|recovery)\s+codes?\b""", RegexOption.IGNORE_CASE)
    private val codeLike = Regex("""\b(\d{4}[ -]?\d{4}|[a-z0-9]{4}-[a-z0-9]{4})\b""", RegexOption.IGNORE_CASE)
    private val idNumber = Regex(
        """\b(passport (no|number)|ssn|social security|driver'?s licen[cs]e|national id)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun detect(text: String): List<String> {
        val reasons = mutableListOf<String>()
        if (cardCandidate.findAll(text).any { luhn(it.value.filter(Char::isDigit)) }) reasons += "card"
        if (password.containsMatchIn(text)) reasons += "password"
        if (backupContext.containsMatchIn(text) && codeLike.findAll(text).count() >= 3) reasons += "backupcodes"
        if (idNumber.containsMatchIn(text)) reasons += "id"
        return reasons
    }

    private fun luhn(digits: String): Boolean {
        if (digits.length !in 13..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }
}
