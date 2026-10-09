package com.screenshotbutler.index

/**
 * Tells media screenshots (games, comics and memes, social feeds) from screenshots holding information.
 * Real phones are mostly media: on the first real test set about 3 in 5 screenshots were games or comics.
 * Rules are tuned on OCR text from real screenshots; each counts several distinct signals so one stray
 * word ("score", "follow") doesn't move an info screenshot.
 */
object MediaClassifier {

    private val comicSite = Regex(
        """asurascans|asura|comix|manga|manhwa|webtoon|mangadex|tapas|edscans|kmanga|chapter \d+""",
        RegexOption.IGNORE_CASE,
    )
    private val gameSite = Regex("""loldle|uma\.guide|hakuraku|goldenroad|mobilelegends|op\.gg|playoff bracket""", RegexOption.IGNORE_CASE)

    // Words of game and sports-sim screens: stats, ranks, rewards, seasons. Five different ones make a game.
    private val gameWord = Regex(
        """\b(lvl|lv\.?|rank(ing)?s?|stamina|speed|power|guts|wit|enhance|rewards?|claim|xp|hp|inventory|obtained|scout|ssr|sr|trials|club|fans?|fan count|bond|spark|career|recruit(ing|s)?|season|offseason|draft|playoffs?|standings|roster|coach|skills?|skill (pts|points)|turf|dirt|race(s|course)?|victory|win|lose|wins|losses|quest|gacha|summon|loot|level up|battle|mmr|kda|leaderboard|tournament|champions?|championship|epithet|support (cards?|deck)|legacy|trainee|trainer|high score|score|rating|pts|stats|results|round|league|conference|offense|defense|record|ppg|rpg|apg|overall|potential|prestige|lessons?|training|performance|turns?|story|limited time|back|details|retention|g1|field size|placing|final four|net|attack)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ordinal = Regex("""\b\d{1,2}(st|nd|rd|th)\b""", RegexOption.IGNORE_CASE) // race and league places
    private const val GAME_SIGNALS = 5

    private val socialWord = Regex(
        """\b(liked by|likes|followers|following|follow|reply|replies|reels?|views|share|comments?|retweet|repost|posts?|shorts|reddit|facebook|instagram|twitter|tiktok|youtube|subscribe|\d+[hdmy] ago|\d+(h|d|mo|y)|posted|updated (his|her|their) profile)\b|\b[ru]/\w+|@\w+ ?[•·]""",
        RegexOption.IGNORE_CASE,
    )
    private const val SOCIAL_SIGNALS = 3

    /** Speech bubbles and meme captions are set in capitals; info screens rarely are. */
    private const val CAPS_RATIO = 0.75
    private val infoInCaps = Regex("""\$|total|password|code""", RegexOption.IGNORE_CASE)

    /** "game", "comic", "social", or null for an info screenshot. */
    fun classify(text: String): String? = when {
        comicSite.containsMatchIn(text) -> "comic"
        capsRatio(text) > CAPS_RATIO && !infoInCaps.containsMatchIn(text) -> "comic"
        gameSite.containsMatchIn(text) || distinct(gameWord, text) + minOf(distinct(ordinal, text), 2) >= GAME_SIGNALS -> "game"
        distinct(socialWord, text) >= SOCIAL_SIGNALS -> "social"
        else -> null
    }

    private fun distinct(regex: Regex, text: String): Int =
        regex.findAll(text).map { it.value.lowercase().replace(Regex("""\s+"""), " ") }.toSet().size

    private fun capsRatio(text: String): Double {
        val letters = text.filter { it in 'A'..'Z' || it in 'a'..'z' }
        if (letters.length < 15) return 0.0
        return letters.count { it.isUpperCase() }.toDouble() / letters.length
    }
}
