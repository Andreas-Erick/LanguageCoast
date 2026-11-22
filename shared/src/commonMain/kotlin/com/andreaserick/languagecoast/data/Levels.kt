package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The language levels a coast is climbed through. Every island belongs to one, so the coast can show how
 * far the user is and what comes next (see [routeFor]).
 *
 * @property title What the level is called in plain words.
 * @property can What a learner can do once the level is reached, in a line.
 * @property writing How sentences at this level are written, for a model's prompt.
 */
enum class Level(val title: String, val can: String, val writing: String) {
    A1(
        "Beginner",
        "Say who you are and get by with the basics.",
        "very short, simple sentences of about 3 to 8 words with the most common words, almost all in the present tense"
    ),
    A2(
        "Elementary",
        "Handle everyday situations and talk about your own life.",
        "short, simple sentences of about 4 to 12 words with common words. Present, simple past and simple future, and can, want, like, have to"
    ),
    B1(
        "Intermediate",
        "Tell stories, give opinions with reasons and deal with problems.",
        "connected sentences of about 8 to 18 words that give reasons (because, so, although), order events (when, while, before, after) and set conditions (if), " +
            "with the past and perfect tenses, comparisons and opinions. Everyday vocabulary, a step richer than a beginner's"
    );

    /** "A2". */
    val label: String get() = name

    /** "A2 Elementary": the code with its plain title, for pills and labels a newcomer reads, who may not know what "A2" means. */
    val shortLabel: String get() = "$label $title"
}

/** How sentences about the user are worded in languages that mark the speaker's gender. */
enum class Wording(val key: String, val label: String) {
    Unspecified("", "Not set"),
    Feminine("feminine", "As a woman"),
    Masculine("masculine", "As a man"),
    Neutral("neutral", "Neutral");

    companion object {
        fun fromKey(key: String?): Wording = entries.firstOrNull { it.key == key } ?: Unspecified
    }
}

/**
 * The rule a prompt needs so first-person sentences come out right in [targetLanguage] ("I'm tired" is
 * "estoy cansada" for a woman), as a line starting with a hyphen; empty when the user hasn't said.
 */
fun speakerRule(wording: Wording, targetLanguage: String): String = when (wording) {
    Wording.Unspecified -> ""
    Wording.Feminine -> "- The learner is a woman: wherever $targetLanguage marks the speaker's gender (adjectives, participles, job titles), use the feminine forms for sentences she says about herself. This is a note on grammar; never say it in a sentence."
    Wording.Masculine -> "- The learner is a man: wherever $targetLanguage marks the speaker's gender (adjectives, participles, job titles), use the masculine forms for sentences he says about himself. This is a note on grammar; never say it in a sentence."
    Wording.Neutral -> "- The learner prefers gender-neutral wording about themselves: wherever $targetLanguage marks the speaker's gender, choose a phrasing that avoids it where a natural one exists."
}

/** Starter topics of level A2; the others are A1, except the [EXTRA_TOPICS]. */
private val A2_TOPICS = setOf(
    "Airport", "Getting Around", "Hotel", "Help & Health", "Making Plans", "Phone & Internet", "Money & Bank", "Home & Renting",
    "Paperwork", "Wishes", "Weather & Seasons", "Feelings", "Describing People", "Appointments & Calls", "Likes & Opinions",
    "Clothes", "Body & Symptoms", "House & Furniture", "At Work"
)

/** Starter topics that are useful at any level and belong to none. */
private val EXTRA_TOPICS = setOf("Phone Settings")

/** The level a starter topic belongs to, or null for an extra that belongs to none. */
fun levelOf(theme: PhraseBank.Theme): Level? = when (theme.category) {
    in EXTRA_TOPICS -> null
    in A2_TOPICS -> Level.A2
    else -> Level.A1
}

/**
 * One stop on a coast's route: an island the level asks for, and where it stands.
 *
 * @property id Stable identifier within the route ("topic:Greetings", "about:family", "about:family@B1").
 * @property state Where it stands on this coast.
 * @property learned Cards on its island that were studied at least once, of [cards]; both 0 while it isn't on the coast.
 */
