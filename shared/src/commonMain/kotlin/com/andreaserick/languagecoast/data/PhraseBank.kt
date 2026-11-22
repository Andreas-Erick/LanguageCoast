package com.andreaserick.languagecoast.data

/**
 * Starter sentences everyone needs in a new language, in English, by topic: greetings, getting by in
 * the language, small talk, saying who you are, the situations every traveller and newcomer meets
 * (restaurant, directions, airport, transport, hotel, shopping, help, making plans), and the building
 * blocks of time, dates and numbers. Topics that differ from person to person (family, work, hobbies) are
 * deliberately left out; those are written from what the user says about themselves (see [PersonalIslands]).
 *
 * Sentences may have placeholders in square brackets, such as `[city]` or `[name]`, which the user
 * fills in before the sentence is translated (see [fill] and [PLACEHOLDER]). `[language]` is filled
 * with the coast's language automatically.
 *
 * Used in two places: the starter sentences sheet on Today, where the user browses and ticks them, and
 * the daily session's pool when no AI model can be asked, where the topics the goal points to are
 * drawn from first.
 */
object PhraseBank {

    /** `[city]`, `[name]` and so on: what the user fills in. The group is the placeholder's name. */
    val PLACEHOLDER = Regex("\\[([a-z][a-z ]*)]")

    /** The placeholder the coast's language fills in on its own. */
    const val LANGUAGE_PLACEHOLDER = "language"

    /** "a [job]": an article right before a blank, which depends on the word that goes in. The groups are the article and the placeholder's name. */
    private val ARTICLE_BEFORE_BLANK = Regex("\\b([Aa]) \\[([a-z][a-z ]*)]")

    /**
     * A topic of the bank: the island its sentences go on, words in a goal that point to it, and the sentences.
     *
     * @property inPool Whether the daily session's pool may draw from it. Word lists (numbers, days) are for
     *     the starter sheet only: "seven" is not a sentence worth offering as the day's new one.
     */
    class Theme(val category: String, val emoji: String, val keywords: List<String>, val sentences: List<String>, val inPool: Boolean = true)

