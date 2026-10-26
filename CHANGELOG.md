# Changelog

## 0.3.0

- Added a single, understated AI Assistant entry point and persistent multi-chat UI.
- Added API configuration for Google Gemini, Anthropic, OpenAI, OpenRouter, Groq, Mistral AI, Cohere, DeepSeek, xAI, and Together AI.
- Added editable provider model IDs/endpoints, provider docs, model-list discovery where supported, HTTPS enforcement, connection test, and per-provider encrypted API keys backed by Android Keystore AES-GCM.
- Added local rate limiting with configurable per-provider daily request caps and minimum spacing; improved HTTP 401/403/429 messages and Retry-After reporting.
- Linked AI chats to named Termux working sessions and passed recent session output into prompts; added send-to-all-chats and send-to-all-terminal-workdirs flows with confirmation.
- Added review-before-run actions for shell code suggested by the assistant.
- Added explicit Android MediaProjection screen sharing with an ongoing notification; AI-requested screenshots require an app confirmation and Android system consent, then capture one frame after opening Termux.
- Added an optional auto-analysis loop that sends approved command output to the same AI chat for next-step diagnosis; suggested follow-up commands still require user approval.
- Added a screenshot-saved signal and timed capture path so a confirmed AI screenshot request can complete while the user is viewing Termux.
- Added terminal command/output popups, named working-directory sessions, TUI launching in Termux, app settings, and Midnight/AMOLED/Light themes.
- Added Termux runtime/package helpers and HTTPS downloads, preserved existing file browsing/editing and selected-text `RUN CODE ON TERMUX` functionality.
- Fixed Termux result callback integration to use the documented `RUN_COMMAND_PENDING_INTENT` extra and a mutable one-shot PendingIntent so Termux can return the command result.
- Incremented app version to 0.3.0 / versionCode 3.

## 0.2.0

- Added Android `PROCESS_TEXT` action labeled `RUN CODE ON TERMUX` for selected text in compatible apps, with preview and confirmation before execution.
- Added a Tools Center for Shell commands, working directories, packages/libraries, runtime inventory, diagnostics and archive operations.
- Added automatic launch commands for common programming-language extensions and custom runner fallback.
- Added terminal editor action (`nano` / `vi`) for larger files and increased the built-in UTF-8 editor limit to 256 KiB.
- Improved folder/search transitions and stale-result handling.
