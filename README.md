# Termux File Manager · TFM

**[Download signed Android APK releases](https://github.com/its-zain-dev/TermuxFileManger/releases) · [Release installation guide](docs/RELEASES.md) · [Changelog](CHANGELOG.md)**

The official Android Release builds use a **permanent signing key** stored in private GitHub Actions Secrets. No Debug APKs are published.

A native Android workspace for Termux: files, an editor, saved terminal sessions, developer utilities and a multi-provider AI assistant.

**0.5.0 update:** Kotlin, Jetpack Compose and Material 3; English and Arabic; phone and tablet layouts; light, dark and AMOLED themes. Android 6.0+; no root required.

## Workspace

- **Files:** list/grid, breadcrumbs, editable paths, recursive search, hidden files, sorting, favorites and recent folders. Multi-select copy/move/delete with per-item results, skip/rename conflicts and stopping remaining items. Persistent operation history survives leaving the screen.
- **Editor:** full-screen UTF-8 editing up to 256 KiB, line numbers, find, undo/redo and local drafts. Writes preserve permissions and line endings, use atomic replacement, and reject an externally modified file rather than overwriting it.
- **Terminal:** named working directories, multiline commands, stdout/stderr and exit status, command review and broadcasting to saved sessions. Interactive TUI programs open in Termux.
- **Assistant:** separate conversations linked to terminal sessions; streamed text and provider-exposed reasoning summaries; image attachments and explicit screen capture; reviewed tool actions; optional automatic execution; web research, imported `SKILL.md` skills and durable Termux workspace memory.
- **Tools:** Termux packages; pip, npm, cargo, gem, Go, Composer and LuaRocks; runtime/storage diagnostics; HTTPS downloads; tar/zip utilities.

Providers: Gemini, Anthropic, OpenAI, OpenRouter, Groq, Mistral, Cohere, DeepSeek, xAI and Together AI. Configure your own API key, model ID and HTTPS endpoint. Provider availability, costs and limits depend on your account; model IDs remain editable.

**ChatGPT plan:** Settings → **Continue with ChatGPT** opens OpenAI's system-browser sign-in and consent flow. This uses the official [open-source Sign in with ChatGPT flow](https://developers.openai.com/siwc/token-sharing-open-source/sign-in), with dynamic public-client registration, PKCE and no developer secret or API key. Models come from the signed-in account's catalog; streamed Responses requests consume that account's authorized plan allowance. Account/workspace policy, app limits and the preview's supported models still apply. View usage from the Settings link to ChatGPT. Browser login and consent must be completed by the account owner.

Each registration keeps its issued client ID and validated identity separate. Tokens are encrypted with Android Keystore, refreshes are serialized and rotating tokens are persisted together. Sign-out stops active requests, attempts session revocation and removes local tokens while retaining the registration for subsequent sign-in.

## Assistant tools (0.5)

ChatGPT uses native Responses function calls grouped in the `termux` namespace. Calls and results are paired by `call_id`, persisted locally and replayed as context without re-executing them. Other providers retain the text-action protocol, including compatibility with JSON surrounded by explanatory prose.

The shared tool registry covers app/model inspection, file listing/reading/search, guarded file updates/creation, project inspection, Git status, reviewed shell commands, web research, memory and skills. `app_info` reports the configured model ID actually sent with the request; it does not read credentials or claim to attest the provider's internal model weights. Read-only tools run directly; writes and shell commands require review by default. Existing explicit auto-execution settings are retained. Greetings do not authorize shell execution.

The tools hub supports search, categories and request drafts. Requests are shown as status cards in the conversation with review controls and expandable results. Tool results feed the next assistant turn automatically; the old shell-only auto-analysis toggle is retired. Stopping the agent or restarting the app does not replay interrupted calls.

## Connect Termux

1. Install Termux from [termux.dev](https://termux.dev/) and open it once.
2. Open TFM → **Connect to Termux** and grant **Run commands in Termux environment**.
3. Copy the displayed setup command into Termux. It enables `allow-external-apps=true` and installs Python and curl.
4. Restart Termux, then select **Check connection**.
5. For shared Android storage, run `termux-setup-storage` in Termux and grant its storage permission. TFM accesses that storage through Termux, within Android's sandbox.
6. Configure an AI provider in **Settings** if you want the assistant. File operations do not require an AI key.

The TFM APK supports Android 6/API 23 and newer; current supported Termux releases require Android 7 or newer for the file/command backend.

English/Arabic follows the device by default and can be changed in Settings. Paths, source code and terminal output retain left-to-right direction inside the Arabic interface.

## Upgrade and data

Keep the same application ID **and signing key** when updating an installation. On first launch, the app transactionally imports v0.3 conversations, messages and sessions into Room. IDs, conversation/session associations, custom metadata and auto-execution overrides are preserved. Existing preference files remain intact; malformed imports stop with a recoverable error instead of silently resetting data.

Preferences migrate to DataStore. Provider codecs and the Termux workspace format use a compatibility preference mirror. API keys remain in the original encrypted store and use the existing Android Keystore alias `tfm.ai.keys.aesgcm.v1`; keys are not exported or re-encrypted during migration.

Termux files under `~/.TFM-setting/` retain their names and semantics, including `config.json`, `Memory.md`, `instructions.md` and the historical `system-propmet.md` spelling.

Commands are recorded before dispatch and results are committed before the UI observes them. Rotation and navigation do not own a running command. On process recovery, partial AI messages are marked interrupted and commands are **not replayed**. A pending command may still be running in Termux; its eventual callback updates the stored result. Stopping the assistant cancels its HTTP call and prevents subsequent actions; it does not claim to kill an already dispatched shell process.

## Build and validate

Requirements: **JDK 17**, Android SDK platform **35**, and Python 3 for bridge tests. The checked-in Gradle **8.9** wrapper has a distribution checksum. Dependencies use fixed versions. `minSdk=23`; `targetSdk=32` is retained for the current Termux integration, so this release is not a Google Play target-SDK migration.

```sh
python3 -m unittest discover -s tests -v
python3 scripts/release.py --build
```

Release APK: `app/build/outputs/apk/release/`. Unit tests cover legacy migration/rollback, callback persistence/idempotency, drafts, provider streaming/authentication/cancellation, and agent actions. Python tests exercise real filesystem operations, including 10,000 entries, Unicode, symlinks, conflicts, encoding limits and external edits.

With an emulator or test device:

```sh
./gradlew connectedDebugAndroidTest
```

Instrumented tests exercise navigation, locale/theme recreation and, when Termux is configured, actual file/command/Keystore round trips in a disposable directory. Debug builds are for local testing; distributed artifacts are Release variants.

## APK signing and CI

`scripts/release.py --build` creates a signing identity once in the ignored, owner-only `.signing/` directory and reuses it for later builds. It runs JVM tests, Android Lint and the signed Release build. An existing identity with a missing keystore is an error, never a reason to silently rotate the key. Preserve that directory privately for future updates; it is never committed or uploaded as a public artifact.

For this installation the RSA-3072 signing key was generated off-device and securely imported from an encrypted bundle; the GitHub CLI uploads the existing key as Actions Secrets, so Termux does not need OpenJDK. The older optional local key-generation script also supports `--publish-secrets` with authorized GitHub CLI permissions. It checks authorization first and refuses to overwrite an existing signing identity. A normal workflow `GITHUB_TOKEN` cannot create repository secrets. If GitHub denies access, local signed builds still work, but secure repository persistence is not completed.

The secure signing import stores these four secrets without printing their values:

- `TFM_RELEASE_KEYSTORE_BASE64`
- `TFM_RELEASE_STORE_PASSWORD`
- `TFM_RELEASE_KEY_ALIAS`
- `TFM_RELEASE_KEY_PASSWORD`

GitHub Actions runs filesystem/JVM tests, Lint and only the Release build. Pull requests validate without access to signing secrets or distributing a Debug APK. Successful pushes to `main` and manual runs publish a signed Release APK with a SHA-256 checksum. All four permanent `TFM_RELEASE_*` secrets must exist; missing secrets stop publication. The private signing key is imported from the encrypted maintainer bundle and uploaded securely through GitHub CLI, without requiring Java on your Android device. Never commit the key or passwords.

Direct Gradle builds also accept `TFM_RELEASE_KEYSTORE_PATH` plus the three password/alias variables. Without signing configuration, direct Gradle output is unsigned. A newly generated key cannot update an app signed by an older, different key. Keys and passwords must never be committed.

## Architecture

The single `app` module is organized by behavior:

- `core`: typed file/command/AI interfaces and models.
- `data`: Room, transactional legacy import, DataStore, persistent Termux dispatch and OkHttp provider transport.
- `features`: file/editor/workspace state and application-scoped agent orchestration.
- `ui`: Compose screens, localization and shared design components.
- `assets/file_bridge.py`: independently testable JSON protocol v1, executed **inside Termux** through `RUN_COMMAND` with JSON on stdin.

The small Java compatibility components retain the existing provider payloads, Keystore format, skill/workspace semantics and MediaProjection flow; obsolete programmatic UI Activities and tracked `.bak` copies have been removed.

## Boundaries

TFM cannot bypass Android storage permissions and does not bundle a PTY or a Termux environment. The editor intentionally rejects binary/non-UTF-8 files and files above 256 KiB. Recursive search reports when its 5,000-match cap is reached. Batch cancellation stops later items; the current item may finish. Shell commands and AI tools run with Termux's user permissions. Provider requests send the conversation and explicitly attached content to the configured endpoint. No analytics or server account was added.

See [the Arabic guide](دليل-الاستخدام-عربي.md) and [validation notes](docs/VALIDATION.md).
