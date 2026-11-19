#!/usr/bin/env python3
"""Automated F-Droid inclusion, scanner, Fastlane, and reproducible-build compliance checker."""
import argparse
import glob
import os
from pathlib import Path
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]

FORBIDDEN_DEPS = (
    'com.google.android.gms',
    'com.google.firebase',
    'com.facebook.',
    'io.sentry',
    'com.amplitude',
    'com.mixpanel',
    'com.adjust',
    'com.appsflyer',
    'com.onesignal',
    'com.huawei.hms',
    'com.crashlytics',
    'com.flurry',
)

FORBIDDEN_BIN_EXTS = {'.apk', '.aab', '.so', '.dex', '.aar', '.jks', '.keystore', '.p12', '.class'}

# APK Signing Block magic & Google DependencyInfoBlock ID (0x504b4453)
APK_SIG_BLOCK_MAGIC = b'APK Sig Block 42'
GOOGLE_DEP_INFO_BLOCK_ID = 0x504B4453


def check_apk_binary(apk_path: Path, errors: list[str]) -> None:
    if not apk_path.is_file():
        errors.append(f'APK not found: {apk_path}')
        return
    with zipfile.ZipFile(apk_path, 'r') as zf:
        names = set(zf.namelist())
        if 'META-INF/version-control-info.textproto' in names:
            errors.append('APK contains META-INF/version-control-info.textproto (set vcsInfo.include = false)')
        bad_native = [n for n in names if n.startswith('com/google/android/gms/') or n.startswith('com/google/firebase/')]
        if bad_native:
            errors.append(f'APK contains proprietary Google Play/Firebase classes: {bad_native[:3]}')

    raw = apk_path.read_bytes()
    magic_idx = raw.rfind(APK_SIG_BLOCK_MAGIC)
    if magic_idx != -1 and magic_idx >= 8:
        block_size = struct.unpack('<Q', raw[magic_idx - 8:magic_idx])[0]
        block_start = magic_idx + 16 - block_size - 8
        if 0 <= block_start < magic_idx - 8:
            pos = block_start + 8
            end = magic_idx - 8
            while pos + 12 <= end:
                pair_len = struct.unpack('<Q', raw[pos:pos + 8])[0]
                if pair_len < 4 or pos + 8 + pair_len > end:
                    break
                pair_id = struct.unpack('<I', raw[pos + 8:pos + 12])[0]
                if pair_id == GOOGLE_DEP_INFO_BLOCK_ID:
                    errors.append('APK Signing Block contains Google encrypted DependencyInfoBlock (0x504b4453)!')
                pos += 8 + pair_len


