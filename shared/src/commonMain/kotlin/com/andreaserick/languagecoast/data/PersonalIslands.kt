package com.andreaserick.languagecoast.data

/**
 * One thing the app asks the user about themselves, answered in a line or two of free text.
 *
 * @property key Where the answer is stored; never changes once released.
 * @property label The question as the form shows it.
 * @property example An answer of the kind that helps, shown while the field is empty.
 */
class PersonalQuestion(val key: String, val label: String, val example: String)

/**
 * An island about the user's own life (their family, their work, their weekends), which the starter bank
 * can't hold because it differs from person to person. The app asks a few [questions], and an AI model
 * writes the island's sentences from the answers (see [PersonalIslandBuilder]).
 *
 * @property id Stable identifier, used in what is stored about the island.
 * @property name The island's name on a coast.
 * @property about What the island lets the user talk about, as the form says it.
 * @property guidance What the sentences should cover, for the model.
 * @property profileFields The [Profile] blanks (see [Profile.PLACEHOLDERS]) that are facts for this island too.
 * @property level The level its sentences are written at.
 * @property related Starter topics that say some of the same things, whose sentences the island must not repeat.
 *
 * An island without [questions] isn't about the user: it is a theme of its level (giving opinions, telling a
 * story) that the model writes for anyone, see [PersonalIslands.WRITTEN].
 */
class PersonalIsland(
    val id: String,
    val name: String,
    val emoji: String,
    val about: String,
    val guidance: String,
    val questions: List<PersonalQuestion>,
    val profileFields: List<String> = emptyList(),
    val level: Level = Level.A2,
    val related: List<String> = emptyList()
) {
    /** Whether the island is written from what the user answered, rather than on a theme anyone needs. */
    val isAboutUser: Boolean get() = questions.isNotEmpty()

    /** The questions of this island that [answers] (by [PersonalQuestion.key]) has something for. */
    fun answered(answers: Map<String, String>): List<PersonalQuestion> = questions.filter { !answers[it.key].isNullOrBlank() }

    /** Whether the island can be written: at least one of its questions is answered (a theme island always can). */
    fun isUnlocked(answers: Map<String, String>): Boolean = !isAboutUser || answered(answers).isNotEmpty()

    /**
     * What is known about the user for this island, as label and value: the profile's fields it uses, then
     * the answered questions. Empty answers are left out.
     */
    fun facts(profile: Profile, answers: Map<String, String>): List<Pair<String, String>> {
        val fillers = profile.fillers()
        val fromProfile = profileFields.mapNotNull { field -> fillers[field]?.let { profileLabel(field) to it } }
        return fromProfile + answered(answers).map { it.label to answers.getValue(it.key).trim() }
    }

    /**
     * A short mark of the facts the island was written from, so a later change to them can be noticed
     * (see [SettingsRepository.personalIslandsBuilt]).
     */
    fun fingerprint(profile: Profile, answers: Map<String, String>): String =
        facts(profile, answers).joinToString("\n") { "${it.first}=${it.second}" }.hashCode().toString()
}

/** Sentences an island about the user aims for; fewer when the answers don't honestly support that many. */
const val PERSONAL_ISLAND_SENTENCES = 25

/**
 * The islands about the user that level A2 asks for: who they are, the people around them, where they
 * live and come from, work and school, how their days and weekends go, what they like, their health,
 * their past and their plans. Together with the [PhraseBank]'s topics these are the islands a learner
 * needs to get through everyday conversation.
 */
object PersonalIslands {

