package com.andreaserick.languagecoast.data

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.preparePost
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/*
 * Sending a prompt to a cloud AI provider and reading its answer. Every provider gets the same treatment here:
 * the answer is read as a stream while it is written, a request that failed for a moment is sent again, the
 * time limits depend on what is asked, an answer cut off in mid-line keeps only its whole lines, and every
 * way of failing becomes a [Completion] the rest of the app knows: an answer, busy (wait and ask again), too
 * large (ask for less) or failed. What differs between providers is only the wire format, which a
 * [CloudWire] describes.
 */

/**
 * How long a request may take.
 *
 * @property totalMillis The longest an answer may take in all. A streamed answer is read as it comes, so for
 *     [Batch] this is a backstop against an answer that never ends, not the time a slow model is given.
 * @property silenceMillis How long the provider may send nothing at all. While a model thinks the stream can
 *     stay quiet for minutes; a connection that is gone is still noticed.
 */
data class Patience(val totalMillis: Long, val silenceMillis: Long) {
    companion object {
        /** One sentence's translation: the screen that waits for it has no way out, so it keeps two minutes. */
        val Sentence = Patience(totalMillis = 120_000L, silenceMillis = 125_000L)

        /** An answer a model may think over for minutes: a batch of practice-sheet sentences, an island, a check. */
        val Batch = Patience(totalMillis = 600_000L, silenceMillis = 300_000L)
    }
}

/** How an answer ended, as far as what it brought is concerned. */
sealed interface Ending {
    /** It said it was done. */
    data object Done : Ending

    /** It ran out of room (the output limit, which a model's thinking uses too): it may stop in mid-line. */
    data object OutOfRoom : Ending

    /** The stream ended without saying why: it broke off on the way, and may stop in mid-line. */
    data object BrokeOff : Ending

    /** The provider would not answer this; [message] says so to the user. */
    data class Declined(val message: String) : Ending
}

/**
 * What an answer from [providerName] that ended as [ending] with [text] comes to. An answer that ran out of room
 * or broke off keeps the lines before the one it stopped in, so half a sentence never gets onto a card or a sheet.
 */
internal fun answerOutcome(providerName: String, text: String, ending: Ending): Completion {
    if (ending is Ending.Declined) return Completion.failure(ending.message)
    val cut = ending == Ending.OutOfRoom || ending == Ending.BrokeOff
    val whole = if (cut) text.substringBeforeLast('\n', missingDelimiterValue = "") else text
    if (whole.isBlank()) {
        return when (ending) {
            Ending.OutOfRoom -> Completion.tooLarge("$providerName ran out of room before it had answered. Ask for less at a time, or pick another model in Settings.")
            Ending.BrokeOff -> Completion.failure("$providerName's answer broke off. Check your internet connection and try again.")
            else -> Completion.failure("$providerName returned an empty answer. Try again or pick another model.")
        }
    }
    return Completion(whole.trim())
}

/**
 * An answer read from a stream of server-sent events, one line at a time. Only `data:` lines carry anything;
 * a provider's subclass reads each event's payload and hands over the pieces of text with [append], and
 * says how the answer ended ([ending]) or that it failed ([failure]).
 *
 * [onPartialText] gets the text so far whenever a piece brings a line break, and once more at the end.
 */
internal abstract class StreamedAnswer(private val providerName: String, private val onPartialText: ((String) -> Unit)?) {
    private val text = StringBuilder()
    private var sawEvent = false
    protected var ending: Ending? = null
    protected var failure: Completion? = null

    fun read(line: String) {
        if (!line.startsWith("data:")) return
        val payload = line.substring(5).trim()
        if (payload.isEmpty() || payload == "[DONE]") return
        if (event(payload)) sawEvent = true
    }

    /** Reads one event's [payload]; false if it wasn't one this provider sends. */
    protected abstract fun event(payload: String): Boolean

    protected fun append(piece: String) {
        text.append(piece)
        if ('\n' in piece) flush()
    }

    protected fun flush() {
        if (text.isNotEmpty()) onPartialText?.invoke(text.toString())
    }

