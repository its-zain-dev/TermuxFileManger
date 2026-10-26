# Termux File Manager (TFM) — v0.3.0

A native Java Android companion app for browsing Termux files, running explicitly approved commands, managing packages/runtimes, and optionally connecting multiple AI providers directly from the device.

## What's new in 0.3.0

### AI Assistant Agent
- One subtle **AI ★** entry point in the main screen.
- Ten API providers: Google Gemini, Anthropic Claude, OpenAI, OpenRouter, Groq, Mistral AI, Cohere, DeepSeek, xAI, and Together AI.
- Choose the provider per chat, edit a model ID, change the HTTPS endpoint, fetch model catalogs where supported, or enter a model ID manually.
- API keys are encrypted with AES-GCM using an Android Keystore key. Emptying the key field does **not** erase an existing key; use the separate delete-key button to remove it.
- Multiple named chat histories; each chat can be connected to a saved Terminal work session so the assistant can inspect recent command output.
- Send one prompt to all chats (one request per configured chat/provider), and display local per-provider request counts.
- Local rate controls limit daily requests and the minimum delay between calls. These controls are a safety cushion, not a replacement for the provider's account-side quota, billing, or rate limits. HTTP 429 errors are surfaced. An optional setting can automatically send outputs from an AI-approved command back to the same chat for diagnosis; follow-up commands still require confirmation.
- The assistant may propose shell commands. The user must review and confirm before commands are executed.
- A large response can include an action to review and run a fenced code block inside Termux.

### Screen capture / terminal context
- Screenshot analysis uses Android's MediaProjection consent dialog and an ongoing system notification. Capturing is never started secretly.
- While screen sharing is active, the user can request a screenshot from the TFM notification when Termux/TUI is on screen. If the AI asks for a screenshot, the app first shows a confirmation dialog; after approval it opens Termux and captures one frame after a short delay. First-time use also requires Android MediaProjection consent. The frame is sent only for that AI follow-up request, not continuously.
- The user can stop screen sharing from the notification or the AI page. Screenshots may contain secrets or private data; review what is sent to an external AI provider.
- TUI programs such as `nano`, `vim`, `top`, and `htop` open in the Termux app. Android does not allow this project to embed Termux's actual interactive PTY view directly into an ordinary Activity. The app provides command/output panes and terminal popups for non-interactive commands.

### Termux tools and file workspace
- Browse directories, show hidden files, recursively search and page listings.
- Create, edit, copy, move, rename, delete, inspect metadata, change permissions, and manage archives.
- UTF-8 built-in editor up to 256 KiB; use `nano`/`vi` for larger files.
- Run one-line or multi-line Shell commands with a selected working directory.
- Package/library helpers for `pkg`, Python `pip`, Node.js `npm`, Rust `cargo`, Ruby `gem`, Go `go install`, plus runtime/compiler inventory and system/storage diagnostics.
- Download a file over HTTPS to a selected path.
- Language-based runner suggestions for common scripts and compiled language extensions, plus a custom-runner fallback. The required compiler/interpreter must be installed inside Termux.
- Named working sessions keep their own working directory and recent output history. The broadcast command action repeats the same approved command in each session's working directory.
- `RUN CODE ON TERMUX` appears under Android's selected-text actions (sometimes under **More/⋮**). Android does not let a normal app inject a button into another app's keyboard row; this uses the system text-processing action.
- Short fade/slide transitions for folder/search navigation and dark, AMOLED, and light themes.

## Supported API providers

The app includes endpoint defaults and a model field for each provider. Provider model catalogs, availability, multimodal support, limits, and charges can change; use **Load model list from API** when supported or consult the provider's official documentation before choosing a model.

| Provider | Official API documentation |
|---|---|
| Google Gemini | <https://ai.google.dev/gemini-api/docs> |
| Anthropic Claude | <https://docs.anthropic.com/en/api/messages> |
| OpenAI | <https://developers.openai.com/api/docs> |
| OpenRouter | <https://openrouter.ai/docs/api-reference/chat-completion> |
| Groq | <https://console.groq.com/docs/api-reference> |
| Mistral AI | <https://docs.mistral.ai/api/endpoint/chat> |
| Cohere | <https://docs.cohere.com/reference/chat> |
| DeepSeek | <https://api-docs.deepseek.com/api/create-chat-completion> |
| xAI | <https://docs.x.ai/developers/rest-api-reference/inference/chat-completions> |
| Together AI | <https://docs.together.ai/docs/openai-api-compatibility> |

## Requirements

- Android 6.0+ (API 23+).
- Termux installed and supporting `com.termux.RUN_COMMAND` (Termux 0.109+ recommended).
- Termux `allow-external-apps=true` and Android's **Run commands in Termux environment** permission enabled for this app.
- Internet access to use provider APIs and HTTPS downloads. The app sends requests directly to the selected provider; the provider may bill the account separately.
- API key for whichever provider you intend to use. No AI API key is bundled into the project.

## One-time Termux setup

1. Open **الربط** or **Settings → Termux link** and copy the setup command.
2. Paste the command inside Termux, then fully close and restart Termux.
3. In Android Settings, grant this app **Run commands in Termux environment** if shown.
4. Keep only providers you trust configured with API keys, and test a provider from **Settings** before chatting.

## Build with GitHub Actions

Push the project to your GitHub repository. The **Android APK** workflow builds a debug APK using Java 17, Gradle 8.9, and Android Gradle Plugin 8.7.3. Download `TermuxFileManager-debug-apk` from the Artifacts section of a successful run.

## Security and limits

- All shell commands run under the Termux app's own user permissions. This app does not provide root access or bypass Android's sandbox.
- Commands from chat, selected text, and tools require a user confirmation before execution. An approved command can still delete files, alter packages, or run downloaded code; inspect it first.
- The API key is encrypted locally using Android Keystore-backed AES-GCM. It survives normal app restarts and updates but is not guaranteed to survive uninstalling the app, clearing app data, or Android device reset. Keep a secure copy of any key if needed.
- Keys and chat contents are sent directly to the configured provider when a request is made. Provider policies, logging, billing, and privacy terms still apply. Never include an API key in the chat text.
- The last screenshot is stored in app-private storage until replaced or removed with **حذف آخر لقطة**. Screenshots are sent to the selected AI provider only when deliberately attached to a request (including a screenshot request you explicitly confirm). Check the screen for passwords, tokens, notifications, and personal information.
- Local rate limits cannot override provider quotas or guarantee a request is free.
- Android does not let an ordinary app secretly capture another app or embed an external app's interactive terminal without the appropriate system flow. Screen capture therefore requires explicit MediaProjection consent and can be stopped at any time.
