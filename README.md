<div align="center">

# Termux File Manager (TFM)

### A native Android file manager and AI coding companion for Termux

Browse files, manage projects, run approved shell commands, install developer tools, and connect AI providers — directly from your Android phone or tablet.

<p>
  <a href="https://github.com/its-zain-dev/TermuxFileManger/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/its-zain-dev/TermuxFileManger?display_name=tag&label=release&color=35C9A5"></a>
  <a href="https://github.com/its-zain-dev/TermuxFileManger/actions/workflows/android.yml"><img alt="Android release build" src="https://github.com/its-zain-dev/TermuxFileManger/actions/workflows/android.yml/badge.svg"></a>
  <a href="https://github.com/its-zain-dev/TermuxFileManger/releases"><img alt="GitHub downloads" src="https://img.shields.io/github/downloads/its-zain-dev/TermuxFileManger/total?label=downloads&color=5271FF"></a>
  <a href="https://github.com/its-zain-dev/TermuxFileManger"><img alt="Stars" src="https://img.shields.io/github/stars/its-zain-dev/TermuxFileManger?style=flat&color=F5B942"></a>
  <img alt="Android" src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Java" src="https://img.shields.io/badge/Native_Java-ED8B00?logo=openjdk&logoColor=white">
</p>

<p>
  <a href="https://github.com/its-zain-dev/TermuxFileManger/releases/latest"><strong>Download latest build</strong></a>
  · <a href="https://github.com/its-zain-dev/TermuxFileManger/issues">Report a bug</a>
  · <a href="https://github.com/its-zain-dev/TermuxFileManger/actions">Build history</a>
</p>

</div>

---

## What is Termux File Manager?