data class RouteStop(
    val id: String,
    val name: String,
    val emoji: String,
    val level: Level,
    val kind: Kind,
    val state: State,
    val cards: Int = 0,
    val learned: Int = 0
) {
    enum class Kind {
        /** A starter topic from the [PhraseBank], translated. */
        Topic,
        /** An island about the user, written from their answers. */
        About,
        /** An island on a theme, written by the AI provider. */
        Written,
        /** An island about the user, taken a level up. */
        LevelUp
    }

    enum class State {
        /** Needs answers under About you first. */
        Locked,
        /** Can be added to the coast now. */
        Ready,
        /** On the coast. */
        Added,
        /** On the coast, but the answers it was written from have changed. */
        Changed
    }

    val isOnCoast: Boolean get() = state == State.Added || state == State.Changed

    /** How much of the stop is done, from 0 to 1: nothing until it is on the coast, then the share of its cards studied. */
    val progress: Float get() = if (!isOnCoast || cards == 0) 0f else learned.toFloat() / cards
}

/** A level on a coast's route with its stops. */
data class RouteLevel(val level: Level, val stops: List<RouteStop>) {
    /** The share of the level done, from 0 to 1: the average of its stops' progress. */
    val progress: Float get() = if (stops.isEmpty()) 0f else stops.sumOf { it.progress.toDouble() }.toFloat() / stops.size
    val onCoast: Int get() = stops.count { it.isOnCoast }
    val isComplete: Boolean get() = progress >= LEVEL_DONE
}

/** A level counts as reached once this share of it is studied; the last cards of a level shouldn't hold up the next. */
const val LEVEL_DONE = 0.9f

/**
 * A coast's route through the levels.
 *
 * @property current The first level that isn't done yet (the last one, once all are).
 * @property next The stop to take next: the first on [current] that can be added now, else the first that needs answers.
 */
data class Route(val levels: List<RouteLevel>) {
    val current: RouteLevel get() = levels.firstOrNull { !it.isComplete } ?: levels.last()
    val next: RouteStop?
        get() = current.stops.firstOrNull { it.state == RouteStop.State.Ready || it.state == RouteStop.State.Changed }
            ?: current.stops.firstOrNull { it.state == RouteStop.State.Locked }
    fun stop(id: String): RouteStop? = levels.firstNotNullOfOrNull { level -> level.stops.firstOrNull { it.id == id } }
}

fun topicStopId(category: String) = "topic:$category"
fun aboutStopId(islandId: String) = "about:$islandId"
fun levelUpStopId(islandId: String, level: Level) = "about:$islandId@${level.label}"

/**
 * The route of a coast: every level with the islands it asks for, and how far each is.
 *
 * @param islands The coast's islands with their card counts.
 * @param topicsAdded Starter topics added to the coast, by category.
 * @param built The islands written on the coast: [builtKey] to the fingerprint of the facts used.
 */
