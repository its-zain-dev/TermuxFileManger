# F-Droid Request for Packaging (RFP) — Termux File Manager

- **App Name:** Termux File Manager
- **Application ID:** `dev.itszain.termuxfilemanager`
- **License:** `GPL-3.0-only` (see `LICENSE` and `COPYRIGHT`)
- **Source Code:** https://github.com/its-zain-dev/TermuxFileManger
- **Issue Tracker:** https://github.com/its-zain-dev/TermuxFileManger/issues
- **Categories:** `Development`, `System`
- **Build System:** Gradle (`app` module, JDK 17, AGP 8.7.3, Gradle 8.9 with verified `distributionSha256Sum`)
- **Reproducible Build Configuration:**
  - `dependenciesInfo { includeInApk = false; includeInBundle = false }`
  - `buildTypes.release.vcsInfo { include = false }`
  - Unsigned release APK built cleanly when `TFM_RELEASE_KEYSTORE_PATH` is unset.
- **FOSS Dependencies:** 100% open-source AndroidX / Jetpack Compose / Material 3 / Room / DataStore / Kotlin Coroutines / OkHttp. Zero Google Play Services, Firebase, analytics, ads, or proprietary SDKs.
- **AntiFeatures Disclosure:**
  - `NonFreeNet`: Optional user-configured commercial AI APIs (OpenAI, Gemini, Anthropic, ChatGPT OAuth, etc.). All core file manager, code editor, archive/hash/disk tools, terminal sessions, and local loopback LLM integration (Ollama / llama.cpp on `127.0.0.1`) operate locally without any cloud service.
- **Fastlane Metadata:** Complete `en-US` and `ar` titles, descriptions, changelogs, icons (512×512), and feature graphics (1024×500) under `fastlane/metadata/android/`.
- **Draft `fdroiddata` Metadata Recipe:** Included at `fdroid/dev.itszain.termuxfilemanager.yml`.
- **Companion Requirement:** Requires [Termux](https://f-droid.org/packages/com.termux/) (`com.termux`) installed separately with `allow-external-apps=true` and `python`.
