# Store listings

Texts, images and form answers for Google Play and the App Store, ready to paste into the Play Console and App Store Connect.

- `icon-512.png`: the Play Store icon (512 × 512 px).
- `feature-graphic.png`: the Play Store feature graphic (1024 × 500 px).
- The iOS app icon is in `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset` (1024 × 1024 px, made from the 512 px icon).
- Privacy policy: [docs/privacy-policy.md](../privacy-policy.md), published at
  `https://github.com/Andreas-Erick/LanguageCoast/blob/main/docs/privacy-policy.md` and linked under Settings → About.

## Listing text

**App name:** Language Coast

**Short description** (Play, max. 80 characters):

> Turn your own sentences into flashcards and remember them with spaced repetition.

**Subtitle** (App Store, max. 30 characters):

> Flashcards from your sentences

**Full description** (Play max. 4000 characters, App Store max. 4000):

> Language Coast turns the sentences you actually want to say into flashcards, and brings each one back just before you'd forget it.
>
> Type a sentence in your own language. Language Coast translates it and files it on an island by topic, like Travel or Restaurant. Every language you learn gets its own coast.
>
> STUDY YOUR WAY
> • Flip Cards: see the sentence, recall the translation, grade yourself.
> • Active Type: type the translation and check it. Accents count, capitals and punctuation don't.
> • Study in either direction.
> • Spaced repetition (FSRS) decides when each card comes back.
>
> ON THE BACK OF EVERY CARD
> • Hear the translation read aloud.
> • Tap any word to look it up on dict.cc.
> • See other ways to say it.
>
> STAY ON TRACK
> • Daily streak and an optional study reminder.
> • Search all your cards, edit them, and move them between islands.
> • Undo instead of "Are you sure?"
>
> PRIVATE BY DESIGN
> No account, no ads, no tracking. Your cards stay on your phone. Translate with Google Gemini or OpenRouter using your own API key.

On Android, add these lines under STAY ON TRACK:

> • Home-screen widget with your streak and the cards due today.
> • Back up everything to a file, export to Anki or Markdown, import from CSV.

And this line under PRIVATE BY DESIGN:

> Or translate on your phone, for free and offline.

**Category:** Education

**Keywords** (App Store, max. 100 characters):

> flashcards,vocabulary,spaced repetition,language learning,sentences,translate,anki,fsrs,study

## Screenshots

Both stores need phone screenshots: Play at least 2, App Store at least 1 for the 6.9" display (1320 × 2868 px). Good screens to show: My Coasts, Create, an island's cards, Flip Cards with the back showing, and Active Type.

## Google Play: Data safety

- **Does your app collect or share any of the required user data types?** Yes.
- **Is all of the user data collected by your app encrypted in transit?** Yes (HTTPS only).
- **Do you provide a way for users to request that their data is deleted?** Yes. Everything is stored on the device and is deleted with the app or in the app.

Data types:

| Data type | Collected | Shared | Optional | Purpose |
|---|---|---|---|---|
| App activity → Other user-generated content (sentences to translate, island names) | Yes | Yes, with the translation provider the user picks (Google Gemini or OpenRouter) | Yes. Only when the user picks Gemini or OpenRouter and adds an API key | App functionality |

Processed ephemerally: **No.** The provider may keep requests under its own terms.

Nothing else is collected: no location, contacts, identifiers, analytics, crash logs or advertising data. The API key is the user's own credential for the provider and isn't a data type in the form.

## Google Play: other forms

- **Content rating** (IARC questionnaire): category *Reference, News, or Educational*. No violence, sexual content, profanity, drugs, gambling or user-to-user communication. It shares no location. Expected rating: Everyone / PEGI 3.
- **Target audience:** 13 and older. The app is fine for children, but choosing under 13 brings the Families policy requirements.
- **Ads:** No.
- **App access:** All features work without an account. For Gemini and OpenRouter the reviewer needs their own API key; on-device translation works without one.
- **Government app / financial features / health:** No.

## App Store: App Privacy

Answer **"Data Not Collected"**. Apple counts data as collected only when the developer or its partners can access it. The app has no server, and translation requests go straight from the phone to the provider under the user's own key. If you'd rather be cautious, declare *User Content → Other User Content*, *Not linked to the user*, *Not used for tracking*, purpose *App Functionality*.

- **Age rating:** 4+.
- **Export compliance:** already answered in `Info.plist` (`ITSAppUsesNonExemptEncryption = false`; the app only uses HTTPS).
- **Review notes:** "Translation with Gemini or OpenRouter needs the user's own API key. Without a key, cards can be created by typing the translation yourself (Create → I'll type it)."