fun routeFor(
    islands: List<IslandSummary>,
    topicsAdded: Set<String>,
    built: Map<String, String>,
    profile: Profile,
    answers: Map<String, String>
): Route {
    val byName = islands.associateBy { it.island.name }
    fun counts(name: String): Pair<Int, Int> = byName[name]?.let { it.cardCount to (it.cardCount - it.newCount) } ?: (0 to 0)

    val stops = ArrayList<RouteStop>()
    for (theme in PhraseBank.THEMES) {
        val level = levelOf(theme) ?: continue
        // A topic counts as on the coast when it was added whole, or its island is there from the daily session.
        val onCoast = theme.category in topicsAdded && theme.category in byName
        val (cards, learned) = counts(theme.category)
        stops += RouteStop(
            topicStopId(theme.category), theme.category, theme.emoji, level, RouteStop.Kind.Topic,
            if (onCoast) RouteStop.State.Added else RouteStop.State.Ready, if (onCoast) cards else 0, if (onCoast) learned else 0
        )
    }
    for (island in PersonalIslands.ALL + PersonalIslands.WRITTEN) {
        val written = built[island.id]?.takeIf { island.name in byName }
        val state = when {
            written == null -> if (island.isUnlocked(answers)) RouteStop.State.Ready else RouteStop.State.Locked
            island.isUnlocked(answers) && written != island.fingerprint(profile, answers) -> RouteStop.State.Changed
            else -> RouteStop.State.Added
        }
        val (cards, learned) = counts(island.name)
        val onCoast = written != null
        stops += RouteStop(
            aboutStopId(island.id), island.name, island.emoji, island.level,
            if (island.questions.isEmpty()) RouteStop.Kind.Written else RouteStop.Kind.About,
            state, if (onCoast) cards else 0, if (onCoast) learned else 0
        )
    }
    // An island about the user can be taken up to B1 once it is on the coast: the same answers, said with more to them.
    for (island in PersonalIslands.ALL.filter { it.level < Level.B1 }) {
        val base = built[island.id]?.takeIf { island.name in byName }
        val raised = built[builtKey(island.id, Level.B1)]?.takeIf { island.name in byName }
        val (cards, learned) = counts(island.name)
        stops += RouteStop(
            levelUpStopId(island.id, Level.B1), island.name, island.emoji, Level.B1, RouteStop.Kind.LevelUp,
            when {
                raised != null -> RouteStop.State.Added
                base != null -> RouteStop.State.Ready
                else -> RouteStop.State.Locked
            },
            if (raised != null) cards else 0, if (raised != null) learned else 0
        )
    }
    return Route(Level.entries.map { level -> RouteLevel(level, stops.filter { it.level == level }) })
}

/** The part of [personalIslandKey] after the coast for an island written at [level]; its own level has no suffix. */
fun builtKey(islandId: String, level: Level?): String = if (level == null) islandId else "$islandId@${level.label}"

/**
 * What the settings remember about one coast. They keep it for every coast in one place, each entry starting with
 * its coast ("3:Greetings", see [starterTopicKey], [personalIslandKey] and [levelKey]); these are one coast's
 * share, with the coast taken off, so no screen needs to know how the entries are written.
 *
 * @property topicsAdded Starter topics added whole, by name.
 * @property built Islands about the user written here, by [builtKey], to the fingerprint of the facts they came from.
 * @property celebrated Levels whose reaching was congratulated on.
 */
data class CoastRecords(val topicsAdded: Set<String>, val built: Map<String, String>, val celebrated: Set<Level> = emptySet())

/** Coast [coastId]'s share of the settings' records for every coast; see [CoastRecords]. */
fun coastRecords(coastId: Int, topicsAdded: Set<String>, built: Map<String, String>, celebrated: Set<String> = emptySet()): CoastRecords {
    val prefix = "$coastId:"
    return CoastRecords(
        topicsAdded = topicsAdded.filter { it.startsWith(prefix) }.mapTo(HashSet()) { it.removePrefix(prefix) },
        built = built.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) },
        celebrated = Level.entries.filterTo(HashSet()) { levelKey(coastId, it) in celebrated }
    )
}

/**
 * The route of coast [coastId] as it changes, for screens that show where the user stands without showing
 * the coast itself (Today, My Coasts). Due cards are counted at [now].
 */
fun observeRoute(flashcards: FlashcardRepository, settings: SettingsRepository, coastId: Int, now: Long): Flow<Route> =
    combine(
        flashcards.observeIslandSummaries(coastId, now),
        settings.starterTopicsAdded,
        settings.personalIslandsBuilt,
        settings.profile,
        settings.personalAnswers
    ) { islands, topics, built, profile, answers ->
        val records = coastRecords(coastId, topics, built)
        routeFor(islands, records.topicsAdded, records.built, profile, answers)
    }

/** The entry in [SettingsRepository.levelsCelebrated] for [level] on coast [coastId]. */
fun levelKey(coastId: Int, level: Level): String = "$coastId:${level.label}"

/** The level each island on the coast belongs to, by island name: the level of the first stop on the [Route] with that name. */
fun Route.islandLevels(): Map<String, Level> {
    val levels = LinkedHashMap<String, Level>()
    this.levels.forEach { level -> level.stops.forEach { stop -> if (stop.name !in levels) levels[stop.name] = stop.level } }
    return levels
}
