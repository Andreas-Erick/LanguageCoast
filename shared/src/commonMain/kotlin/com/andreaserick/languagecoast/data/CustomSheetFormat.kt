package com.andreaserick.languagecoast.data

import kotlin.random.Random

/*
 * The prompt that asks a model for a custom sheet's exercises, and reading its answer.
 *
 * A custom sheet practises what the learner's brief asks for ("Plural from the singular"), not the cards
 * themselves: the cards are only the words the model should reach for first. What an exercise must be comes
 * in the order it matters, as on a translation sheet: correct first, then a single right answer that the
 * task doesn't give away, then the length, and the learner's own words last. The 2026-11-15 sheet redesign
 * showed that a model told to reuse words above all bends its sentences to fit them.
 *
 * The reply is one line per exercise, `form || instruction || task || answer || options || word help`, split
 * on `||` as a translation sheet's reply is. The options field is only asked for when multiple choice may
 * appear, and word help only where the sheet prints it, so the parser reads the fields after the answer by
 * the exercise's form rather than by position.
 */

/** The exercise forms a custom sheet can have, in the order a mixed sheet names them. */
internal val CUSTOM_SHEET_FORMS = listOf(ExerciseForm.GapFill, ExerciseForm.MultipleChoice, ExerciseForm.ShortAnswer)

/** The forms a custom sheet of [picked] forms uses: the ones picked, or every one when none was. */
internal fun customSheetForms(picked: Set<ExerciseForm>): List<ExerciseForm> =
    CUSTOM_SHEET_FORMS.filter { it in picked }.ifEmpty { CUSTOM_SHEET_FORMS }

/** Whether a sheet of [picked] forms is "Mixed: let the AI choose": none picked, or every one. */
internal fun isMixed(picked: Set<ExerciseForm>): Boolean = customSheetForms(picked).size == CUSTOM_SHEET_FORMS.size

/** The forms whose ticks show as ticked for [picked]: on a mixed sheet, all of them, since all may appear. */
internal fun tickedForms(picked: Set<ExerciseForm>): Set<ExerciseForm> = customSheetForms(picked).toSet()

/**
 * The forms picked once the learner taps [form]'s tick: it comes off or goes on. The last ticked form stays,
 * since a sheet with no form could have no exercise, and ticking every form is mixed again, stored as none
 * picked so the sheet is back on the default.
 */
internal fun tickForm(picked: Set<ExerciseForm>, form: ExerciseForm): Set<ExerciseForm> {
    val ticked = tickedForms(picked)
    val after = if (form in ticked) ticked - form else ticked + form
    return when {
        after.isEmpty() -> picked
        isMixed(after) -> emptySet()
        else -> after
    }
}

/** A brief longer than this is cut short in the prompt; the box takes a sentence or two, not an essay. */
internal const val BRIEF_LINE = 300

/**
 * The name the prompt gives [language]: Norwegian is written in Bokmål unless the coast's [region] says
 * Nynorsk, since the app has one Norwegian and most learners mean Bokmål.
 */
internal fun customSheetLanguage(language: String, region: String?): String = when {
    language != "Norwegian" -> language
    region?.contains("nynorsk", ignoreCase = true) == true -> "Norwegian Nynorsk"
    else -> "Norwegian Bokmål"
}

/** How long and how built an exercise of [difficulty] is, as the prompt says it after "It is". */
private fun customLengthRule(difficulty: WorksheetDifficulty): String = when (difficulty) {
    WorksheetDifficulty.Easy -> "short: a word, a phrase or one short clause, with one thing to change"
    WorksheetDifficulty.Medium -> "an everyday sentence or phrase, with one thing to get right"
    WorksheetDifficulty.Hard -> "longer: a sentence in two parts, or one with two things to get right at once (such as the case and the ending)"
}

