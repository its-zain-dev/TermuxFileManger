#!/usr/bin/env python3
"""Create/reuse a local release identity, optionally provision GitHub Secrets, and build.
Never commit .signing or upload it as a build artifact. No credentials are printed.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]
NAMES = ['TFM_RELEASE_KEYSTORE_BASE64', 'TFM_RELEASE_STORE_PASSWORD', 'TFM_RELEASE_KEY_ALIAS', 'TFM_RELEASE_KEY_PASSWORD']


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--publish-secrets', action='store_true')
    parser.add_argument('--build', action='store_true')
    parser.add_argument('--repo', default='its-zain-dev/TermuxFileManger')
    args = parser.parse_args()
    directory = ROOT / '.signing'; directory.mkdir(mode=0o700, exist_ok=True)
    config = directory / 'identity.json'; keystore = directory / 'release.jks'
    if config.exists():
        if not keystore.exists():
            raise SystemExit('Signing identity exists but its keystore is missing; refusing to rotate it.')
        identity = json.loads(config.read_text())
    else:
        if keystore.exists():
            raise SystemExit('Existing keystore has no identity metadata; refusing to rotate it.')
        identity = {'password': secrets.token_urlsafe(40), 'alias': 'tfm-release'}
        fd = os.open(config, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as stream: json.dump(identity, stream)
    env = os.environ.copy()
    env.update(TFM_RELEASE_KEYSTORE_PATH=str(keystore), TFM_RELEASE_STORE_PASSWORD=identity['password'],
               TFM_RELEASE_KEY_ALIAS=identity['alias'], TFM_RELEASE_KEY_PASSWORD=identity['password'])
    if not keystore.exists():
        java = Path(env['JAVA_HOME']) / 'bin/keytool' if env.get('JAVA_HOME') else 'keytool'
        subprocess.run([str(java), '-genkeypair', '-keystore', str(keystore), '-storetype', 'JKS', '-alias', identity['alias'],
                        '-storepass:env', 'TFM_RELEASE_STORE_PASSWORD', '-keypass:env', 'TFM_RELEASE_KEY_PASSWORD',
                        '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000', '-dname', 'CN=Termux File Manager, O=TFM', '-noprompt'], env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        keystore.chmod(0o600)
    print('Persistent local release signing identity is ready.')
    if args.publish_secrets:
        # Check authorization before writing anything. A normal GITHUB_TOKEN cannot manage repository secrets.
        subprocess.run(['gh','api',f'repos/{args.repo}/actions/secrets/public-key'], check=True, stdout=subprocess.DEVNULL)
        existing = json.loads(subprocess.check_output(['gh','secret','list','--repo',args.repo,'--json','name'], text=True))
        if any(item['name'] in NAMES for item in existing):
            raise SystemExit('Repository already has signing secrets; refusing to overwrite its signing identity.')
        values = [base64.b64encode(keystore.read_bytes()).decode(), identity['password'], identity['alias'], identity['password']]
        for name,value in zip(NAMES,values):
            subprocess.run(['gh','secret','set',name,'--repo',args.repo], input=value, text=True, check=True)
        print('Signing identity saved to repository Actions Secrets; subsequent CI builds reuse it.')
    if args.build:
        subprocess.run([str(ROOT/'gradlew'), '--no-daemon', '--max-workers=2', 'testReleaseUnitTest', 'lintRelease', 'assembleRelease'], cwd=ROOT, env=env, check=True)
        print('Signed release: app/build/outputs/apk/release/app-release.apk')


if __name__ == '__main__': main()