**Termux File Manager (TFM)** is a native Java Android app that provides a graphical workspace for [Termux](https://termux.dev/). It helps Android users browse and manage files, edit code, run shell commands, work with programming runtimes, and use AI coding assistants without needing a desktop computer.

Instead of doing every file operation in a terminal, use a visual file manager alongside Termux's real command-line environment. The optional AI assistant supports several API providers and can help reason about projects and terminal output.

> **No root required.** TFM does not bypass Android's sandbox. Commands run with Termux's own user permissions, subject to Android and Termux configuration.

## Features

<table>
<tr>
<td width="50%" valign="top">

### 📁 Graphical file manager

- Browse folders and hidden files.
- Search for files and directories.
- Create, edit, copy, move, rename, and delete items.
- Inspect metadata and change file permissions.
- Manage archives and download files over HTTPS.
- Edit UTF-8 text files up to 256 KiB in the built-in editor.

</td>
<td width="50%" valign="top">

### 💻 Termux command workspace

- Run one-line and multi-line shell commands in a selected working directory.
- View command output and exit status.
- Save named work sessions with recent output.
- Open interactive TUI programs in Termux.
- Use Android's **RUN CODE ON TERMUX** selected-text action.
- Review proposed commands before running them by default.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🤖 Multi-provider AI assistant

Configure a separate API provider, model, HTTPS endpoint, and key as needed:

- Google Gemini
- Anthropic Claude
- OpenAI
- OpenRouter
- Groq
- Mistral AI
- Cohere
- DeepSeek
- xAI
- Together AI

</td>
<td width="50%" valign="top">

### 🛠️ Developer tools

- Helpers for Termux packages and language package managers.
- Python `pip`, Node.js `npm`, Rust `cargo`, Ruby `gem`, and Go tools.
- Runtime and compiler inventory.
- System and storage diagnostics.
- Language-based runner suggestions for common scripts and source files.
- Midnight/dark, AMOLED, and light themes.

</td>
</tr>
</table>

## User interface

The whole app is built in code (no XML layouts) on top of one shared design system, so every screen speaks the same visual language.

**File manager**

- A large header shows the current folder, the full path, and a live connection pill (`● متصل بـ Termux`).
- Breadcrumb chips walk the folder tree (`~ / projects / app`), with quick-access chips for home, `PREFIX`, storage and downloads.
- Inline search filters as you type (debounced, so it does not spam the Termux bridge).
- Sort by name, size, date or type, and switch between a comfortable list and a two-column grid.
- Every entry is a card with an icon tile, its name, size, permission bits and last-modified time.
- A floating `+` button creates a folder or file, and long-press (or the row menu) opens a themed action sheet.

**AI assistant and agent console**

- The header carries the chat identity, provider, model, linked Termux session and an auto-execution switch, plus a live status pill.
- Each agent run is rendered as one step timeline: thinking summary, tool call with the exact command, tool output, and the final answer — every step expandable.
- Agent tool approval shows the risk level, the working directory and the command in a code block before anything runs.
- Starting panels offer ready-made prompts, and code blocks can be copied or executed in Termux with one tap.

**Everywhere**

- Bottom sheets replace plain list dialogs, cards replace default text dialogs, and all tappable surfaces share the same ripple feedback and typography.

## AI coding assistant

TFM's AI assistant is designed for practical work with Termux projects, not just general chat.

- Select a provider per conversation and use a custom model ID or HTTPS endpoint.
- Store API keys encrypted with Android Keystore-backed AES-GCM encryption.
- Keep separate named chat histories and link chats to saved terminal sessions.
- Fetch model lists where supported or enter model IDs manually.
- Configure local request limits and delays to reduce accidental repeated calls.
- Optionally send command output to the assistant for follow-up diagnosis.
- Commands proposed by AI require user approval by default. Enabling unattended execution changes this safety boundary.
- Use web research support for tasks that require external verification.

Model availability, multimodal inputs, streaming support, token/context limits, rate limits, and costs are controlled by each provider and can change. TFM's local rate controls do not override provider-side limits and do not guarantee free usage.

### Official AI API documentation

| Provider | Documentation |
|---|---|
| Google Gemini | [Gemini API](https://ai.google.dev/gemini-api/docs) |
| Anthropic Claude | [Messages API](https://docs.anthropic.com/en/api/messages) |
| OpenAI | [API documentation](https://developers.openai.com/api/docs) |
| OpenRouter | [Chat Completions API](https://openrouter.ai/docs/api-reference/chat-completion) |
| Groq | [API reference](https://console.groq.com/docs/api-reference) |
| Mistral AI | [Chat API](https://docs.mistral.ai/api/endpoint/chat) |
| Cohere | [Chat API](https://docs.cohere.com/reference/chat) |
| DeepSeek | [Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion) |
| xAI | [API reference](https://docs.x.ai/developers/rest-api-reference/inference/chat-completions) |
| Together AI | [OpenAI-compatible API](https://docs.together.ai/docs/openai-api-compatibility) |

## Install TFM on Android

1. Open the [latest GitHub Release](https://github.com/its-zain-dev/TermuxFileManger/releases/latest) and download its Release APK.
2. Install it through Android's package installer. Android may ask you to allow installs from the app used to download the file.
3. Install Termux, then open TFM and select the Termux link/setup option.
4. Follow the displayed steps to enable external command requests, restart Termux, and grant Android's **Run commands in Termux environment** permission if shown.
5. To use AI, open **AI & App Settings**, select a provider, set a supported model ID, and save your own API key.

**APK signing:** If a release asset is named `-unsigned.apk`, it is a Release build that has not been signed and cannot be installed as a normal Android app until signed. For installable, upgrade-compatible builds, configure the persistent signing secrets in [Automatic Release Builds](#automatic-release-builds). Never download or install APKs from sources you do not trust.

## Requirements

| Requirement | Details |
|---|---|
| Android | Android 6.0+ (API 23 or newer) |
| App technology | Native Android Java |
| Terminal | Termux installed; Termux 0.109+ recommended |
| Command access | Termux external command requests enabled and Android permission granted where required |
| Network | Internet for AI APIs and HTTPS downloads |
| AI usage | Your own API key for each hosted provider you choose to use |

TFM does not bundle API credentials or AI usage credits.

## Automatic Release Builds

GitHub Actions builds the **Release variant only** on every push to `main` and on manual workflow runs. Pull requests targeting `main` also build the Release variant as a validation check, but do not publish a Release. Successful push/manual builds publish a uniquely tagged GitHub Release containing the APK and a SHA-256 checksum. The workflow does **not** build or upload Debug APKs.

### Configure persistent APK signing (recommended)

A production Android APK should use a persistent signing key. Without signing secrets, Gradle generates `app-release-unsigned.apk`. GitHub Actions will still publish the Release build and mark the asset as unsigned.

Configure these repository Actions secrets to have the workflow sign release APKs:

| Repository secret | Description |
|---|---|
| `TFM_RELEASE_KEYSTORE_BASE64` | Base64-encoded Java keystore file |
| `TFM_RELEASE_STORE_PASSWORD` | Keystore password |
| `TFM_RELEASE_KEY_ALIAS` | Signing key alias |
| `TFM_RELEASE_KEY_PASSWORD` | Signing key password |

Create and safeguard your own release keystore. Open **Settings → Secrets and variables → Actions → New repository secret** to add each value. Never commit a keystore or passwords. Back up the keystore securely: losing it can prevent future versions from updating an already installed app.

### Build pipeline

1. Checks out the repository and configures Java 17 and Gradle.
2. Runs `clean assembleRelease` — not `assembleDebug`.
3. Collects the Release APK and produces a SHA-256 checksum.
4. Publishes both files to a uniquely tagged GitHub Release on pushes to `main` and manual runs.
5. Leaves the workflow logs in the [Actions tab](https://github.com/its-zain-dev/TermuxFileManger/actions).

## Build locally

With Java 17, Android SDK tooling, and Gradle available, run from the repository root:

```bash
gradle --no-daemon clean assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. If your checkout includes a Gradle wrapper, prefer `./gradlew --no-daemon clean assembleRelease`.

## Security and privacy

- TFM does not request root access or bypass Android's app sandbox.
- Shell commands run as the Termux user. Commands can still delete or overwrite files; review commands before approval.
- API keys are stored encrypted with a key backed by Android Keystore.
- Chat content and intentionally attached inputs are sent to the configured AI provider when a request is made. Provider logging, retention, privacy, and billing depend on that provider's terms.
- Never paste API keys, passwords, access tokens, or credentials into chats or bug reports.
- HTTPS encrypts traffic but does not prove a custom endpoint belongs to the provider you intended. Only use endpoints you trust.
- Keep backups of important projects and the signing keystore.

## Known limitations

- TFM is a graphical companion to Termux, not a root file manager.
- Access outside Termux depends on Android storage permissions and Termux storage setup.
- Interactive TUI tools such as `nano`, `vim`, `top`, and `htop` open in Termux. TFM cannot embed Termux's actual interactive PTY view inside a normal Android Activity.
- The built-in editor is intended for UTF-8 text files up to 256 KiB; use a terminal editor for larger files.
- Provider-specific API features and token limits vary by model.

## Troubleshooting

<details>
<summary><strong>TFM cannot run commands in Termux</strong></summary>

Check that Termux is installed, external command requests are enabled in `~/.termux/termux.properties`, Termux has been fully restarted, and Android granted the required command permission.

</details>

<details>
<summary><strong>An AI request returns HTTP 429</strong></summary>

The provider's rate or token limit was reached. Wait for the provider's retry period, reduce conversation size, select another supported model/provider, or review the limits in your provider account.

</details>

<details>
<summary><strong>The APK will not install</strong></summary>

Check whether the release asset is marked `unsigned`, verify the Android minimum version, and confirm you are not installing an APK signed with a different key over an existing installation.

</details>

<details>
<summary><strong>A file or folder cannot be opened</strong></summary>

Check the path, file permissions, Android storage access, and whether the target is readable by Termux.

</details>

## Contributing and bug reports

Open an [issue](https://github.com/its-zain-dev/TermuxFileManger/issues) with the TFM version, Android version, reproduction steps, and relevant sanitized logs. Remove keys, passwords, tokens, personal files, and other private data before posting logs.

For code contributions, open a pull request against `main`. GitHub Actions validates the Release build for pull requests and publishes Releases on pushes to `main`.

---

<div align="center">

**Termux File Manager (TFM)** · Android GUI file manager · Termux development workspace · AI coding assistant

<sub>Built for developers who want to manage files, work with Termux, and use AI tools directly on Android.</sub>

</div>
