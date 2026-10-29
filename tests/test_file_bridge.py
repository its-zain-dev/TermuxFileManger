import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest

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

    def test_utf8_atomic_save_keeps_permissions_and_line_endings(self):
        file = self.root / 'code.py'; file.write_bytes('أهلًا\r\n'.encode()); file.chmod(0o751)
        initial = self.call('read', file)
        self.assertEqual('أهلًا\r\n', initial['text'])
        saved = self.call('write', file, text='new\r\n', fingerprint=initial['fingerprint'])
        self.assertTrue(saved['ok']); self.assertEqual(b'new\r\n', file.read_bytes())
        self.assertEqual(0o751, stat.S_IMODE(file.stat().st_mode))
        self.assertNotEqual(initial['fingerprint'], saved['fingerprint'])
        self.assertFalse(list(self.root.glob('.tfm-*')))

    def test_external_edit_does_not_get_overwritten(self):
        file = self.root / 'code'; file.write_text('before')
        initial = self.call('read', file); file.write_text('outside')
        saved = self.call('write', file, text='inside', fingerprint=initial['fingerprint'])
        self.assertEqual('changed', saved['code']); self.assertEqual('outside', file.read_text())

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