    val ALL: List<PersonalIsland> = listOf(
        PersonalIsland(
            id = "details", name = "Personal Details", emoji = "🪪",
            about = "Your age, birthday, languages and the rest of an introduction.",
            guidance = "introducing themselves in full: name, age, birthday, where they live and come from, what they do, the languages they speak and how well, their relationship status",
            questions = listOf(
                PersonalQuestion("details_age", "How old are you?", "e.g., 34"),
                PersonalQuestion("details_birthday", "When is your birthday?", "e.g., 12 March"),
                PersonalQuestion("details_languages", "Which languages do you speak, and how well?", "e.g., Italian (native), English (fluent), a little French"),
                PersonalQuestion("details_relationship", "Are you single, in a relationship, married?", "e.g., married for three years")
            ),
            profileFields = listOf("name", "city", "country", "home country", "hometown", "job"),
            level = Level.A1,
            related = listOf("About Me")
        ),
        PersonalIsland(
            id = "family", name = "My Family", emoji = "👪",
            about = "Your parents, brothers and sisters, and the relatives you're close to.",
            guidance = "talking about their family: who is in it, names and ages, where they live, what they do, how often they see each other and what they do together",
            questions = listOf(
                PersonalQuestion("family_parents", "Your parents: names, where they live, what they do", "e.g., Maria, a retired teacher, and Paolo, a baker; they live in Bologna"),
                PersonalQuestion("family_siblings", "Brothers and sisters: names, ages, what they do", "e.g., one older sister, Giulia, 38, a doctor in Milan; no brothers"),
                PersonalQuestion("family_relatives", "Other relatives you're close to", "e.g., my grandmother Rosa, 86; two cousins in Naples"),
                PersonalQuestion("family_together", "How often do you see or call them, and what do you do together?", "e.g., I call my mother every Sunday; we all meet at Christmas")
            )
        ),
        PersonalIsland(
            id = "partner", name = "My Partner & Children", emoji = "💞",
            about = "Your partner and your children. Leave it empty if it doesn't apply.",
            guidance = "talking about their partner and children: names, ages, what they do, how long they have been together, what they like doing together",
            questions = listOf(
                PersonalQuestion("partner_partner", "Your partner: name, what they do, how long you've been together", "e.g., my wife Lena, an architect; together for eight years"),
                PersonalQuestion("partner_children", "Your children: names and ages", "e.g., Emma, 6, and Noah, 3; Emma has just started school"),
                PersonalQuestion("partner_together", "What do you like doing together?", "e.g., cooking on Sundays, cycling, board games")
            )
        ),
        PersonalIsland(
            id = "friends", name = "My Friends", emoji = "🧑‍🤝‍🧑",
            about = "One or two close friends, how you met and what you do together.",
            guidance = "talking about their friends: who they are, what they are like, how they met, what they do together and how often",
            questions = listOf(
                PersonalQuestion("friends_who", "One or two close friends: names and what they're like", "e.g., Marco, funny and always late; Sara, very calm"),
                PersonalQuestion("friends_met", "How did you meet?", "e.g., Marco at university; Sara at my first job"),
                PersonalQuestion("friends_together", "What do you do together, and how often?", "e.g., football on Thursdays, a beer after work, long calls with Sara")
            )
        ),
        PersonalIsland(
            id = "home", name = "My Home", emoji = "🛋️",
            about = "The place you live in, who with, and what you like about it.",
            guidance = "describing their home: what kind of home it is, who they live with, its rooms and what is in them, what they like and dislike about it",
            questions = listOf(
                PersonalQuestion("home_kind", "What kind of home do you live in?", "e.g., a two-room flat on the third floor of an old building"),
                PersonalQuestion("home_with", "Who do you live with?", "e.g., with my wife and our cat; or alone; or with two flatmates"),
                PersonalQuestion("home_rooms", "Rooms and things in it worth mentioning", "e.g., a small kitchen, a balcony, no lift, a big desk in the living room"),
                PersonalQuestion("home_likes", "What do you like or dislike about it?", "e.g., bright and quiet, but too small and the rent is high")
            )
        ),
        PersonalIsland(
            id = "area", name = "Where I Live", emoji = "🏘️",
            about = "Your neighbourhood or town and what is around you.",
            guidance = "describing the place they live: the town or neighbourhood, what is nearby, how long they have lived there, whether they like it and why",
            questions = listOf(
                PersonalQuestion("area_like", "What is your neighbourhood or town like?", "e.g., a quiet area near the centre, lots of students and cafés"),
                PersonalQuestion("area_nearby", "What is near your home?", "e.g., a park, a bakery, the tram stop, a supermarket five minutes away"),
                PersonalQuestion("area_since", "How long have you lived there, and do you like it?", "e.g., two years; I like it but the winters are dark")
            ),
            profileFields = listOf("city", "country")
        ),
        PersonalIsland(
            id = "origin", name = "Where I'm From", emoji = "🌍",
            about = "Your hometown and country: what they're like and what you miss.",
            guidance = "talking about where they come from: their home country and hometown, what it is like there, what it is known for, what they miss and how often they go back",
            questions = listOf(
                PersonalQuestion("origin_like", "What is it like where you come from?", "e.g., a small town by the sea, hot summers, everybody knows everybody"),
                PersonalQuestion("origin_known", "What is your hometown or country known for?", "e.g., the food, an old university, the beaches"),
                PersonalQuestion("origin_miss", "What do you miss, and how often do you go back?", "e.g., my family and the food; I go back twice a year")
            ),
            profileFields = listOf("home country", "hometown")
        ),
        PersonalIsland(
            id = "work", name = "My Work or Studies", emoji = "💼",
            about = "What you do all day, where, with whom, and how you feel about it.",
            guidance = "talking about their work or studies: what they do and where, what a normal day involves, their hours and how they get there, their colleagues or classmates, what they like and dislike about it",
            questions = listOf(
                PersonalQuestion("work_place", "Where do you work or study?", "e.g., at a hospital in the city centre; or at the technical university"),
                PersonalQuestion("work_tasks", "What do you do on a normal day?", "e.g., I look after patients, write reports and have a lot of meetings"),
                PersonalQuestion("work_hours", "Your hours, and how you get there", "e.g., 8 to 5, sometimes night shifts; twenty minutes by bike"),
                PersonalQuestion("work_feel", "What do you like or dislike about it?", "e.g., I like my colleagues; the work is stressful and the pay is okay")
            ),
            profileFields = listOf("job")
        ),
        PersonalIsland(
            id = "education", name = "My Education", emoji = "🎓",
            about = "What you studied or trained as, where and when.",
            guidance = "talking about their education: what they studied or trained as, where and when, their favourite subjects, anything they are learning now",
            questions = listOf(
                PersonalQuestion("education_studied", "What did you study or train as, and where?", "e.g., nursing, at the University of Bologna"),
                PersonalQuestion("education_when", "When did you finish, or when will you?", "e.g., I finished in 2015"),
                PersonalQuestion("education_more", "Anything else: favourite subjects, courses you're taking now", "e.g., I loved biology and hated maths; I'm taking an evening course in photography")
            )
        ),
        PersonalIsland(
            id = "routine", name = "My Daily Routine", emoji = "⏰",
            about = "A normal day of yours, from getting up to going to bed.",
            guidance = "describing a normal day: when they get up, breakfast, how they get to work, lunch, when they finish, what they do in the evening, when they go to bed",
            questions = listOf(
                PersonalQuestion("routine_morning", "Your morning: when you get up, breakfast, getting to work", "e.g., up at 6:30, coffee and toast, I leave at 7:30 and take the tram"),
                PersonalQuestion("routine_day", "Your day: lunch, breaks, when you finish", "e.g., lunch at 12:30 in the canteen, I finish at 5"),
                PersonalQuestion("routine_evening", "Your evening: dinner, what you do, bedtime", "e.g., I cook at 7, watch a series or read, in bed by 11")
            )
        ),
        PersonalIsland(
            id = "weekend", name = "My Weekends", emoji = "🛶",
            about = "What you usually do at the weekend, and what you did last time.",
            guidance = "talking about their weekends: what they usually do, what they did last weekend (in the past tense), what a perfect free day looks like",
            questions = listOf(
                PersonalQuestion("weekend_usual", "What does a typical weekend look like?", "e.g., I sleep late, go to the market on Saturday and meet friends in the evening"),
                PersonalQuestion("weekend_last", "What did you do last weekend?", "e.g., we went hiking and had dinner at my sister's"),
                PersonalQuestion("weekend_perfect", "What would a perfect free day be?", "e.g., a long breakfast, a walk by the lake and no phone")
            )
        ),
        PersonalIsland(
            id = "hobbies", name = "Hobbies & Interests", emoji = "🎯",
            about = "What you do in your free time, how often and since when.",
            guidance = "talking about their hobbies and interests: what they do, how often, since when, with whom, what they would like to try or learn",
            questions = listOf(
                PersonalQuestion("hobbies_what", "Your hobbies and interests", "e.g., photography, cooking, chess"),
                PersonalQuestion("hobbies_detail", "How often, since when, with whom?", "e.g., I take photos most weekends; I've played chess since I was ten, mostly online"),
                PersonalQuestion("hobbies_want", "Something you'd like to try or learn", "e.g., I'd like to learn the guitar")
            )
        ),
        PersonalIsland(
            id = "sports", name = "Sports & Exercise", emoji = "⚽",
            about = "The sports you do, the ones you follow, the ones you used to do.",
            guidance = "talking about sport and exercise: what they do and how often, the sports and teams they follow, what they used to do",
            questions = listOf(
                PersonalQuestion("sports_do", "Sports or exercise you do, and how often", "e.g., running twice a week, yoga at home, skiing in winter"),
                PersonalQuestion("sports_follow", "Sports or teams you follow", "e.g., football; I support Bologna FC"),
                PersonalQuestion("sports_past", "Sports you used to do", "e.g., I played volleyball at school")
            )
        ),
        PersonalIsland(
            id = "media", name = "Music, Films & Books", emoji = "🎬",
            about = "What you listen to, watch and read.",
            guidance = "talking about music, films, series and books: what they like, their favourites, what they are watching or reading now",
            questions = listOf(
                PersonalQuestion("media_music", "Music you like, and instruments you play", "e.g., jazz and old Italian pop; I play a little piano"),
                PersonalQuestion("media_films", "Films and series you like, or are watching now", "e.g., crime series; I'm watching a Danish one right now"),
                PersonalQuestion("media_books", "Books or authors, and what you're reading now", "e.g., I read mostly history; right now a book about Rome")
            )
        ),
        PersonalIsland(
            id = "food", name = "Food I Like", emoji = "🍝",
            about = "Your favourite dishes, what you cook, and what you don't eat.",
            guidance = "talking about food and drink: their favourite dishes, what they cook, how often they eat out, what they don't eat and why (diet, allergies, taste), what they usually drink",
            questions = listOf(
                PersonalQuestion("food_favourites", "Your favourite dishes and foods", "e.g., lasagne, anything with mushrooms, dark chocolate"),
                PersonalQuestion("food_cook", "What do you cook, and how often do you eat out?", "e.g., I cook pasta and soups most days; we eat out once a week"),
                PersonalQuestion("food_diet", "Anything you don't eat: diet, allergies, dislikes", "e.g., I'm vegetarian; I can't eat nuts; I don't like fish"),
                PersonalQuestion("food_drinks", "What you usually drink", "e.g., coffee without sugar in the morning, a glass of red wine at the weekend")
            )
        ),
        PersonalIsland(
            id = "pets", name = "Pets", emoji = "🐾",
            about = "Your animals. Leave it empty if you have none.",
            guidance = "talking about their pets: what kind, their names and ages, what they are like, who looks after them",
            questions = listOf(
                PersonalQuestion("pets_which", "Your pets: kind, name, age", "e.g., a cat called Milo, four years old"),
                PersonalQuestion("pets_like", "What are they like, and who looks after them?", "e.g., he sleeps all day and wakes me at five; my neighbour feeds him when I travel")
            )
        ),
        PersonalIsland(
            id = "health", name = "My Health", emoji = "🩺",
            about = "What a doctor or pharmacist abroad would need to hear from you.",
            guidance = "telling a doctor, pharmacist or host about their health: allergies, conditions, the medication they take, glasses or anything else, and how they look after themselves",
            questions = listOf(
                PersonalQuestion("health_allergies", "Allergies", "e.g., penicillin and cats; hay fever in spring"),
                PersonalQuestion("health_conditions", "Conditions a doctor should know about", "e.g., asthma since I was a child; a bad knee"),
                PersonalQuestion("health_medication", "Medication you take", "e.g., an inhaler when I need it; a tablet for blood pressure every morning"),
                PersonalQuestion("health_other", "Anything else: glasses, sleep, how you keep fit", "e.g., I wear contact lenses; I sleep badly; I don't smoke")
            ),
            related = listOf("Help & Health")
        ),
        PersonalIsland(
            id = "celebrations", name = "Celebrations & Traditions", emoji = "🎊",
            about = "The days you celebrate, how, and with whom.",
            guidance = "talking about the holidays and occasions they celebrate: which ones, when, what they do and eat, who they spend them with, their favourite one",
            questions = listOf(
                PersonalQuestion("celebrations_which", "Which holidays and occasions do you celebrate?", "e.g., Christmas, Easter, my name day, the village festival in August"),
                PersonalQuestion("celebrations_how", "What do you do and eat, and with whom?", "e.g., a big lunch at my parents' with tortellini; we open presents on the 24th"),
                PersonalQuestion("celebrations_favourite", "Your favourite one, and why", "e.g., New Year's Eve, because all my friends come over")
            )
        ),
        PersonalIsland(
            id = "past", name = "My Past", emoji = "🕰️",
            about = "Where you grew up, the places you've lived and the trips you've taken.",
            guidance = "telling their story in the past tense: where they grew up and what it was like, the places they have lived and when they moved, trips they have taken, the big moments",
            questions = listOf(
                PersonalQuestion("past_grew", "Where did you grow up, and what was it like?", "e.g., in a village near Bologna; we played outside all day"),
                PersonalQuestion("past_moves", "Places you have lived, and when you moved", "e.g., Milan for university in 2010, London for two years, Oslo since 2022"),
                PersonalQuestion("past_trips", "Trips you have taken", "e.g., Japan in 2019, three weeks; Portugal last summer with friends"),
                PersonalQuestion("past_moments", "Big moments: first job, wedding, a year that changed things", "e.g., my first job in 2015; we got married in 2021")
            )
        ),
        PersonalIsland(
            id = "plans", name = "My Plans & Dreams", emoji = "🌅",
            about = "Your next holiday, this year's plans and where you want to be later.",
            guidance = "talking about the future: their next holiday or trip, their plans for this year, their longer-term goals and dreams",
            questions = listOf(
                PersonalQuestion("plans_holiday", "Your next holiday or trip", "e.g., two weeks in Greece in August with my family"),
                PersonalQuestion("plans_year", "Plans for this year", "e.g., pass the language exam, move to a bigger flat, run a half marathon"),
                PersonalQuestion("plans_dreams", "Longer-term goals and dreams", "e.g., open my own café one day; live by the sea")
            )
        )
    )

