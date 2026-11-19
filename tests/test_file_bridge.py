import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zipfile

BRIDGE = Path(__file__).resolve().parents[1] / 'app/src/main/assets/file_bridge.py'
spec = importlib.util.spec_from_file_location('bridge', BRIDGE)
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class FileBridgeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def call(self, action, path=None, **kwargs):
        return bridge.dispatch(action, dict(path=str(path or self.root), **kwargs))

    def test_protocol_and_invalid_input(self):
        self.assertEqual('protocol', self.call('list', version=99)['code'])
        self.assertEqual('unknown_action', self.call('bogus')['code'])
        self.assertEqual('not_found', self.call('stat', self.root / 'gone')['code'])

    def test_arabic_quotes_newlines_and_shell_characters_are_literal(self):
        name = "مشروع ' خاص\n$(touch HACKED).txt"
        self.assertTrue(self.call('create', name=name)['ok'])
        file = self.root / name
        self.assertTrue(file.exists())
        listing = self.call('list')
        self.assertEqual(name, listing['items'][0]['name'])
        self.assertFalse((self.root / 'HACKED').exists())
        self.assertEqual('exists', self.call('create', name=name)['code'])

    def test_paging_sort_hidden_and_directory_priority(self):
        for i in range(237): (self.root / f'{i:03}.txt').write_text('x' * i)
        (self.root / 'folder').mkdir()
        (self.root / '.hidden').write_text('private')
        first = self.call('list', showHidden=False, sort='size', descending=True)
        self.assertEqual('folder', first['items'][0]['name'])
        self.assertEqual(100, len(first['items']))
        self.assertEqual(238, first['total'])
        second = self.call('list', showHidden=False, sort='size', descending=True, offset=first['nextOffset'])
        self.assertFalse(set(i['path'] for i in first['items']) & set(i['path'] for i in second['items']))

    def test_sort_by_extension_and_category_filter(self):
        (self.root / 'sub').mkdir()
        (self.root / 'main.py').write_text('print(1)')
        (self.root / 'readme.md').write_text('# hi')
        (self.root / 'photo.png').write_bytes(b'\x89PNG')
        (self.root / 'pack.zip').write_bytes(b'PK')
        by_ext = self.call('list', sort='ext')
        self.assertEqual('sub', by_ext['items'][0]['name'])
        self.assertEqual('code', self.call('list', filter='code')['items'][0]['category'])
        self.assertEqual(['main.py'], [x['name'] for x in self.call('list', filter='code')['items']])
        self.assertEqual(['readme.md'], [x['name'] for x in self.call('list', filter='docs')['items']])
        self.assertEqual(['photo.png'], [x['name'] for x in self.call('list', filter='media')['items']])
        self.assertEqual(['pack.zip'], [x['name'] for x in self.call('list', filter='archives')['items']])
        self.assertEqual(['sub'], [x['name'] for x in self.call('list', filter='dirs')['items']])

    def test_git_branch_and_disk_stats_detection(self):
        git_dir = self.root / '.git'
        git_dir.mkdir()
        (git_dir / 'HEAD').write_text('ref: refs/heads/overhaul/v1\n')
        sub = self.root / 'src' / 'app'
        sub.mkdir(parents=True)
        res = self.call('list', sub)
        self.assertTrue(res['ok'])
        self.assertEqual('overhaul/v1', res['gitBranch'])
        self.assertGreater(res['disk']['total'], 0)

    def test_search_does_not_follow_symlink_cycles(self):
        folder = self.root / 'nested'; folder.mkdir()
        (folder / 'match.txt').touch()
        (folder / 'loop').symlink_to(self.root, target_is_directory=True)
        result = self.call('search', query='match')
        self.assertEqual(1, result['total'])

    def test_search_hidden_and_case(self):
        (self.root / '.private').mkdir(); (self.root / '.private' / 'MATCH').touch()
        (self.root / 'Match.txt').touch()
        result = self.call('search', query='match', showHidden=False)
        self.assertEqual(['Match.txt'], [x['name'] for x in result['items']])

    def test_grep_content_search_literal_regex_and_skips_binary(self):
        (self.root / 'a.py').write_text('def hello_world():\n    return "HELLO"\n')
        (self.root / 'bin.dat').write_bytes(b'\x00hello\x00')
        (self.root / 'node_modules').mkdir()
        (self.root / 'node_modules' / 'x.js').write_text('hello')
        res = self.call('grep', query='hello')
        self.assertTrue(res['ok'])
        self.assertEqual(2, res['total'])
        self.assertEqual([1, 2], [m['line'] for m in res['matches']])
        case_res = self.call('grep', query='HELLO', caseSensitive=True)
        self.assertEqual(1, case_res['total'])
        self.assertEqual(2, case_res['matches'][0]['line'])
        regex_res = self.call('grep', query=r'def\s+\w+\(\)', regex=True)
        self.assertEqual(1, regex_res['total'])
        self.assertEqual('invalid_regex', self.call('grep', query='[unclosed', regex=True)['code'])

    def test_utf8_atomic_save_keeps_permissions_and_line_endings(self):
        file = self.root / 'code.py'; file.write_bytes('أهلًا\r\n'.encode()); file.chmod(0o751)
        initial = self.call('read', file)
        self.assertEqual('أهلًا\r\n', initial['text'])
        saved = self.call('write', file, text='new\r\n', fingerprint=initial['fingerprint'])
        self.assertTrue(saved['ok']); self.assertEqual(b'new\r\n', file.read_bytes())
        self.assertEqual(0o751, stat.S_IMODE(file.stat().st_mode))
        self.assertNotEqual(initial['fingerprint'], saved['fingerprint'])
        self.assertFalse(list(self.root.glob('.tfm-*')))

    def test_external_edit_does_not_get_overwritten_unless_forced(self):
        file = self.root / 'code'; file.write_text('before')
        initial = self.call('read', file); file.write_text('outside')
        saved = self.call('write', file, text='inside', fingerprint=initial['fingerprint'])
        self.assertEqual('changed', saved['code']); self.assertEqual('outside', file.read_text())
        forced = self.call('write', file, text='forced', fingerprint=initial['fingerprint'], force=True)
        self.assertTrue(forced['ok']); self.assertEqual('forced', file.read_text())

    def test_patch_action_replaces_atomically_and_returns_diff(self):
        file = self.root / 'app.kt'
        file.write_text('val a = 1\nval b = 2\nval c = 1\n')
        initial = self.call('read', file)
        self.assertEqual('patch_ambiguous', self.call('patch', file, oldText='1', newText='9', fingerprint=initial['fingerprint'])['code'])
        self.assertEqual('patch_not_found', self.call('patch', file, oldText='missing', newText='x', fingerprint=initial['fingerprint'])['code'])
        patched = self.call('patch', file, oldText='val b = 2', newText='val b = 42', fingerprint=initial['fingerprint'])
        self.assertTrue(patched['ok'])
        self.assertEqual(1, patched['replacements'])
        self.assertIn('-val b = 2', patched['diff'])
        self.assertIn('+val b = 42', patched['diff'])
        self.assertEqual('val a = 1\nval b = 42\nval c = 1\n', file.read_text())

    def test_read_range_handles_large_files(self):
        file = self.root / 'large.log'
        file.write_text('\n'.join(f'line {i}' for i in range(1, 1001)) + '\n')
        page = self.call('read_range', file, startLine=500, maxLines=50)
        self.assertTrue(page['ok'])
        self.assertEqual(500, page['startLine'])
        self.assertEqual(549, page['endLine'])
        self.assertEqual(1000, page['totalLines'])
        self.assertTrue(page['more'])
        self.assertTrue(page['text'].startswith('line 500\n'))

    def test_read_base64_and_hash_and_disk_usage(self):
        img = self.root / 'pixel.png'
        raw = b'\x89PNG\r\n\x1a\nfake-png-bytes'
        img.write_bytes(raw)
        b64 = self.call('read_base64', img)
        self.assertTrue(b64['ok'])
        self.assertEqual('image/png', b64['mime'])
        self.assertEqual(raw, base64.b64decode(b64['data']))
        self.assertEqual('unsupported_media', self.call('read_base64', self.root / 'x.txt')['code'] if (self.root / 'x.txt').write_text('hi') else '')
        hashes = self.call('hash', img)
        self.assertTrue(hashes['ok'])
        self.assertEqual(hashlib.sha256(raw).hexdigest(), hashes['sha256'])
        self.assertEqual(hashlib.md5(raw).hexdigest(), hashes['md5'])
        du = self.call('disk_usage', self.root)
        self.assertTrue(du['ok'])
        self.assertGreaterEqual(du['totalBytes'], len(raw))
        self.assertEqual(2, du['fileCount'])

    def test_archive_create_list_extract_and_zip_slip_blocked(self):
        src = self.root / 'proj'
        src.mkdir()
        (src / 'hello.txt').write_text('hello archive')
        arc = self.call('archive_create', src, sources=[str(src)], destDir=str(self.root), name='proj.zip', format='zip')
        self.assertTrue(arc['ok'])
        listed = self.call('archive_list', Path(arc['path']))
        self.assertTrue(listed['ok'])
        self.assertGreaterEqual(listed['total'], 1)
        out_dir = self.root / 'extracted'
        ext = self.call('archive_extract', Path(arc['path']), destDir=str(out_dir))
        self.assertTrue(ext['ok'])
        self.assertEqual('hello archive', (out_dir / 'proj' / 'hello.txt').read_text())
        # Zip slip must be rejected
        evil_zip = self.root / 'evil.zip'
        with zipfile.ZipFile(evil_zip, 'w') as zf:
            zf.writestr('../escape.txt', 'pwned')
        self.assertEqual('unsafe_archive', self.call('archive_extract', evil_zip, destDir=str(self.root / 'safe'))['code'])
        self.assertFalse((self.root / 'escape.txt').exists())
        # Tar symlink attack must be rejected
        evil_tar = self.root / 'evil.tar.gz'
        with tarfile.open(evil_tar, 'w:gz') as tf:
            ti = tarfile.TarInfo('badlink')
            ti.type = tarfile.SYMTYPE
            ti.linkname = '/etc/passwd'
            tf.addfile(ti)
        self.assertEqual('unsafe_archive', self.call('archive_extract', evil_tar, destDir=str(self.root / 'safe2'))['code'])

    def test_symlink_creation(self):
        target = self.root / 'orig.txt'
        target.write_text('hi')
        created = self.call('symlink', self.root, name='alias.txt', target=str(target))
        self.assertTrue(created['ok'])
        self.assertTrue((self.root / 'alias.txt').is_symlink())
        self.assertEqual('exists', self.call('symlink', self.root, name='alias.txt', target=str(target))['code'])

    def test_editor_limits_binary_and_invalid_utf8(self):
        file = self.root / 'data'
        for content, error in [(b'\0abc', 'binary'), (b'\xff', 'encoding'), (b'a' * (262144 + 1), 'too_large')]:
            file.write_bytes(content); self.assertEqual(error, self.call('read', file)['code'])
        file.write_bytes(b'a' * 262144); initial = self.call('read', file)
        self.assertTrue(initial['ok'])
        self.assertEqual('too_large', self.call('write', file, text='ب' * 131073, fingerprint=initial['fingerprint'])['code'])

    def test_write_symlink_preserves_link_and_target_mode(self):
        target = self.root / 'target'; target.write_text('old'); target.chmod(0o640)
        link = self.root / 'link'; link.symlink_to(target)
        initial = self.call('read', link)
        self.assertTrue(self.call('write', link, text='new', fingerprint=initial['fingerprint'])['ok'])
        self.assertTrue(link.is_symlink()); self.assertEqual('new', target.read_text())
        self.assertEqual(0o640, stat.S_IMODE(target.stat().st_mode))

    def test_copy_conflict_rename_and_broken_link(self):
        source = self.root / 'source'; source.write_text('new')
        dest = self.root / 'dest'; dest.mkdir(); (dest / 'source').write_text('old')
        self.assertEqual('exists', self.call('copy', source, destDir=str(dest))['code'])
        self.assertTrue(self.call('copy', source, destDir=str(dest), name='renamed')['ok'])
        self.assertEqual('old', (dest / 'source').read_text())
        link = self.root / 'broken'; link.symlink_to('missing')
        self.assertTrue(self.call('copy', link, destDir=str(dest))['ok'])
        self.assertTrue((dest / 'broken').is_symlink())

    def test_copy_into_self_via_symlink_is_rejected(self):
        folder = self.root / 'folder'; folder.mkdir()
        alias = self.root / 'alias'; alias.symlink_to(folder, target_is_directory=True)
        self.assertEqual('recursive', self.call('copy', folder, destDir=str(alias))['code'])

    def test_delete_symlink_does_not_delete_target(self):
        folder = self.root / 'target'; folder.mkdir(); (folder / 'keep').touch()
        link = self.root / 'link'; link.symlink_to(folder, target_is_directory=True)
        self.assertTrue(self.call('delete', link)['ok']); self.assertTrue((folder / 'keep').exists())
        self.assertEqual('protected', self.call('delete', '/')['code'])

    def test_rename_rejects_traversal_and_chmod_validation(self):
        file = self.root / 'file'; file.touch()
        self.assertEqual('invalid_name', self.call('rename', file, name='../escape')['code'])
        for mode in ['888', '-1', '10000', 'abc']:
            self.assertEqual('invalid_mode', self.call('chmod', file, mode=mode)['code'])
        self.assertTrue(self.call('chmod', file, mode='755')['ok'])

    def test_large_folder_is_paginated(self):
        for i in range(10000): (self.root / f'{i:05}').touch()
        result = self.call('list', offset=9900)
        self.assertEqual(10000, result['total']); self.assertFalse(result['more']); self.assertEqual(100, len(result['items']))

    def test_command_line_contract(self):
        result = subprocess.run([sys.executable, str(BRIDGE), 'list'], input=json.dumps({'version': 1, 'path': str(self.root)}), text=True, capture_output=True, check=True)
        payload = json.loads(result.stdout)
        self.assertTrue(payload['ok']); self.assertEqual(1, payload['version']); self.assertEqual('', result.stderr)


if __name__ == '__main__': unittest.main()
