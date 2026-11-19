# Privacy policy

*Language Coast · Last updated November 19, 2026*

Language Coast is a flashcard app for learning languages, made by Andreas Erick. This policy explains what happens to your data when you use it on Android or iPhone.

**In short:** there is no account and no advertising, and the app has no server of its own, so the developer receives nothing from it. Your cards are stored on your phone. If you choose a cloud AI provider (Google Gemini, OpenAI, Anthropic, Groq, DeepSeek or OpenRouter), the app sends that provider the text a feature needs, straight from your phone and under your own API key. It asks your permission first, once for each provider. Not every request starts with a tap: while the app is open, it has new sentences prepared for Today. On Android, the built-in translator and the photo text reader (Google ML Kit) send Google diagnostic data but not your text, and Local AI downloads its model from Hugging Face.

## What the app stores on your phone

- Your coasts, islands and cards, with their review history, your streak and the days you studied.
- Each coast's goal and region, and your settings, such as your native language, your pace, your translation provider and your reminder time.
- What you choose to tell the app under "About you": your name, where you live and come from, your job, and your answers to its optional questions about yourself (such as your age and birthday), your family, home, work, free time, health, past and plans. Also how you want sentences about you worded: as a woman, as a man or neutral.
- The API keys you enter for cloud providers, which providers you have allowed, and a Hugging Face token if you add one for gated local models.
- On Android, any Local AI model you download.

All of this is kept in the app's private storage on your phone. The developer never receives any of it. It leaves your phone only in the cases below.

## When data leaves your phone

**Cloud AI providers: permission first.** A cloud provider is used only with your own API key, which you enter in Settings. Before the app sends a provider anything, it shows what is sent and to whom, with a link to that provider's privacy policy, and asks you to allow it. It asks once for each provider. If you don't allow it, nothing is sent. To withdraw your permission, remove the key under Settings > Translation: that removes the key and the permission together.

The providers are:

