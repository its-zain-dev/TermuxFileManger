#!/usr/bin/env python3
"""Reupload an EXISTING, local, permanent signing key to GitHub Secrets. No Java needed."""
import base64
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REPO = 'its-zain-dev/TermuxFileManger'
NAMES = ('TFM_RELEASE_KEYSTORE_BASE64', 'TFM_RELEASE_STORE_PASSWORD',
         'TFM_RELEASE_KEY_ALIAS', 'TFM_RELEASE_KEY_PASSWORD')


def main():
    signing = ROOT / '.signing'
    store, metadata = signing / 'release.jks', signing / 'identity.json'
    if not (store.is_file() and metadata.is_file()) or store.is_symlink() or metadata.is_symlink():
        raise RuntimeError('Original private .signing key is missing. Recover your original backup; DO NOT rotate keys.')
    data = json.loads(metadata.read_text(encoding='utf-8'))
    if not data.get('password') or not data.get('alias'):
        raise RuntimeError('Invalid private signing metadata.')
    proc = subprocess.run(['gh', 'secret', 'list', '--repo', REPO, '--app', 'actions', '--json', 'name'],
                          capture_output=True, text=True, check=True)
    found = {x['name'] for x in json.loads(proc.stdout)} & set(NAMES)
    if found:
        raise RuntimeError('Existing signing secrets detected; refusing to overwrite without verifying their original key. Keep existing secrets.')
    val = (base64.b64encode(store.read_bytes()).decode('ascii'), data['password'],
           data['alias'], data['password'])
    payload = ''.join(f'{k}={v}\n' for k, v in zip(NAMES, val))
    subprocess.run(['gh', 'secret', 'set', '--repo', REPO, '--app', 'actions', '-f', '-'],
                   input=payload, text=True, capture_output=True, check=True)
    print('Permanent signing secrets uploaded. Key never entered Git.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as exc:
        print('ERROR: ' + str(exc) if isinstance(exc, RuntimeError) else 'ERROR: GitHub rejected the secret upload.', file=sys.stderr)
        sys.exit(1)
