package com.andreaserick.languagecoast.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Which islands a practice sheet is about, and how much.
 *
 * A sheet's sentences are each built from one island's cards, so "what should this sheet practise?" comes
 * down to how many sentences every island gets. They are shared out by how much practice an island's cards
 * need, which the app knows from studying them:
 *
 * 1. A card needs practice when it is slipping (the chance of recalling it today, which FSRS models, has
 *    dropped) or when it keeps being forgotten ([practiceNeed]). Recalling something does most for memory
 *    when it takes effort and still works, so the need grows as recall drops, but only down to
 *    [RECALL_FLOOR]: below that a card is not practised but learned again, and counts no more than at it.
 * 2. An island's focus is the need of its cards added up, on top of a small share that every island has
 *    for the cards there are to build from, and then damped ([islandFocus]). The damping is what keeps a
 *    sheet mixed, which serves grammar better than one topic in a row: an island with four times the need
 *    gets twice the sentences, not four times.
 * 3. The sentences are dealt to the islands in those proportions by stride scheduling ([FocusPlanner]):
 *    evenly through the sheet, so that every batch of a long sheet has the same mix, exactly in the long
 *    run, and with every island coming up sooner or later.
 *
 * A card that was never studied has no need: a sheet is practice for what the learner has met, not a first
 * meeting. A coast nobody has studied yet has no needs at all, and its islands share the sheet by what
 * there is on them, as they did before there was any of this.
 */

/** Whether the learner has studied [card] at least once. */
internal fun isStudied(card: WorksheetSourceCard): Boolean = card.stability != null && card.lastReviewed != null

/**
 * How much [card] needs practice at [now] (epoch millis), from 0 (never studied, or fresh in memory and
 * never a problem) to 1 (long overdue and forgotten again and again).
 *
 * [SLIPPING_SHARE] of it is how far the card has slipped: nothing right after a review, a third on the day
 * it comes due (recall at 90%), all of it once recall is down to [RECALL_FLOOR]. The rest is how hard the
 * card has been so far, by its lapses or by the difficulty FSRS gives it, whichever says more; a card
 * counts as leaky ([isLeaky]) at about half of that.
 */
internal fun practiceNeed(card: WorksheetSourceCard, now: Long): Double {
    val stability = card.stability ?: return 0.0
    val lastReviewed = card.lastReviewed ?: return 0.0
    val elapsedDays = max(0.0, (now - lastReviewed) / MILLIS_PER_DAY)
    val recall = Fsrs.retrievability(elapsedDays, max(stability, 0.01))
    val slipping = ((1 - recall) / (1 - RECALL_FLOOR)).coerceIn(0.0, 1.0)
    val byLapses = min(card.lapses, STRUGGLE_LAPSES) / STRUGGLE_LAPSES.toDouble()
    val byDifficulty = (((card.difficulty ?: 0.0) - USUAL_DIFFICULTY) / (MAX_DIFFICULTY - USUAL_DIFFICULTY)).coerceIn(0.0, 1.0)
    return SLIPPING_SHARE * slipping + (1 - SLIPPING_SHARE) * max(byLapses, byDifficulty)
}

/**
 * The weight of an island with [cardCount] cards whose needs ([practiceNeed]) add up to [need], when a
 * sheet's sentences are shared out.
 *
 * Every island has a share for its material: [MATERIAL_FOCUS] for [MATERIAL_CARDS] cards or more, less for
 * fewer, so a sheet still gets around islands that need nothing, and an island of three cards isn't asked
 * for as many sentences as one of thirty. The needs come on top, and the square root damps the sum.
 */
internal fun islandFocus(cardCount: Int, need: Double): Double {
    val material = MATERIAL_FOCUS * min(cardCount, MATERIAL_CARDS) / MATERIAL_CARDS
    return sqrt(material + max(0.0, need))
}

/**
 * Deals a sheet's sentences to its islands in proportion to their [weights], by stride scheduling
 * (Waldspurger & Weihl, 1995): an island's stride is how many sentences pass between two of its own, each
 * sentence goes to the island whose next one is due first, and that island then moves on by its stride.
 * Unlike drawing lots, this gives every island its share to within a sentence, spread evenly, so the first
 * ten sentences of a sheet have the mix of all five hundred.
 *
 * Where an island starts within its first stride is drawn from [random], so two sheets from the same coast
 * differ, and an island with a small share is on some short sheets rather than on none.
 */
internal class FocusPlanner(weights: DoubleArray, random: Random) {
    private val stride: DoubleArray
    private val next: DoubleArray

    init {
        // An island without a usable weight still gets a turn, a rare one.
        val usable = DoubleArray(weights.size) { if (weights[it].isFinite() && weights[it] > MIN_WEIGHT) weights[it] else MIN_WEIGHT }
        val total = usable.sum()
        stride = DoubleArray(usable.size) { total / usable[it] }
        next = DoubleArray(usable.size) { stride[it] * random.nextDouble() }
    }

    /**
     * How many of the next [sentences] each island gets, by its index. At most [maxIslands] islands get any,
     * for a request that only has room for the cards of so many; the others keep their place in the queue
     * and come first in the next request.
     */
    fun plan(sentences: Int, maxIslands: Int): IntArray {
        val quotas = IntArray(stride.size)
        if (stride.isEmpty()) return quotas
        val room = maxIslands.coerceAtLeast(1)
        var chosen = 0
        repeat(sentences) {
            var first = -1
            for (island in stride.indices) {
                if (quotas[island] == 0 && chosen >= room) continue
                if (first < 0 || next[island] < next[first]) first = island
            }
            if (quotas[first] == 0) chosen++
            quotas[first]++
            next[first] += stride[first]
        }
        return quotas
    }

    /** When island [island]'s next sentence is due: the smaller, the sooner. */
    fun due(island: Int): Double = next[island]

    /** Saves the scheduler's cursor so a failed request can roll back without burning island turns. */
    fun snapshot(): DoubleArray = next.copyOf()

    /** Restores a cursor captured with [snapshot]. */
    fun restore(saved: DoubleArray) {
        saved.copyInto(next)
    }

    private companion object {
        const val MIN_WEIGHT = 1e-6
    }
}

/** Recall below this is no longer practice but learning again: a card's need stops growing here. */
private const val RECALL_FLOOR = 0.7

/** How much of a card's need is how far it has slipped; the rest is how hard it has been. */
private const val SLIPPING_SHARE = 2.0 / 3.0

/** Lapses from which a card counts as having been as hard as can be. */
private const val STRUGGLE_LAPSES = 4

/** The difficulty FSRS gives a card that is first graded Good, about; a card is harder the further it is above it. */
private const val USUAL_DIFFICULTY = 5.5
private const val MAX_DIFFICULTY = 10.0

/** An island's share for its material: as much as the full need of this many cards... */
private const val MATERIAL_FOCUS = 4.0

/** ...once it has this many cards, which is as many as a prompt shows of an island. */
private const val MATERIAL_CARDS = 40

private const val MILLIS_PER_DAY = 86_400_000.0