    val THEMES: List<Theme> = listOf(
        // ---- Sentences, which the daily session's pool may draw from ----
        Theme(
            "Greetings", "👋",
            listOf("polite", "greet", "greetings", "manners"),
            listOf(
                "Hello, how are you?",
                "Good morning!",
                "Good evening!",
                "Good night.",
                "Nice to meet you.",
                "Excuse me, may I ask you something?",
                "Sorry to bother you.",
                "Thank you very much.",
                "You're welcome.",
                "Yes, please.",
                "No, thank you.",
                "I'm sorry, that was my fault.",
                "No problem.",
                "Have a nice day!",
                "See you later.",
                "Goodbye, and thanks for everything."
            )
        ),
        Theme(
            "Getting By", "🗣️",
            listOf("learn", "learning", "speak", "speaking", "practice", "practise", "fluent", "understand"),
            listOf(
                "Could we speak [language], please? I'm learning it.",
                "I only speak a little [language].",
                "Sorry, I didn't understand that.",
                "Could you say that again, more slowly?",
                "What does [word] mean?",
                "How do you say [word] in [language]?",
                "Could you write that down for me?",
                "Do you speak English?",
                "Is that right?",
                "I'm still learning, please correct me.",
                "Can you spell that, please?",
                "One moment, let me look it up.",
                "What's the word for this?",
                "I understood everything, thank you!"
            )
        ),
        Theme(
            "Small Talk", "💬",
            listOf("friend", "friends", "people", "talk", "chat", "conversation", "family", "partner", "girlfriend", "boyfriend", "wife", "husband", "parents", "colleague", "colleagues", "neighbours", "neighbors", "weather"),
            listOf(
                "How was your day?",
                "My day was good, thanks. And yours?",
                "I had a long day today.",
                "What a beautiful day!",
                "It's cold today, isn't it?",
                "It looks like rain.",
                "The weather is lovely this week.",
                "It's [number] degrees outside.",
                "It's sunny.",
                "It's raining.",
                "It's snowing.",
                "It's windy.",
                "What are you up to this weekend?",
                "I'm just relaxing at home tonight.",
                "Did you sleep well?",
                "I'm a bit tired today.",
                "That sounds great!",
                "What do you think about it?",
                "Let's keep in touch.",
                "Say hello to your family from me."
            )
        ),
        Theme(
            "About Me", "🙋",
            listOf("introduce", "myself", "me", "move", "moving", "live", "living", "abroad", "new", "study", "university", "school"),
            listOf(
                "My name is [name].",
                "I'm from [home country].",
                "I live in [city].",
                "I live in [country] now.",
                "I was born in [hometown].",
                "I'm [number] years old.",
                "How old are you?",
                "I'm turning [number] next year.",
                "I work as a [job].",
                "I'm a student.",
                "I'm here on holiday.",
                "I moved here [number] months ago.",
                "I'm learning [language] because of my work.",
                "I have two brothers and a sister.",
                "I'm here with my family.",
                "I'm staying for [number] days.",
                "I love it here."
            )
        ),
        Theme(
            "Restaurant", "🍽️",
            listOf("food", "cook", "cooking", "restaurant", "eat", "eating", "dinner", "lunch", "cafe", "coffee", "wine", "cuisine"),
            listOf(
                "A table for two, please.",
                "Do you have a table by the window?",
                "Can I have the menu, please?",
                "What do you recommend?",
                "I'd like a coffee with milk.",
                "Still or sparkling water?",
                "Do you have anything vegetarian?",
                "I'm allergic to [word].",
                "We'd like to order now.",
                "This is delicious.",
                "Could we have the bill, please?",
                "Can I pay by card?",
                "Keep the change.",
                "Is service included?",
                "Could we have separate bills?",
                "Can I get this to go?"
            )
        ),
        Theme(
            "Directions", "🧭",
            listOf("directions", "walk", "walking", "lost", "map", "street", "city"),
            listOf(
                "Excuse me, where is the [place]?",
                "How do I get to [place]?",
                "Is it far from here?",
                "Can I walk there?",
                "Turn left at the corner.",
                "Go straight ahead, then turn right.",
                "It's on the other side of the street.",
                "Is this the way to the city centre?",
                "Could you show me on the map?",
                "I think I'm lost.",
                "Which way is the exit?",
                "It's next to the [place].",
                "How long does it take on foot?",
                "Where are the toilets?"
            )
        ),
        Theme(
            "Airport", "✈️",
            listOf("flight", "flights", "fly", "flying", "airport", "plane", "trip", "travel", "holiday", "vacation", "visit", "visiting"),
            listOf(
                "Where is the check-in desk for [place]?",
                "I'd like a window seat, please.",
                "Is this the gate for the flight to [destination]?",
                "How much does the extra bag cost?",
                "My flight is delayed.",
                "Where is passport control?",
                "I only have hand luggage.",
                "Where can I pick up my luggage?",
                "My suitcase didn't arrive.",
                "What time does boarding start?",
                "Is there free Wi-Fi here?",
                "Where can I get a taxi?",
                "I'm here for [number] days, on holiday.",
                "I have nothing to declare."
            )
        ),
        Theme(
            "Getting Around", "🚆",
            listOf("train", "trains", "bus", "metro", "subway", "taxi", "tram", "commute", "ticket", "drive", "driving", "car"),
            listOf(
                "Where is the train station?",
                "One ticket to [destination], please.",
                "How much is a ticket to the city centre?",
                "Which platform does the train leave from?",
                "Does this bus go to [place]?",
                "What time is the next train?",
                "Is this seat taken?",
                "I'd like to get off at the next stop.",
                "Where do I change trains?",
                "Could you take me to this address, please?",
                "How much is the fare?",
                "Do I need to validate the ticket?",
                "The train leaves at [time].",
                "Where can I park?",
                "Fill it up, please."
            )
        ),
        Theme(
            "Hotel", "🏨",
            listOf("hotel", "hostel", "stay", "staying", "room", "booking"),
            listOf(
                "I have a reservation under the name [name].",
                "I'd like to check in, please.",
                "We're staying for [number] nights.",
                "What time is breakfast?",
                "Could I have another key, please?",
                "The Wi-Fi isn't working in my room.",
                "Is there a safe in the room?",
                "Could you call me a taxi?",
                "Can I leave my luggage here until [time]?",
                "What time is check-out?",
                "The room is too noisy.",
                "Do you have a room for tonight?",
                "Could I have an extra towel, please?",
                "Thank you, the stay was wonderful."
            )
        ),
        Theme(
            "Shopping", "🛍️",
            listOf("shop", "shopping", "buy", "buying", "market", "store", "clothes", "groceries", "supermarket"),
            listOf(
                "How much does this cost?",
                "Do you have this in a bigger size?",
                "I'm just looking, thank you.",
                "Where can I find the bread?",
                "Can I try this on?",
                "I'll take it.",
                "Is there a discount?",
                "Could I have a bag, please?",
                "What time do you close?",
                "Can I pay in cash?",
                "I'd like to return this.",
                "Do you have a receipt?",
                "That's too expensive for me.",
                "Do you have it in another colour?"
            )
        ),
        Theme(
            "Help & Health", "🆘",
            listOf("doctor", "hospital", "health", "medical", "pharmacy", "emergency", "sick", "nurse", "safe", "police"),
            listOf(
                "Can you help me, please?",
                "I don't feel well.",
                "I need to see a doctor.",
                "Is there a pharmacy near here?",
                "I have a headache.",
                "I have a fever.",
                "My stomach hurts.",
                "It hurts here.",
                "I've lost my wallet.",
                "I've lost my passport.",
                "Please call an ambulance.",
                "Where is the nearest hospital?",
                "I need to call the police.",
                "Is it serious?",
                "I'm allergic to penicillin.",
                "Do I need a prescription for this?",
                "I take medication for [word].",
                "Head",
                "Stomach",
                "Back pain",
                "Tooth",
                "Throat",
                "Pain",
                "Medicine",
                "Insurance"
            )
        ),
        Theme(
            "Making Plans", "📅",
            listOf("meet", "meeting", "plans", "date", "dating", "weekend", "invite", "party"),
            listOf(
                "Are you free tomorrow?",
                "Shall we meet at [time]?",
                "Let's meet at the [place].",
                "What time suits you?",
                "I'll be there in ten minutes.",
                "Sorry, I'm running late.",
                "Can we do another day?",
                "I'd love to come.",
                "See you on Saturday!",
                "Would you like to join us?",
                "Let me know when you're there.",
                "I'll call you later.",
                "Where shall we go?"
            )
        ),
        Theme(
            "Phone & Internet", "📶",
            listOf("phone", "call", "text", "message", "internet", "wifi", "online", "sim"),
            listOf(
                "Can I have your number?",
                "I'll text you.",
                "Send me the address, please.",
                "What's the Wi-Fi password?",
                "My battery is dead.",
                "Do you have a charger?",
                "I'll call you back.",
                "Sorry, I missed your call.",
                "The connection is bad.",
                "I need a SIM card.",
                "How much is a month of mobile data?",
                "Can you hear me?",
                "I'll send you a photo."
            )
        ),
        Theme(
            "Money & Bank", "💶",
            listOf("money", "bank", "pay", "paying", "cash", "card", "account", "salary"),
            listOf(
                "Where is the nearest cash machine?",
                "Do you have change for a fifty?",
                "Do you take credit cards?",
                "I'd like to open a bank account.",
                "What is the exchange rate?",
                "I'd like to change money.",
                "Could I have a receipt, please?",
                "I think there's a mistake on the bill.",
                "Is there a fee?",
                "My card isn't working.",
                "Can I withdraw money here?",
                "How much do I owe you?",
                "I've already paid."
            )
        ),
        Theme(
            "Home & Renting", "🏠",
            listOf("flat", "apartment", "rent", "renting", "house", "home", "landlord", "roommate", "roommates", "neighbour", "neighbor"),
            listOf(
                "I'm looking for a flat.",
                "How much is the rent per month?",
                "Is the deposit included?",
                "Are bills included?",
                "When can I move in?",
                "Could I see the apartment?",
                "The heating doesn't work.",
                "There's no hot water.",
                "I'm your new neighbour.",
                "Where do I put the rubbish?",
                "The key doesn't fit.",
                "Is the flat furnished?",
                "I'd like to sign the contract."
            )
        ),
        Theme(
            "Paperwork", "📋",
            listOf("visa", "register", "registration", "office", "appointment", "document", "documents", "permit", "citizenship", "immigration"),
            listOf(
                "I need to register my address.",
                "Do I need an appointment?",
                "I have an appointment at [time].",
                "Here is my ID.",
                "Here is my passport.",
                "Which form do I need?",
                "Where do I sign?",
                "Could you help me fill this in?",
                "Do you need a copy?",
                "How long does it take?",
                "I'm waiting for my residence permit.",
                "Is this the right office?",
                "I need a certificate."
            )
        ),
        Theme(
            "Wishes", "🎉",
            listOf("birthday", "wedding", "christmas", "celebrate", "celebration", "party"),
            listOf(
                "Happy birthday!",
                "Congratulations!",
                "Cheers!",
                "Enjoy your meal!",
                "Bless you!",
                "Get well soon.",
                "Good luck!",
                "Happy New Year!",
                "Merry Christmas!",
                "Have a good trip!",
                "Welcome!",
                "All the best!",
                "Happy holidays!",
                "My condolences."
            )
        ),
        Theme(
            "Time", "🕰️",
            listOf("time", "clock", "late", "early", "punctual"),
            listOf(
                "What time is it?",
                "Do you have the time, please?",
                "It's [number] o'clock.",
                "It's half past [number].",
                "It's a quarter past [number].",
                "It's a quarter to [number].",
                "It's ten past nine.",
                "It's twenty to six.",
                "It's noon.",
                "It's midnight.",
                "In the morning.",
                "In the afternoon.",
                "In the evening.",
                "At night.",
                "What time does it start?",
                "It starts at [time].",
                "I'm a little early.",
                "We're running late."
            )
        ),
        Theme(
            "Weather & Seasons", "🌦️",
            listOf("weather", "rain", "snow", "climate", "season", "seasons", "summer", "winter"),
            listOf(
                "What's the weather like today?",
                "What will the weather be like tomorrow?",
                "It's very hot today.",
                "It's warm outside.",
                "It's cloudy.",
                "It's foggy this morning.",
                "There's a storm coming.",
                "It's freezing!",
                "The sun is shining.",
                "It rained all day yesterday.",
                "It's going to snow tonight.",
                "Take an umbrella with you.",
                "I love the summer here.",
                "Winter is too long for me.",
                "Spring is my favourite season.",
                "The leaves are so beautiful in autumn.",
                "Is it always this cold here?",
                "It gets dark early in winter.",
                "What a storm last night!"
            )
        ),
        Theme(
            "Feelings", "😊",
            listOf("feel", "feeling", "feelings", "emotions", "mood"),
            listOf(
                "How are you feeling?",
                "I'm happy today.",
                "I'm so happy for you!",
                "I'm sad.",
                "I'm exhausted.",
                "I'm nervous about tomorrow.",
                "I'm so excited!",
                "I'm worried about it.",
                "I'm angry about that.",
                "I'm bored.",
                "I'm hungry.",
                "I'm thirsty.",
                "I'm stressed at the moment.",
                "I'm in a good mood.",
                "I'm proud of you.",
                "I was really surprised.",
                "That makes me happy.",
                "Don't worry.",
                "I'm fine, really.",
                "I miss you.",
                "I feel much better now.",
                "What's wrong?"
            )
        ),
        Theme(
            "Describing People", "🧍",
            listOf("describe", "describing", "appearance", "personality", "character", "looks"),
            listOf(
                "What does he look like?",
                "What is she like?",
                "She is tall.",
                "He is quite short.",
                "She has long dark hair.",
                "He has short blond hair.",
                "He has a beard.",
                "She wears glasses.",
                "She has blue eyes.",
                "He is very friendly.",
                "She is really funny.",
                "He is a bit shy.",
                "She is very kind.",
                "He is always in a good mood.",
                "She is hard-working.",
                "He talks a lot.",
                "They are very nice people.",
                "She looks like her mother.",
                "He is older than me.",
                "She is the same age as me.",
                "He is about forty."
            )
        ),
        Theme(
            "Appointments & Calls", "☎️",
            listOf("appointment", "appointments", "booking", "reservation", "dentist", "hairdresser", "telephone"),
            listOf(
                "Hello, this is [name] speaking.",
                "I'm calling about my appointment.",
                "I'd like to make an appointment.",
                "Do you have anything free this week?",
                "Is tomorrow morning possible?",
                "Could it be a bit later?",
                "That works for me.",
                "I need to cancel my appointment.",
                "Could I move my appointment to another day?",
                "Could I speak to the manager, please?",
                "One moment, please.",
                "Could you put me through?",
                "Could I leave a message?",
                "Could you call me back, please?",
                "My number is [number].",
                "Sorry, I have the wrong number.",
                "Could you speak a little louder, please?",
                "Could you send me a confirmation by email?",
                "Thank you for your help. Goodbye."
            )
        ),
        Theme(
            "Likes & Opinions", "👍",
            listOf("like", "likes", "opinion", "opinions", "prefer", "favourite", "favorite", "hobby", "hobbies", "interests"),
            listOf(
                "I like it a lot.",
                "I don't like it very much.",
                "I love cooking.",
                "I hate getting up early.",
                "I prefer tea to coffee.",
                "What do you like doing?",
                "What's your favourite food?",
                "My favourite colour is blue.",
                "I think so too.",
                "I don't think so.",
                "I agree with you.",
                "I'm not sure.",
                "In my opinion, it's too expensive.",
                "I think it's a good idea.",
                "It depends.",
                "That's interesting.",
                "This one is better than that one.",
                "It's the best restaurant in town.",
                "I'd rather stay at home.",
                "I'm interested in history.",
                "It's not my thing.",
                "Which one do you prefer?"
            )
        ),
        Theme(
            "At Work", "🏢",
            listOf("work", "job", "office", "colleague", "colleagues", "boss", "meeting", "career", "business"),
            listOf(
                "What do you do for a living?",
                "Where do you work?",
                "I work full-time.",
                "I work part-time.",
                "I work from home on Fridays.",
                "I start at nine and finish at five.",
                "I have a meeting at ten.",
                "Can we talk for a minute?",
                "I'm busy right now.",
                "I'll send you an email.",
                "Could you help me with this?",
                "When is the deadline?",
                "I'm on my lunch break.",
                "I'm off tomorrow.",
                "I'm on holiday next week.",
                "I'm looking for a job.",
                "I have a job interview.",
                "Who is your boss?",
                "I like my colleagues.",
                "It was a busy day at work."
            )
        ),
        // ---- Word lists, for the starter sheet only (see Theme.inPool) ----
        Theme(
            "Days & Months", "📆",
            listOf("date", "calendar", "week", "month", "year"),
            listOf(
                "What day is it today?",
                "Today is [day].",
                "Monday",
                "Tuesday",
                "Wednesday",
                "Thursday",
                "Friday",
                "Saturday",
                "Sunday",
                "January",
                "February",
                "March",
                "April",
                "May",
                "June",
                "July",
                "August",
                "September",
                "October",
                "November",
                "December",
                "My birthday is in [month].",
                "The weekend",
                "Next week",
                "Last month",
                "Every day",
                "What's the date today?",
                "Today's date is [number] [month].",
                "Spring",
                "Summer",
                "Autumn",
                "Winter"
            ),
            inPool = false
        ),
        Theme(
            "Numbers", "🔢",
            listOf("number", "numbers", "count", "counting", "price", "prices"),
            listOf(
                // Every number is built from these: 0 to 30 in full (the teens and twenties are where languages are irregular),
                // the tens, the hundreds and thousands, and how they are used.
                "zero",
                "one",
                "two",
                "three",
                "four",
                "five",
                "six",
                "seven",
                "eight",
                "nine",
                "ten",
                "eleven",
                "twelve",
                "thirteen",
                "fourteen",
                "fifteen",
                "sixteen",
                "seventeen",
                "eighteen",
                "nineteen",
                "twenty",
                "twenty-one",
                "twenty-two",
                "twenty-three",
                "twenty-four",
                "twenty-five",
                "twenty-six",
                "twenty-seven",
                "twenty-eight",
                "twenty-nine",
                "thirty",
                "thirty-one",
                "forty",
                "forty-two",
                "fifty",
                "sixty",
                "seventy",
                "seventy-five",
                "eighty",
                "ninety",
                "ninety-nine",
                "one hundred",
                "one hundred and one",
                "one hundred and ten",
                "one hundred and twenty-five",
                "two hundred",
                "three hundred",
                "five hundred",
                "nine hundred and ninety-nine",
                "one thousand",
                "one thousand five hundred",
                "two thousand",
                "ten thousand",
                "one hundred thousand",
                "one million",
                "one billion",
                "first",
                "second",
                "third",
                "fourth",
                "fifth",
                "sixth",
                "seventh",
                "eighth",
                "ninth",
                "tenth",
                "twentieth",
                "hundredth",
                "once",
                "twice",
                "three times",
                "a half",
                "a quarter",
                "a third",
                "one and a half",
                "a dozen",
                "a pair",
                "a couple",
                "twenty percent",
                "minus five",
                "plus",
                "equals",
                "the year 1999",
                "the year 2026",
                "It costs [number] euros.",
                "It costs two euros fifty.",
                "My phone number is [number].",
                "My number is zero seven one two, three four five, six seven eight."
            ),
            inPool = false
        ),
        Theme(
            "Everyday Words", "🧩",
            listOf("basics", "basic", "beginner", "vocabulary"),
            listOf(
                "I",
                "you",
                "he",
                "she",
                "we",
                "they",
                "this",
                "that",
                "here",
                "there",
                "now",
                "later",
                "today",
                "tomorrow",
                "yesterday",
                "always",
                "never",
                "sometimes",
                "yes",
                "no",
                "maybe",
                "and",
                "or",
                "but",
                "because",
                "very",
                "a little",
                "a lot",
                "more",
                "less",
                "big",
                "small",
                "good",
                "bad",
                "hot",
                "cold",
                "new",
                "old",
                "on the left",
                "on the right",
                "near",
                "far",
                "cheap",
                "expensive",
                "early",
                "late",
                "with",
                "without",
                "before",
                "after",
                "I want",
                "I need",
                "I have",
                "I like",
                "I don't know",
                "I can",
                "I can't"
            ),
            inPool = false
        ),
        Theme(
            "Question Words", "❓",
            listOf("question", "questions", "ask", "asking"),
            listOf(
                "Who?",
                "What?",
                "Where?",
                "When?",
                "Why?",
                "How?",
                "How much?",
                "How many?",
                "Which one?",
                "Whose?",
                "How long?",
                "How often?",
                "What time?",
                "Where from?"
            ),
            inPool = false
        ),
        Theme(
            "Family & People", "👨‍👩‍👧",
            listOf("family", "relatives", "children", "kids", "baby", "friends"),
            listOf(
                "mother",
                "father",
                "parents",
                "sister",
                "brother",
                "daughter",
                "son",
                "children",
                "baby",
                "grandmother",
                "grandfather",
                "aunt",
                "uncle",
                "cousin",
                "wife",
                "husband",
                "partner",
                "friend",
                "neighbour",
                "colleague",
                "boss",
                "man",
                "woman",
                "boy",
                "girl"
            ),
            inPool = false
        ),
        Theme(
            "Colours", "🎨",
            listOf("colour", "colours", "color", "colors"),
            listOf(
                "red",
                "blue",
                "green",
                "yellow",
                "orange",
                "purple",
                "pink",
                "brown",
                "black",
                "white",
                "grey",
                "light blue",
                "dark green"
            ),
            inPool = false
        ),
        Theme(
            "Food & Drink", "🥐",
            listOf("groceries", "ingredients", "recipe", "recipes", "breakfast"),
            listOf(
                "water",
                "coffee",
                "tea",
                "milk",
                "juice",
                "beer",
                "wine",
                "bread",
                "butter",
                "cheese",
                "eggs",
                "rice",
                "pasta",
                "potatoes",
                "chicken",
                "beef",
                "pork",
                "fish",
                "vegetables",
                "fruit",
                "apple",
                "salad",
                "soup",
                "salt",
                "pepper",
                "sugar",
                "chocolate",
                "cake",
                "breakfast",
                "lunch",
                "dinner",
                "vegetarian",
                "vegan",
                "spicy",
                "sweet"
            ),
            inPool = false
        ),
        Theme(
            "Clothes", "👕",
            listOf("clothing", "wear", "wearing", "fashion", "outfit"),
            listOf(
                "T-shirt",
                "shirt",
                "sweater",
                "jacket",
                "coat",
                "trousers",
                "jeans",
                "shorts",
                "skirt",
                "dress",
                "suit",
                "shoes",
                "boots",
                "sports shoes",
                "socks",
                "underwear",
                "hat",
                "cap",
                "scarf",
                "gloves",
                "belt",
                "bag",
                "umbrella",
                "sunglasses",
                "swimsuit",
                "pyjamas",
                "size",
                "to wear",
                "to try on"
            ),
            inPool = false
        ),
        Theme(
            "Body & Symptoms", "🩹",
            listOf("body", "symptom", "symptoms", "illness", "ill", "injury", "hurt"),
            listOf(
                "arm",
                "leg",
                "hand",
                "foot",
                "finger",
                "knee",
                "shoulder",
                "elbow",
                "neck",
                "chest",
                "eye",
                "ear",
                "nose",
                "mouth",
                "skin",
                "heart",
                "blood",
                "cough",
                "a cold",
                "flu",
                "sore throat",
                "toothache",
                "nausea",
                "dizzy",
                "swollen",
                "broken",
                "injury",
                "plaster",
                "bandage",
                "tablet",
                "prescription"
            ),
            inPool = false
        ),
        Theme(
            "House & Furniture", "🪑",
            listOf("furniture", "furnish", "rooms", "kitchen", "moving"),
            listOf(
                "room",
                "kitchen",
                "bathroom",
                "bedroom",
                "living room",
                "hallway",
                "balcony",
                "garden",
                "stairs",
                "elevator",
                "door",
                "window",
                "floor",
                "wall",
                "roof",
                "table",
                "chair",
                "sofa",
                "bed",
                "wardrobe",
                "shelf",
                "desk",
                "lamp",
                "mirror",
                "carpet",
                "curtains",
                "fridge",
                "oven",
                "cooker",
                "washing machine",
                "dishwasher",
                "sink",
                "shower",
                "toilet",
                "heating",
                "key",
                "upstairs",
                "downstairs"
            ),
            inPool = false
        ),
        Theme(
            "Places in Town", "🏙️",
            listOf("town", "city", "places", "buildings", "neighbourhood", "neighborhood"),
            listOf(
                "supermarket",
                "bakery",
                "post office",
                "library",
                "school",
                "university",
                "church",
                "museum",
                "cinema",
                "theatre",
                "park",
                "square",
                "bridge",
                "station",
                "bus stop",
                "town hall",
                "police station",
                "petrol station",
                "shopping centre",
                "market",
                "swimming pool",
                "gym",
                "hairdresser",
                "dentist",
                "doctor's office",
                "playground",
                "car park",
                "city centre",
                "street",
                "corner"
            ),
            inPool = false
        ),
        Theme(
            "Signs", "🪧",
            listOf("signs", "sign", "read", "reading"),
            listOf(
                "Entrance",
                "Exit",
                "Emergency exit",
                "Push",
                "Pull",
                "Open",
                "Closed",
                "Toilets",
                "Men",
                "Women",
                "No smoking",
                "No entry",
                "Out of order",
                "Cashier",
                "Sale",
                "Platform",
                "Arrivals",
                "Departures",
                "Information",
                "Ticket office",
                "Pharmacy",
                "Police",
                "Hospital",
                "Danger",
                "Caution",
                "Vacant",
                "Occupied",
                "Parking",
                "Reserved",
                "Opening hours"
            ),
            inPool = false
        ),
        Theme(
            "Phone Settings", "📱",
            listOf("settings", "android", "iphone", "device"),
            listOf(
                // Setting the phone to the language is the oldest immersion trick; this is what the Settings app then says.
                "Settings",
                "Wi-Fi",
                "Bluetooth",
                "Mobile data",
                "Airplane mode",
                "Hotspot",
                "Network",
                "Connected devices",
                "Battery",
                "Battery saver",
                "Charging",
                "Display",
                "Brightness",
                "Dark mode",
                "Font size",
                "Wallpaper",
                "Lock screen",
                "Screen timeout",
                "Sound",
                "Volume",
                "Vibration",
                "Ringtone",
                "Silent",
                "Notifications",
                "Do not disturb",
                "Storage",
                "Apps",
                "Permissions",
                "Privacy",
                "Security",
                "Location",
                "Accounts",
                "Password",
                "Fingerprint",
                "Language",
                "Keyboard",
                "Date and time",
                "Time zone",
                "Accessibility",
                "System",
                "Software update",
                "About phone",
                "Backup",
                "Reset",
                "Restart",
                "Power off",
                "Camera",
                "Microphone",
                "Contacts",
                "Messages",
                "Calendar",
                "Photos",
                "Files",
                "Downloads",
                "Search",
                "Save",
                "Cancel",
                "Delete",
                "Allow",
                "Deny",
                "Turn on",
                "Turn off",
                "Connect",
                "Disconnect",
                "Back",
                "Next",
                "Done",
                "Skip",
                "Continue",
                "Update",
                "Install",
                "Uninstall",
                "Sign in",
                "Sign out",
                "Share",
                "Copy",
                "Paste",
                "Edit",
                "Undo",
                "Default",
                "Advanced",
                "Available",
                "Unavailable",
                "Connected",
                "Enabled",
                "Disabled"
            ),
            inPool = false
        )
    )

