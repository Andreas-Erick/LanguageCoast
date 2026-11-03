package com.andreaserick.languagecoast.data

/**
 * The few things about the user that starter sentences ask for: the blanks `[name]`, `[city]` and so on
 * in the [PhraseBank]. Entered once in Settings ("About you") and filled in wherever a blank appears, so a
 * topic that only needs a name never asks. Blank fields are simply unknown.
 *
 * @property city Where the user lives, and [country] its country; [homeCountry] is where they are from
 *     and [hometown] where they were born, which may differ.
 */
data class Profile(
    val name: String = "",
    val city: String = "",
    val country: String = "",
    val homeCountry: String = "",
    val hometown: String = "",
    val job: String = ""
) {
    /** The placeholder name each field fills, with its value, leaving out blank fields. */
    fun fillers(): Map<String, String> = mapOf(
        "name" to name,
        "city" to city,
        "country" to country,
        "home country" to homeCountry,
        "hometown" to hometown,
        "job" to job
    ).mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }

    /** The profile with [placeholder] set to [value]. */
    fun with(placeholder: String, value: String): Profile = when (placeholder) {
        "name" -> copy(name = value)
        "city" -> copy(city = value)
        "country" -> copy(country = value)
        "home country" -> copy(homeCountry = value)
        "hometown" -> copy(hometown = value)
        "job" -> copy(job = value)
        else -> this
    }

    val isEmpty: Boolean get() = fillers().isEmpty()

    companion object {
        /** Placeholders the profile fills, in the order the fields are shown. They have one value per person. */
        val PLACEHOLDERS = listOf("name", "city", "country", "home country", "hometown", "job")
    }
}

/** The placeholders a whole starter topic can be added with (the person's, from the [Profile]); the rest need a value per sentence. */
val PROFILE_PLACEHOLDERS: List<String> get() = Profile.PLACEHOLDERS

/** What to call a profile blank in a form: "Your name", "Country you're from", ... */
fun profileLabel(placeholder: String): String = when (placeholder) {
    "name" -> "Your name"
    "city" -> "City you live in"
    "country" -> "Country you live in"
    "home country" -> "Country you're from"
    "hometown" -> "Town you were born in"
    "job" -> "Your job"
    else -> "Your $placeholder"
}

/** A short label for a profile blank when the field sits next to another under a group heading: "Name", "City", ... */
fun profileShortLabel(placeholder: String): String = when (placeholder) {
    "name" -> "Name"
    "city" -> "City"
    "country" -> "Country"
    "home country" -> "Country"
    "hometown" -> "Town"
    "job" -> "Job"
    else -> placeholder.replaceFirstChar { it.uppercase() }
}

/**
 * How the starter sentences will read with [profile] filled in, e.g. "My name is Anna. I work as a nurse.
 * I live in Oslo, Norway. I'm from Italy, born in Bologna." Null while nothing is filled in.
 */
fun profilePreview(profile: Profile): String? {
    val f = profile.fillers()
    if (f.isEmpty()) return null
    val lives = listOfNotNull(f["city"], f["country"]).joinToString(", ")
    val homeCountry = f["home country"]
    val hometown = f["hometown"]
    val from = when {
        homeCountry != null && hometown != null -> "I'm from $homeCountry, born in $hometown."
        homeCountry != null -> "I'm from $homeCountry."
        hometown != null -> "I was born in $hometown."
        else -> null
    }
    return listOfNotNull(
        f["name"]?.let { "My name is $it." },
        f["job"]?.let { "I work as a $it." },
        lives.takeIf { it.isNotEmpty() }?.let { "I live in $it." },
        from
    ).joinToString(" ")
}

/** An example for a profile blank's field. */
fun profileExample(placeholder: String): String = when (placeholder) {
    "name" -> "e.g., Anna"
    "city" -> "e.g., Oslo"
    "country" -> "e.g., Norway"
    "home country" -> "e.g., Italy"
    "hometown" -> "e.g., Bologna"
    "job" -> "e.g., nurse"
    else -> ""
}