/** What the prompt says an exercise of [form] is. */
private fun formRule(form: ExerciseForm): String = when (form) {
    ExerciseForm.ShortAnswer ->
        "A \"${form.token}\" exercise is a task the learner answers in writing: a word or phrase to change into another form, " +
            "or a short sentence to rewrite. Its answer is exactly what the learner writes, and nothing more."
    ExerciseForm.GapFill ->
        "A \"${form.token}\" exercise is a sentence with exactly one gap, written as $GAP, where a word or a few words are missing. " +
            "The base form of what is missing goes in round brackets right after the gap, such as \"$GAP (base form)\", so the learner knows which word to change. " +
            "Its answer is exactly what goes in the gap, and nothing more."
    // The options have a field of their own: in the task they would be printed twice, and a task that lists
    // them shows its answer, so the check drops it.
    ExerciseForm.MultipleChoice ->
        "A \"${form.token}\" exercise is a sentence with a gap ($GAP) or a short question, with three options: exactly one of them is right, " +
            "and the other two are real forms a learner could mix up with it. The options go in their own field, not in the task. " +
            "Its answer is the right option, written out."
}

/**
 * The prompt that asks a model for [count] exercises in [targetLanguage] that practise what the learner's
 * [brief] asks for, using the words of [cards] where it can.
 *
 * @param forms The exercise forms the exercises may take; see [customSheetForms].
 * @param region Where the language will be used; for Norwegian it also says Bokmål or Nynorsk.
 * @param avoid Tasks already on this sheet, when asking for more.
 * @param level The level the learner has reached on the coast's route, or null when the route says nothing about them.
 * @param wording How the learner speaks of themselves, so an exercise about "I" comes out in the right gender.
 * @param wordHelp Whether to ask for the task's words that are on no card, to print under it.
 */
fun buildCustomSheetPrompt(
    brief: String,
    forms: List<ExerciseForm>,
    cards: List<WorksheetCard>,
    targetLanguage: String,
    nativeLanguage: String,
    count: Int,
    difficulty: WorksheetDifficulty,
    region: String? = null,
    avoid: List<String> = emptyList(),
    level: Level? = null,
    wording: Wording = Wording.Unspecified,
    wordHelp: Boolean = true,
    goal: String? = null
): String {
    val language = customSheetLanguage(targetLanguage, region)
    val where = region?.trim()?.takeIf { it.isNotEmpty() }?.let { " from $it" } ?: ""
    val levelClause = level?.let { " and their level is about ${it.label}" } ?: ""
    val goalClause = goal?.let(WorksheetCardDealer::oneLine)?.takeIf { it.isNotEmpty() }
        ?.let { " Why they are learning $language: \"${shortened(it, GOAL_LINE)}\"." } ?: ""
    val asked = shortened(WorksheetCardDealer.oneLine(brief), BRIEF_LINE)
    val flashcards = cards.joinToString("\n") { "${it.targetText} = ${it.nativeText}" }
    val exercises = if (count == 1) "1 exercise" else "$count exercises"
    val formNames = forms.map { "\"${it.token}\"" }
    val formsSentence = if (forms.size == 1) "Every exercise is a ${formNames.single()} exercise." else
        "Each exercise is one of these forms: ${formNames.joinToString(", ")}; pick the form that suits each exercise best and vary them."
    val intro = """
        You are a native $language speaker$where who teaches $language to adults. You are writing a grammar exercise for one learner. Their native language is $nativeLanguage$levelClause.$goalClause

        What the learner wants to practise, in their own words: "$asked"

        These are the learner's flashcards, one per line, as "$language = $nativeLanguage":
    """.trimIndent()
    val task = "Write $exercises that practise exactly that. $formsSentence"

    val suits = if (level != null) "Its words and grammar suit level ${level.label}." else "Its words and grammar suit the learner, whose level the flashcards show."
    val newWords = if (wordHelp) "; list the ones that are not on the flashcards in the last field" else ""
    val notes = buildList {
        forms.map(::formRule).filter { it.isNotEmpty() }.forEach { add("- $it") }
        add(
            "- The instruction is in $nativeLanguage and says in a few words what to do, such as \"Write the plural.\" " +
                "Exercises that are done the same way have the same instruction, word for word."
        )
        add("- The task, its hints and the answer are in $language.")
        speakerRule(wording, language).takeIf { it.isNotEmpty() }?.let { add(it) }
        if (avoid.isNotEmpty()) add("- Do not repeat any of these, which are already in the exercise: " + avoid.joinToString(" | "))
    }
    val rules = """
        What matters, most important first:
        1. Every exercise practises what the learner asked for, and is correct, natural $language: what a native speaker$where would really say, with every ending, article, case, preposition and the word order right.
        2. It has a single right answer, and the answer is never visible in the task. Where more than one answer would fit, add a short hint in round brackets to the task, such as the base form of the word to change, so that only one does.
        3. It is ${customLengthRule(difficulty)}. $suits
        4. It leans on what the learner knows. When an exercise needs a noun, verb or adjective to work on, take it from the flashcards first. Other common, everyday words are welcome where they are needed$newWords.
        5. No two exercises are the same or differ by a single word.
    """.trimIndent() + notes.joinToString("") { "\n$it" }

    val options = if (ExerciseForm.MultipleChoice in forms) " || [the three options, as \"a) option; b) option; c) option\", or - if it is not multiple choice]" else ""
    val words = if (wordHelp) {
        " || [the $language words of the task that are not on the flashcards, each with its $nativeLanguage meaning, " +
            "as \"word = meaning; word = meaning\", or - if there are none]"
    } else ""
    val form = if (forms.size == 1) forms.single().token else "[form: ${forms.joinToString(", ") { it.token }}]"
    val format = "Answer with exactly one line per exercise and nothing else, in this format:\n" +
        "$form || [instruction in $nativeLanguage] || [task in $language] || [answer in $language]$options$words"

    return "$intro\n\n$flashcards\n\n$task\n\n$rules\n\n$format"
}

