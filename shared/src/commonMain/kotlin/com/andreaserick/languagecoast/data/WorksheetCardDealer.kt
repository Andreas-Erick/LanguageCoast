package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.ln
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Deals out a coast's cards to the prompts of one sheet, [limit] cards and [characters] of card text per
 * prompt at most, in the coast's order.
 *
 * Every prompt is told which islands its sentences are to be about and how many each ([WorksheetDeal.quotas]):
 * the islands whose cards need practice most get the most, by the rules in `WorksheetFocus.kt`. And it is
 * told which card each sentence is built from ([WorksheetDeal.anchors]): of an island's cards, the studied
 * ones that need practice most come first, and no card is used twice on a sheet until its island has run
 * out of others. The islands take turns down the list, so the sheet is mixed.
 *
 * A coast that fits is shown whole every time. A larger one, be it tens of thousands of cards, is shown
 * island by island: a sentence is built from one island's cards, so a prompt gets up to [perIsland] cards of
 * an island (enough to build from) rather than a few of each. The islands the prompt's sentences are about
 * come first, and of an island the cards the learner has studied, those most that need practice ([now]
 * being when that is asked). Room that is left goes to other islands, for their everyday words, the ones
 * that haven't been shown for longest first, and then to more cards of the islands that are there.
 */
