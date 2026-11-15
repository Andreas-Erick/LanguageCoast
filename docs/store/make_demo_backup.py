"""Writes docs/store/demo-backup.json: the demo data the store screenshots show.

The dates in a backup go stale (the streak, what is due, this month's recap), so the file is made for a day:

    python docs/store/make_demo_backup.py                 # for today, in this computer's time zone
    python docs/store/make_demo_backup.py --today 2026-11-15 --utc-offset 1

Restore the file under Settings > Backup on an emulator set to the same date and time zone.

What it holds: three coasts (German is the one in focus), 16 islands named after starter topics and islands
about you so the route shows levels, about 100 cards (most learned, a few due today, some still new), a
12-day streak that ends yesterday so Today offers a session, and About you answers for "Anna".
"""
import argparse
import datetime as dt
import json
import os

DAY = 24 * 60 * 60 * 1000

# (island name, emoji, level-relevant kind, cards). Island names must match the app's starter topics
# (data/PhraseBank.kt) and islands about you (data/PersonalIslands.kt), or the route won't count them.
# Each card: (native, translation, state) with state "learned", "due", "leaky" (due, forgotten three times) or "new".
GERMAN = [
    ("Greetings", "👋", [
        ("Good morning!", "Guten Morgen!", "learned"),
        ("Good evening.", "Guten Abend.", "learned"),
        ("How are you?", "Wie geht es dir?", "learned"),
        ("Nice to meet you.", "Freut mich, Sie kennenzulernen.", "learned"),
        ("See you tomorrow.", "Bis morgen.", "learned"),
        ("Have a nice day.", "Schönen Tag noch.", "learned"),
    ]),
    ("Getting By", "🗣️", [
        ("I only speak a little German.", "Ich spreche nur ein bisschen Deutsch.", "learned"),
        ("Could you say that again, more slowly?", "Könnten Sie das bitte langsamer wiederholen?", "leaky"),
        ("Sorry, I didn't understand that.", "Entschuldigung, das habe ich nicht verstanden.", "learned"),
        ("What does that mean?", "Was bedeutet das?", "learned"),
        ("How do you say that in German?", "Wie sagt man das auf Deutsch?", "learned"),
        ("Could you write that down, please?", "Könnten Sie das bitte aufschreiben?", "learned"),
    ]),
    ("Small Talk", "💬", [
        ("How was your day?", "Wie war dein Tag?", "due"),
        ("Where are you from?", "Woher kommst du?", "learned"),
        ("What do you do for work?", "Was machst du beruflich?", "due"),
        ("The weather is lovely today.", "Das Wetter ist heute herrlich.", "learned"),
        ("Do you live around here?", "Wohnst du hier in der Nähe?", "learned"),
        ("What are you doing this weekend?", "Was machst du am Wochenende?", "learned"),
    ]),
    ("About Me", "🙋", [
        ("My name is Anna.", "Ich heiße Anna.", "learned"),
        ("I'm from Norway.", "Ich komme aus Norwegen.", "learned"),
        ("I live in Oslo.", "Ich wohne in Oslo.", "learned"),
        ("I'm a designer.", "Ich bin Designerin.", "learned"),
        ("I'm learning German.", "Ich lerne Deutsch.", "learned"),
    ]),
    ("Restaurant", "🍽️", [
        ("A table for two, please.", "Einen Tisch für zwei, bitte.", "learned"),
        ("Could we see the menu, please?", "Könnten wir bitte die Speisekarte sehen?", "due"),
        ("I'd like the soup of the day.", "Ich hätte gern die Tagessuppe.", "learned"),
        ("What do you recommend?", "Was empfehlen Sie?", "learned"),
        ("The bill, please.", "Die Rechnung, bitte.", "learned"),
        ("Can I pay by card?", "Kann ich mit Karte zahlen?", "due"),
    ]),
    ("Directions", "🧭", [
        ("Where is the train station?", "Wo ist der Bahnhof?", "learned"),
        ("Is it far from here?", "Ist es weit von hier?", "learned"),
        ("Turn left at the traffic lights.", "Biegen Sie an der Ampel links ab.", "leaky"),
        ("Go straight ahead.", "Gehen Sie geradeaus.", "learned"),
        ("I'm lost.", "Ich habe mich verlaufen.", "learned"),
        ("Which bus goes to the city centre?", "Welcher Bus fährt ins Stadtzentrum?", "learned"),
    ]),
    ("Shopping", "🛍️", [
        ("How much does this cost?", "Wie viel kostet das?", "learned"),
        ("I'm just looking, thank you.", "Ich schaue mich nur um, danke.", "learned"),
        ("Do you have this in a bigger size?", "Haben Sie das eine Nummer größer?", "learned"),
        ("Can I try this on?", "Kann ich das anprobieren?", "learned"),
        ("I'll take it.", "Ich nehme es.", "learned"),
    ]),
    ("Time", "🕰️", [
        ("What time is it?", "Wie spät ist es?", "learned"),
        ("It's half past eight.", "Es ist halb neun.", "learned"),
        ("It's a quarter to six.", "Es ist Viertel vor sechs.", "learned"),
        ("At what time does it open?", "Um wie viel Uhr öffnet es?", "learned"),
        ("I'm running late.", "Ich bin spät dran.", "learned"),
    ]),
    ("Numbers", "🔢", [
        ("One coffee and two teas, please.", "Einen Kaffee und zwei Tee, bitte.", "learned"),
        ("That's twelve euros fifty.", "Das macht zwölf Euro fünfzig.", "learned"),
        ("My room number is forty-two.", "Meine Zimmernummer ist zweiundvierzig.", "learned"),
        ("I'm thirty-four years old.", "Ich bin vierunddreißig Jahre alt.", "learned"),
        ("Platform seven.", "Gleis sieben.", "learned"),
    ]),
    ("Food & Drink", "🥐", [
        ("I'd like a coffee with milk.", "Ich hätte gern einen Kaffee mit Milch.", "learned"),
        ("A glass of water, please.", "Ein Glas Wasser, bitte.", "learned"),
        ("I don't eat meat.", "Ich esse kein Fleisch.", "learned"),
        ("The bread is fresh.", "Das Brot ist frisch.", "learned"),
        ("Is there cheese in it?", "Ist da Käse drin?", "new"),
        ("Breakfast is at eight.", "Frühstück gibt es um acht.", "new"),
    ]),
    ("Personal Details", "🪪", [
        ("I'm 34 years old.", "Ich bin 34 Jahre alt.", "learned"),
        ("My birthday is on the twelfth of March.", "Ich habe am zwölften März Geburtstag.", "learned"),
        ("I speak Norwegian and English.", "Ich spreche Norwegisch und Englisch.", "learned"),
        ("I've been married for three years.", "Ich bin seit drei Jahren verheiratet.", "learned"),
        ("I was born in Bergen.", "Ich bin in Bergen geboren.", "learned"),
        ("I work as a designer in Oslo.", "Ich arbeite als Designerin in Oslo.", "learned"),
    ]),
    ("Days & Months", "📆", [
        ("See you on Monday.", "Bis Montag.", "learned"),
        ("The museum is closed on Sundays.", "Das Museum ist sonntags geschlossen.", "learned"),
        ("I'm arriving in May.", "Ich komme im Mai an.", "new"),
        ("What's the date today?", "Der Wievielte ist heute?", "new"),
        ("My train leaves on Friday morning.", "Mein Zug fährt am Freitagmorgen.", "new"),
    ]),
    ("Airport", "✈️", [
        ("Where is the check-in desk for Berlin?", "Wo ist der Check-in-Schalter für Berlin?", "learned"),
        ("I'd like a window seat, please.", "Ich hätte gern einen Fensterplatz, bitte.", "learned"),
        ("My flight is delayed.", "Mein Flug hat Verspätung.", "learned"),
        ("Where is passport control?", "Wo ist die Passkontrolle?", "new"),
        ("I only have hand luggage.", "Ich habe nur Handgepäck.", "new"),
    ]),
    ("Hotel", "🏨", [
        ("I have a reservation under the name Anna Berg.", "Ich habe eine Reservierung auf den Namen Anna Berg.", "new"),
        ("Is breakfast included?", "Ist das Frühstück inbegriffen?", "new"),
        ("What time is check-out?", "Wann muss ich auschecken?", "new"),
        ("The key card doesn't work.", "Die Schlüsselkarte funktioniert nicht.", "new"),
        ("Could I have another towel?", "Könnte ich noch ein Handtuch bekommen?", "new"),
    ]),
    ("My Family", "👪", [
        ("My parents live in Bergen.", "Meine Eltern wohnen in Bergen.", "learned"),
        ("My mother is a retired teacher.", "Meine Mutter ist pensionierte Lehrerin.", "learned"),
        ("I have an older sister called Ingrid.", "Ich habe eine ältere Schwester, sie heißt Ingrid.", "learned"),
        ("She is a doctor in Trondheim.", "Sie ist Ärztin in Trondheim.", "new"),
        ("I call my mother every Sunday.", "Ich rufe meine Mutter jeden Sonntag an.", "new"),
        ("We all meet at Christmas.", "Wir treffen uns alle an Weihnachten.", "new"),
    ]),
]