/**
 * Parses a model's reply to [buildCustomSheetPrompt]: one exercise per line, as `form || instruction || task
 * || answer`, then the options of a multiple-choice exercise, then word help. It is as forgiving as
 * [parseWorksheetResponse]: numbering, bullets, markdown, brackets, quotes and lines wrapped at `||` are all
 * read. Dropped are the lines whose form isn't one of [ExerciseForm], the format line sent back, exercises
 * with no task or no answer, ones whose task shows the answer, and repeats ([exerciseKey]).
 */
fun parseCustomSheetResponse(text: String): List<Exercise> =
    coalesceWrappedWorksheetLines(text.replace("```", "").replace("**", "").lines())
        .filter { it.contains("||") }
        .mapNotNull(::parseExerciseLine)
        .distinctBy(::exerciseKey)

/**
 * What makes two exercises the same one: the words of the instruction and of the task. The task alone isn't
 * enough, since "der Mann" into the accusative and "der Mann" into the dative are two different drills.
 */
internal fun exerciseKey(exercise: Exercise): Pair<List<String>, List<String>> =
    normalizedWords(exercise.instruction.orEmpty()) to normalizedWords(exercise.task)

private fun parseExerciseLine(line: String): Exercise? {
    var parts = line.split("||").map { unwrapped(it.trim()) }
    var first = unwrapped(parts[0].replace(BULLET, "").trim())
    // "2 || short || ..." puts the number in a field of its own; "1. short || ..." and "[4] short || ..." before the form.
    if (NUMBER_ALONE.matches(first)) {
        parts = parts.drop(1)
        first = unwrapped(parts.firstOrNull().orEmpty().replace(BULLET, "").trim())
    }
    NUMBERED_FORM.matchEntire(first)?.let { first = unwrapped(it.groupValues[1].trim()) }
    val form = formOf(first) ?: return null
    val instruction = parts.getOrNull(1)?.takeUnless { it.isEmpty() || it == "-" || it.equals("none", ignoreCase = true) }
    // A multiple-choice sentence may have a gap too, printed the same way.
    val gapped = form == ExerciseForm.GapFill || form == ExerciseForm.MultipleChoice
    val task = parts.getOrNull(2).orEmpty().let { if (gapped) it.replace(GAP_MARKER, GAP) else it }
    val written = parts.getOrNull(3).orEmpty()
    if (task.isEmpty() || written.isEmpty()) return null
    if (isFieldEcho(task) || isFieldEcho(written) || instruction != null && isFieldEcho(instruction)) return null
    if (form == ExerciseForm.GapFill && GAP_MARKER.findAll(task).count() != 1) return null
    val rest = parts.drop(4)
    val options = if (form == ExerciseForm.MultipleChoice) choiceOptions(rest) ?: return null else emptyList()
    val answer = if (form == ExerciseForm.MultipleChoice) chosenOption(written, options) ?: return null else written
    if (normalizedWords(answer).isEmpty()) return null
    if (gapped) {
        // The hint may name the answer ("Ich sehe ___ (die Katze)."): telling the learner the word to change is
        // the point. And a one-word answer is often another word of the sentence too ("Mit dem Auto fahre ich zu
        // ___ (der) Bahnhof." || dem) without giving anything away; a phrase that is there word for word does.
        if (normalizedWords(answer).size > 1 && shows(task.replace(HINT, " "), answer)) return null
    } else if (shows(task, answer)) return null
    // A multiple-choice exercise gets no word help: it would point at the right option.
    val help = if (form == ExerciseForm.MultipleChoice) emptyList() else {
        val answerWords = normalizedWords(answer).toSet()
        parseWordHelp(rest.lastOrNull().orEmpty())
            .filter { entry -> normalizedWords(entry.word).none { it in answerWords } }
            .take(WORKSHEET_WORD_HELP)
    }
    return Exercise(form, instruction, task, answer, options, help)
}

