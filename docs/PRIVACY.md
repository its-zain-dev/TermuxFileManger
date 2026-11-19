# Privacy Information — Termux File Manager

*Applies to the open-source Android application. Last reviewed for source version 1.0.0. This document describes behavior visible in the published source; external providers set their own policies.*

## Local-first operation

TFM accesses files and executes commands through Termux only after the user grants the Android Termux command permission and completes setup. File lists, grep searches, archive inspection/extraction, checksum calculations, disk usage reports, edited files, terminal outputs, snippets, local chat history, drafts and settings are stored and processed locally on the device or within the Termux environment.

## Network requests and optional AI services

All AI features are strictly optional. The file manager, code editor, archive tools, terminal workspace and local diagnostics work completely offline.

- **Local loopback AI (Ollama / llama.cpp / OpenCode):** When configured against `127.0.0.1` or `localhost`, requests stay on the device's loopback interface. `network_security_config.xml` permits cleartext HTTP exclusively for `127.0.0.1` and `localhost`; all remote hosts require HTTPS.
- **Optional commercial cloud AI providers:** When a user explicitly sends a prompt to a configured HTTPS provider (Gemini, Anthropic, OpenAI, OpenRouter, Groq, Mistral, Cohere, DeepSeek, xAI, Together, or ChatGPT OAuth), TFM transmits the prompt, necessary conversation history, and explicitly attached images or tool results to that endpoint.
- **Optional web search, skill imports, and OpenCode apt setup:** These contact external HTTPS services only when explicitly initiated and reviewed by the user.

## Credentials, encryption and execution safety

- Provider API keys, ChatGPT OAuth tokens, and local OpenCode service passwords are encrypted at rest using a non-exportable Android Keystore AES-GCM key (`AiSecureStore`).
- Internal shell commands that retrieve pairing tokens or service passwords are immediately purged from the local command history database.
- Screen capture uses explicit Android `MediaProjection` consent and only attaches images when the user explicitly selects them.
- Archive extraction strictly validates member paths to block path traversal (Zip Slip) and malicious symlink entries.

## Zero analytics, tracking, or proprietary SDKs

TFM contains zero Google Play Services, Firebase, Crashlytics, advertising, telemetry, or third-party analytics SDKs. The Gradle release build disables Google's `DependencyInfoBlock` and Git `vcsInfo` metadata for full auditability and F-Droid reproducible builds.

## Control and contact

Users can clear chat history, clear terminal history, remove saved API keys, sign out of ChatGPT accounts, disable background task notifications, or uninstall the app to wipe all app-private data.

- Source code: https://github.com/its-zain-dev/TermuxFileManger
- Issues & privacy questions: https://github.com/its-zain-dev/TermuxFileManger/issues
