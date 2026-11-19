# F-Droid Submission & Patch Compliance Guide — Termux File Manager

## Verified Source & Build Compliance (v1.0.0 / versionCode 9)

- **Source Repository:** https://github.com/its-zain-dev/TermuxFileManger
- **Application ID:** `dev.itszain.termuxfilemanager`
- **License:** `GPL-3.0-only` (`LICENSE` + `COPYRIGHT`)
- **Toolchain:** Single `:app` Gradle module, AGP `8.7.3`, Gradle `8.9` (`distributionSha256Sum` pinned), JDK 17, `compileSdk 35`, `minSdk 23`, `targetSdk 32` (intentionally kept for Termux `RUN_COMMAND` compatibility).
- **Reproducible & Scanner-Clean Gradle Build:**
  - `dependenciesInfo { includeInApk = false; includeInBundle = false }` strips Google's encrypted `DependencyInfoBlock` (`0x504b4453`).
  - `buildTypes.release.vcsInfo { include = false }` omits `META-INF/version-control-info.textproto`.
  - Release signing is disabled when `TFM_RELEASE_KEYSTORE_PATH` is unset, so F-Droid produces an unsigned Release APK to sign with its own infrastructure key.
  - Zero proprietary SDKs, Google Play Services, Firebase, Crashlytics, or analytics libraries in Gradle dependencies.
  - Zero committed APKs, native `.so` blobs, keystores, or unauthorized JARs.
- **Security & Network Transparency:**
  - `android:usesCleartextTraffic="false"` and `android:allowBackup="false"`.
  - `network_security_config.xml` permits cleartext HTTP **only** for `127.0.0.1` and `localhost` (used by optional local Ollama / llama.cpp and local OpenCode services). All remote endpoints strictly enforce HTTPS.
  - All API keys, OAuth tokens, and local OpenCode service passwords are encrypted using Android Keystore AES-GCM (`AiSecureStore`).
  - Optional third-party OpenCode Termux repository setup (`bd-loser/opencode-bionic`) is never executed silently — users must review the exact shell command and repository URL first, and can copy/run it manually or skip it entirely.
- **AntiFeatures Disclosure:**
  - `NonFreeNet`: Optional cloud AI providers (Gemini, Claude, OpenAI, ChatGPT OAuth, etc.). Core file manager, editor, terminal, tools, and local loopback Ollama/llama.cpp work 100% locally.

## Repository Files for F-Droid

- `LICENSE` — Verbatim GNU GPL v3 (`GPL-3.0-only`).
- `COPYRIGHT` — Scope and third-party notices.
- `fdroid/dev.itszain.termuxfilemanager.yml` — Ready-to-submit `fdroiddata` recipe.
- `docs/FDROID_RFP.md` — Ready-to-file GitLab RFP template.
- `docs/PRIVACY.md` — Privacy and network behavior documentation.
- `fastlane/metadata/android/en-US/` and `ar/` — Titles, short/full descriptions, icons (`512×512`), feature graphics (`1024×500`), and changelogs for versionCodes `6`, `7`, `8`, and `9`.
- `scripts/check_fdroid.py` — Automated compliance verifier executed in GitHub Actions CI (pre-build source checks + post-build Release APK binary inspection).

## Automated Verification Commands

```bash
python3 -m unittest discover -s tests -v
python3 scripts/check_fdroid.py
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
python3 scripts/check_fdroid.py --apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## Remaining Manual Step Before Opening the `fdroiddata` MR

1. Merge the `v1.0.0` release commit to `main` and create an annotated Git tag `v1.0.0` pointing at that commit.
2. Optionally capture fresh phone screenshots (`fastlane/metadata/android/en-US/images/phoneScreenshots/1.png`, etc.) on a clean workspace with no private paths or keys.
3. Submit `fdroid/dev.itszain.termuxfilemanager.yml` as a merge request to `https://gitlab.com/fdroid/fdroiddata` (or file an RFP at `https://gitlab.com/fdroid/rfp/-/issues/new` using `docs/FDROID_RFP.md`).
