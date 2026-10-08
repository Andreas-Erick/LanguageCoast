<p align="center">
  <img src="docs/logo.png" alt="Language Coast logo" width="140" />
</p>

<h1 align="center">🌊 Language Coast</h1>

<p align="center">
  An AI-powered flashcard app for Android, built with Jetpack Compose and Google Gemini.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin + Jetpack Compose" />
  <img src="https://img.shields.io/badge/min%20SDK-26-blue" alt="Min SDK 26" />
</p>

---

Language Coast is a flashcard app that changes how you build vocabulary. It combines **Google Gemini** with a coastal-themed UI. Type what you want to say, and Gemini translates it and files it into one of your "Language Islands", ready to study.

## ✨ Features

### 🤖 AI-powered card creation
- **Smart translations**: uses Google Gemini (`gemini-2.5-flash` by default, selectable in Settings) for natural, context-aware translations.
- **Auto-categorization**: Gemini sorts each new card into an island such as *Travel*, *Restaurant* or *Greetings*, reusing your existing islands whenever one fits.
- **Icelandic noun rule**: single Icelandic nouns are returned with their definite and plural forms (e.g. *hestur, hesturinn, hestar*).
- **Manual mode**: you can skip the AI and enter your own translation and category.

### 🏝️ My Coast
- **Language Islands**: your vocabulary is grouped into islands by category.
- **Daily streaks**: a streak counter tracks how many days in a row you finish a study session.
- **Offline-first storage**: cards are stored locally with **Room**, and preferences with **Jetpack DataStore**.

### 🧠 Study modes
- **Flip Cards**: classic flashcards with an animated flip and *Again* / *Easy* buttons. *Again* moves the card to the end of the session.
- **Active Type**: test your recall by typing the translation.
- **Built-in dictionary**: tap any word on the back of a card to look it up on **dict.cc** for your language pair.

### 🔔 Study reminders
- A daily reminder arrives at a random time between 9:00 and 21:00, scheduled with **WorkManager**.

**Supported languages:** English, Spanish, French, German, Italian, Japanese, Korean, Icelandic, Norwegian.

## 🛠️ Tech stack

| Area | Library |
| --- | --- |
| UI | [Jetpack Compose](https://developer.android.com/compose) + Material 3 (100% Kotlin) |
| AI | [Google Gen AI Java SDK](https://github.com/googleapis/java-genai) (Gemini) |
| Database | [Room](https://developer.android.com/training/data-storage/room) |
| Preferences | [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |
| Navigation | [Navigation Compose](https://developer.android.com/jetpack/compose/navigation) with type-safe routes |
| Background work | [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) |

## 📁 Project structure

```
app/src/main/java/com/andreaserick/languagecoast/
├── MainActivity.kt          # Entry point, bottom navigation and NavHost
├── data/                    # Room entities/DAO, DataStore settings, Gemini client
├── navigation/              # Type-safe route definitions
├── notifications/           # Daily study reminder (WorkManager + notification)
├── ui/
│   ├── screens/             # Create, My Coast, Study and Settings screens
│   └── theme/               # Colors, typography and Material theme
└── util/                    # dict.cc lookup helpers
```

## 🚀 Getting started

### Prerequisites
- A recent version of **Android Studio** that supports Android Gradle Plugin 9.2.
- **JDK 21** (Gradle can download it automatically through the toolchain resolver).
- A **Google Gemini API key**, which you can get from [Google AI Studio](https://aistudio.google.com/). It is only needed for AI mode.

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/<your-username>/LanguageCoast.git
   ```
2. Open the project in Android Studio and let Gradle sync.
3. Run the app on an emulator or a physical device (Android 8.0 / API 26 or higher).
4. Open **Settings**, choose your native and target languages, and paste your **Gemini API key**.
5. Create your first island!

> **Your API key stays on your device.** The app keeps it in local app storage (DataStore). It is never compiled into the app, so you don't need to add it to `local.properties`.

### Running tests
```bash
./gradlew testDebugUnitTest
```

## 🎨 Design

Language Coast uses a deep-sea color palette:
- **Deep Ocean Blue**: the app's main background.
- **Sand Beige**: warm, readable text and primary accents.
- **Coral & Wave Teal**: bright highlights for interactive elements and feedback.

---

<p align="center"><i>"Build your coast, one word at a time."</i> 🌊⚓</p>
