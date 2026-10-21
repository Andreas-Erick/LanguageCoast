# Privacy Policy

*Language Coast · Last updated October 21, 2026*

Language Coast is a flashcard app for learning languages, made by Andreas Erick. This policy explains what happens to your data when you use it on Android or iPhone.

**In short:** there is no account, no advertising, no analytics and no tracking. Your cards stay on your phone. Text leaves your phone only when you ask the app to translate it with Gemini or OpenRouter, and then it goes straight to the provider you picked.

## What the app stores on your phone

- Your coasts, islands and cards, with their review history, your streak and the days you studied.
- Your settings, such as your native language, your translation provider and reminder times.
- The API keys you enter for Gemini or OpenRouter.

All of this stays in the app's private storage on your phone. The developer never receives any of it, and there is no server that receives it.

## When data leaves your phone

**Translating with Gemini or OpenRouter.** When you translate a sentence with one of these providers, the app sends your API key, the sentence, your native language, the language you're learning, and the names of the islands on that coast (so a fitting one can be picked) directly to that provider:

- Google Gemini API ([Google's privacy policy](https://policies.google.com/privacy), [Gemini API terms](https://ai.google.dev/gemini-api/terms))
- OpenRouter, which passes the request on to the model you chose ([OpenRouter's privacy policy](https://openrouter.ai/privacy))

The key is your own, so the request is made under your account with that provider, and its terms decide how long it keeps the text and whether it may use it. The developer never sees these requests. If you don't add an API key, nothing is sent.

**On-device translation (Android only).** Translation runs on your phone with Google ML Kit. The first time you use a language, the app downloads its translation model from Google. Your sentences are not sent.

**Dictionary lookups.** Tapping a word opens it on dict.cc in your browser. What happens there is covered by [dict.cc's privacy policy](https://www.dict.cc/?s=privacy).

**Read aloud.** The speaker button uses your phone's own text-to-speech voices.

**Phone backups.** If you have device backups turned on (Google backup on Android, iCloud Backup on iPhone), your phone may include the app's cards in them. Your API keys are always left out of these backups. You can turn device backups off in your phone's settings.

**Backup and export files (Android only).** When you back up or export cards, the app writes the file to the place you choose. API keys are never included in these files.

## Notifications

If you turn on study reminders, the app schedules them on your phone. No push service is involved.

## Children

The app does not collect personal information from anyone, including children.

## Deleting your data

Uninstalling the app deletes everything it stored on your phone. You can also delete coasts, islands and cards in the app, and remove your API keys in Settings. To delete data that a translation provider holds, use that provider's account settings.

## Changes

If this policy changes, the new version will be published here with a new date.

## Contact

Questions about this policy? Open an issue at [github.com/Andreas-Erick/LanguageCoast/issues](https://github.com/Andreas-Erick/LanguageCoast/issues).
