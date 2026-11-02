package com.andreaserick.languagecoast.notifications

import kotlinx.datetime.LocalDate

/** What a study reminder is about, which decides how it looks (icon, colour) as well as what it says. */
enum class ReminderKind {
    /** Cards are due; the everyday reminder. */
    Due,
    /** Quotes one due card as a quick self-test. */
    Teaser,
    /** The streak is a few days old and today hasn't been studied yet. */
    StreakAtRisk,
    /** Studying today reaches a round streak number. */
    Milestone,
    /** Nothing is due. */
    CaughtUp
}

/**
 * A card the reminder can quote, with where it lives.
 *
 * @property cardId The card, so the reminder's buttons can grade it; 0 if unknown.
 * @property targetText The answer, shown when the reminder is asked to reveal it.
 */
data class ReminderCard(
    val nativeText: String,
    val language: String,
    val islandName: String,
    val emoji: String,
    val cardId: Int = 0,
    val targetText: String = ""
)

/**
 * A study reminder's content.
 *
 * @property emoji Stands for the reminder where the platform shows a picture next to it.
 * @property teaser The card a [ReminderKind.Teaser] quotes, for its Reveal and grade buttons.
 */
data class ReminderMessage(val kind: ReminderKind, val title: String, val text: String, val emoji: String, val teaser: ReminderCard? = null)

/** Streak lengths worth a reminder of their own, reached by studying today. */
private val MILESTONES = setOf(3, 7, 14, 30, 50, 100, 150, 200, 365, 500, 730, 1000)

/**
 * The reminder for [day]: one of several designs, picked from what is true that day and rotated so
 * that two days in a row never look the same.
 *
 * @param dueCount Cards due across all coasts.
 * @param streak The current streak, which [studiedToday] says whether today already counts towards.
 * @param teaser A due card to quote, or null if there is none.
 */
fun reminderMessage(dueCount: Int, streak: Int, studiedToday: Boolean, teaser: ReminderCard?, day: LocalDate): ReminderMessage {
    // Changes every day, so both the design and its wording move on.
    val seed = day.year * 366 + day.dayOfYear
    val cards = if (dueCount == 1) "1 card" else "$dueCount cards"

    if (dueCount == 0) {
        val variants = listOf(
            "All caught up" to "Nothing is due today. Your islands are explored.",
            "Smooth sailing" to "No cards waiting. Add a sentence you'd like to say?",
            "Quiet coast today" to "Nothing to review. Come back tomorrow, or practice an island anyway."
        )
        val (title, text) = variants[seed % variants.size]
        return ReminderMessage(ReminderKind.CaughtUp, title, text, "🏝️")
    }

    if (!studiedToday && streak + 1 in MILESTONES) {
        val next = streak + 1
        return ReminderMessage(
            kind = ReminderKind.Milestone,
            title = "Day $next is today",
            text = "Finish an island and your streak reaches $next days. $cards waiting.",
            emoji = "🏆"
        )
    }

    // Everything else takes turns, day by day.
    val kinds = buildList {
        add(ReminderKind.Due)
        if (teaser != null) add(ReminderKind.Teaser)
        if (!studiedToday && streak >= 3) add(ReminderKind.StreakAtRisk)
    }
    val kind = kinds[seed % kinds.size]
    val variant = seed / kinds.size

    return when (kind) {
        ReminderKind.Teaser -> {
            checkNotNull(teaser)
            val titles = listOf("Still know this one?", "Quick one", "Say it in ${teaser.language}")
            ReminderMessage(
                kind = kind,
                title = titles[variant % titles.size],
                text = "“${teaser.nativeText}” in ${teaser.language} · ${teaser.emoji} ${teaser.islandName}",
                emoji = teaser.emoji,
                teaser = teaser
            )
        }
        ReminderKind.StreakAtRisk -> {
            val titles = listOf("Your $streak-day streak ends tonight", "$streak days and counting", "Don't break the chain")
            ReminderMessage(
                kind = kind,
                title = titles[variant % titles.size],
                text = "Finish one island today to keep it. $cards waiting.",
                emoji = "🔥"
            )
        }
        else -> {
            val titles = listOf("$cards waiting", "Time for a quick review", "Your coasts miss you", "Five minutes is all it takes")
            val texts = listOf(
                "$cards due for review today.",
                "Review $cards and they'll stick a little longer.",
                "$cards due. Start with the busiest island."
            )
            ReminderMessage(kind = ReminderKind.Due, title = titles[variant % titles.size], text = texts[variant % texts.size], emoji = "🌅")
        }
    }
}

/**
 * Reminders for platforms that schedule them days ahead, without knowing what will be due: one per
 * [dayIndex] of the coming week, each worded differently.
 */
fun scheduledReminderMessage(dayIndex: Int): ReminderMessage {
    val variants = listOf(
        "Time to study" to "A few minutes on your coast keeps the words from drifting away.",
        "Your coasts miss you" to "Review today's cards before they're forgotten.",
        "Quick review?" to "Flip through what's due and keep your streak alive.",
        "Five minutes is all it takes" to "Finish one island today.",
        "Still remember yesterday's cards?" to "Find out: open Language Coast and review.",
        "Keep the chain going" to "Study today and your streak grows by one.",
        "Words fade, reviews fix that" to "Your due cards are waiting."
    )
    val (title, text) = variants[dayIndex.mod(variants.size)]
    return ReminderMessage(ReminderKind.Due, title, text, "🌅")
}
