# Application screenshot guide

The two images in `docs/screenshots/` are **authentic in-app captures** taken on 2026-10-28, from an older Android interface. They are intentionally labeled **legacy** in the README and should not be used to claim that v0.5.0 has the same UI.

## Replace with current version screenshots

1. Install the latest signed Release APK from [GitHub Releases](https://github.com/its-zain-dev/TermuxFileManger/releases/latest).
2. Open the main Files page, the Editor with a harmless demo file, the Terminal showing non-sensitive sample output, the Tools hub, and the AI Agent with a fictional example conversation.
3. Capture each screen on your Android device (usually Power + Volume Down); transfer captures to your repository's `docs/screenshots/` folder.
4. Suggested names: `files-v0.5.0.png`, `editor-v0.5.0.png`, `terminal-v0.5.0.png`, `tools-v0.5.0.png`, `assistant-v0.5.0.png`. Prefer original PNG/JPG captures. Keep realistic screen ratios rather than stretching images.
5. **Remove confidential information before publishing:** tokens, API keys, account identifiers, filesystem paths identifying other people, personal chats, notifications, email addresses, IPs and file contents not intended for release.
6. Update the README screenshot table to reference the new paths and dates, then delete legacy images only if they are no longer referenced.

Do not use illustration SVGs as replacements for in-app screenshots without clearly labeling them as diagrams. SVG animations should remain declarative and self-contained (no JavaScript, external assets, fonts or network requests), with reduced-motion fallbacks.