    /** The theme named [category], if there is one. */
    fun theme(category: String): Theme? = THEMES.firstOrNull { it.category == category }

    /** The placeholder names in [sentence] (`[city]` gives `city`), in order, without repeats. */
    fun placeholders(sentence: String): List<String> = PLACEHOLDER.findAll(sentence).map { it.groupValues[1] }.distinct().toList()

    /**
     * [sentence] with its placeholders replaced by [fillers] (placeholder name to value), or null if one
     * has no value (blank counts as none), so the sentence can't be used as it is. An "a" right before a
     * blank becomes "an" where the word that goes in asks for it: "I work as an engineer."
     */
    fun fill(sentence: String, fillers: Map<String, String>): String? {
        var missing = false
        val withArticles = ARTICLE_BEFORE_BLANK.replace(sentence) { match ->
            val value = fillers[match.groupValues[2]]?.trim()
            if (value.isNullOrEmpty() || indefiniteArticle(value) == "a") match.value
            else (if (match.groupValues[1] == "A") "An" else "an") + " [" + match.groupValues[2] + "]"
        }
        val filled = PLACEHOLDER.replace(withArticles) { match ->
            val value = fillers[match.groupValues[1]]?.trim()
            if (value.isNullOrEmpty()) {
                missing = true
                ""
            } else value
        }
        return if (missing) null else filled
    }

