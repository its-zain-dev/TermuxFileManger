# 0.5.0 validation

## Assistant tools

- Native ChatGPT function calls execute through the shared 13-tool registry. Tool-only Responses output is valid; completed function calls, encrypted reasoning items and matching `function_call_output` results are carried into the next request.
- Older providers keep the JSON protocol. A balanced-object parser accepts the Arabic prose plus embedded action shown in the reported screenshots. Commands are displayed as readable tool cards, with raw request/result details available on expansion.
- `app_info` reads the actual configured model ID sent with the request and selected session. It does not inspect credential files or claim to identify hidden server model weights.
- Writes and arbitrary shell commands wait for review by default. Review decisions are bound to the displayed message ID. Cancelled or interrupted commands are never replayed automatically; saved pending/running rows recover as interrupted.
- Greetings do not trigger workspace synchronization or Termux execution. Tool errors are returned to the model, rather than treated as successful execution.
- Tools hub supports search, categories, responsive cards and editable assistant drafts. Preparing a draft does not send it or run a command.

## Checks

- 30 JVM tests passed: provider payloads and streaming, official ChatGPT transport with a local TLS mock server, native tool-only responses, namespace normalization, persisted function-call pairing, interruption recovery, model/account isolation of encrypted reasoning, mixed-text JSON parsing, real app-info execution through the controller, and approval/decline behavior.
- Android Lint passed with no errors.
- Android instrumentation on API 23: four passed (navigation/settings, Arabic/theme recreation, editable tool draft, and Android Keystore credential persistence). One real-Termux integration test was skipped because the emulator does not have a compatible Termux installation; it is not counted as a pass.
- Final minified, resource-shrunk Release APK installed successfully over the existing 0.4.0 release with `adb install -r`, without uninstalling or clearing data. The updated package reports 0.5.0 / code 6.
- Release smoke on an 800×1280 / 160 dpi API 23 tablet passed: two-column tools hub, tool details dialog, editable unsent request and enabled send control. Focused composer remained adjacent to the visible keyboard; the tablet navigation rail remained available. Screenshots and UI hierarchy dumps are retained with the artifacts. No fatal app exception appeared in the crash log.
- No live inference using the user's ChatGPT account was performed. The user's phone credentials are unavailable in this workspace. Transport and tool-loop behavior were exercised locally; an active account and a connected Termux installation are still required on the device.
- Application ID remains `dev.itszain.termuxfilemanager`; versionCode 6 / versionName 0.5.0. The existing release signing identity is reused.

- Signed APK passes Android signature verification (v1 + v2) and 16-KiB-aware zip alignment. Certificate SHA-256: `2e1c8bb07188efebc29ed527d91a48634c33f9f9dba4638a647905c0fdf783b3`, unchanged from 0.4.x.
- APK SHA-256: `c73036d7f722f3aff6121e360fc7a5f3bb501525b97c89054aa0e8ee5893995d`.

Build logs, signature verification, test reports and release-device smoke evidence are recorded with the delivery artifacts.