SPANISH = [
    ("Greetings", "👋", [
        ("Good morning!", "¡Buenos días!", "learned"),
        ("How are you?", "¿Cómo estás?", "learned"),
        ("Nice to meet you.", "Mucho gusto.", "learned"),
        ("See you later.", "Hasta luego.", "due"),
        ("Thank you very much.", "Muchas gracias.", "learned"),
    ]),
    ("Restaurant", "🍽️", [
        ("A table for two, please.", "Una mesa para dos, por favor.", "learned"),
        ("The bill, please.", "La cuenta, por favor.", "learned"),
        ("What do you recommend?", "¿Qué me recomienda?", "due"),
        ("It was delicious.", "Estuvo delicioso.", "new"),
    ]),
    ("Family & People", "👨‍👩‍👧", [
        ("This is my husband.", "Él es mi esposo.", "learned"),
        ("Do you have brothers or sisters?", "¿Tienes hermanos?", "learned"),
        ("My sister lives in Oslo.", "Mi hermana vive en Oslo.", "new"),
        ("Your family is lovely.", "Tu familia es encantadora.", "new"),
    ]),
]

ITALIAN = [
    ("Greetings", "👋", [
        ("Good morning!", "Buongiorno!", "learned"),
        ("How are you?", "Come stai?", "learned"),
        ("Thank you very much.", "Grazie mille.", "new"),
        ("See you tomorrow.", "A domani.", "new"),
    ]),
]

