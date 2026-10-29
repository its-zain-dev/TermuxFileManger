# F-Droid submission preparation — Termux File Manager

## Verified source facts (checked 2026-10-30)

- Source repository: https://github.com/its-zain-dev/TermuxFileManger
- Android application ID: `dev.itszain.termuxfilemanager`
- Source version at inspection: `versionName '0.5.0'`, `versionCode 6`
- Android build: single Gradle `app` module; AGP 8.7.3, Gradle 8.9, JDK 17, compileSdk 35, minSdk 23, targetSdk 32.
- Main branch GitHub release v0.5.0-build.24 predates addition of GPL-3.0 licensing files and upstream store metadata.
- Licensing choice from the project owner: **GPL-3.0-only**. Review contributor and asset rights before publishing.
- Direct Gradle deps appear to be AndroidX/Jetpack Compose, OkHttp, Kotlin coroutines, Room, JUnit and Robolectric; no Firebase, Play Services or obvious proprietary SDK in direct dependencies.
- Optional user-configured commercial AI services (ChatGPT, Gemini, Anthropic, etc.) warrant transparent disclosure; F-Droid maintainers may apply `NonFreeNet` anti-feature. The core file tools do not require an AI account.
- Release-key / signing secrets are NOT to be committed to the source, supplied to F-Droid, or included in metadata.

## Package contents

- `LICENSE` — verbatim GNU General Public License version 3 text.
- `COPYRIGHT` — notice of licensing scope and third-party code.
- `fastlane/metadata/android/en-US` and `ar` — storefront titles, descriptions, release notes for **versionCode 6**, and original graphic based on app launcher icon.
- `docs/PRIVACY.md` — reviewable description of local and optional remote processing.
- `fdroiddata-draft/dev.itszain.termuxfilemanager.yml` — build-metadata TEMPLATE, **not ready for direct submission** until a newer tagged source release exists.
- `RFP-DRAFT.md` — request-for-packaging text to paste into GitLab if preferred to a direct fdroiddata merge request.

## Before submitting an official F-Droid request

1. Merge this source change, inspect `LICENSE`, review copyrights of contributors and any copied assets.
2. Build and test the app from the published tree, including offline/without AI account. Confirm no proprietary binary dependencies appear in the transitive dependency graph and no downloaded executables bypass user consent.
3. **Create a new version and tagged commit AFTER these new metadata/license files are merged**, e.g. update the project to `versionName '0.5.1'`, `versionCode 7`, then tag that source commit `v0.5.1`. F-Droid builds a tag/commit, not an APK from GitHub Releases. Avoid reusing the existing `v0.5.0-build.24` tag: it points at earlier sources without this license.
4. Update the `fdroiddata-draft` template to match the actual newly tagged `versionName`, `versionCode` and commit/tag. Do not copy placeholder versions into fdroiddata.
5. Capture **real current-version screenshots** from the actual app (phone and optional tablet). Add them as PNG/JPEG at `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` etc. Old screenshots from different versions were deliberately excluded.
6. Confirm the `NonFreeNet` disclosure with maintainers. Note that cloud AI is optional and the file/terminal features are local.
7. Prefer a merge request to `https://gitlab.com/fdroid/fdroiddata` with the final metadata YAML or open a request at `https://gitlab.com/fdroid/rfp/-/issues/new` using `RFP-DRAFT.md`.
8. In the application description, link to `docs/PRIVACY.md` if helpful; avoid implying GitHub-signed APKs can update F-Droid-signed APKs. They cannot unless both share the same signing key.
9. When approved, F-Droid's build infrastructure compiles from source and signs its own APK. Expect review and possible changes requested by maintainers.

## Testing (developer machine / GitHub Actions, not required on Termux)

```
python3 -m unittest discover -s tests -v
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

Do not publish release APKs signed with the permanent GitHub key inside the source tree. For the F-Droid official build, leave `TFM_RELEASE_KEYSTORE_PATH` unset so Gradle creates an unsigned Release APK for F-Droid to sign. The GitHub Actions workflow can continue using its private signing secrets independently.

## Why the draft is not already an accepted F-Droid release

- F-Droid official inclusion requires build verification and human review.
- Current tagged source does not yet contain these files. A new tag is essential.
- Confirm optional cloud functionality, licenses of any imported code, transitive dependency licenses and Android/Termux permission behavior.

Official guides:
- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Inclusion_Policy/
- https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/
