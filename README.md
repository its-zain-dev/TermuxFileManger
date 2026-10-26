# Termux File Manager (TFM)

A native Java Android companion for Termux. It combines file management with a command/tool center, and can send selected text from other Android apps to a Termux Bash session.

## What's included in v0.2.0

### File workspace
- Browse Termux home, prefix, root-visible paths, and shared-storage paths that Termux can access.
- Show hidden dotfiles; search recursively; page large folder listings.
- Create, view, edit, copy, move, rename, delete, inspect, and change file permissions.
- Edit UTF-8 text files up to 256 KiB in the built-in editor; use `nano` or `vi` in Termux for larger files.
- Open scripts with suggested runners for Bash, Python, JavaScript/Node, TypeScript/tsx, Ruby, PHP, Perl, Lua, R, Go, Swift, Haskell, Elixir, Erlang, Nim, Zig, Dart, C, C++, Rust, Java, Kotlin, and several other common extensions.
- Use a custom runner command for any other language/tool installed in Termux.
- Short, lightweight fade/slide transitions when navigating folders and search results.

### Tools center
- Run arbitrary one-line or multi-line Shell commands in a real Termux terminal session.
- Set the working directory for a command.
- Search/install/update Termux packages with `pkg`.
- Install/manage libraries or tools through helpers for Python `pip`, Node.js `npm`, Rust `cargo`, Ruby `gem`, and Go `go install`.
- Inspect installed runtimes/compilers and system/storage information.
- Create/extract common `.tar.gz`, `.tar`, and `.zip` archives.

### Run selected text in Termux
When text is selected in a compatible Android app, open the text-selection actions menu (sometimes under **More/⋮**) and choose **RUN CODE ON TERMUX**. Review the selected text in the confirmation screen, then choose **تنفيذ الآن** to open a Termux session and run it with Bash. This is an Android text-selection action, not a button injected into the Gboard keyboard row; Android does not allow an ordinary app to add arbitrary actions to another keyboard's UI.

## Requirements
- Android 6.0+ (API 23+).
- Termux installed and supporting the `com.termux.RUN_COMMAND` interface (Termux 0.109+ recommended).
- Termux's `allow-external-apps=true` setting and the Android `Run commands in Termux environment` permission enabled for this app.
- Languages and package tools must be installed inside Termux before their runners can use them. The app does not bundle every language runtime and cannot install tools without the user's consent.

## One-time setup
1. Open **الربط** in the app and copy the setup command.
2. Paste and run it inside Termux, then fully close and restart Termux.
3. In Android Settings, grant **Run commands in Termux environment** to Termux File Manager when the permission is available.
4. Return to the app and refresh.

## Build with GitHub Actions
Push this directory to GitHub. The **Android APK** workflow builds a debug APK using Java 17, Gradle 8.9, and Android Gradle Plugin 8.7.3. Download `TermuxFileManager-debug-apk` from the successful run's Artifacts section.

## Security and limits
- Commands run as the Termux app user. This app does not provide root access and cannot bypass Android's app sandbox.
- Both the selected-text action and the command center display a confirmation before running user-supplied shell text. Shell commands can still delete files, change packages, or execute downloaded code; only run commands you trust.
- The selected text is passed as a Bash command, not treated as plain text. Do not select and run untrusted snippets.
- The built-in editor is for small/medium UTF-8 text files. Binary files and files over 256 KiB are not opened in it.
- Recursive search displays at most 150 results at a time. Directory listings are paginated.
- File deletion is permanent and recursive after confirmation.