/** "a) ", "B. " or "(c) " before an option or an answer. */
private val OPTION_LETTER = Regex("^\\(?([a-cA-C])[).]\\s*")

/** Where the next option starts in "a) den b) dem c) der", written with nothing between the options. */
private val NEXT_OPTION = Regex("\\s+(?=\\(?[b-cB-C][).]\\s)")

/**
 * How the options of a multiple-choice field may be set apart: as asked, with slashes, not at all, or with
 * commas. Commas come last, since an option may be a phrase with a comma in it ("Ja, gerne").
 */
private val OPTION_SEPARATORS = listOf(Regex(";"), Regex("/"), NEXT_OPTION, Regex(","))

/** A note in brackets after a multiple-choice answer, as in "b) dem (dative)". */
private val ANSWER_NOTE = Regex("\\s*\\([^)]*\\)\\s*$")

/** A full stop or other mark after a multiple-choice answer, as in "dem." or "b.". */
private val ANSWER_MARK = Regex("[\\s.,;:!]+$")

/**
 * The three options of a multiple-choice line, from the fields after its answer: "a) den; b) dem; c) der" as
 * asked, or the same with slashes, with nothing between them, or with each option in a field of its own.
 * Null unless there are exactly three, all different: with two the guess is too easy, and with four the
 * sheet prints something the learner wasn't told to expect.
 */
private fun choiceOptions(fields: List<String>): List<String>? {
    val separate = fields.take(3).takeIf { it.size == 3 && it.all(OPTION_LETTER::containsMatchIn) }
    val field = fields.firstOrNull().orEmpty()
    val pieces = separate ?: OPTION_SEPARATORS.map { field.split(it) }.firstOrNull { it.size > 1 } ?: return null
    val options = pieces.map { unwrapped(it.trim().replace(OPTION_LETTER, "").trim()) }.filter { it.isNotEmpty() }
    return options.takeIf { it.size == 3 && options.map { option -> option.lowercase() }.distinct().size == 3 }
}

/**
 * The option a multiple-choice [answer] names, spelled as the option is: the answer may be the option itself,
 * its letter ("b") or both ("b) dem"). An answer that is one of the options word for word is that option
 * rather than a letter, since Italian "a" is a word too. A full stop or a note in brackets after it ("dem.",
 * "b) dem (dative)") is read past. Null when the answer is none of the [options].
 */
private fun chosenOption(written: String, options: List<String>): String? {
    options.firstOrNull { it.equals(written.trim(), ignoreCase = true) }?.let { return it }
    val answer = written.replace(ANSWER_NOTE, "").replace(ANSWER_MARK, "").trim()
    options.firstOrNull { it.equals(answer, ignoreCase = true) }?.let { return it }
    val letter = OPTION_LETTER.find(answer)
        ?: return answer.trim().lowercase().singleOrNull()?.let { options.getOrNull(it - 'a') }
    val named = options[letter.groupValues[1].lowercase()[0] - 'a']
    val word = answer.substring(letter.range.last + 1).trim()
    return named.takeIf { word.isEmpty() || it.equals(word, ignoreCase = true) }
}

/**
 * The [count] exercises of [candidates] a custom sheet keeps, in the order the model wrote them. Only the
 * sheet's [forms] are kept, shared out between them as evenly as the candidates allow (the first of each
 * form first), and an exercise that nearly repeats one already taken waits until the others are in. There
 * is no ranking beyond that: a model asked for grammar is not measured by how close it stays to the cards.
 */