COASTS = [
    # (id, language, goal, region, starter sentences already offered, islands, days since the coast was started)
    (1, "German", "A trip to Berlin in May", "Germany", 60, GERMAN, 70),
    (2, "Spanish", "Talking with Lucía's family", "Mexico", 12, SPANISH, 40),
    (3, "Italian", None, None, 4, ITALIAN, 6),
]

# Islands about you: the facts each is written from, as the app labels them (data/PersonalIslands.kt and
# data/Profile.kt). The app compares a mark of these facts with the one stored when the island was written;
# if the labels there change, change them here too, or the route shows the island as "changed".
PROFILE = {"name": "Anna", "city": "Oslo", "country": "Norway", "home country": "Norway", "hometown": "Bergen", "job": "designer"}
PROFILE_LABELS = {
    "name": "Your name", "city": "City you live in", "country": "Country you live in",
    "home country": "Country you're from", "hometown": "Town you were born in", "job": "Your job",
}
ANSWERS = {
    "details_age": "34",
    "details_birthday": "12 March",
    "details_languages": "Norwegian (native), English (fluent)",
    "details_relationship": "married for three years",
    "family_parents": "Kari, a retired teacher, and Jon; they live in Bergen",
    "family_siblings": "one older sister, Ingrid, 38, a doctor in Trondheim",
    "family_together": "I call my mother every Sunday; we all meet at Christmas",
}
ABOUT_ISLANDS = {
    # island id: (island name, profile fields it uses, [(question key, question label)])
    "details": ("Personal Details", ["name", "city", "country", "home country", "hometown", "job"], [
        ("details_age", "How old are you?"),
        ("details_birthday", "When is your birthday?"),
        ("details_languages", "Which languages do you speak, and how well?"),
        ("details_relationship", "Are you single, in a relationship, married?"),
    ]),
    "family": ("My Family", [], [
        ("family_parents", "Your parents: names, where they live, what they do"),
        ("family_siblings", "Brothers and sisters: names, ages, what they do"),
        ("family_relatives", "Other relatives you're close to"),
        ("family_together", "How often do you see or call them, and what do you do together?"),
    ]),
}


# Islands studied in the week of the screenshots, one short of the weekly goal of 5.
ISLANDS_THIS_WEEK = 4


def kotlin_hash(text):
    """String.hashCode() as Kotlin and Java compute it."""
    h = 0
    data = text.encode("utf-16-be")
    for i in range(0, len(data), 2):
        h = (31 * h + ((data[i] << 8) | data[i + 1])) & 0xFFFFFFFF
    return h - (1 << 32) if h & 0x80000000 else h


