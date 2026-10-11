package com.andreaserick.languagecoast.data

/** Shown for islands whose category doesn't match any keyword below. */
const val DEFAULT_ISLAND_EMOJI = "🏝️"

/** Keywords (lowercase, matched at the start of a word in the category name) and their emoji; first match wins. */
private val KEYWORD_EMOJIS = listOf(
    listOf("greet", "hello", "intro") to "👋",
    listOf("small talk", "chat", "conversation", "talk") to "💬",
    listOf("restaurant", "dining", "menu") to "🍽️",
    listOf("food", "cook", "kitchen", "eat") to "🍲",
    listOf("drink", "coffee", "café", "cafe", "bar") to "☕",
    listOf("travel", "trip", "airport", "transport", "train") to "✈️",
    listOf("hotel", "accommodation") to "🏨",
    listOf("direction", "city", "place") to "🧭",
    listOf("shop", "store", "buy", "money") to "🛍️",
    listOf("animal", "pet") to "🐾",
    listOf("nature", "weather", "season") to "🌤️",
    listOf("family", "friend", "people", "about me") to "👪",
    listOf("work", "job", "office", "business") to "💼",
    listOf("school", "academ", "study", "university") to "🎓",
    listOf("sport", "fitness", "game") to "⚽",
    listOf("health", "doctor", "body", "medic") to "🩺",
    listOf("help", "emergency") to "🆘",
    listOf("home", "house", "household") to "🏠",
    listOf("time", "date", "number") to "🕒",
    listOf("hobby", "hobbies", "passion", "music", "art") to "🎨",
    listOf("my words", "vocab", "word") to "📝"
)

/** The emoji to show for [island]: its own if it has one, otherwise one guessed from its name. */
fun islandEmoji(island: LanguageIsland): String = island.emoji ?: emojiForCategory(island.name)

/** Guesses an emoji for a category name from common keywords, or returns [DEFAULT_ISLAND_EMOJI]. */
fun emojiForCategory(category: String): String {
    val name = category.lowercase()
    return KEYWORD_PATTERNS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(name) }?.second ?: DEFAULT_ISLAND_EMOJI
}

// Match at word starts only, so "weather" doesn't hit "eat" and "restaurant" doesn't hit "art".
private val KEYWORD_PATTERNS: List<Pair<Regex, String>> = KEYWORD_EMOJIS.map { (keywords, emoji) ->
    Regex(keywords.joinToString("|") { "\\b" + Regex.escape(it) }) to emoji
}
