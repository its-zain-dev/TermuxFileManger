"""TFM file protocol v1. Runs inside Termux; stdin and stdout contain only JSON."""
import hashlib
import json
import os
import shutil
import stat
import sys
import tempfile

VERSION = 1
EDITOR_LIMIT = 256 * 1024
HOME = '/data/data/com.termux/files/home'
PREFIX = '/data/data/com.termux/files/usr'


class BridgeError(Exception):
    def __init__(self, code, path=''):
        self.code, self.path = code, path
        super().__init__(code)


def norm(path):
    path = str(path or HOME)
    for token, actual in (('${PREFIX}', PREFIX), ('$PREFIX', PREFIX),
                          ('${HOME}', HOME), ('$HOME', HOME), ('~', HOME)):
        if path == token or path.startswith(token + '/'):
            path = actual + path[len(token):]
            break
    return os.path.abspath(path)


def info(path):
    st = os.lstat(path)
    link = stat.S_ISLNK(st.st_mode)
    return dict(name=os.path.basename(path) or path, path=path,
                kind='symlink' if link else 'dir' if stat.S_ISDIR(st.st_mode) else 'file',
                isDir=os.path.isdir(path), isLink=link,
                size=st.st_size, mtime=st.st_mtime, mode=format(stat.S_IMODE(st.st_mode), '04o'),
                modeText=stat.filemode(st.st_mode), readable=os.access(path, os.R_OK),
                writable=os.access(path, os.W_OK), linkTarget=os.readlink(path) if link else '')


def read_text(path):
    if not os.path.isfile(path):
        raise BridgeError('not_file', path)
    with open(path, 'rb') as stream:
        raw = stream.read(EDITOR_LIMIT + 1)
    if len(raw) > EDITOR_LIMIT:
        raise BridgeError('too_large', path)
    if b'\0' in raw:
        raise BridgeError('binary', path)
    try:
        text = raw.decode('utf-8')
    except UnicodeDecodeError:
        raise BridgeError('encoding', path)
    return text, hashlib.sha256(raw).hexdigest()


def child_path(parent, name):
    if not isinstance(name, str) or not name or name in ('.', '..') or '/' in name or '\0' in name:
        raise BridgeError('invalid_name', parent)
    return os.path.join(parent, name)


def no_conflict(path):
    if os.path.lexists(path):
        raise BridgeError('exists', path)