internal fun pickExercises(candidates: List<Exercise>, forms: List<ExerciseForm>, count: Int): List<Exercise> {
    val wordSets = HashMap<Exercise, Set<String>>()
    fun words(exercise: Exercise) = wordSets.getOrPut(exercise) { normalizedWords(exercise.task).toHashSet() }
    val fresh = ArrayList<Exercise>()
    val waiting = ArrayList<Exercise>()
    for (exercise in candidates.filter { it.form in forms }) {
        if (fresh.any { isNearRepeat(words(it), words(exercise)) }) waiting += exercise else fresh += exercise
    }
    // Round by round, one of each form, until the sheet is full.
    val queues = forms.map { form -> ArrayDeque(fresh.filter { it.form == form }) }
    val taken = HashSet<Exercise>()
    while (taken.size < count && queues.any { it.isNotEmpty() }) {
        for (queue in queues) if (taken.size < count) queue.removeFirstOrNull()?.let { taken += it }
    }
    waiting.take(count - taken.size).forEach { taken += it }
    return candidates.filter { it in taken }
}

/**
 * One exercise as a custom sheet prints it.
 *
 * @property number Its number on the sheet and in the answer key.
 * @property instruction The instruction to print above it, or null when it is the same as the one before.
 */
data class CustomSheetRow(val number: Int, val exercise: Exercise, val instruction: String?)

/** The order forms are printed in: the quick ones first, the ones to write out last. */
private val PRINTED_FORM_ORDER = listOf(ExerciseForm.GapFill, ExerciseForm.MultipleChoice, ExerciseForm.ShortAnswer)

/**
 * How a custom sheet prints [exercises]: grouped by form (gap fills, then multiple choice, then short
 * answers), numbered straight through, and each instruction printed once, above the first exercise of a run
 * that shares it. A run of "Write the plural." reads as one task, not ten.
 */
fun customSheetLayout(exercises: List<Exercise>): List<CustomSheetRow> {
    var last: String? = null
    var lastForm: ExerciseForm? = null
    return PRINTED_FORM_ORDER.flatMap { form -> exercises.filter { it.form == form } }.mapIndexed { index, exercise ->
        val instruction = exercise.instruction?.takeIf { it != last || exercise.form != lastForm }
        last = exercise.instruction
        lastForm = exercise.form
        CustomSheetRow(index + 1, exercise, instruction)
    }
}

/**
 * [exercise] with its options in an order drawn from [random], when it is multiple choice. Models tend to
 * put the right option in the same slot every time (often first), and a learner soon circles that letter
 * without reading. The answer is the option's own spelling, so [keyAnswer]'s letter follows the new order.
 */
internal fun shuffledOptions(exercise: Exercise, random: Random): Exercise =
    if (exercise.form == ExerciseForm.MultipleChoice) exercise.copy(options = exercise.options.shuffled(random)) else exercise

/** The letter of the option at [index]: a, b or c. */
private fun optionLetter(index: Int): Char = 'a' + index

/**
 * A multiple-choice exercise's [options] as the sheet prints them, lettered on one line under the task:
 * "a) den     b) dem     c) der". The wide gaps keep three short options apart on paper.
 */
fun choiceLine(options: List<String>): String =
    options.mapIndexed { index, option -> "${optionLetter(index)}) $option" }.joinToString("     ")

/**
 * What the answer key says for [exercise]: the letter and the word of a multiple-choice answer ("b) dem"),
 * quick to mark against what was circled, and the answer as it is for every other form.
 */
fun keyAnswer(exercise: Exercise): String {
    val index = exercise.options.indexOf(exercise.answer)
    return if (exercise.form == ExerciseForm.MultipleChoice && index >= 0) "${optionLetter(index)}) ${exercise.answer}" else exercise.answer
}

/** A handwritten answer takes about this many characters a line of a sheet's answer box. */
private const val ANSWER_BOX_LINE = 30

/** An answer of up to this many characters fits a box of one line. */
private const val ANSWER_BOX_ONE_LINE = 25

/** An answer box is never more lines than this, however long the key answer. */
const val ANSWER_BOX_MAX_LINES = 6

