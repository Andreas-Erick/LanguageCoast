# Store listings

Texts, images and form answers for Google Play and the App Store, ready to paste into the Play Console and App Store Connect. Written for version 3.1.0 on November 15, 2026.

The texts describe 3.1.0: the permission dialog for cloud AI providers, "Report this answer", the links under Settings > About, reminders that ask after the first session, and practice sheets with word help. Do not publish them with an older build.

Two placeholders appear in this file:

- `<support e-mail>`: the address people can write to. The owner has to choose one (see [What only you can do](#what-only-you-can-do)).
- `<review API key>`: a Gemini key made only for store review. It goes into the Play Console and App Store Connect, nowhere else. Never commit it: this repository is public.

## Files

- `icon-512.png`: the Play Store icon (512 × 512 px).
- `feature-graphic.png`: the Play Store feature graphic (1024 × 500 px).
- `screenshots/android/`: Play Store phone screenshots. `demo-backup.json`: the demo data they show.
- The iOS app icon is in `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset` (1024 × 1024 px).
- Privacy policy: [docs/privacy-policy.md](../privacy-policy.md), published at
  `https://github.com/Andreas-Erick/LanguageCoast/blob/main/docs/privacy-policy.md` and linked under Settings > About.

## Listing text

Character counts include line breaks. The two full descriptions share most of their text, so change them together. They say "flashcards" in the short description, the subtitle and the first sentence, because that is the word people search for. Everywhere else it is "card", as in the app.

| Field | Store | Limit | This text |
|---|---|---|---|
| App name | both | 30 | 14 |
| Short description | Google Play | 80 | 77 |
| Subtitle | App Store | 30 | 30 |
| Promotional text | App Store | 170 | 160 |
| Keywords | App Store | 100 bytes | 96 |
| Full description | Google Play | 4000 | 3962 |
| Full description | App Store | 4000 | 3362 |
| What's new in 3.1.0 | Google Play | 500 | 469 |

**App name** (both stores, 14 of 30 characters):

```text
Language Coast
```

**Category:** Education (both stores).

**Short description** (Google Play, 77 of 80 characters):

```text
Your own sentences as flashcards, one short session a day. No account needed.
```

**Subtitle** (App Store, 30 of 30 characters):

```text
Flashcards from your sentences
```

**Promotional text** (App Store, 160 of 170 characters):

```text
One short session a day: a few cards to review, then new sentences picked for your goal. Follow a route from A1 to B1, with islands written about your own life.
```

**Keywords** (App Store, 96 of 100 bytes). No word from the name or the subtitle is repeated, every keyword is longer than two characters, and no other app is named:

```text
vocabulary,learn,study,translate,phrases,srs,fsrs,spaced repetition,cefr,memorize,german,spanish
```

**Full description, Google Play** (3962 of 4000 characters). The last paragraph is the Languages line. Google Translate is named because Google's attribution rules ask the description to say that it powers the translation:

```text
Language Coast is a flashcard app for the sentences you actually want to say. It brings each card back just before you would forget it.

Every language you learn gets its own coast. On a coast, your cards sit on islands by topic, like Travel, Restaurant or My Family.

One short session a day
• Today shows one button. The session reviews a limited number of due cards, never the whole pile, then offers new sentences to keep or skip.
• Say why you are learning the language (a trip, a person, a job) and where you will use it. New sentences are picked for that goal, and an AI provider translates with that place's words.
• Pick a pace: Steady, Fast or Sprint (3, 10 or 20 new cards a day). After a few days away the session shrinks instead of piling up.

A route from A1 to B1
• Every coast shows the level you are on, how far you are, and the next island to take.
• About 900 starter sentences and words in 37 topics, from greetings to paperwork. One tap adds a topic as an island.
• Islands about you: answer a few optional questions about your family, home, work and free time, and an AI model writes sentences about your own life. You look them over and keep what is true.
• At B1: opinions with reasons, telling a story, complaints, what-ifs.

Your own sentences
• Type a sentence in your language. It is translated and filed on an island.
• Or write it in the language you are learning and have an AI provider correct it.
• Paste a paragraph, or read the text off a photo (Latin letters only), and tick the sentences you want as cards.
• With an AI provider, longer sentences come with other ways to say them.

Study your way
• Flip the card and grade yourself, type the translation, or fill the gap in it.
• Spaced repetition (FSRS) decides when each card comes back.
• Leaky words: the cards you keep forgetting, in one short session.
• Practice sheets: a PDF of new sentences to translate, each built from one of your cards, with word help and an answer key.

On the back of every card
• Hear the translation read aloud.
• Tap a word and your AI provider explains it, with a dict.cc link alongside.
• Check translation: your AI provider reads the card as a native speaker would and suggests a fix.
• Ask about this card: why this form, when would I say this?
• Rehearse an island as a one-minute talk.

Stay on track
• A streak with a weekly goal, and a daily reminder you can switch off.
• A recap of your month, also as a picture to share.
• Home-screen widget with your streak and the cards due today.
• Back up everything to a file, export to Anki, CSV or Markdown, and import Anki decks (.apkg) and CSV files.

No account, no ads
There is no account, and the app has no server of its own: your cards are stored on your phone.

Translate on your phone, free and offline once a language is downloaded. The built-in translator is powered by Google Translate. It sends Google usage diagnostics, not your text, and it only translates.

Islands about you, the B1 islands, practice sheets, word explanations, checks and questions need an AI model: a local one that runs on your phone (a download of 0.6 GB or more, on recent phones), or a cloud AI provider with your own API key (Google Gemini, OpenAI, Anthropic Claude, Groq, DeepSeek or OpenRouter). Before the app sends a cloud provider anything, it shows what is sent and asks your permission. Your text then goes straight from your phone to it.

AI can make mistakes. Every card can be checked and edited, and answers to your questions have a "Report this answer" link.

30 languages: Albanian, Bosnian, Bulgarian, Croatian, Czech, Danish, Dutch, English, Esperanto, Finnish, French, German, Greek, Hungarian, Icelandic, Italian, Japanese, Korean, Latin, Norwegian, Polish, Portuguese, Romanian, Russian, Serbian, Slovak, Spanish, Swedish, Turkish and Ukrainian. No Chinese, Arabic or Hindi yet. The built-in translator covers all but Bosnian, Latin and Serbian. The app's own texts are in English.
```

**Full description, App Store** (3362 of 4000 characters). No Android-only feature is named, and the second paragraph says up front that a key is needed:

```text
Language Coast is a flashcard app for the sentences you actually want to say. It brings each card back just before you would forget it.

On iPhone, translation and the AI features need your own API key from an AI provider: Google Gemini, OpenAI, Anthropic Claude, Groq, DeepSeek or OpenRouter. Without a key you can still add cards by typing both sides, and study them.

Every language you learn gets its own coast. On a coast, your cards sit on islands by topic, like Travel, Restaurant or My Family.

One short session a day
• Today shows one button. The session reviews a limited number of due cards, never the whole pile, then offers new sentences to keep or skip.
• Say why you are learning the language (a trip, a person, a job) and where you will use it. New sentences are picked for that goal, and your AI provider translates with that place's words.
• Pick a pace: Steady, Fast or Sprint (3, 10 or 20 new cards a day). After a few days away the session shrinks instead of piling up.

A route from A1 to B1
• Every coast shows the level you are on, how far you are, and the next island to take.
• About 900 starter sentences and words in 37 topics, from greetings to paperwork. One tap adds a topic as an island.
• Islands about you: answer a few optional questions about your family, home, work and free time, and your AI provider writes sentences about your own life. You look them over and keep what is true.
• At B1: opinions with reasons, telling a story, complaints, what-ifs.

Your own sentences
• Type a sentence in your language. It is translated and filed on an island.
• Or write it in the language you are learning and have it corrected.
• Paste a paragraph and tick the sentences you want as cards.
• Longer sentences come with other ways to say them.

Study your way
• Flip the card and grade yourself, type the translation, or fill the gap in it.
• Spaced repetition (FSRS) decides when each card comes back.
• Leaky words: the cards you keep forgetting, in one short session.

On the back of every card
• Hear the translation read aloud.
• Tap a word and your AI provider explains it, with a dict.cc link alongside.
• Check translation: your AI provider reads the card as a native speaker would and suggests a fix.
• Ask about this card: why this form, when would I say this?
• Rehearse an island as a one-minute talk.

Stay on track
• A streak with a weekly goal, and a daily reminder you can switch off.
• A recap of your month.
• Search all your cards, edit them, and move them between islands.

No account, no ads
There is no account, and the app has no server of its own: your cards are stored on your iPhone.

Before the app sends your AI provider anything, it shows what is sent and asks your permission. Your text then goes straight from your iPhone to that provider, under your own key. Removing the key in Settings takes the permission back.

AI can make mistakes. Every card can be checked and edited, and answers to your questions have a "Report this answer" link.

30 languages: Albanian, Bosnian, Bulgarian, Croatian, Czech, Danish, Dutch, English, Esperanto, Finnish, French, German, Greek, Hungarian, Icelandic, Italian, Japanese, Korean, Latin, Norwegian, Polish, Portuguese, Romanian, Russian, Serbian, Slovak, Spanish, Swedish, Turkish and Ukrainian. No Chinese, Arabic or Hindi yet. The app's own texts are in English.
```

**What's new in 3.1.0** (Google Play release notes, 469 of 500 characters; also for the GitHub Release). The first App Store version has no "What's New" field:

```text
• Better practice sheets: each sentence is built from one of your cards, correct and natural first, with word help under it and your card under each answer.
• A softer light theme, easier on the eyes.
• Before anything goes to a cloud AI provider, the app asks your permission, once per provider.
• Remove a saved API key in Settings.
• Notifications are asked about after your first session, not at first launch.
• Settings > About: feedback, licences, privacy policy.
```

**Version 3.0 in one paragraph** (for anyone who asks what the 3.x line added):

```text
Version 3.0 gave every coast a route through the levels A1, A2 and B1, showing the level you are on and the next island to take. It added islands about you: answer a few questions and your AI provider writes sentences about your family, work and free time. It also added a pace for new cards (Steady, Fast or Sprint), talks that turn an island into about a minute of speech to rehearse, and ten more starter topics.
```

**Contact fields:**

| Field | Store | Value |
|---|---|---|
| Contact e-mail (required, shown publicly) | Google Play | `<support e-mail>` |
| Website (optional) | Google Play | `https://github.com/Andreas-Erick/LanguageCoast` |
| Support URL (required) | App Store | `https://github.com/Andreas-Erick/LanguageCoast/issues` for now. Apple expects this page to lead to a way to reach you, so point it at a page that shows `<support e-mail>` once the address exists. |
| Marketing URL (optional) | App Store | `https://github.com/Andreas-Erick/LanguageCoast` |
| Privacy policy URL | both | `https://github.com/Andreas-Erick/LanguageCoast/blob/main/docs/privacy-policy.md` |

## Screenshots

**Status:** the eight files in `screenshots/android/` were taken from the 3.1.0 code on November 15, 2026, in the dark theme, which is the app's default. They are 1080 × 1920 px, 24-bit PNG without alpha. There are no iPhone screenshots yet.

**How the Android ones are made:** run `python docs/store/make_demo_backup.py --today <the emulator's date> --utc-offset <the emulator's time zone>`, restore the `demo-backup.json` it writes under Settings > Backup on an emulator with a 1080 × 1920 screen (the "pixel" device profile), and turn on the emulator's demo mode for a tidy status bar. The data is made for one day: the streak ends the day before and the session is ready, so make it again on the day the screenshots are taken.

**File list:**

| File | Screen |
|---|---|
| `1-today.png` | Today: the German coast with its goal and level, today's session ready, and the pace |
| `2-my-coasts.png` | My Coasts: the streak and weekly goal, leaky words, and the coasts with their levels |
| `3-coast.png` | A coast: its level and route, the next island to add, and its islands |
| `4-route.png` | The route: the islands of level A1, each with how far it is |
| `5-flip.png` | Flip mode: a flipped card with its grade buttons |
| `6-type.png` | Type mode: a typed translation that was right |
| `7-cards.png` | The cards of an island about the user |
| `8-recap.png` | This month's recap |

**Format rules:**

| | Google Play (phone) | App Store (iPhone) |
|---|---|---|
| Format | JPEG or 24-bit PNG without alpha, as the current files are. | `.jpeg`, `.jpg` or `.png`, no alpha channel and no transparency |
| How many | 2 to 8 per device type. At least 4 at 1080 px or more to be eligible for promotion. | 1 to 10 per display size |
| Size | Each side 320 to 3840 px, the long side at most twice the short side. 1080 × 1920 (9:16) fits. | Required: iPhone with Dynamic Island, medium display, 1179 × 2556 or 1206 × 2622 px (portrait). Also provide the large display: 1260 × 2736, 1290 × 2796 or 1320 × 2868 px. Without a large set, Apple lists the older 6.5-inch sizes (1284 × 2778 or 1242 × 2688 px) as required. |
| Other | Must show the app itself. | Must show the iPhone app in use. Android screenshots cannot be reused. The app is iPhone only, so no iPad set is needed. |

The iPhone sizes are from Apple's [screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/screenshot-specifications/), read on November 15, 2026. Check which slots App Store Connect marks as required on the day. The same page lists sizes for a folding iPhone that become required in April 2027 for apps built with the iOS 27.1 SDK or later.

The owner works on Windows, so iPhone screenshots can only come from the simulator in CI (it already produces 1206 × 2622 px) or from a real iPhone. The iPhone app cannot restore the demo backup, so the demo data needs a way in first.

The other Play images: the feature graphic must be 1024 × 500 px, JPEG or 24-bit PNG without alpha (the current file qualifies). The icon must be 512 × 512 px, at most 1024 KB. Play asks for a 32-bit PNG with alpha and the current icon has no alpha channel. Re-export it only if the console refuses it.

## Google Play forms

All of these are on the App content page of the Play Console.

### Data safety

These answers must agree with the [privacy policy](../privacy-policy.md). Change both together.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes. Every connection is HTTPS. |
| Account creation (part of the form's data deletion questions) | The app has no accounts. Choose the answer that says users cannot create one. |
| Do you provide a way for users to request that their data is deleted? | Yes. Google counts in-app features as such a way. The data is on the phone and is deleted in the app or with the app. If the form asks for a link, use the policy's deletion section: `https://github.com/Andreas-Erick/LanguageCoast/blob/main/docs/privacy-policy.md#deleting-your-data` |

Data types. "Collected" in this form means sent off the phone, to anyone. Nothing here reaches the developer.

| Data type in the form | What it is in this app | Collected | Shared | Processed ephemerally | Required or optional | Purpose (collected and shared) |
|---|---|---|---|---|---|---|
| Personal info > Name | The name under About you, and names the user writes in answers | Yes | Yes, with the cloud AI provider the user picked | No | Optional | App functionality |
| Personal info > Other info | Age, birthday, relationship status, where the user lives and comes from, job, the wording choice (as a woman, as a man) | Yes | Yes, same | No | Optional | App functionality |
| Health and fitness > Health info | What the user types for the My Health island: allergies, conditions, medication | Yes | Yes, same | No | Optional | App functionality |
| Health and fitness > Fitness info | What the user types about sports and exercise | Yes | Yes, same | No | Optional | App functionality |
| App activity > Other user-generated content | Sentences and cards, island names, a coast's goal and region, questions about a card, the other About you answers | Yes | Yes, same | No | Optional | App functionality |
| App info and performance > Diagnostics | ML Kit: performance metrics, error codes, device and app information | Yes | No | No | Required | Analytics |
| App info and performance > Other app performance data | ML Kit: the languages set up for translation, feature events | Yes | No | No | Required | Analytics |
| Device or other IDs > Device or other IDs | ML Kit: a per-installation identifier | Yes | No | No | Required | Analytics |

Notes on these answers:

- **Optional** is right for the first five rows: they are sent only when the user picks a cloud provider, enters a key and allows it. With the built-in translator or Local AI none of it leaves the phone.
- **Shared: yes** is the cautious answer. The form exempts transfers that follow "a prominent in-app disclosure and consent", which the permission dialog is. Declaring them anyway keeps the form in line with the policy.
- **Not ephemeral:** a provider may keep requests under its own terms.
- **ML Kit rows** come from Google's [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) and cover the built-in translator and the photo text reader. Google says it encrypts this data in transit and does not pass it to third parties. Read that page again before submitting, since Google updates it.
- **Not declared:** the photo (read on the phone, never sent), location (only if the user switches on exact sun times for Follow the sun: the approximate location, rounded to about 10 km, used on the phone for sunrise and sunset and never sent, so it is not "collected" in the form's sense; a city the user types is declared under Other info), contacts, advertising data, crash logs. The API key and the Hugging Face token are the user's own credentials, sent only to the service they belong to. A cloud provider gets its key only after the user has allowed that provider. "Check for new models" and the check when a key is saved send the key and nothing else. Google backup is a feature of the phone under the user's Google account. The policy describes it.

### Content rating

The questionnaire (IARC) asks for an e-mail address: use `<support e-mail>`. Pick the category for reference or educational apps, not a game and not a social app.

| Topic | Answer |
|---|---|
| Violence, fear, sexual content, gambling, crude language | No |
| Alcohol, tobacco or drugs | The starter sentences teach everyday words such as beer and wine, and a "No smoking" sign. If the form asks about references, say yes: references only, no use shown, nothing promoted. |
| Users can talk to each other or exchange content | No |
| Shares the user's location | No |
| Digital purchases | No |

Expected rating: Everyone / PEGI 3. The exact questions were not checked against the live form.

### Target audience and content

| Question | Answer |
|---|---|
| Target age | **18 and over** only (recommended) |
| Restrict minor access | Leave it off. It is optional. |
| Could the listing unintentionally appeal to children? | No |

Why 18 and over: the app points people to a Gemini API key, and the [Gemini API terms](https://ai.google.dev/gemini-api/terms) say "You must be 18 years of age or older to use the APIs", and that the API may not be used in an app "directed towards or is likely to be accessed by individuals under the age of 18". Declaring 13 to 17 as a target group would say the app is meant for people who may not use the provider it points to. Choosing 18 and over costs little: the content rating stays Everyone and the Families policy does not apply.

This is the owner's call. Play's own rules also allow 13 and over, and no Play rule requires 18.

### Ads and advertising ID

| Question | Answer |
|---|---|
| Does your app contain ads? | No |
| Does your app use an advertising ID? | No. The app has no `AD_ID` permission. |

### App access

Play's help now lists this under "Sign-in details" on the App content page. Declare that parts of the app are restricted, because the AI features need a key, and paste this as the instructions:

```text
There is no account and no sign-in. Every screen opens without credentials.

Without any key the app translates on the phone with its built-in translator. The first use of a language downloads about 30 MB. Adding cards, starter topics, the daily session on Today, the study modes Flip, Type and Fill gap, search, the streak, the monthly recap, the widget, and backup, import and export all work this way.

These features need an AI model: islands about you, the B1 islands, practice sheets, word explanations, Check translation, Ask about this card, and talks. They work with a cloud AI provider and the user's own API key. A key for review:

<review API key>

To enter it: Settings (third tab) > Translation > pick Gemini > paste the key > Save API key. A dialog shows what is sent to Google and asks for permission. Tap "Allow and save". To take the permission back, remove the key in the same place.

The same features also work without a key: Settings > Translation > Local AI downloads a language model (0.6 GB or more) and runs it on the phone.
```

### Health apps

Answer: **My app doesn't provide any health features.**

The app reads no health data from the phone: no Health Connect, no sensors, no health permission. The My Health island is language practice. The user may type an allergy or a medication so the sentences fit, and that text is declared under Data safety. If Google reads the form's "access health data to support non-health features" as covering typed text, this answer has to be revisited.

### AI-generated content

Play's policy asks apps that generate content with AI to let users report offensive content in the app. It is aimed at chatbots and image generators, and this app is closer to the kind it leaves out: AI improves a feature, and nothing generated is shown to anyone but the user. The app still has the route:

- Free-form AI answers (asking about a card, a word explanation) have a "Report this answer" link.
- Settings > About has "Send feedback or report a problem".

Until `<support e-mail>` is set in the app, both open the public issue tracker in the browser. With an address they open a mail draft. The policy says "without needing to exit the app", so supply the address before production. Whether Play accepts a mail draft as in-app reporting is not confirmed.

### Other declarations

| Declaration | Answer |
|---|---|
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| News app | No |
| Permissions that need a declaration | None. The app requests internet access and notifications. |

## App Store forms

### App Privacy

Recommended answers:

| Question | Answer |
|---|---|
| Do you or your third-party partners collect data from this app? | Yes |
| Data type | User Content > Other User Content |
| Purpose | App Functionality |
| Linked to the user's identity | No |
| Used for tracking | No |

Why not "Data Not Collected": Apple counts data as collected when "you and/or your third-party partners" can access it, and defines partners as vendors "whose code you've added to your app". The app contains no provider code and sends text under the user's own key, so "Data Not Collected" can be argued. But the app now shows a dialog saying that text goes to an AI provider, guideline 5.1.2(i) names third-party AI, and a reviewer who sets that dialog beside a "not collected" label may stop the review to ask. Declaring the data costs nothing and matches what the app says about itself.

Why "not linked": nobody on the developer's side receives the text, and the developer cannot tie it to a person. The provider sees it under the user's own account with that provider. If App Review reads that as linked, change this one answer. The label can be changed without a new build.

Name and Health are not declared separately: they are free text the user chooses to write, which is what Other User Content means. Add Contact Info > Name and Health & Fitness > Health with the same answers only if App Review asks.

The privacy manifest (`iosApp/iosApp/PrivacyInfo.xcprivacy`) declares the same. If you answer differently in App Store Connect, have the manifest changed to match before the upload.

### Age rating

| Question | Answer |
|---|---|
| Parental controls | No |
| Age assurance | No |
| Unrestricted web access | No. dict.cc and the policies open in Safari, not in the app. |
| User-generated content | No. Nothing a user writes is shown to other users. |
| Social media | No |
| Messaging and chat | No. People cannot talk to each other. Asking about a card goes to the user's own AI provider. |
| Advertising | No |
| Shared location, Dating and relationships (new on November 16, 2026) | No |
| Profanity or crude humor, Horror or fear themes | None |
| Alcohol, tobacco, or drug use or references | Infrequent, if the starter sentences still teach beer, wine and "No smoking" (they did in 3.0.1). None if those are gone. |
| Medical or treatment information, Health or wellness topics | None. Sentences about the user's own allergies are language practice, not guidance. |
| Sexuality or nudity, Violence, Guns or other weapons | None |
| Gambling, Simulated gambling, Contests, Loot boxes | None / No |
| Made for Kids | No |

Result: 13+ with "Infrequent" for alcohol, 4+ without it. If it comes out as 4+, use "Override to Higher Age Rating" and choose 13+: the app gives free-form AI answers, and the AI providers set their own minimum ages. Choosing 18+ would mirror the Play target audience exactly, but that rating limits who can get the app. Play's counterpart to this rating is its content rating (Everyone), not its target audience. The owner decides.

### Export compliance

Already answered in the build: `ITSAppUsesNonExemptEncryption` is `false` in the Info.plist that `iosApp/project.yml` generates. The app only uses HTTPS. If App Store Connect asks anyway: the app uses only standard encryption that is exempt.

### App Review information

| Field | Value |
|---|---|
| Sign-in required | No |
| Contact name, phone, e-mail | The owner's own. Phone in international format, starting with + and the country code. E-mail: `<support e-mail>`. |
| Notes | The text below (2142 of 4000 bytes) |

```text
Language Coast is a free flashcard app for learning languages. It has no account, no sign-in, no purchases, no subscription and no ads. The app has no server of its own: cards are stored on the iPhone.

Translation and the AI features use an AI provider that the user picks, with the user's own API key. Here is a key for review:

<review API key>

First launch
Pick the language you speak and the language to learn, for example English and Spanish. With no key yet, Today says that translation needs setting up.

How to enter the key
1. Settings (third tab) > Translation.
2. Pick Gemini.
3. Paste the key and tap "Save API key".
4. A dialog shows what is sent to Google and asks for permission. Tap "Allow and save". Nothing is sent to a provider before this.

What to try with the key
- Today > Add your own: type an English sentence and save it. It is translated and filed on an island.
- Today: start the daily session. It reviews due cards and offers new sentences to keep or skip.
- My Coasts > open the coast: the route through the levels A1, A2 and B1. Add a starter topic as an island.
- Settings > About you: answer one question, then have that island written from the coast's route.
- While studying a card: tap a word for an explanation. The menu has Check translation and Ask about this card. AI answers have a "Report this answer" link.

Without a key
- Today > Add your own > Manual: type both sides of a card yourself.
- Studying (Flip, Type, Fill gap), search, the streak and the monthly recap need no key.

Other notes
- Reminders are local notifications scheduled on the iPhone. There is no push service. The app asks for notification permission after the first finished study session.
- To withdraw the permission for a provider, remove the key under Settings > Translation.
- The interface is in English.
- These features exist only in the Android version and are not in this build: file backup, import and export, the home-screen widget, on-device translation, Local AI, practice sheet PDFs and reading text from a photo.
- Privacy policy: https://github.com/Andreas-Erick/LanguageCoast/blob/main/docs/privacy-policy.md
```

### EU trader status and availability

| Item | What to do |
|---|---|
| Trader status (Digital Services Act) | App Store Connect asks whether you are a trader. Without an answer the app is not offered in the EU. This is the owner's legal call. A trader's address, phone number and e-mail are shown on the product page in the EU. For a non-trader, EU customers see a note that consumer protection law does not apply. |
| Price | Free |
| Availability | All countries and regions. Apple asks for an ICP filing number for some apps in China mainland, so leave China mainland out unless you plan to file. |

### Support URL, copyright, content rights

| Field | Value |
|---|---|
| Support URL | See [Contact fields](#listing-text) |
| Privacy Policy URL | `https://github.com/Andreas-Erick/LanguageCoast/blob/main/docs/privacy-policy.md` |
| Copyright | `2026 Andreas Erick` (Apple adds the © sign) |
| Content rights | Yes, the app shows third-party content and has the rights to it. The content is text written by the AI provider the user picked, under that provider's terms and the user's own key. The Nunito font and the open-source libraries are used under their licences, listed in Settings > About. dict.cc opens in Safari. |
| Primary language | English |

## What only you can do

Nothing below can be done from the repository. Each list is in order.

### Google Play

1. **Choose a support e-mail address.** Google shows it publicly on the listing. It is also needed for the content rating form, the App Store review contact and the app itself. Then hand it to the developers: it goes into `Support.EMAIL` in the app, and into the two commented lines in `docs/privacy-policy.md`.
2. **Open a developer account** in the Play Console: a personal account, with a one-time fee of 25 US dollars. Google verifies your legal name and address. It shows your legal name, your country and your developer e-mail address on Google Play. It also asks whether you are a trader under EU law.
3. **Check the privacy policy link.** The policy lives in this repository, and the same address is built into the app. It works only while the repository is public and the file stays at `docs/privacy-policy.md` on `main`. If it ever moves, change the app, this file and both consoles in the same release.
4. **Make the review API key.** A Gemini key used for nothing else. A key on a paid plan avoids the free tier's rate limits; set a spending limit if the provider offers one. Paste it into the App access text above, in the console only. Revoke it once the app is approved, and make a new one for the next review that needs it.
5. **Create the app** in the console (app, free) and fill in the listing and every form from this file. A free app cannot be made paid later.
6. **Choose the app signing key** before the first release to open testing or production. After that the choice is fixed.

   | | A. Google's key (the default) | B. The key that signs the GitHub APKs |
   |---|---|---|
   | People who installed from GitHub Releases | The Play build has a different signature, and Android will not install it over the GitHub build. They back up under Settings > Backup, uninstall, install from Play and restore. Uninstalling without a backup deletes their cards. | The Play build has the same signature. It installs over the GitHub build and keeps the cards. |
   | Safety | Google holds the signing key. A lost or leaked upload key can be reset. | You stay responsible for your copy. Google's help says a signing key you manage yourself "cannot be reset" if you lose it, and that the upload key and the signing key "should be different". Today one keystore, the one in the repository secrets, would be both. |
   | Work | None | In the console: Protected with Play > Play Store protection > Manage Play app signing > Change key, and provide a copy of the key. To follow Google's advice, also make a second key for uploads and keep signing the GitHub APK with the first. That needs a change to the release workflow. |

   Recommendation: take A unless people other than you rely on the GitHub build and must move to Play without reinstalling. With A, GitHub Releases can later offer the universal APK that the Play Console provides for download, signed with Google's key.
7. **Upload to internal testing first.** Use the `LanguageCoast-vX.Y.Z.aab` from the GitHub Release, never a bundle built on your PC: a local bundle has version code 1, and Play accepts each version code once. Every upload needs a new tag. Install from Play on a real phone and walk through the app.
8. **Run the closed test.** Google requires it for personal accounts created after November 13, 2023: at least 12 testers, opted in for 14 days without a break, at the moment you apply. A tester who opts out and back in starts again. Start early, since it sets the launch date. Agree where testers send feedback, because the form asks for it.
9. **Apply for production access** from the console's Dashboard. The form has three parts:

   | Part | What it asks |
   |---|---|
   | About your closed test | How easy it was to find testers. How they used the app, and whether they used all of it. What feedback you got and how you collected it. |
   | About your app | Who it is for. What value it gives them. How many installs you expect in the first year. |
   | About your production readiness | What you changed because of the test. How you decided the app is ready. |

   Google says the review usually takes seven days or less. It can ask for more testing if the testers hardly used the app.
10. **Release to production.** Afterwards, crashes and freezes are under Android vitals in the console.
11. **Later:** register the app for Android developer verification in the console. Google plans to extend that programme to all countries from 2027, and it also covers apps installed from outside Play, such as the GitHub APK.

### App Store

1. **Enrol in the Apple Developer Program** as an individual, under your legal name. There is a yearly fee, and approval can take a few days.
2. **Set up the upload,** all in the browser. The steps are in the repository README under Releases:
   - Register the bundle ID `com.andreaserick.languagecoast` in the Apple Developer portal.
   - Create the app with that bundle ID in App Store Connect.
   - Generate an App Store Connect API key with Admin access and download the `.p8` file. It can be downloaded only once.
   - Add the four repository secrets: `APP_STORE_CONNECT_KEY_BASE64`, `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID` and `APPLE_TEAM_ID`.
3. **Get a first build into TestFlight** by pushing a version tag. Starting the workflow by hand works too: it uploads the version you type, or the latest tag's. The upload has never run, so expect to repeat this step.
4. **Run it on a real iPhone.** The iPhone app has only ever run in a simulator. With the review key, walk through every screen and check: typing with the keyboard up (Today, Type, Fill gap, About you), sheets and dialogs, swiping back from the edge, the light theme, read aloud with the silent switch on, a reminder arriving, and one full study session. Also check: the launch screen and the status bar text with the iPhone in light and in dark appearance, in each of the three themes; that no notification question appears at first launch (it comes after the first finished session, and after "Don't Allow" Settings shows a notice whose button opens the iPhone's Settings); and that the app stays in portrait.
5. **Take the iPhone screenshots** in the sizes listed under [Screenshots](#screenshots).
6. **Keep the privacy manifest and the label the same.** `iosApp/iosApp/PrivacyInfo.xcprivacy` already declares Other User Content, not linked to you, not used for tracking, for app functionality. If you answer differently in App Store Connect, have the manifest changed to match before the upload.
7. **Fill in App Store Connect** from this file: listing text, App Privacy, age rating, trader status, price and availability, content rights, and the App Review information with the review key in the notes.
8. **Submit for review.** Answer questions in App Store Connect; most need no new build. Revoke the review key after approval.