def main() -> int:
    parser = argparse.ArgumentParser(description='Verify F-Droid inclusion and reproducible-build compliance.')
    parser.add_argument('--apk', type=Path, help='Optional Release APK path to inspect for non-FOSS signing/metadata blocks.')
    args = parser.parse_args()

    errors: list[str] = []
    warnings: list[str] = []

    # 1. License & Copyright
    license_file = ROOT / 'LICENSE'
    if not license_file.is_file() or 'GNU GENERAL PUBLIC LICENSE' not in license_file.read_text(encoding='utf-8'):
        errors.append('LICENSE missing or does not contain GNU GPL v3 text')
    if not (ROOT / 'COPYRIGHT').is_file():
        errors.append('COPYRIGHT file missing')

    # 2. app/build.gradle checks
    gradle_text = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
    vc_match = re.search(r'^\s*versionCode\s+(\d+)', gradle_text, re.MULTILINE)
    vn_match = re.search(r"^\s*versionName\s+['\"]([^'\"]+)['\"]", gradle_text, re.MULTILINE)
    if not vc_match or not vn_match:
        errors.append('Could not parse versionCode / versionName in app/build.gradle')
        version_code, version_name = 0, ''
    else:
        version_code, version_name = int(vc_match.group(1)), vn_match.group(1)

    if 'dependenciesInfo' not in gradle_text or 'includeInApk = false' not in gradle_text or 'includeInBundle = false' not in gradle_text:
        errors.append('app/build.gradle must disable dependenciesInfo (includeInApk = false, includeInBundle = false)')
    if 'vcsInfo' not in gradle_text or 'include = false' not in gradle_text:
        errors.append('app/build.gradle must disable release vcsInfo (include = false) for reproducible builds')
    for bad in FORBIDDEN_DEPS:
        if bad in gradle_text:
            errors.append(f'Forbidden proprietary/tracker dependency found in app/build.gradle: {bad}')

    # 3. Gradle wrapper verification
    wrapper_props = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text(encoding='utf-8')
    if 'distributionSha256Sum=' not in wrapper_props:
        errors.append('gradle-wrapper.properties must pin distributionSha256Sum')
    if 'https\\://services.gradle.org/distributions/' not in wrapper_props:
        errors.append('gradle-wrapper.properties must point to official HTTPS Gradle distribution URL')

    # 4. Scan repository for unexpected binaries or committed secrets
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in ('.git', '.gradle', 'build', '.signing')]
        for fn in filenames:
            p = Path(dirpath) / fn
            rel = p.relative_to(ROOT).as_posix()
            ext = p.suffix.lower()
            if ext in FORBIDDEN_BIN_EXTS:
                errors.append(f'Forbidden binary committed in source tree: {rel}')
            elif ext == '.jar' and rel != 'gradle/wrapper/gradle-wrapper.jar':
                errors.append(f'Unexpected JAR committed in source tree: {rel}')

    # 5. AndroidManifest & Network Security Config
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
    if 'android:usesCleartextTraffic="false"' not in manifest:
        errors.append('AndroidManifest.xml must set android:usesCleartextTraffic="false"')
    if 'android:allowBackup="false"' not in manifest:
        errors.append('AndroidManifest.xml must set android:allowBackup="false"')
    nsc_root = ET.parse(ROOT / 'app/src/main/res/xml/network_security_config.xml').getroot()
    for dc in nsc_root.findall('domain-config'):
        if dc.attrib.get('cleartextTrafficPermitted') == 'true':
            for dom in dc.findall('domain'):
                if (dom.text or '').strip() not in ('127.0.0.1', 'localhost'):
                    errors.append(f'Cleartext traffic permitted for non-loopback host: {dom.text}')

    # 6. XML validity + complete EN/AR string parity + zero unresolved string refs
    en_root = ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()
    ar_root = ET.parse(ROOT / 'app/src/main/res/values-ar/strings.xml').getroot()
    en_keys = {el.attrib['name'] for el in en_root.findall('string')}
    ar_keys = {el.attrib['name'] for el in ar_root.findall('string')}
    if en_keys != ar_keys:
        errors.append(f'String parity mismatch: EN-only={sorted(en_keys - ar_keys)}, AR-only={sorted(ar_keys - en_keys)}')

    refs: set[str] = set()
    for p in (ROOT / 'app/src/main').rglob('*'):
        if not p.is_file():
            continue
        if p.suffix in ('.kt', '.java'):
            refs |= set(re.findall(r'R\.string\.([A-Za-z0-9_]+)', p.read_text(encoding='utf-8')))
        elif p.suffix == '.xml' and p.name != 'strings.xml':
            ET.parse(p)  # verify XML well-formedness
            refs |= set(re.findall(r'@string/([A-Za-z0-9_]+)', p.read_text(encoding='utf-8')))
    missing_refs = sorted(refs - en_keys)
    if missing_refs:
        errors.append(f'Unresolved string resource references: {missing_refs}')

    # 7. Fastlane metadata validation (en-US and ar)
    for locale in ('en-US', 'ar'):
        base = ROOT / 'fastlane/metadata/android' / locale
        checks = [
            ('title.txt', 30),
            ('short_description.txt', 80),
            ('full_description.txt', 4000),
            (f'changelogs/{version_code}.txt', 500),
        ]
        for rel_file, max_chars in checks:
            f = base / rel_file
            if not f.is_file():
                errors.append(f'Missing Fastlane file: {f.relative_to(ROOT)}')
            else:
                text = f.read_text(encoding='utf-8').strip()
                if not text:
                    errors.append(f'Empty Fastlane file: {f.relative_to(ROOT)}')
                elif len(text) > max_chars:
                    errors.append(f'Fastlane file exceeds {max_chars} chars ({len(text)}): {f.relative_to(ROOT)}')
        for img_rel, expected_dim in (('images/icon.png', (512, 512)), ('images/featureGraphic.png', (1024, 500))):
            img = base / img_rel
            if not img.is_file():
                errors.append(f'Missing Fastlane graphic: {img.relative_to(ROOT)}')
            else:
                hdr = img.read_bytes()[:24]
                if hdr[:8] != b'\x89PNG\r\n\x1a\n':
                    errors.append(f'Not a valid PNG: {img.relative_to(ROOT)}')
                else:
                    dim = struct.unpack('>II', hdr[16:24])
                    if dim != expected_dim:
                        errors.append(f'Fastlane graphic {img.relative_to(ROOT)} is {dim}, expected {expected_dim}')

    # 8. fdroiddata recipe consistency
    recipe = ROOT / 'fdroid/dev.itszain.termuxfilemanager.yml'
    if not recipe.is_file():
        errors.append('Missing F-Droid recipe: fdroid/dev.itszain.termuxfilemanager.yml')
    else:
        rtxt = recipe.read_text(encoding='utf-8')
        if f'CurrentVersion: {version_name}' not in rtxt:
            errors.append(f'fdroid recipe CurrentVersion does not match versionName {version_name}')
        if f'CurrentVersionCode: {version_code}' not in rtxt:
            errors.append(f'fdroid recipe CurrentVersionCode does not match versionCode {version_code}')
        if 'License: GPL-3.0-only' not in rtxt:
            errors.append('fdroid recipe missing License: GPL-3.0-only')

    # 9. Optional APK binary inspection
    if args.apk is not None:
        check_apk_binary(args.apk, errors)

    for w in warnings:
        print(f'WARN: {w}')
    if errors:
        for e in errors:
            print(f'ERROR: {e}', file=sys.stderr)
        return 1
    print(f'OK: F-Droid compliance verified for v{version_name} (versionCode {version_code}, {len(en_keys)} EN/AR strings).')
    return 0


if __name__ == '__main__':
    sys.exit(main())