    /** The answer's text as it is handed over at the end; a provider whose models think out loud leaves that out. */
    protected open fun finalText(text: String): String = text

    fun completion(): Completion {
        failure?.let { return it }
        // A stream that ends without saying why broke off on the way, unless nothing came at all.
        val end = ending ?: if (sawEvent) Ending.BrokeOff else Ending.Done
        return answerOutcome(providerName, finalText(text.toString()), end)
    }
}

/**
 * One provider's wire format: where a prompt goes, how it is asked for, and how the answer reads. A wire is
 * made for one request (it carries the key and the model).
 */
internal interface CloudWire {
    /** The provider's name as the user knows it, used in every message about it. */
    val providerName: String

    /** Fills in [builder]'s address, headers and body to ask [prompt]. */
    fun request(builder: HttpRequestBuilder, prompt: String)

    /** A reader for the answer when it comes as a stream. */
    fun stream(onPartialText: ((String) -> Unit)?): StreamedAnswer

    /** Reads an answer that came whole, or an error, with its HTTP [status] and `retry-after` header. */
    fun answer(status: Int, body: String, retryAfter: String?): Completion

    /**
     * Another way to ask after the provider turned the request down with [status] and [body] for good, e.g. a
     * model that was retired or a model that may not be streamed; null when there is none.
     */
    fun instead(status: Int, body: String): CloudWire? = null
}

/**
 * Whether an HTTP [status] is a failure of the moment worth sending the same request again for at once: the
 * provider's gateway or the provider itself is overloaded, or a rate limit refills within two seconds. A
 * timeout is not (a smaller request may go through; see [Completion.tooLarge]), nor is a longer rate limit,
 * which the work that sends many requests waits out itself (see [completeWaiting]).
 */
internal fun isWorthRetryingNow(status: Int, retryAfter: String?): Boolean =
    status == 502 || status == 503 || status == 529 ||
        (status == 429 && (parseRetryAfterMillis(retryAfter) ?: 0L) <= 2_000L)

/** Sends prompts to cloud providers over [client]; see the top of this file. */
internal class CloudTransport(private val client: HttpClient) {

    /** [wire]'s answer to [prompt]. Never throws, except to pass on the caller's own cancellation. */
    suspend fun ask(wire: CloudWire, prompt: String, patience: Patience, onPartialText: ((String) -> Unit)? = null): Completion {
        val first = attemptWithRetries(wire, prompt, patience, onPartialText)
        if (first is Attempt.Turned) {
            wire.instead(first.status, first.body)?.let { other ->
                logError(TAG, "${wire.providerName} answered ${first.status}; asking another way")
                return attemptWithRetries(other, prompt, patience, onPartialText).toCompletion(other)
            }
        }
        return first.toCompletion(wire)
    }

    private sealed interface Attempt {
        data class Done(val completion: Completion) : Attempt
        data class Turned(val status: Int, val body: String, val retryAfter: String?) : Attempt

        fun toCompletion(wire: CloudWire): Completion = when (this) {
            is Done -> completion
            is Turned -> wire.answer(status, body, retryAfter)
        }
    }

    private suspend fun attemptWithRetries(wire: CloudWire, prompt: String, patience: Patience, onPartialText: ((String) -> Unit)?): Attempt =
        retryHttpWithBackoff(
            shouldRetry = { it is Attempt.Turned && isWorthRetryingNow(it.status, it.retryAfter) },
            retryAfterMillis = { (it as? Attempt.Turned)?.let { turned -> parseRetryAfterMillis(turned.retryAfter) } }
        ) { attempt(wire, prompt, patience, onPartialText) }

