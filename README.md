# Termux File Manager (TFM)

Android file-manager UI for browsing and managing files available to the Termux app. The project is written in Java with a native Android interface; it does not display a terminal as its main UI.

## Features in this first build

- Browse Termux `$HOME`, `$PREFIX`, `/`, and shared storage paths.
- Show hidden dotfiles by default, including `.bashrc` and `.termux`.
- Search filenames recursively from the current directory.
- Create files/folders; view and edit small text files; copy, move, rename, and delete files/folders.
- Inspect basic metadata and change read/write/execute permissions.
- Run a selected file in a Termux terminal session.
- Use Termux's `RUN_COMMAND` interface rather than asking for root access or opening a terminal as the primary screen.

## Important limits

- This is a source-project preview. It has not been compiled into an APK in this environment because Android SDK/Gradle downloads are unavailable here. The included GitHub Actions workflow builds a debug APK after the project is pushed to a GitHub repository; it uploads `app-debug.apk` as an Actions artifact.
- Termux must be installed and recent enough to support `RUN_COMMAND` result callbacks (Termux 0.109 or newer recommended). Python is required inside Termux (`pkg install python`).
- Android sandbox rules still apply. The app sees files the Termux app UID can access; it does not gain root or permission to read other apps' private directories.
- The built-in text editor is intentionally capped at 32 KB when reading a file, to keep command-result callbacks within Android's transaction limits. Larger and binary files can still be copied, moved, renamed, chmod'ed, executed (when appropriate), or deleted.
- Recursive search caps its displayed results at 150 per search. Folder listings load 100 entries per page.
- Deletion is permanent. Directories are removed recursively after the confirmation dialog.

## One-time setup on the device

1. Install Termux (use the same trusted distribution source used for Termux add-ons).
2. Open Termux File Manager and tap **الربط** (Link), then **نسخ أمر الإعداد** (Copy setup command). The app copies the setup command and opens Termux.
3. Paste and run the command in Termux. It sets `allow-external-apps=true` and installs Python. Fully close and restart Termux afterward.
4. In Android Settings, open **Termux File Manager → Permissions / Additional permissions** and grant **Run commands in Termux environment** if Android shows it there.
5. Reopen the file manager and press Refresh.

The setup command changes Termux's `~/.termux/termux.properties` to allow external app commands. This is an explicit security boundary in Termux. Only use this app build if you trust its source.

## Build an APK with GitHub Actions

1. Push this project folder to a GitHub repository.
2. In the repository, open **Actions** and run **Android APK**, or push a commit to trigger it automatically.
3. Open the completed workflow run and download the `TermuxFileManager-debug-apk` artifact. Extract the ZIP artifact and install `app-debug.apk` on Android.

The workflow uses Java 17, Gradle 8.9, and Android Gradle Plugin 8.7.3. A signing/release pipeline is not included yet; this first workflow produces a debug-signed APK.

## Security note

The app executes file operations with Termux's own user permissions. It does not use root or a network server. File execution is a deliberate user action and can run arbitrary code from the selected file; only run files you trust.