def fingerprint(island_id):
    _, fields, questions = ABOUT_ISLANDS[island_id]
    facts = [(PROFILE_LABELS[f], PROFILE[f]) for f in fields if PROFILE.get(f)]
    facts += [(label, ANSWERS[key].strip()) for key, label in questions if ANSWERS.get(key, "").strip()]
    return str(kotlin_hash("\n".join("%s=%s" % fact for fact in facts)))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--today", help="the day the screenshots are taken, YYYY-MM-DD (default: today)")
    parser.add_argument("--utc-offset", type=float, help="the emulator's time zone as hours from UTC (default: this computer's)")
    args = parser.parse_args()

    today = dt.date.fromisoformat(args.today) if args.today else dt.date.today()
    if args.utc_offset is None:
        offset = dt.datetime.now().astimezone().utcoffset()
    else:
        offset = dt.timedelta(hours=args.utc_offset)
    zone = dt.timezone(offset)

    def start_of(day):
        return int(dt.datetime(day.year, day.month, day.day, tzinfo=zone).timestamp() * 1000)

    def evening_of(day):
        return start_of(day) + 19 * 60 * 60 * 1000

    def days_ago(n):
        return today - dt.timedelta(days=n)

    built_names = {name for name, _, _ in ABOUT_ISLANDS.values()}
    coasts, islands, cards, topics_added = [], [], [], []
    island_id = card_id = 0
    since_monday = today.weekday()
    this_week = 0
    for coast_id, language, goal, region, bank_offered, content, age in COASTS:
        coast = {"id": coast_id, "language": language, "creationDate": start_of(days_ago(age)), "bankOffered": bank_offered}
        if goal:
            coast["goal"] = goal
        if region:
            coast["region"] = region
        coasts.append(coast)
        for position, (name, emoji, island_cards) in enumerate(content):
            island_id += 1
            studied = any(state != "new" for _, _, state in island_cards)
            # A few islands were studied since Monday, the first of them yesterday, and the rest the week before:
            # the weekly goal then reads as under way ("4 of 5 islands"), not as passed three times over.
            last_studied = None
            if studied and this_week < ISLANDS_THIS_WEEK and since_monday > 0:
                last_studied = evening_of(days_ago(1 + this_week % since_monday))
                this_week += 1
            elif studied:
                last_studied = evening_of(days_ago(since_monday + 1 + position % 5))
            island = {"id": island_id, "coastId": coast_id, "name": name, "creationDate": start_of(days_ago(max(age - position * 4, 2))), "emoji": emoji}
            if last_studied:
                island["lastStudied"] = last_studied
            islands.append(island)
            if name not in built_names:
                topics_added.append("%d:%s" % (coast_id, name))
            for index, (native, target, state) in enumerate(island_cards):
                card_id += 1
                card = {"id": card_id, "islandId": island_id, "nativeText": native, "targetText": target, "alternatives": []}
                if state == "learned":
                    # Reviewed in the last two weeks, back between next week and next month.
                    reviewed = days_ago(1 + (card_id * 3) % 12)
                    interval = 9 + (card_id * 7) % 40
                    card.update(stability=float(interval), difficulty=4.0 + (card_id % 3), lastReviewed=evening_of(reviewed),
                                due=start_of(reviewed + dt.timedelta(days=interval)))
                elif state == "due":
                    card.update(stability=3.0, difficulty=5.5, lastReviewed=evening_of(days_ago(3)), due=start_of(today))
                elif state == "leaky":
                    card.update(stability=1.5, difficulty=7.5, lastReviewed=evening_of(days_ago(2)), due=start_of(today), lapses=3)
                cards.append(card)

    study_days = [days_ago(n).isoformat() for n in range(1, 13)]
    settings = {
        "nativeLanguage": "English",
        "activeCoastId": 1,
        "translationProvider": "OnDevice",
        "streakCount": len(study_days),
        "lastStudyDate": study_days[0],
        "studyDays": study_days,
        "reminderEnabled": True,
        "reminderMinuteOfDay": 19 * 60,
        "studyReversed": False,
        "weeklyGoal": 5,
        "themeMode": "dark",
        "starterTopicsAdded": topics_added,
        "profile": PROFILE,
        "personalAnswers": ANSWERS,
        "personalIslandsBuilt": {"1:%s" % island: fingerprint(island) for island in ABOUT_ISLANDS},
        "wording": "feminine",
        "pace": "steady",
    }
    backup = {
        "format": "language-coast-backup",
        "version": 1,
        "createdAt": evening_of(days_ago(1)),
        "settings": settings,
        "coasts": coasts,
        "islands": islands,
        "cards": cards,
    }
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "demo-backup.json")
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(backup, out, ensure_ascii=False, indent=1)
        out.write("\n")
    due = sum(1 for c in cards if c.get("due") is not None and c["due"] <= start_of(today))
    new = sum(1 for c in cards if c.get("due") is None)
    print("%s: %d coasts, %d islands, %d cards (%d due today, %d new), streak %d, for %s UTC%+g" % (
        path, len(coasts), len(islands), len(cards), due, new, len(study_days), today.isoformat(), offset.total_seconds() / 3600))


if __name__ == "__main__":
    main()