    /** The themes in the order to draw from for [goal]: those its words point to first, then the rest. */
    fun themesFor(goal: String?): List<Theme> {
        val words = normalizedWords(goal.orEmpty()).toSet()
        val scored = THEMES.map { theme -> theme to theme.keywords.count { it in words } }
        val matching = scored.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        return matching + THEMES.filterNot { it in matching }
    }

    /**
     * The topic [goal] points to most, with the first of its sentences the pool can offer, to show while the
     * goal is being typed what it will change; null when the goal names none of the topics' words, since the
     * sentences then don't depend on it.
     */
    fun goalPreview(goal: String, fillers: Map<String, String> = emptyMap()): Pair<Theme, String>? {
        val words = normalizedWords(goal).toSet()
        val theme = THEMES.filter { it.inPool }
            .map { it to it.keywords.count { keyword -> keyword in words } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first ?: return null
        val sentence = theme.sentences.firstNotNullOfOrNull { fill(it, fillers) } ?: return null
        return theme to sentence
    }

    /**
     * Up to [count] sentences for [goal] that aren't in [avoid], for the daily session's pool: the first
     * three themes (those the goal points to) taking turns, then the rest. Sentences with a placeholder
     * that [fillers] can't fill are left out, since nobody is there to fill them.
     */
    fun pick(goal: String?, avoid: Collection<String>, count: Int, fillers: Map<String, String> = emptyMap()): List<ProposedSentence> {
        val taken = avoid.mapTo(HashSet()) { normalizedWords(it) }
        val themes = themesFor(goal).filter { it.inPool }
        val queues = themes.map { theme ->
            theme to ArrayDeque(theme.sentences.mapNotNull { fill(it, fillers) }.filter { normalizedWords(it) !in taken })
        }
        val picked = ArrayList<ProposedSentence>(count)
        val leading = queues.take(3)
        while (picked.size < count && leading.any { it.second.isNotEmpty() }) {
            for ((theme, queue) in leading) {
                if (picked.size >= count) break
                queue.removeFirstOrNull()?.let { picked += ProposedSentence(it, "", theme.category, theme.emoji) }
            }
        }
        for ((theme, queue) in queues.drop(leading.size)) {
            while (picked.size < count) {
                val sentence = queue.removeFirstOrNull() ?: break
                picked += ProposedSentence(sentence, "", theme.category, theme.emoji)
            }
        }
        return picked
    }
}
