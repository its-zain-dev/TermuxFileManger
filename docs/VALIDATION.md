# Validation — 0.4.0

Validation uses JDK 17, Gradle 8.9 and Android SDK 35. Distributed APKs use the minified Release variant.

## Automated coverage

- 15 Python filesystem tests passed: 10,000-entry pagination, Unicode and shell-special names, symlink handling, copy conflicts, atomic writes, permissions, binary/size limits and optimistic concurrency.
- 20 JVM/Robolectric tests passed: transactional legacy import and rollback, encrypted-store preservation, durable/idempotent callbacks, draft persistence, stream cancellation and provider protocols, agent actions, signed OAuth identity validation and ChatGPT Responses completion/error handling.
- 3 instrumented tests passed on Android 6/API 23: navigation through the main screens/settings, Arabic/AMOLED activity recreation, and real Android Keystore encrypted credential persistence/replacement. The separate Termux file/command integration test was skipped because a supported Termux backend was not installed on that device. The runner reports four tests, of which one is an assumption skip.
- Android Lint passed with no errors. Warnings include intentionally synchronous credential writes on IO threads, newer dependency versions, plural/resource suggestions and APK-versus-app-bundle locale configuration. `targetSdk=32` remains deliberate; this is not a Play target-SDK migration.
- Release assembly and `apksigner verify` passed. RSA-3072 certificate SHA-256: `2e1c8bb07188efebc29ed527d91a48634c33f9f9dba4638a647905c0fdf783b3`. Repeated builds reuse this identity.

`WorkspaceUiTest` covers navigation and Arabic/AMOLED activity recreation. The workflow is configured to run it on API 30, before release publication. The file/command case in `TermuxIntegrationTest` requires Termux, Python and RUN_COMMAND permission, and otherwise reports a skip. Its Keystore case runs independently of Termux. The skipped case must not be counted as real Termux integration coverage.

## Signing persistence

The signed preview uses the automatically generated local `.signing/` identity, with directory mode 0700 and file mode 0600. The directory is excluded from source control and public artifacts. Automatic provisioning was attempted with the connected GitHub identity; GitHub returned `403 Resource not accessible by integration` for the repository Actions Secrets public-key endpoint. Therefore **secure repository persistence of this key is not completed**. CI refuses unsigned publication until a persistent signing identity is available. It never commits a private key or rotates it on each run.

A new identity cannot update an installation signed with an older, different key. The migration tests establish data-format compatibility; they do not establish signing compatibility with an unknown previous APK.

Repository upload was also attempted through authenticated Git push and the GitHub connector's branch API; both returned HTTP 403. The commits and workflow are available locally, but no remote branch, pull request, release or successful Actions run is claimed. The source archive excludes `.signing/`, build caches and local SDK configuration.

## ChatGPT verification boundary

The implementation follows the official [registration/sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in), [accounts/sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions), [models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference) and [preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations) documentation.

No developer API key or client secret is embedded. Tests validate signature, audience and nonce rejection, supported Responses fields, partial-stream behavior and plan-limit errors. **Live account authorization, token renewal/revocation against OpenAI and deduction from a personal ChatGPT allowance have not been verified**, because no signed-in account is available for that test. The owner must complete official browser login and consent at runtime; model availability and usage limits remain account-dependent.

## Device coverage

The signed Release APK was installed and launched on an API 23 x86 emulator. Device tests exposed a lost Activity context when opening the assistant; the localized Compose context now wraps the Activity, preserving activity-result registration and browser launching. Navigation and locale recreation passed after this fix.

API 30 software emulation did not reach a stable Android system in this environment without KVM, before TFM could be tested. There is no claim of API 30/35 device verification, physical ARM testing, real Termux end-to-end validation, every provider's live API, or a live ChatGPT subscription test.

Final signed APK SHA-256: `4a9c7e8ce056669c2120fc834a4deceae9cdf1c323c2057bd323354aacef0ace`.