    /**
     * The islands of level B1 that aren't about the user: what a learner must be able to do at that level
     * (give an opinion with a reason, tell a story, complain politely), which the model writes for anyone.
     * The islands about the user reach B1 by being written a level up from the same answers.
     */
    val WRITTEN: List<PersonalIsland> = listOf(
        PersonalIsland(
            id = "opinions", name = "Opinions & Reasons", emoji = "💭", level = Level.B1,
            about = "Say what you think and why, agree and disagree.",
            guidance = "giving an opinion and the reason for it, agreeing and disagreeing politely, weighing two sides, asking what the other person thinks",
            questions = emptyList()
        ),
        PersonalIsland(
            id = "stories", name = "Telling a Story", emoji = "📖", level = Level.B1,
            about = "Tell what happened, in order, and how it felt.",
            guidance = "telling what happened to them in order: setting the scene, what happened next, how it ended and how it felt; and reacting to someone else's story",
            questions = emptyList()
        ),
        PersonalIsland(
            id = "problems", name = "Problems & Complaints", emoji = "🛠️", level = Level.B1,
            about = "Say something is wrong and get it fixed, politely.",
            guidance = "saying that something is wrong (an order, a bill, a room, a delivery, a booking), explaining what happened, asking for a solution, staying polite but firm",
            questions = emptyList()
        ),
        PersonalIsland(
            id = "whatifs", name = "Hopes & What-Ifs", emoji = "🔮", level = Level.B1,
            about = "Hopes, intentions, and what you would do if.",
            guidance = "talking about hopes, intentions and possibilities: what they hope for, what they will do when something happens, what they would do if things were different",
            questions = emptyList()
        ),
        PersonalIsland(
            id = "teamwork", name = "Work & Meetings", emoji = "🗂️", level = Level.B1,
            about = "Explain your work, ask, propose, agree on deadlines.",
            guidance = "working with others: explaining what they are working on, asking for help or clarification, proposing something, agreeing on deadlines, emails and calls",
            questions = emptyList(), profileFields = listOf("job")
        ),
        PersonalIsland(
            id = "advice", name = "Advice & Suggestions", emoji = "🤝", level = Level.B1,
            about = "Ask for advice, recommend, warn, persuade.",
            guidance = "asking for and giving advice, recommending something, warning someone, suggesting what to do and persuading gently",
            questions = emptyList()
        ),
        PersonalIsland(
            id = "heart", name = "Feelings in Depth", emoji = "🫶", level = Level.B1,
            about = "Explain how you feel and why; comfort and apologize.",
            guidance = "explaining how they feel and why, comforting someone, apologizing properly, clearing up a misunderstanding",
            questions = emptyList()
        ),
        PersonalIsland(
            id = "news", name = "News & the World", emoji = "📰", level = Level.B1,
            about = "Talk about something you read or heard.",
            guidance = "talking about something they read or heard, saying how things are changing, reacting to news and asking whether the other person has heard about it",
            questions = emptyList()
        ),
        PersonalIsland(
            id = "explaining", name = "Explaining Things", emoji = "⚙️", level = Level.B1,
            about = "Explain how something works, even without the word.",
            guidance = "explaining how to do something step by step, describing what a thing is for or looks like when the word is missing, checking that the other person follows",
            questions = emptyList()
        )
    )