    private suspend fun attempt(wire: CloudWire, prompt: String, patience: Patience, onPartialText: ((String) -> Unit)?): Attempt {
        val name = wire.providerName
        // Set once the provider has begun to answer. A time limit that runs out before that is a connection that
        // couldn't be made, which both platforms report as a timeout too: asking for less would not help then.
        var answering = false
        return try {
            client.preparePost {
                wire.request(this, prompt)
                timeout {
                    requestTimeoutMillis = patience.totalMillis
                    socketTimeoutMillis = patience.silenceMillis
                }
            }.execute { response ->
                answering = true
                val status = response.status.value
                val isStream = response.contentType()?.match(ContentType.Text.EventStream) == true
                when {
                    status >= 400 -> Attempt.Turned(status, response.bodyAsText(), response.headers["retry-after"])
                    !isStream -> Attempt.Done(wire.answer(status, response.bodyAsText(), response.headers["retry-after"]))
                    else -> {
                        val stream = wire.stream(onPartialText)
                        val channel = response.bodyAsChannel()
                        while (true) {
                            val line = channel.readUTF8Line() ?: break
                            stream.read(line)
                        }
                        Attempt.Done(stream.completion())
                    }
                }
            }
        } catch (e: CancellationException) {
            // Ours to pass on if this coroutine was cancelled. If it wasn't, the request was cancelled under us,
            // which is how a time limit ends an answer that is being read.
            currentCoroutineContext().ensureActive()
            logError(TAG, "$name took too long to answer", e)
            Attempt.Done(Completion.tooLarge(tookTooLongMessage(name)))
        } catch (e: Exception) {
            // Everything: this must never throw, and a key pasted with a line break in it, for one, fails before anything is sent.
            if (answering && ranOutOfTime(e)) {
                logError(TAG, "$name took too long to answer", e)
                Attempt.Done(Completion.tooLarge(tookTooLongMessage(name)))
            } else {
                logError(TAG, "$name request failed", e)
                Attempt.Done(Completion.failure("Couldn't reach $name. Check your internet connection and try again."))
            }
        }
    }

    private companion object {
        const val TAG = "CloudTransport"
    }
}

/** What the user is told when [providerName] didn't answer within the time a request is given. */
internal fun tookTooLongMessage(providerName: String): String = "$providerName took too long to answer. Try again or pick a faster model."

/**
 * Whether [error] says an answer took too long or stopped coming, rather than failing to connect at all.
 * Each platform's networking says that its own way, and while an answer is being read it arrives wrapped
 * ("channel closed: Read timed out"), so the causes are looked through as well.
 */
internal fun ranOutOfTime(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.take(8).any {
        val name = it::class.simpleName.orEmpty()
        val msg = it.message.orEmpty()
        name != "ConnectTimeoutException" && !msg.contains("connect timed out", ignoreCase = true) &&
            (it is HttpRequestTimeoutException || it is SocketTimeoutException ||
                name.contains("Timeout") || msg.contains("timed out", ignoreCase = true))
    }

/** Words in a provider's own error text that say the account, not the request, is the problem. */
private val ACCOUNT_WORDS = listOf("credit", "billing", "quota", "balance", "payment")

/**
 * What the user is told when [providerName] turned a request down for a reason this app has no message of
 * its own for. The provider's own text ([detail]) is English, technical and often long, so it goes to the
 * log and not on screen. The one thing read out of it is an account without credit, which only the user can fix.
 */
internal fun providerErrorMessage(providerName: String, status: Int, detail: String?): String {
    logError("CloudTransport", "$providerName answered $status: ${detail.orEmpty()}")
    val aboutAccount = detail != null && ACCOUNT_WORDS.any { detail.contains(it, ignoreCase = true) }
    return when {
        status == 402 || aboutAccount ->
            "Your $providerName account is out of credit or over its quota. Check your plan with $providerName, or pick another provider in Settings."
        status == 403 -> "$providerName didn't allow this request. Check that your key can use the selected model, or pick another model in Settings."
        status >= 500 -> "$providerName is having trouble right now. Try again in a moment."
        else -> "$providerName couldn't answer. Try again, or pick another model in Settings."
    }
}

/**
 * This answer read as a translation: [parseTranslationResponse] of its text, or the failure it already is.
 * An answer that doesn't parse is reported with [unreadable].
 */
internal fun Completion.toTranslation(unreadable: String = "The model's answer couldn't be read. Try again or pick another model."): TranslationResult {
    val answer = text ?: return TranslationResult.failure(errorMessage ?: "The model sent no answer.")
    return parseTranslationResponse(answer).let { if (it.isSuccess) it else TranslationResult.failure(unreadable) }
}