internal class WorksheetCardDealer(
    cards: List<WorksheetSourceCard>,
    private val limit: Int,
    private val perIsland: Int,
    private val characters: Int,
    now: Long,
    private val random: Random,
    private val recentAnchors: Set<String> = emptySet(),
    private val difficulty: WorksheetDifficulty = WorksheetDifficulty.Medium
) {
    internal class Snapshot(
        val prompts: Int,
        val lastShown: IntArray,
        val anchored: Set<Int>,
        val planner: DoubleArray
    )

    fun snapshot(): Snapshot = Snapshot(prompts, lastShown.copyOf(), HashSet(anchored), planner.snapshot())

    fun restore(saved: Snapshot) {
        prompts = saved.prompts
        saved.lastShown.copyInto(lastShown)
        anchored.clear()
        anchored.addAll(saved.anchored)
        planner.restore(saved.planner)
    }
    private val lines = cards.map { WorksheetCard(it.islandName, oneLine(it.targetText), oneLine(it.nativeText)) }
    private val notes = cards.map { it.note }
    private val sizes = IntArray(lines.size) { lines[it].targetText.length + lines[it].nativeText.length + SEPARATOR_LENGTH }
    private val shortest = sizes.minOrNull() ?: 0
    private val fitsWhole = lines.size <= limit && sizes.sumOf { it.toLong() } <= characters

    /** How much each card needs practice ([practiceNeed]), or [NOT_STUDIED] for one the learner has never studied. */
    private val needs = DoubleArray(cards.size) { if (isStudied(cards[it])) practiceNeed(cards[it], now) else NOT_STUDIED }

    /** The islands' cards (as indices), and the islands' names. */
    private val islands: List<List<Int>> = cards.indices.groupBy { cards[it].islandId }.values.toList()
    private val names = islands.map { cards[it.first()].islandName }
    private val planner = FocusPlanner(
        DoubleArray(islands.size) { island -> islandFocus(islands[island].size, islands[island].sumOf { needs[it].coerceAtLeast(0.0) }) },
        random
    )

    /** Where the cards of the island being dealt stand in a prompt's draw; see [inPickOrder]. */
    private val draw = DoubleArray(cards.size)

    /** The number of the prompt an island's cards were last in, 0 for none yet. */
    private val lastShown = IntArray(islands.size)
    private var prompts = 0

    /** The cards a sentence of this sheet has been built from already. */
    private val anchored = HashSet<Int>()

    /** How many islands the cards are on. */
    val islandCount: Int get() = islands.size

    /** The cards for the next prompt, which asks for [sentences] sentences, and the islands those are to be about. */
    fun next(sentences: Int): WorksheetDeal {
        prompts++
        val quotas = planner.plan(sentences, maxIslands = if (fitsWhole) islands.size else maxOf(1, limit / perIsland))
        val planned = islands.indices.filter { quotas[it] > 0 }
        // The prompt names the islands in the order it shows them, which is the coast's. Islands of one name are one topic to it.
        val named = LinkedHashMap<String, Int>()
        if (islands.size > 1) planned.sortedBy { islands[it].first() }.forEach { named[names[it]] = (named[names[it]] ?: 0) + quotas[it] }
        val islandQuotas = named.map { (island, count) -> IslandQuota(island, count) }
        // An island's cards in this prompt's order, drawn once: its anchors are the first of the cards it shows.
        val orders = HashMap<Int, List<Int>>()
        fun order(island: Int) = orders.getOrPut(island) { inPickOrder(islands[island]) }
        val inCoastOrder = planned.sortedBy { islands[it].first() }
        if (fitsWhole) return WorksheetDeal(lines, islandQuotas, anchorsFor(inCoastOrder, quotas, ::order, shown = null, sentences))

        val picked = ArrayList<Int>(limit)
        val passedOver = ArrayList<Int>()
        var room = characters
        fun full() = picked.size >= limit || room < shortest
        fun take(index: Int): Boolean {
            if (picked.size >= limit || sizes[index] > room) return false
            picked += index
            room -= sizes[index]
            return true
        }

        val others = islands.indices.filter { quotas[it] == 0 }.sortedWith(compareBy({ lastShown[it] }, { planner.due(it) }))
        for (island in planned.sortedByDescending { quotas[it] } + others) {
            if (full()) break
            var taken = 0
            for (index in order(island)) {
                if (taken < perIsland && take(index)) taken++ else passedOver += index
            }
            if (taken > 0) lastShown[island] = prompts
        }
        if (!full()) for (index in passedOver.shuffled(random)) if (full()) break else take(index)
        // Cards too long for any prompt: the shortest ones are still better than a prompt without cards.
        if (picked.isEmpty()) picked += sizes.indices.sortedBy { sizes[it] }.take(WORKSHEET_MIN_CARDS)
        val shown = picked.toHashSet()
        return WorksheetDeal(picked.sorted().map { lines[it] }, islandQuotas, anchorsFor(inCoastOrder, quotas, ::order, shown, sentences))
    }

    /**
     * The cards a prompt's [sentences] sentences are built from: each of the [planned] islands' share
     * ([quotas]), mixed. An island whose cards found no room in the prompt has none to give, so its
     * sentences go to the islands that are shown, one at a time in turn.
     */
    private fun anchorsFor(planned: List<Int>, quotas: IntArray, order: (Int) -> List<Int>, shown: Set<Int>?, sentences: Int): List<WorksheetAnchor> {
        val dealt = planned.map { anchorsOf(order(it), quotas[it], shown).toMutableList() }
        val withCards = dealt.indices.filter { dealt[it].isNotEmpty() }
        var turn = 0
        while (withCards.isNotEmpty() && dealt.sumOf { it.size } < sentences) {
            val at = withCards[turn++ % withCards.size]
            dealt[at] += anchorsOf(order(planned[at]), 1, shown, already = dealt[at])
        }
        return anchors(dealt)
    }

    /**
     * The [wanted] cards of one island that its sentences are built from, out of the island's cards in
     * [order] (those that need practice most first). Cards the prompt doesn't show ([shown], null for all of
     * them) and very long ones are passed over, and cards used on this sheet before come last: an island
     * with fewer cards than sentences has them come round again.
     *
     * @param already The island's cards this prompt has been given before this call.
     */
    private fun anchorsOf(order: List<Int>, wanted: Int, shown: Set<Int>?, already: List<Int> = emptyList()): List<Int> {
        val there = order.filter { shown == null || it in shown }
        val usable = there.filter { sizes[it] <= ANCHOR_CHARACTERS }.ifEmpty { there }
        if (usable.isEmpty()) return emptyList()
        val picked = ArrayList<Int>(wanted)
        val pickedSet = HashSet<Int>(wanted)
        fun takeMatching(predicate: (Int) -> Boolean) {
            for (index in usable) {
                if (picked.size >= wanted) return
                if (index !in anchored && index !in pickedSet && predicate(index)) {
                    picked += index
                    pickedSet += index
                }
            }
        }
        fun isRichForHard(index: Int): Boolean {
            if (difficulty != WorksheetDifficulty.Hard) return true
            val target = lines[index].targetText
            if (target.any { it in '\u0E00'..'\u0EFF' || it in '\u1000'..'\u109F' || it in '\u1780'..'\u17FF' || it in '\u3040'..'\u30FF' || it in '\u3400'..'\u4DBF' || it in '\u4E00'..'\u9FFF' }) {
                return target.length >= 2
            }
            val words = normalizedWords(target)
            return words.size >= 2 || (words.singleOrNull()?.length ?: 0) >= 5
        }
        // First the cards no sentence of this sheet was built from yet (preferring studied cards that weren't
        // anchored on a recent sheet, then studied cards that were, before unstudied cards; on Hard, bare short
        // interjections like "Hi" or "Ja" wait behind multi-word or content cards), then the ones this prompt
        // doesn't have yet; only an island with fewer cards than the prompt wants has a card twice.
        if (recentAnchors.isEmpty()) {
            if (difficulty == WorksheetDifficulty.Hard) takeMatching(::isRichForHard)
            takeMatching { true }
        } else {
            if (difficulty == WorksheetDifficulty.Hard) takeMatching { needs[it] != NOT_STUDIED && lines[it].historyKey() !in recentAnchors && isRichForHard(it) }
            takeMatching { needs[it] != NOT_STUDIED && lines[it].historyKey() !in recentAnchors }
            if (difficulty == WorksheetDifficulty.Hard) takeMatching { needs[it] != NOT_STUDIED && isRichForHard(it) }
            takeMatching { needs[it] != NOT_STUDIED }
            if (difficulty == WorksheetDifficulty.Hard) takeMatching { lines[it].historyKey() !in recentAnchors && isRichForHard(it) }
            takeMatching { lines[it].historyKey() !in recentAnchors }
            takeMatching { true }
        }
        val inPrompt = (already + picked).toHashSet()
        for (index in usable) if (picked.size >= wanted) break else if (inPrompt.add(index)) picked += index
        while (picked.size < wanted) picked += usable[(already.size + picked.size) % usable.size]
        anchored += picked
        return picked
    }

    /** The islands' anchors as the prompt lists them: the islands take turns, so the sheet is mixed. */
    private fun anchors(byIsland: List<List<Int>>): List<WorksheetAnchor> {
        val mixed = ArrayList<WorksheetAnchor>()
        var turn = 0
        while (true) {
            val before = mixed.size
            for (island in byIsland) island.getOrNull(turn)?.let { mixed += WorksheetAnchor(lines[it], notes[it]) }
            if (mixed.size == before) return mixed
            turn++
        }
    }

    /**
     * An island's [cards] in the order a prompt takes them: the studied ones first, drawn by their need (the
     * more, the earlier, most of the time), then the ones never studied, shuffled. A different order every
     * time, so the prompts of a sheet don't all show the same cards of a large island.
     */
    private fun inPickOrder(cards: List<Int>): List<Int> {
        // A weighted draw without putting back is a sort by -ln(u)/weight (Efraimidis & Spirakis, 2006).
        for (index in cards) {
            val need = needs[index]
            draw[index] = if (need == NOT_STUDIED) NEVER_STUDIED + random.nextDouble() else -ln(1 - random.nextDouble()) / (STUDIED_PICK + need)
        }
        return cards.sortedBy { draw[it] }
    }

    internal companion object {
        /** " = " and the line break around a card's two sides. */
        const val SEPARATOR_LENGTH = 4

        /** A card longer than this (both sides) is a paragraph, not something to build one sentence from. */
        const val ANCHOR_CHARACTERS = 160

        /**
         * A studied card's weight in a prompt's draw before its need is added: a card that needs all the
         * practice there is (1) is five times as likely to be drawn next as one that needs none.
         */
        const val STUDIED_PICK = 0.25

        /** Sorts the cards never studied behind every studied one, whose keys are far below it. */
        const val NEVER_STUDIED = 1e9

        /** Stands for a card's need where the card was never studied. */
        const val NOT_STUDIED = -1.0

        /**
         * A card's text on one line, as the prompt lists one card per line. Nearly every text is on one line
         * already and is used as it is, so a large coast's texts aren't all held twice.
         */
        fun oneLine(text: String): String {
            val oneLine = text.none { it == '\n' || it == '\r' } && text.firstOrNull()?.isWhitespace() != true && text.lastOrNull()?.isWhitespace() != true
            return if (oneLine) text else text.lines().joinToString(" ") { it.trim() }.trim()
        }
    }
}