    /** Every question the app asks, in the order of the form. */
    val QUESTIONS: List<PersonalQuestion> = ALL.flatMap { it.questions }

    /** The island with [id], about the user or on a theme, if there is one. */
    fun byId(id: String): PersonalIsland? = (ALL + WRITTEN).firstOrNull { it.id == id }
}

/**
 * The prompt that has a model write up to [count] sentences for [island] from [facts] (label and value,
 * see [PersonalIsland.facts]): each in [nativeLanguage] for the front of the card and in [targetLanguage]
 * (as spoken in [region], if given) for the back.
 *
 * @param goal Why the user learns the language, if they said.
 * @param avoid Sentences (in [nativeLanguage]) the island already has, which must not come again.
 * @param level The level to write at; an island about the user written above its own level says more about the same facts.
 * @param wording How the user is spoken of where the language marks it.
 */
fun buildPersonalIslandPrompt(
    island: PersonalIsland,
    facts: List<Pair<String, String>>,
    nativeLanguage: String,
    targetLanguage: String,
    region: String?,
    goal: String?,
    count: Int,
    avoid: List<String> = emptyList(),
    level: Level = island.level,
    wording: Wording = Wording.Unspecified
): String {
    val target = languageIn(targetLanguage, region)
    val factLines = facts.joinToString("\n") { (label, value) -> "- $label: ${value.replace('\n', ' ')}" }
    val goalLine = goal?.trim()?.takeIf { it.isNotEmpty() }?.let { " Why they are learning $targetLanguage: \"$it\"." } ?: ""
    val rules = ArrayList<String>()
    rules += "- Level ${level.label}: ${level.writing}. A rarer word is fine only when the topic or their answers call for it (a job, a hobby, an illness)."
    if (island.isAboutUser) {
        rules += "- Every statement about the learner must come from what they told the app. Do not invent names, numbers, places, dates, opinions or events. Names of people and places stay as they wrote them, adapted only as grammar requires."
        rules += "- Write in the first person, the way the learner would say it in conversation. An important fact may appear twice in two useful shapes (a short answer and a fuller one), and two facts may be joined."
        rules += "- Up to a fifth of the sentences may be questions the learner would ask the other person about the same topic (\"And you, do you have brothers or sisters?\"), using the form of address a native speaker would use with a new acquaintance."
        rules += "- $count is a limit, not a target to pad up to. If their answers honestly support fewer good sentences, write fewer. Never repeat a sentence or say the same thing twice."
        if (level > island.level) {
            rules += "- They already know the simple versions of these facts. Now say more about the same facts: why, how it feels, how it compares, what happened before and what comes next."
        }
    } else {
        rules += "- These are the sentences anyone at this level needs for this, said the way the learner would say them in a real conversation: concrete and reusable, a mix of statements, questions and reactions."
        rules += "- Where a sentence is about the learner, use only what is known about them; do not invent personal facts. Keep the rest general enough to fit anyone's life."
        rules += "- Write $count sentences that are all different in what they do. Never repeat a sentence or say the same thing twice."
    }
    rules += "- The $targetLanguage must be what a native speaker would really say, not a word-for-word copy of the $nativeLanguage, with the punctuation and capitalization of $targetLanguage."
    region?.trim()?.takeIf { it.isNotEmpty() }?.let {
        rules += "- They will use this in $it: use the words, forms, spelling and register of $it, not of other places the language is spoken."
    }
    speakerRule(wording, targetLanguage).takeIf { it.isNotEmpty() }?.let { rules += it }
    if (island.isAboutUser) rules += "- The answers may be written in any language; the sentences are always in $nativeLanguage and $targetLanguage."
    if (avoid.isNotEmpty()) rules += "- They already have these sentences; do not write them or the same thing in other words: " + avoid.joinToString(" | ")

    val known = when {
        facts.isEmpty() -> ""
        island.isAboutUser -> "What the learner told the app about themselves:\n$factLines\n\n"
        else -> "What is known about the learner:\n$factLines\n\n"
    }
    val intro = if (island.isAboutUser) {
        "You are writing a personal \"language island\" for someone learning $target: sentences about their own life that they will memorize and say in real conversations."
    } else {
        "You are writing a \"language island\" for someone learning $target: sentences on one theme that they will memorize and say in real conversations."
    }
    return buildString {
        append("$intro Their native language is $nativeLanguage. Accuracy matters: the learner will memorize exactly what you write, so a wrong or unnatural sentence does lasting damage.$goalLine\n\n")
        append("The island is \"${island.name}\". Its sentences are for ${island.guidance}.\n\n")
        append(known)
        append("Write up to $count sentences. Rules:\n")
        append(rules.joinToString("\n"))
        append("\n\nReply with EXACTLY one line per sentence, nothing else, in this format:\n")
        append("[number] || [the sentence in $nativeLanguage] || [the sentence in $targetLanguage]")
    }
}

/**
 * Parses a reply to [buildPersonalIslandPrompt] into sentences for [island], at most [count], in the order
 * given. Lines that don't fit, repeats and sentences in [avoid] are dropped.
 */
fun parsePersonalIslandResponse(text: String, island: PersonalIsland, count: Int, avoid: List<String> = emptyList()): List<ProposedSentence> {
    val taken = avoid.mapTo(HashSet()) { normalizedWords(it) }
    return parseStarterTranslations(text, count, bothSides = true).entries
        .sortedBy { it.key }
        .map { (_, sides) -> ProposedSentence(withoutQuotes(sides[0]), withoutQuotes(sides[1]), island.name, island.emoji) }
        .filter { it.nativeText.isNotBlank() && it.targetText.isNotBlank() && taken.add(normalizedWords(it.nativeText)) }
}

/** [value] without the quotation marks a model put around the whole sentence. */
internal fun withoutQuotes(value: String): String {
    val quotes = "\"“”„«»"
    val trimmed = value.trim()
    return if (trimmed.length >= 2 && trimmed.first() in quotes && trimmed.last() in quotes) trimmed.substring(1, trimmed.length - 1).trim() else trimmed
}
