# 0.4.1 validation

- Signed Release build, 22 JVM tests and Android Lint passed.
- Tablet keyboard checked on API 23 at 800×1280 pixels / 160 dpi: editor text bottom 901, keyboard content top 933, gap 32 pixels including composer padding. The navigation rail remained visible. Adjust-resize and consumed content insets keep IME space applied once.
- Responses regression tests cover SSE event names, completed JSON responses, no duplicated final text, identity validation, interrupted streams, and original server errors/incomplete reasons.
- Empty failed assistant messages are excluded from future request history.
- OAuth account inference was not exercised live: no account credentials from the user's device are available. Screenshots alone do not establish the exact original server rejection; the parser now preserves its code/message instead of masking it with a generic incomplete-response error.
- Same release certificate as 0.4.0; application ID unchanged, versionCode 5 / versionName 0.4.1.
- APK SHA-256: `5f413e66b205c930435a530a39c14d0eac961e4b68d318673f8d6c91c675bf4f`.
