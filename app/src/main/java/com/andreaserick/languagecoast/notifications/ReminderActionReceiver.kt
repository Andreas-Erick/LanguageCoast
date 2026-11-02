package com.andreaserick.languagecoast.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.reviewOutcomes
import com.andreaserick.languagecoast.widget.StudyWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

/**
 * Handles the buttons on a teaser reminder: "Reveal" shows the quoted card's answer, "Got it" and
 * "Forgot" grade it (Good or Again) without opening the app.
 */
class ReminderActionReceiver : BroadcastReceiver(), KoinComponent {

    private val flashcards: FlashcardRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val teaser = ReminderCard(
            nativeText = intent.getStringExtra(EXTRA_NATIVE).orEmpty(),
            language = intent.getStringExtra(EXTRA_LANGUAGE).orEmpty(),
            islandName = intent.getStringExtra(EXTRA_ISLAND).orEmpty(),
            emoji = intent.getStringExtra(EXTRA_EMOJI).orEmpty(),
            cardId = intent.getIntExtra(EXTRA_CARD_ID, 0),
            targetText = intent.getStringExtra(EXTRA_TARGET).orEmpty()
        )
        when (intent.action) {
            ACTION_REVEAL -> NotificationHelper.showRevealedTeaser(context, teaser)
            ACTION_GOOD -> grade(context, teaser.cardId, Grade.Good)
            ACTION_AGAIN -> grade(context, teaser.cardId, Grade.Again)
        }
    }

    /** Saves [grade] for card [cardId] and takes the reminder down; the receiver stays alive until that is done. */
    private fun grade(context: Context, cardId: Int, grade: Grade) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val placed = flashcards.observeAllCards().first().firstOrNull { it.card.cardId == cardId }
                if (placed != null) {
                    val reviewed = reviewOutcomes(placed.card, Clock.System.now(), TimeZone.currentSystemDefault()).getValue(grade).card
                    flashcards.saveReview(reviewed)
                }
                NotificationHelper.dismiss(context)
                StudyWidgetProvider.requestUpdate(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REVEAL = "com.andreaserick.languagecoast.REVEAL"
        const val ACTION_GOOD = "com.andreaserick.languagecoast.GOOD"
        const val ACTION_AGAIN = "com.andreaserick.languagecoast.AGAIN"
        private const val EXTRA_CARD_ID = "cardId"
        private const val EXTRA_NATIVE = "native"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_LANGUAGE = "language"
        private const val EXTRA_ISLAND = "island"
        private const val EXTRA_EMOJI = "emoji"

        /** The intent a reminder button sends: [action] for [teaser]. Each action has its own request code, so they don't replace each other. */
        fun pendingIntent(context: Context, action: String, teaser: ReminderCard): PendingIntent {
            val intent = Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_CARD_ID, teaser.cardId)
                putExtra(EXTRA_NATIVE, teaser.nativeText)
                putExtra(EXTRA_TARGET, teaser.targetText)
                putExtra(EXTRA_LANGUAGE, teaser.language)
                putExtra(EXTRA_ISLAND, teaser.islandName)
                putExtra(EXTRA_EMOJI, teaser.emoji)
            }
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