/**
 * How many writing lines the answer box of an exercise whose key answer is [answer] has: one for a word or
 * a phrase, more for a sentence, and a line for each line of a table such as a conjugation.
 */
fun answerBoxLines(answer: String): Int {
    val lines = answer.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size <= 1 && answer.trim().length <= ANSWER_BOX_ONE_LINE) return 1
    return lines.sumOf { (it.length + ANSWER_BOX_LINE - 1) / ANSWER_BOX_LINE }.coerceIn(1, ANSWER_BOX_MAX_LINES)
}

/** How a gap fill's gap is written, in the prompt and on every exercise read from a reply. */
internal const val GAP = "___"

/** A gap as models write it: two underscores or more. */
private val GAP_MARKER = Regex("_{2,}")

/** A hint in round brackets, such as a gap fill's base form. */
private val HINT = Regex("\\([^)]*\\)")

/** The gap and the base-form hint that follows it, as the answer key fills them in. */
private val GAP_AND_HINT = Regex("_{2,}(?:\\s*\\([^)]*\\))?")

/** A gap is never printed shorter than this many underscores, room for a short word in handwriting. */
private const val PRINTED_GAP_MIN = 8

/** Nor longer than this many, so that a long answer doesn't push the sentence onto a line of its own. */
private const val PRINTED_GAP_MAX = 30

/**
 * A gap fill's [task] as it is printed: the gap a writing line long enough for [answer] in handwriting,
 * which takes more room than print, and the base-form hint left as it is.
 */
fun printedGapFill(task: String, answer: String): String =
    task.replace(GAP_MARKER, "_".repeat((answer.length * 8 / 5).coerceIn(PRINTED_GAP_MIN, PRINTED_GAP_MAX)))

/** A gap fill's [task] as the answer key shows it: the whole sentence, with [answer] in the gap and no hint. */
fun filledGapFill(task: String, answer: String): String =
    task.replace(GAP_AND_HINT) { answer }.replace(SPACES, " ").replace(SPACE_BEFORE_MARK, "$1").trim()

private val SPACES = Regex(" {2,}")
private val SPACE_BEFORE_MARK = Regex(" ([.,!?;:])")

/** The form a reply's first field names: "short", "Short answer", "gap-fill", "multiple choice", "MC". */
private fun formOf(field: String): ExerciseForm? {
    val word = field.lowercase().filter { it.isLetter() }
    return when {
        word.startsWith("short") -> ExerciseForm.ShortAnswer
        word.startsWith("gap") -> ExerciseForm.GapFill
        word == "mc" || word.endsWith("choice") -> ExerciseForm.MultipleChoice
        else -> null
    }
}

/** Whether [task] shows [answer]: the answer's words, one after the other, are among the task's. */
private fun shows(task: String, answer: String): Boolean {
    val taskWords = normalizedWords(task)
    val answerWords = normalizedWords(answer)
    if (answerWords.isEmpty()) return true
    return (0..taskWords.size - answerWords.size).any { start -> answerWords.indices.all { taskWords[start + it] == answerWords[it] } }
}

/** "1. short", "4) short" or "[4] short" in the first field: a number before the form. */
private val NUMBERED_FORM = Regex("^[\\[(#]?\\s*\\d{1,3}\\s*[\\])]?[.):]?\\s+(.+)$")

/** The languages a placeholder of the prompt names: every language of the app, and Norwegian as the prompt writes it. */
private val ECHOED_LANGUAGES = Languages.ALL.map { it.name } + listOf("Norwegian Bokmål", "Norwegian Nynorsk")

/**
 * The prompt's own placeholders sent back as fields: "task in German", "answer in German", "instruction in
 * English", word for word with nothing after the language, and "form: gap, choice, short". An instruction
 * such as "Answer in the past tense." only starts the same way, and is the learner's task.
 */
private val FIELD_ECHO = Regex(
    "^(?:instruction|task|answer) in (?:${ECHOED_LANGUAGES.joinToString("|")})$|^form:",
    RegexOption.IGNORE_CASE
)

/** Whether [field] is one of the prompt's placeholders sent back ([FIELD_ECHO]). */
private fun isFieldEcho(field: String): Boolean = FIELD_ECHO.containsMatchIn(field.trim())
