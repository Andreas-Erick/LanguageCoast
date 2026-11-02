# Privacy Policy

*Language Coast · Last updated November 2, 2026*

Language Coast is a flashcard app for learning languages, made by Andreas Erick. This policy explains what happens to your data when you use it on Android or iPhone.

**In short:** there is no account, no advertising and no tracking, and the developer collects nothing. Your cards stay on your phone. Text leaves your phone only when you ask the app to translate it with a cloud provider (Google Gemini, OpenAI, Anthropic, Groq, DeepSeek or OpenRouter), and then it goes straight to the provider you picked. On Android, the on-device translator built into the app (Google ML Kit) sends Google diagnostic data, and Local AI downloads its model from Hugging Face; both are described below.

## What the app stores on your phone

- Your coasts, islands and cards, with their review history, your streak and the days you studied.
- Your settings, such as your native language, your translation provider and reminder times.
- The API keys you enter for cloud providers.
- On Android, any Local AI model you download.

All of this stays in the app's private storage on your phone. The developer never receives any of it, and there is no server that receives it.

## When data leaves your phone

**Translating with a cloud provider.** When you translate a sentence with one of these providers, the app sends your API key, the sentence, your native language, the language you're learning, and the names of the islands on that coast (so a fitting one can be picked) directly to that provider:

- Google Gemini API ([Google's privacy policy](https://policies.google.com/privacy), [Gemini API terms](https://ai.google.dev/gemini-api/terms))
- OpenAI ([OpenAI's privacy policy](https://openai.com/policies/privacy-policy))
- Anthropic, for Claude ([Anthropic's privacy policy](https://www.anthropic.com/privacy))
- Groq ([Groq's privacy policy](https://groq.com/privacy-policy))
- DeepSeek ([DeepSeek's privacy policy](https://www.deepseek.com/))
- OpenRouter, which passes the request on to the model you chose ([OpenRouter's privacy policy](https://openrouter.ai/privacy))

The key is your own, so the request is made under your account with that provider, and its terms decide how long it keeps the text and whether it may use it. The developer never sees these requests. If you don't add an API key, nothing is sent.

**Local AI (Android only).** Translation runs on your phone with a language model you download once, from [Hugging Face](https://huggingface.co/privacy), where the model files are hosted. That download is the only request: your sentences are never sent anywhere, and no account or key is needed. The app reads your phone's memory, storage and processor details to recommend models that fit; those details stay on your phone.

**On-device translation (Android only).** Translation runs on your phone with Google ML Kit. The first time you use a language, the app downloads its translation model from Google. Your sentences are not sent. ML Kit does send Google diagnostic and usage data so Google can measure and improve it: device information (such as manufacturer, model and Android version), app information, performance metrics, error codes, the languages you translate between, and a per-installation identifier. Google describes this in its [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure), and it is covered by [Google's privacy policy](https://policies.google.com/privacy). The iPhone app doesn't include ML Kit.

**Dictionary lookups.** Tapping a word opens it on dict.cc in your browser. What happens there is covered by [dict.cc's privacy policy](https://www.dict.cc/?s=privacy).

**Read aloud.** The speaker button uses your phone's own text-to-speech voices.

**Phone backups.** If you have device backups turned on (Google backup on Android, iCloud Backup on iPhone), your phone may include the app's cards in them. Your API keys are always left out of these backups. You can turn device backups off in your phone's settings.

**Backup and export files (Android only).** When you back up or export cards, the app writes the file to the place you choose. API keys are never included in these files.

## Notifications

If you turn on study reminders, the app schedules them on your phone. No push service is involved.

## Children

The developer does not collect personal information from anyone, including children.

## Deleting your data

Uninstalling the app deletes everything it stored on your phone. You can also delete coasts, islands and cards in the app, and remove your API keys in Settings. To delete data that a translation provider holds, use that provider's account settings.

## Changes

If this policy changes, the new version will be published here with a new date.

## Contact

Questions about this policy? Open an issue at [github.com/Andreas-Erick/LanguageCoast/issues](https://github.com/Andreas-Erick/LanguageCoast/issues).