- Google Gemini API ([Google's privacy policy](https://policies.google.com/privacy), [Gemini API terms](https://ai.google.dev/gemini-api/terms))
- OpenAI ([privacy policy](https://openai.com/policies/privacy-policy/))
- Anthropic, for Claude ([privacy policy](https://www.anthropic.com/legal/privacy))
- Groq ([privacy policy](https://groq.com/privacy-policy))
- DeepSeek ([privacy policy](https://cdn.deepseek.com/policies/en-US/deepseek-privacy-policy.html))
- OpenRouter ([privacy policy](https://openrouter.ai/privacy)), which passes the request on to the company behind the model you chose

**What a provider receives.** Every request carries your API key, and most name the two languages of the coast. The rest depends on the feature:

- **Adding a card:** the sentence, the coast's region, the island you chose if you chose one, and the names of the coast's islands, so a fitting one can be picked.
- **Sentence suggestions on Today:** prepared automatically while the app is open, whenever Today runs low on new sentences, and never while the app is closed. The app sends the language, the region, the coast's goal, the island names and your wording choice. Later on it also sends sentences you already have, so they are not repeated. Each request counts towards your quota or credit with the provider.
- **Starter topics:** the app's built-in sentences, with the coast's region and your wording choice. Blanks in them are filled in from About you (your name, where you live and come from, your job) before they are sent for translation.
- **Islands about you:** your answers for that island, which can include family and health details. With them go the About you basics the island uses, your wording choice, the coast's goal and region, and the sentences already on that island. Answers for other islands are not sent.
- **Islands of level B1 that are not about you:** the coast's goal and region, your wording choice, the sentences already on that island and, for Work & Meetings, your job.
- **Checking a translation or asking about a card:** the card with its alternatives and note, the coast's region, what you type, and the earlier questions and answers in that sheet.
- **Tapping a word in a sentence:** the word and its sentence, for an explanation.
- **Practice sheets (Android) and talks:** the cards of that coast or island, with the coast's region and your wording choice. A practice sheet also carries the level you have reached on that coast's route.
- **Check for new models:** only your API key. The same request is made when you save a key, to check that it works. Both are made only for a provider you have allowed.

Everything under About you is optional, so leave out anything you would rather not share with your provider. Cards written from your answers are ordinary cards afterwards. They are sent with later requests like any other card.

The key is your own, so every request is made under your account with that provider. The provider handles what it receives under its own terms and privacy policy, linked above. They say how long it keeps the text and whether it may use it. The app sends your data to no one else, and the developer never sees these requests.

**Free Gemini keys.** At the time of writing, the [Gemini API terms](https://ai.google.dev/gemini-api/terms) say that with an unpaid key Google may use what you send, and the answers, to improve its products, and that human reviewers may read it once it is separated from your account and key. They ask you not to send sensitive or personal information with an unpaid key. For paid use they say Google does not use what you send to improve its products, and that the paid rules also cover unpaid keys if you are in the European Economic Area, Switzerland or the United Kingdom. If you use a free key, leave out of About you anything you would not want read.

**dict.cc.** "Look up on dict.cc" in a word's explanation opens dict.cc in your browser with that word. What happens there is covered by the privacy policy on [dict.cc's About page](https://www.dict.cc/?s=about%3A).

**Local AI (Android only).** Translation and sentence writing run on your phone with a language model you download once from [Hugging Face](https://huggingface.co/privacy), where the model files are hosted. Nothing you write is sent anywhere. Most models need no account. A gated one (such as Gemma 3) is downloaded with the Hugging Face token you enter, which goes only to Hugging Face. To recommend models that fit, the app reads your phone's memory, storage and processor details. They stay on your phone.

**On-device translation (Android only).** Translation runs on your phone with Google ML Kit and is powered by Google Translate. The first time you use a language, the app downloads its translation model from Google. Your sentences are not sent. ML Kit does send Google diagnostic and usage data so Google can measure and improve it: device information (such as manufacturer, model and Android version), app information, performance metrics, error codes, the languages you translate between, and a per-installation identifier. Google describes this in its [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure), and it is covered by [Google's privacy policy](https://policies.google.com/privacy). The iPhone app doesn't include ML Kit.

**Reading text from a photo (Android only).** The photo you pick is read on your phone with Google ML Kit text recognition. It is not uploaded, and the app does not keep it. You pick it in your phone's photo picker, so the app cannot see your other photos. ML Kit sends Google the same kind of diagnostic data as described above.

**Read aloud.** The speaker button uses the text-to-speech voices on your phone.

**Phone backups.** If device backups are turned on (Google backup on Android, iCloud Backup on iPhone), your phone can include the app's cards and a copy of its settings: your native language, streak, reminder, About you answers and route progress. They are kept in your Google or Apple account. API keys and the Hugging Face token are never included, and neither are downloaded Local AI models. You can turn device backups off in your phone's settings.

**Backup and export files (Android only).** When you back up or export, the app writes a file to the place you choose. A backup file contains your cards, your settings and your About you answers, in readable form. Card exports (Anki, CSV, Markdown) contain only cards. API keys are never in any of these files. Practice sheets are PDF files saved to your Downloads folder, or on Android 9 and older to the place you choose.

**Feedback and reports.** "Send feedback or report a problem" and "Report this answer" open the app's public issue tracker in your browser. Nothing is sent until you post there yourself. What you post is public, and [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement) applies.

<!-- Owner: once a support address is set in the app (Support.EMAIL), replace the paragraph above with:
**Feedback and reports.** "Send feedback or report a problem" and "Report this answer" open a draft in your mail app. For a report, the draft holds the card and the AI answer, so you see what you send before you send it. The developer uses what you send only to answer you and to fix the problem. On an Android phone without a mail app, the public issue tracker opens in your browser instead, and what you post there is public.
-->

## Notifications

Study reminders are scheduled on your phone. No push service is involved, and nothing is sent to anyone. The app asks for permission to show notifications after your first finished study session, or when you switch reminders on. You can turn reminders off in Settings. On Android a reminder can quote one of your cards as a quick test. It never quotes a sentence from an island about you.

## Security

- Everything the app sends travels over an encrypted connection (HTTPS), and cleartext HTTP traffic is blocked.
- Your cards, settings and answers are kept in the app's private storage, which other apps cannot read. API keys and Hugging Face tokens are stored encrypted using hardware-backed OS key storage (Android KeyStore AES-256-GCM on Android, iOS Keychain on iPhone). The app adds no lock of its own, so your phone's screen lock is what keeps your cards from someone holding your phone.
- An API key is sent only to the provider it belongs to, and only after you have allowed that provider. Keys are left out of device backups and of the app's backup files.
- The app has no server, and the developer holds no copy of your data.
- Files you save yourself (backups, exports, practice sheets) are ordinary readable files. Keep them somewhere you trust.

## Children

The app is not directed at children, and the developer does not collect personal information from anyone, including children. Cloud providers set their own minimum age for an API key. The Gemini API terms, for example, require you to be 18 or older.

## Deleting your data

- **In the app:** delete cards, islands and coasts, and clear your answers under About you. Removing an API key under Settings > Translation also withdraws that provider's permission.
- **Everything at once:** uninstalling the app deletes everything it stored on your phone, including downloaded models.
- **Files you saved yourself** (backups, exports, practice sheets) stay where you put them until you delete them.
- **Device backups** stay in your Google or Apple account. Manage or delete them there.
- **What a provider received** is held by that provider under your account with it. Use its account settings, or contact it, to have that deleted. The developer has no access to it.

The developer holds none of your data, so there is nothing to keep and nothing to request from the developer.

## Changes

If this policy changes, the new version will be published here with a new date.

## Contact

Questions about this policy or about your data? Open an issue at [github.com/Andreas-Erick/LanguageCoast/issues](https://github.com/Andreas-Erick/LanguageCoast/issues). The tracker is public, so leave out anything private.

<!-- Owner: when a support address exists, add this to the sentence above: or by e-mail at <support e-mail> -->
