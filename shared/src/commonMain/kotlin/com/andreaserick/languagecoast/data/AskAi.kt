package com.andreaserick.languagecoast.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/*
 * Asking the AI provider picked in Settings something other than a translation: to explain a word, check a card,
 * answer a question, write sentences. A feature gives the prompt and how to read the answer; everything around it
 * is here, once: which provider and engine, that on-device translation can't answer, waiting out a provider that
 * is busy for now, and what the user is told when there is no answer or it can't be read.
 */

/**
 * What asking came to: [value] read from the answer, or [error] in words for the user.
 *
 * @property isTooLarge The request was more than the model could answer in the time or room it has; asking for less
 *     can go through (see [Completion.tooLarge]).
 */
data class AiReply<out T : Any>(val value: T?, val error: String?, val isTooLarge: Boolean = false) {
    companion object {
        fun failed(message: String): AiReply<Nothing> = AiReply(null, message)
    }
}

/** The AI provider picked in Settings, ready to be asked, or why it can't be. */
sealed interface AiConnection {
    val provider: TranslationProvider

    /** The provider can't answer: it needs setting up, or it only translates. */
    data class Unavailable(override val provider: TranslationProvider, val message: String) : AiConnection

    /**
     * The provider, ready with [engine] (its key and model as they were when it connected). Work that runs for
     * minutes keeps one connection; each request is still checked against the permission to send (see
     * [RoutingTranslator]).
     */
    class Ready internal constructor(
        override val provider: TranslationProvider,
        val engine: TranslationEngine,
        private val translator: Translator
    ) : AiConnection {

        /** How much the provider's models take on at once; see [ProviderCapacity]. */
        val capacity: ProviderCapacity get() = ProviderCapacity.of(provider)

        /**
         * Asks [prompt] and reads the answer with [parse] (null: it can't be read).
         *
         * @param waitWhileBusy Whether a provider that turns the request away only for now (a per-minute limit,
         *     or it is overloaded) is asked again after the wait it names: for work that sends many requests in a row.
         * @param onPartialText Gets the answer so far as lines of it arrive, from a provider that streams.
         */
        suspend fun <T : Any> ask(
            prompt: String,
            waitWhileBusy: Boolean = false,
            onPartialText: ((String) -> Unit)? = null,
            parse: (String) -> T?
        ): AiReply<T> {
            val send: suspend () -> Completion =
                if (onPartialText == null) { { translator.complete(engine, prompt) } } else { { translator.complete(engine, prompt, onPartialText) } }
            val completion = if (waitWhileBusy) waitingWhileBusy(ask = send) else send()
            // An empty answer, or one of nothing but formatting, is no answer, not one that couldn't be read.
            val text = completion.text?.takeIf { it.replace("```", "").replace("**", "").isNotBlank() }
                ?: return AiReply(null, completion.errorMessage ?: noAnswer(provider), isTooLarge = completion.isTooLarge)
            return parse(text)?.let { AiReply(it, null) } ?: AiReply(null, unreadable(provider))
        }
    }
}

/** Asks the provider picked in Settings; see the top of this file. */
class AskAi(private val translator: Translator, private val settings: SettingsRepository) {

    /** Connects to [provider], by default the one picked in Settings. */
    suspend fun connect(provider: TranslationProvider? = null): AiConnection {
        val picked = provider ?: settings.translationProvider.first()
        return when (val choice = settings.currentEngine(picked)) {
            is EngineChoice.Missing -> AiConnection.Unavailable(picked, choice.message)
            is EngineChoice.Ready ->
                if (choice.engine == TranslationEngine.OnDevice) AiConnection.Unavailable(picked, ON_DEVICE_CANNOT_ANSWER)
                else AiConnection.Ready(picked, choice.engine, translator)
        }
    }

    /** Connects to the provider picked in Settings and asks it [prompt] once; see [AiConnection.Ready.ask]. */
    suspend fun <T : Any> ask(prompt: String, parse: (String) -> T?): AiReply<T> =
        when (val connection = connect()) {
            is AiConnection.Unavailable -> AiReply.failed(connection.message)
            is AiConnection.Ready -> connection.ask(prompt, parse = parse)
        }
}

/** What the user is told when on-device translation is asked something it can't do. */
const val ON_DEVICE_CANNOT_ANSWER = "On-device translation only translates. Pick an AI provider in Settings for this."

/** What the user is told when [provider] came back with neither an answer nor a reason. */
fun noAnswer(provider: TranslationProvider): String = "${provider.label} gave no answer. Try again."

/** What the user is told when [provider]'s answer couldn't be read. */
fun unreadable(provider: TranslationProvider): String = "${provider.label}'s answer couldn't be read. Try again."

/** How often in a row [waitingWhileBusy] waits for a provider that turns a request away for now. */
const val BUSY_MAX_WAITS = 4

/** The longest [waitingWhileBusy] waits each time, whatever the provider asks for. */
const val BUSY_MAX_WAIT_SECONDS = 60

/**
 * [ask]'s answer, asked again while the provider turns it away only for now ([Completion.retryAfterSeconds]: a
 * per-minute limit, or it is overloaded), after the wait it names, up to [maxWaits] times. Any other failure, and a
 * wait that didn't help, comes back as it is.
 */
internal suspend fun waitingWhileBusy(maxWaits: Int = BUSY_MAX_WAITS, ask: suspend () -> Completion): Completion {
    var completion = ask()
    var waits = 0
    while (completion.text == null && waits < maxWaits) {
        val seconds = completion.retryAfterSeconds?.coerceIn(1, BUSY_MAX_WAIT_SECONDS) ?: break
        delay(seconds * 1_000L)
        waits++
        completion = ask()
    }
    return completion
}

/** [Translator.complete] with [waitingWhileBusy], for work that sends many requests in a row with an engine of its own. */
suspend fun Translator.completeWaiting(engine: TranslationEngine, prompt: String, maxWaits: Int = BUSY_MAX_WAITS): Completion =
    waitingWhileBusy(maxWaits) { complete(engine, prompt) }
