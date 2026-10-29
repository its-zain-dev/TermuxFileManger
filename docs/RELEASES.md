# Termux File Manager APK Download and Releases

**Termux File Manager (TFM)** is an Android file manager and developer workspace that integrates with Termux. It provides file browsing, a code editor, terminal commands and an AI assistant. The interface supports English and Arabic. Android 6.0+ (API 23+) is supported by the app, while current Termux versions require Android 7.0+ for their backend. No root is required.

## Download Termux File Manager for Android

**[Download signed Android APK releases from GitHub](https://github.com/its-zain-dev/TermuxFileManger/releases)**

Every successful main-branch build publishes a **permanently signed Release APK**, a `.sha256` checksum and release notes with the version number. Debug APKs aren't published as downloadable releases. Without permanent signing secrets the release job fails safely; it never publishes a temporary-signed APK.

### Permanent signing and Android app updates

- **All published APKs are permanently signed.** GitHub Actions requires all four `TFM_RELEASE_*` secrets to exist and verifies the APK signature before publication.
- **No temporary preview keys.** If Secrets are missing or incorrect, the build fails rather than publishing an APK that cannot be updated.
- **Android updates:** A newly signed APK updates existing installations only when they share the exact same signing key. Old unsigned builds can't be installed normally. Back up data before uninstalling an app signed with a different key.
- **Private provisioning:** The maintainer receives a **passphrase-encrypted** RSA-3072 signing bundle, created once off-device. Run `python3 tfm-release-secure/apply_and_push.py --repo ~/TermuxFileManger` to decrypt/import the same key to the ignored `.signing/` directory and upload the four secrets privately using `gh secret set -f -`. No Java installation is needed on Termux; Java is only installed on the GitHub Actions runner. Never commit or publicly share the encrypted bundle or the passphrase.
- **Backups:** Keep a secure, encrypted offline copy of the complete `.signing/` directory. GitHub Secrets cannot return a previously stored signing key.

## Install APK on Android

1. Open the [Releases page](https://github.com/its-zain-dev/TermuxFileManger/releases) and select a recent release, then expand **Assets**.
2. Download `Termux-File-Manager-v...-Android-...-signed.apk` and the adjacent `.sha256` file if you want to verify its checksum.
3. Grant installation-from-this-source permission when Android prompts, install the APK, and launch the app.
4. Install and open [Termux](https://termux.dev/) separately. In TFM, tap **Connect to Termux** and follow its setup instructions.
5. Optionally configure an AI provider or supported ChatGPT sign-in from app Settings.

To validate the checksum, place the APK and its `.sha256` file in one directory and run `sha256sum -c <the-apk-name>.sha256` in a terminal.

## What is included

File manager, search, copying/moving/deleting files, favorites, code editor, Termux terminal integration, developer package tools, AI coding assistant with configurable model providers, and light/dark/AMOLED themes.

**More:** [README](../README.md) · [Changelog](../CHANGELOG.md) · [Arabic guide](../دليل-الاستخدام-عربي.md) · [Android GitHub Actions](https://github.com/its-zain-dev/TermuxFileManger/actions/workflows/android.yml)

**بالعربي:** حتى تنزل مدير ملفات تيرموكس للأندرويد، افتح Releases وحمّل ملف APK الموقّع من Assets. كل إصدار يستخدم نفس المفتاح الدائم المخزّن بسرية على GitHub Actions، والتحديث فوق نسخة سابقة يحتاج نفس التوقيع.
