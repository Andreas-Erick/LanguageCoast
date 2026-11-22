# ADR-0002: AI runs on the phone or on the user's own key, with no backend

- **Status:** Accepted
- **Date:** 2026-10-21, revised 2026-11-22
- **Commits:** `dacbdf9`, `b97f1a1` (keys out of backups),
  `a22c7bc` (one transport), `072c17e` (one entry per
  provider)

## Context

Translation, new sentences, card checks and practice sheets
all ask an AI model. A backend would hold a shared API key,
pay for every request, and see every sentence a learner
writes. The app is built and run by one person.

## Options considered

1. **A backend proxy with a bundled key.** Simplest for
   users. It costs money per request, needs accounts and
   abuse limits, and puts learners' text on a server.
2. **On the phone only.** Private and free, but small
   models write weaker sentences, and iOS has no Local AI.
3. **On the phone by default, cloud on the user's own
   key.** Six providers (Gemini, OpenAI, Claude, Groq,
   DeepSeek, OpenRouter), each allowed separately before
   any text is sent.

## Decision

Option 3.

- `RoutingTranslator` picks the engine and checks on every
  request that the key and the permission are still there,
  so removing a key stops work that is already running.
- Every cloud request goes through `CloudTransport`: the
  answer is read as a stream, a provider that goes silent
  is given up on, and 408, 429, 5xx and 529 are retried.
- Keys live in `SecretStore`: the Android KeyStore
  (AES-256-GCM) or the iOS Keychain
  (`WhenUnlockedThisDeviceOnly`). Never in backups, and
  scrubbed from logs by `redactSecrets()`.
- Each provider is one `CloudProvider` entry in
  [`data/CloudProviders.kt`](../../shared/src/commonMain/kotlin/com/andreaserick/languagecoast/data/CloudProviders.kt):
  company, key and model preferences, models, wire,
  capacity, model-list endpoint, and the pages Settings
  links to. `CloudProvidersTest` checks every entry.

## Consequences

- No server to run, pay for or secure, and no account.
- Users have to get a key, so errors must be plain: a bad
  key, no credit left and rate limits each get their own
  message.
- Providers retire model ids on their own schedule, so
  built-in lists go stale. "Check for new models" in
  Settings asks each provider for its current list.
- Adding a provider takes four edits: the enum entry, its
  `CloudProvider` entry, its two preferences in
  `SettingKeys`, and its model in `BackupSettings`, whose
  JSON format stays fixed. Before the entries, it took
  fourteen files.
- Moving to a new phone means a backup file, and keys have
  to be entered again, by design.