/**
 * Estimates the learner's CEFR level ([Level.A1], [Level.A2], [Level.B1]) on custom or imported coasts
 * where built-in route islands account for fewer than half of the cards. Returns `null` until enough cards
 * have been studied and retained ([MIN_STUDIED_FOR_LEVEL], [MIN_MATURE_FOR_LEVEL]) so a freshly imported
 * deck never claims a level its owner hasn't earned yet.
 */
internal fun estimateWorksheetLevel(cards: List<WorksheetSourceCard>): Level? {
    val studied = cards.filter(::isStudied)
    if (studied.size < MIN_STUDIED_FOR_LEVEL) return null
    val mature = studied.filter { val s = it.stability; s != null && s.isFinite() && s >= MATURE_STABILITY_DAYS }
    if (mature.size < MIN_MATURE_FOR_LEVEL) return null
    val avgStability = mature.sumOf { it.stability ?: 0.0 } / mature.size
    val avgWords = mature.sumOf { card ->
        val unspaced = card.targetText.count {
            it in '\u0E00'..'\u0EFF' || it in '\u1000'..'\u109F' || it in '\u1780'..'\u17FF' ||
                it in '\u3040'..'\u30FF' || it in '\u3400'..'\u4DBF' || it in '\u4E00'..'\u9FFF'
        }
        if (unspaced > 0) unspaced / 2.5 else normalizedWords(card.targetText).size.toDouble()
    } / mature.size
    return when {
        (mature.size >= B1_MATURE_CARDS && avgStability >= B1_AVG_STABILITY_DAYS && avgWords >= B1_AVG_WORDS) ||
            (mature.size >= B1_BROAD_MATURE_CARDS && avgStability >= A2_AVG_STABILITY_DAYS) -> Level.B1
        (mature.size >= A2_MATURE_CARDS && avgStability >= A2_AVG_STABILITY_DAYS && avgWords >= A2_AVG_WORDS) ||
            (mature.size >= A2_BROAD_MATURE_CARDS && avgStability >= MATURE_STABILITY_DAYS) -> Level.A2
        else -> Level.A1
    }
}

private const val MIN_STUDIED_FOR_LEVEL = 15
private const val MIN_MATURE_FOR_LEVEL = 10
private const val MATURE_STABILITY_DAYS = 3.0
private const val A2_MATURE_CARDS = 50
private const val A2_BROAD_MATURE_CARDS = 80
private const val A2_AVG_STABILITY_DAYS = 7.0
private const val A2_AVG_WORDS = 4.0
private const val B1_MATURE_CARDS = 150
private const val B1_BROAD_MATURE_CARDS = 250
private const val B1_AVG_STABILITY_DAYS = 14.0
private const val B1_AVG_WORDS = 6.0
