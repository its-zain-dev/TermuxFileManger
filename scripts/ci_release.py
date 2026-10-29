#!/usr/bin/env python3
"""Prepare a signed Android APK and search-friendly GitHub Release notes.

Does not generate or persist signing keys; the workflow owns signing and verifies
its output before invoking this script.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
REPO_URL = 'https://github.com/its-zain-dev/TermuxFileManger'


def app_version(build_file: Path) -> str:
    source = build_file.read_text(encoding='utf-8')
    matches = re.findall(r"^\s*versionName\s+['\"]([^'\"]+)['\"]", source, re.MULTILINE)
    if len(matches) != 1 or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:[-.][A-Za-z0-9.]+)?', matches[0]):
        raise ValueError('Expected one valid versionName in app/build.gradle')
    return matches[0]


def version_changes(changelog: str, version: str) -> str:
    match = re.search(rf'^##\s+v?{re.escape(version)}\s*\n(?P<changes>.*?)(?=^##\s|\Z)', changelog, re.MULTILINE | re.DOTALL)
    return match.group('changes').strip() if match else 'See the [complete changelog](' + REPO_URL + '/blob/main/CHANGELOG.md).'


def make_notes(version: str, changes: str, mode: str, apk_name: str, sha: str, run: int, commit: str) -> str:
    if mode != 'persistent':
        raise ValueError('Publishing temporary-signed releases is forbidden')
    banner = ('**Signed with the configured persistent release key.** '
              'Android can update earlier APKs only when they use the same signing identity.')
    return f'''# Termux File Manager v{version} — Android APK download

**Termux File Manager (TFM)** is a free, native Android file manager and development workspace for Termux, with terminal commands, a code editor and a multi-provider AI coding assistant. No root required.

**Download:** Expand **Assets** below and select `{apk_name}`. This is a **signed Release APK**, not a Debug build. Verify the APK using the matching `.sha256` file.

- **Version:** {version} · **Build:** #{run}
- **Android compatibility:** Android 6.0+ (API 23+); a current Termux installation requires Android 7.0+ for its backend
- **Installation:** Download the APK, allow installation from your chosen browser/file manager if Android asks, and open TFM
- **Package:** `dev.itszain.termuxfilemanager`
- **SHA-256:** `{sha}`

## Signing and Android updates

{banner}

## What's new in v{version}

{changes}

## Key features

- Android file browser: folders, search, copy, move, delete, favorites and recent locations
- Code editor and Termux terminal integration: commands, scripts, packages and developer tools
- AI coding assistant: ChatGPT sign-in support and configurable model providers (provider access varies)
- English and Arabic interface, light/dark/AMOLED themes, and phone/tablet layouts

## Getting started

1. Install the APK from **Assets** below.
2. Install and open [Termux](https://termux.dev/) separately.
3. In TFM, choose **Connect to Termux** and follow the permission/setup instructions.
4. Optional: configure an AI provider or use supported ChatGPT sign-in through Settings.

**Documentation:** [README]({REPO_URL}#readme) · [Arabic guide]({REPO_URL}/blob/main/دليل-الاستخدام-عربي.md) · [Changelog]({REPO_URL}/blob/main/CHANGELOG.md) · [All Android APK releases]({REPO_URL}/releases)

**Source commit:** [`{commit[:12]}`]({REPO_URL}/commit/{commit}) · **Open-source Android app** · **Build:** GitHub Actions

**ملف APK لأندرويد:** مدير ملفات تيرموكس مع محرر أكواد، أوامر الطرفية ومساعد ذكاء اصطناعي. نزّل النسخة من قسم **Assets** وراجع ملاحظة التوقيع أعلاه قبل التحديث.
'''


def prepare(root: Path, apk: Path, mode: str, run: int, commit: str, target: Path) -> dict[str, str]:
    if mode != 'persistent':
        raise ValueError('Unknown signing mode')
    if not re.fullmatch('[0-9a-f]{40}', commit):
        raise ValueError('Invalid full Git commit SHA')
    if not apk.is_file() or apk.stat().st_size == 0:
        raise FileNotFoundError(f'Signed APK is missing or empty: {apk}')
    version = app_version(root / 'app/build.gradle')
    changes = version_changes((root / 'CHANGELOG.md').read_text(encoding='utf-8'), version)
    target.mkdir(parents=True, exist_ok=True)
    suffix = 'signed'
    name = f'Termux-File-Manager-v{version}-Android-{suffix}.apk'
    dest = target / name
    shutil.copy2(apk, dest)
    with dest.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    check = target / (name + '.sha256')
    check.write_text(f'{digest}  {name}\n', encoding='utf-8')
    notes = target / 'release-notes.md'
    notes.write_text(make_notes(version, changes, mode, name, digest, run, commit), encoding='utf-8')
    return {'apk': str(dest.resolve()), 'checksum': str(check.resolve()), 'notes': str(notes.resolve()),
            'version': version, 'release_name': f'Termux File Manager v{version} | Android APK | Build #{run}'}


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument('--signing-mode', choices=['persistent'], required=True)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--run-number', type=int, required=True)
    p.add_argument('--commit', required=True)
    args = p.parse_args()
    output = prepare(ROOT, args.apk, args.signing_mode, args.run_number, args.commit, args.out)
    if github_output := os.environ.get('GITHUB_OUTPUT'):
        with open(github_output, 'a', encoding='utf-8') as f:
            for key, value in output.items():
                f.write(f'{key}={value}\n')
    if github_summary := os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(github_summary, 'a', encoding='utf-8') as f:
            f.write(f'### Android Release v{output["version"]}\n\nSigning: **{args.signing_mode}**. APK and checksum prepared.\n')
    print(f'Prepared {output["apk"]}, SHA-256, and release notes ({args.signing_mode} signing).')


if __name__ == '__main__':
    main()
