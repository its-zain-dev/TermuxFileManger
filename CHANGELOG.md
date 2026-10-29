# Changelog

## 0.5.0

- Added native ChatGPT function calling with a namespaced tool registry, durable call/result pairing and interruption recovery.
- Added 13 assistant tools, including authoritative app/model inspection and guarded file/project helpers.
- Fixed legacy actions embedded in prose or code fences; greetings cannot trigger tools that access Termux.
- Rebuilt the tools hub with searchable categories and editable assistant request drafts.
- Added tool request/result cards, persisted execution states and in-conversation approval controls.
- Replaced the shell-only auto-analysis toggle with automatic result handoff; execution approval remains separate.

## 0.4.1

- Resize the window for the keyboard, consume Scaffold insets once, and keep the tablet conversation full-height.
- Parse named SSE events and completed JSON Responses, retain server error details, and stop reading after completion.
- Exclude empty failed assistant messages from subsequent requests.

## 0.4.0

- Rebuilt the interface in Kotlin, Jetpack Compose and Material 3, with English/Arabic RTL, four themes and adaptive phone/tablet navigation.
- Added paginated file browsing, search, selection, favorites/recent places, sorting, list/grid layouts and batch conflict handling.
- Added an editor with drafts, undo/redo, search and protection against overwriting external changes.
- Moved conversations, sessions, commands and drafts to Room with transactional legacy import; moved preferences to DataStore while retaining Keystore compatibility.
- Added cancellable streamed provider requests, persistent command results and application-scoped assistant execution.
- Added official ChatGPT public-client sign-in with account selection, PKCE, validated identity, encrypted rotating tokens, plan model discovery and streamed Responses requests.
- Added automatic persistent local release signing, secure GitHub Secrets provisioning when authorized, and a publication check that rejects unsigned releases.
- Added filesystem, persistence, provider, OAuth and UI tests; retained Termux tools, skills, memory and screen capture.

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