def handle(action, req):
    if req.get('version', VERSION) != VERSION:
        raise BridgeError('protocol')
    path = norm(req.get('path'))
    if action == 'probe':
        return dict(path=HOME, python=sys.version.split()[0])
    if action in ('list', 'search'):
        if not os.path.isdir(path):
            raise BridgeError('not_directory', path)
        hidden = req.get('showHidden', True)
        query = str(req.get('query', '')).casefold()
        if action == 'search' and not query:
            raise BridgeError('empty_query')
        candidates, errors = [], 0
        truncated = False
        if action == 'list':
            candidates = [entry.path for entry in os.scandir(path)
                          if hidden or not entry.name.startswith('.')]
        else:
            # A deterministic capped search avoids unbounded memory and symlink cycles.
            for base, dirs, names in os.walk(path, followlinks=False):
                dirs[:] = sorted(d for d in dirs if hidden or not d.startswith('.'))
                for name in sorted(dirs + names):
                    if (hidden or not name.startswith('.')) and query in name.casefold():
                        candidates.append(os.path.join(base, name))
                        if len(candidates) >= 5000:
                            truncated = True
                            break
                if truncated:
                    break
        entries = []
        for item in candidates:
            try:
                entries.append(info(item))
            except OSError:
                errors += 1
        key = req.get('sort', 'name')
        if key not in ('name', 'size', 'mtime'):
            key = 'name'
        entries.sort(key=lambda x: (x[key].casefold() if key == 'name' else x[key], x['name']),
                     reverse=bool(req.get('descending', False)))
        entries.sort(key=lambda x: not x['isDir'])
        offset = max(0, int(req.get('offset', 0)))
        limit = min(100, max(1, int(req.get('limit', 100))))
        page = entries[offset:offset + limit]
        return dict(path=path, items=page, total=len(entries), nextOffset=offset + len(page),
                    more=offset + len(page) < len(entries), truncated=truncated, metadataErrors=errors)
    if action == 'stat':
        return dict(item=info(path))
    if action == 'read':
        text, fingerprint = read_text(path)
        return dict(path=path, text=text, fingerprint=fingerprint)
    if action == 'write':
        text = req.get('text')
        if not isinstance(text, str):
            raise BridgeError('invalid_text', path)
        raw = text.encode('utf-8')
        if len(raw) > EDITOR_LIMIT:
            raise BridgeError('too_large', path)
        target = os.path.realpath(path)
        _, fingerprint = read_text(target)
        if req.get('fingerprint') != fingerprint:
            raise BridgeError('changed', path)
        old_mode = stat.S_IMODE(os.stat(target).st_mode)
        fd, temporary = tempfile.mkstemp(prefix='.tfm-', dir=os.path.dirname(target))
        try:
            with os.fdopen(fd, 'wb') as stream:
                stream.write(raw)
                stream.flush()
                os.fsync(stream.fileno())
            os.chmod(temporary, old_mode)
            if read_text(target)[1] != fingerprint:
                raise BridgeError('changed', path)
            os.replace(temporary, target)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)
        return dict(path=path, fingerprint=hashlib.sha256(raw).hexdigest())
    if action in ('create', 'mkdir'):
        path = child_path(path, req.get('name'))
        if action == 'mkdir':
            os.mkdir(path)
        else:
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o644)
            os.close(fd)
        return dict(path=path)
    if action == 'rename':
        dest = child_path(os.path.dirname(path), req.get('name'))
        no_conflict(dest)
        os.rename(path, dest)
        return dict(path=dest)
    if action in ('copy', 'move'):
        if not os.path.lexists(path):
            raise BridgeError('not_found', path)
        dest_dir = norm(req.get('destDir'))
        if not os.path.isdir(dest_dir):
            raise BridgeError('not_directory', dest_dir)
        dest = child_path(dest_dir, req.get('name') or os.path.basename(path))
        no_conflict(dest)
        if os.path.isdir(path) and not os.path.islink(path):
            source_real, dest_real = os.path.realpath(path), os.path.realpath(dest)
            if os.path.commonpath([source_real, dest_real]) == source_real:
                raise BridgeError('recursive', path)
        if action == 'move':
            shutil.move(path, dest)
        else:
            # Stage the copy so a failed copy never appears as a complete destination.
            staging = tempfile.mkdtemp(prefix='.tfm-copy-', dir=dest_dir)
            staged = os.path.join(staging, 'item')
            try:
                if os.path.islink(path):
                    os.symlink(os.readlink(path), staged)
                elif os.path.isdir(path):
                    shutil.copytree(path, staged, symlinks=True)
                else:
                    shutil.copy2(path, staged)
                no_conflict(dest)
                os.rename(staged, dest)
            finally:
                shutil.rmtree(staging, ignore_errors=True)
        return dict(path=dest)
    if action == 'delete':
        if path in ('/', HOME, PREFIX):
            raise BridgeError('protected', path)
        if os.path.islink(path) or not os.path.isdir(path):
            os.unlink(path)
        else:
            shutil.rmtree(path)
        return dict(path=path)
    if action == 'chmod':
        mode = str(req.get('mode', ''))
        if not mode or any(char not in '01234567' for char in mode) or len(mode) > 4:
            raise BridgeError('invalid_mode', path)
        os.chmod(path, int(mode, 8))
        return dict(path=path)
    raise BridgeError('unknown_action')


def dispatch(action, request):
    try:
        result = handle(action, request)
        return dict(result, ok=True, version=VERSION)
    except BridgeError as error:
        return dict(ok=False, version=VERSION, code=error.code, path=error.path)
    except OSError as error:
        code = {2: 'not_found', 13: 'permission', 17: 'exists', 28: 'no_space', 30: 'read_only'}.get(error.errno, 'io')
        return dict(ok=False, version=VERSION, code=code, path=str(error.filename or ''))
    except (ValueError, TypeError, KeyError):
        return dict(ok=False, version=VERSION, code='invalid_request')


if __name__ == '__main__':
    try:
        request = json.loads(sys.stdin.read() or '{}')
        result = dispatch(sys.argv[1], request)
    except (ValueError, IndexError):
        result = dict(ok=False, version=VERSION, code='invalid_request')
    print(json.dumps(result, ensure_ascii=True, separators=(',', ':')))
