"""TFM file protocol v1. Runs inside Termux; stdin and stdout contain only JSON."""
import base64
import difflib
import hashlib
import json
import os
import re
import shutil
import stat
import sys
import tarfile
import tempfile
import zipfile

VERSION = 1
EDITOR_LIMIT = 256 * 1024
GREP_FILE_LIMIT = 512 * 1024
IMAGE_LIMIT = 4 * 1024 * 1024
HOME = '/data/data/com.termux/files/home'
PREFIX = '/data/data/com.termux/files/usr'

CODE_EXTS = {
    'kt', 'kts', 'java', 'py', 'pyw', 'js', 'jsx', 'mjs', 'cjs', 'ts', 'tsx',
    'c', 'h', 'cpp', 'cc', 'cxx', 'hpp', 'rs', 'go', 'rb', 'php', 'lua',
    'sh', 'bash', 'zsh', 'fish', 'xml', 'html', 'htm', 'css', 'scss', 'less',
    'json', 'jsonc', 'yaml', 'yml', 'toml', 'ini', 'cfg', 'conf', 'properties',
    'sql', 'gradle', 'groovy', 'cmake', 'makefile', 'mk', 'dockerfile',
    'dart', 'swift', 'zig', 'nim', 'pl', 'r', 'scala', 'clj', 'ex', 'exs',
    'vue', 'svelte', 'graphql', 'proto', 'env', 'gitignore', 'editorconfig',
}
DOC_EXTS = {'md', 'markdown', 'txt', 'rst', 'org', 'tex', 'csv', 'tsv', 'log', 'pdf', 'rtf', 'adoc'}
IMAGE_EXTS = {'png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp', 'ico', 'svg'}
AUDIO_EXTS = {'mp3', 'wav', 'flac', 'ogg', 'm4a', 'aac', 'opus'}
VIDEO_EXTS = {'mp4', 'mkv', 'webm', 'avi', 'mov', 'm4v', '3gp'}
ARCHIVE_EXTS = {'zip', 'tar', 'gz', 'tgz', 'bz2', 'tbz2', 'xz', 'txz', '7z', 'rar', 'jar', 'apk', 'aab', 'zst'}
GREP_SKIP_DIRS = {'.git', 'node_modules', '__pycache__', '.gradle', '.idea', '.venv', 'venv', 'target'}

IMAGE_MIMES = {
    'png': 'image/png',
    'jpg': 'image/jpeg',
    'jpeg': 'image/jpeg',
    'webp': 'image/webp',
    'gif': 'image/gif',
    'bmp': 'image/bmp',
}


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


def file_ext(name):
    lower = name.lower()
    if lower in ('makefile', 'dockerfile', 'cmakelists.txt', 'gemfile', 'rakefile'):
        return lower
    dot = lower.rfind('.')
    if dot <= 0 or dot == len(lower) - 1:
        return ''
    return lower[dot + 1:]


def file_category(name, is_dir, is_link):
    if is_link:
        return 'symlink'
    if is_dir:
        return 'dir'
    ext = file_ext(name)
    if ext in CODE_EXTS or name.lower() in ('makefile', 'dockerfile', 'vagrantfile', 'procfile'):
        return 'code'
    if ext in DOC_EXTS:
        return 'doc'
    if ext in IMAGE_EXTS:
        return 'image'
    if ext in AUDIO_EXTS:
        return 'audio'
    if ext in VIDEO_EXTS:
        return 'video'
    if ext in ARCHIVE_EXTS:
        return 'archive'
    return 'other'


def info(path):
    st = os.lstat(path)
    link = stat.S_ISLNK(st.st_mode)
    is_dir = os.path.isdir(path)
    name = os.path.basename(path) or path
    ext = '' if is_dir else file_ext(name)
    return dict(
        name=name,
        path=path,
        kind='symlink' if link else 'dir' if stat.S_ISDIR(st.st_mode) else 'file',
        isDir=is_dir,
        isLink=link,
        size=st.st_size,
        mtime=st.st_mtime,
        mode=format(stat.S_IMODE(st.st_mode), '04o'),
        modeText=stat.filemode(st.st_mode),
        readable=os.access(path, os.R_OK),
        writable=os.access(path, os.W_OK),
        linkTarget=os.readlink(path) if link else '',
        ext=ext,
        category=file_category(name, is_dir, link),
    )


def detect_git_branch(path):
    cur = path
    for _ in range(10):
        git_path = os.path.join(cur, '.git')
        head_file = None
        if os.path.isdir(git_path):
            head_file = os.path.join(git_path, 'HEAD')
        elif os.path.isfile(git_path):
            try:
                with open(git_path, 'r', encoding='utf-8', errors='ignore') as fh:
                    line = fh.readline().strip()
                if line.startswith('gitdir:'):
                    gdir = line[7:].strip()
                    if not os.path.isabs(gdir):
                        gdir = os.path.normpath(os.path.join(cur, gdir))
                    head_file = os.path.join(gdir, 'HEAD')
            except OSError:
                pass
        if head_file and os.path.isfile(head_file):
            try:
                with open(head_file, 'r', encoding='utf-8', errors='ignore') as fh:
                    ref = fh.readline().strip()
                if ref.startswith('ref: refs/heads/'):
                    return ref[len('ref: refs/heads/'):]
                if ref.startswith('ref:'):
                    return ref.split('/')[-1]
                if len(ref) >= 7:
                    return ref[:7]
            except OSError:
                return ''
        parent = os.path.dirname(cur)
        if parent == cur:
            break
        cur = parent
    return ''


def disk_stats(path):
    try:
        st = os.statvfs(path)
        total = st.f_frsize * st.f_blocks
        free = st.f_frsize * st.f_bavail
        used = max(0, total - (st.f_frsize * st.f_bfree))
        return dict(total=total, free=free, used=used)
    except OSError:
        return dict(total=0, free=0, used=0)


def matches_filter(entry, filt):
    if not filt or filt == 'all':
        return True
    cat = entry.get('category', 'other')
    if filt == 'dirs':
        return entry['isDir']
    if filt == 'code':
        return cat == 'code'
    if filt == 'docs':
        return cat == 'doc'
    if filt == 'media':
        return cat in ('image', 'audio', 'video')
    if filt == 'archives':
        return cat == 'archive'
    return True


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


def safe_member_target(dest_real, member_name):
    if not member_name or member_name.startswith('/') or '\0' in member_name:
        raise BridgeError('unsafe_archive', member_name)
    normalized = os.path.normpath(member_name)
    if normalized in ('.', '..') or normalized.startswith('../') or os.path.isabs(normalized):
        raise BridgeError('unsafe_archive', member_name)
    target = os.path.realpath(os.path.join(dest_real, normalized))
    if os.path.commonpath([dest_real, target]) != dest_real:
        raise BridgeError('unsafe_archive', member_name)
    return target


def atomic_write(path, text, fingerprint, force=False):
    raw = text.encode('utf-8')
    if len(raw) > EDITOR_LIMIT:
        raise BridgeError('too_large', path)
    target = os.path.realpath(path)
    _, current_fp = read_text(target)
    if not force and fingerprint != current_fp:
        raise BridgeError('changed', path)
    old_mode = stat.S_IMODE(os.stat(target).st_mode)
    fd, temporary = tempfile.mkstemp(prefix='.tfm-', dir=os.path.dirname(target))
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, old_mode)
        if not force and read_text(target)[1] != current_fp:
            raise BridgeError('changed', path)
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return hashlib.sha256(raw).hexdigest()


def handle(action, req):
    if req.get('version', VERSION) != VERSION:
        raise BridgeError('protocol')
    path = norm(req.get('path'))
    if action == 'probe':
        return dict(path=HOME, python=sys.version.split()[0], capabilities=[
            'list', 'search', 'grep', 'stat', 'read', 'read_range', 'read_base64',
            'write', 'patch', 'create', 'mkdir', 'rename', 'copy', 'move', 'delete',
            'chmod', 'symlink', 'hash', 'disk_usage', 'archive_list', 'archive_create',
            'archive_extract',
        ])
    if action in ('list', 'search'):
        if not os.path.isdir(path):
            raise BridgeError('not_directory', path)
        hidden = req.get('showHidden', True)
        query = str(req.get('query', '')).casefold()
        filt = str(req.get('filter', 'all') or 'all').lower()
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
                entry = info(item)
                if matches_filter(entry, filt):
                    entries.append(entry)
            except OSError:
                errors += 1
        key = req.get('sort', 'name')
        if key not in ('name', 'size', 'mtime', 'ext'):
            key = 'name'
        if key == 'ext':
            entries.sort(key=lambda x: (x['ext'].casefold(), x['name'].casefold()),
                         reverse=bool(req.get('descending', False)))
        else:
            entries.sort(key=lambda x: (x[key].casefold() if key == 'name' else x[key], x['name']),
                         reverse=bool(req.get('descending', False)))
        entries.sort(key=lambda x: not x['isDir'])
        offset = max(0, int(req.get('offset', 0)))
        limit = min(100, max(1, int(req.get('limit', 100))))
        page = entries[offset:offset + limit]
        return dict(
            path=path,
            items=page,
            total=len(entries),
            nextOffset=offset + len(page),
            more=offset + len(page) < len(entries),
            truncated=truncated,
            metadataErrors=errors,
            gitBranch=detect_git_branch(path) if action == 'list' else '',
            disk=disk_stats(path) if action == 'list' else dict(total=0, free=0, used=0),
        )
    if action == 'grep':
        if not os.path.isdir(path):
            raise BridgeError('not_directory', path)
        query = str(req.get('query', ''))
        if not query:
            raise BridgeError('empty_query')
        hidden = bool(req.get('showHidden', False))
        case_sensitive = bool(req.get('caseSensitive', False))
        use_regex = bool(req.get('regex', False))
        max_matches = min(500, max(1, int(req.get('maxMatches', 200))))
        flags = 0 if case_sensitive else re.IGNORECASE
        try:
            pattern = re.compile(query if use_regex else re.escape(query), flags)
        except re.error:
            raise BridgeError('invalid_regex', path)
        matches = []
        files_searched = 0
        truncated = False
        for base, dirs, names in os.walk(path, followlinks=False):
            dirs[:] = sorted(
                d for d in dirs
                if (hidden or not d.startswith('.')) and d not in GREP_SKIP_DIRS
            )
            for name in sorted(names):
                if not hidden and name.startswith('.'):
                    continue
                full = os.path.join(base, name)
                try:
                    st = os.lstat(full)
                    if stat.S_ISLNK(st.st_mode) or not stat.S_ISREG(st.st_mode):
                        continue
                    if st.st_size == 0 or st.st_size > GREP_FILE_LIMIT:
                        continue
                    with open(full, 'rb') as fh:
                        raw = fh.read()
                    if b'\0' in raw:
                        continue
                    text = raw.decode('utf-8')
                except (OSError, UnicodeDecodeError):
                    continue
                files_searched += 1
                for line_no, line in enumerate(text.splitlines(), 1):
                    m = pattern.search(line)
                    if m:
                        matches.append(dict(
                            path=full,
                            name=name,
                            line=line_no,
                            column=m.start() + 1,
                            preview=line.strip()[:240],
                        ))
                        if len(matches) >= max_matches:
                            truncated = True
                            break
                if truncated:
                    break
            if truncated:
                break
        return dict(path=path, matches=matches, total=len(matches),
                    filesSearched=files_searched, truncated=truncated)
    if action == 'stat':
        return dict(item=info(path))
    if action == 'read':
        text, fingerprint = read_text(path)
        return dict(path=path, text=text, fingerprint=fingerprint)
    if action == 'read_range':
        if not os.path.isfile(path):
            raise BridgeError('not_file', path)
        st = os.stat(path)
        start_line = max(1, int(req.get('startLine', 1)))
        max_lines = min(500, max(1, int(req.get('maxLines', 200))))
        with open(path, 'rb') as probe_fh:
            sample = probe_fh.read(8192)
        if b'\0' in sample:
            raise BridgeError('binary', path)
        collected = []
        total_lines = 0
        try:
            with open(path, 'r', encoding='utf-8', errors='strict') as fh:
                for idx, line in enumerate(fh, 1):
                    total_lines = idx
                    if start_line <= idx < start_line + max_lines:
                        collected.append(line.rstrip('\r\n'))
        except UnicodeDecodeError:
            raise BridgeError('encoding', path)
        end_line = start_line + len(collected) - 1 if collected else start_line - 1
        return dict(
            path=path,
            text='\n'.join(collected),
            startLine=start_line,
            endLine=max(0, end_line),
            totalLines=total_lines,
            size=st.st_size,
            more=end_line < total_lines,
        )
    if action == 'read_base64':
        if not os.path.isfile(path):
            raise BridgeError('not_file', path)
        ext = file_ext(os.path.basename(path))
        if ext not in IMAGE_MIMES:
            raise BridgeError('unsupported_media', path)
        st = os.stat(path)
        if st.st_size > IMAGE_LIMIT:
            raise BridgeError('too_large', path)
        with open(path, 'rb') as fh:
            raw = fh.read(IMAGE_LIMIT + 1)
        if len(raw) > IMAGE_LIMIT:
            raise BridgeError('too_large', path)
        return dict(
            path=path,
            mime=IMAGE_MIMES[ext],
            size=len(raw),
            data=base64.b64encode(raw).decode('ascii'),
        )
    if action == 'write':
        text = req.get('text')
        if not isinstance(text, str):
            raise BridgeError('invalid_text', path)
        force = bool(req.get('force', False))
        new_fp = atomic_write(path, text, req.get('fingerprint'), force=force)
        return dict(path=path, fingerprint=new_fp)
    if action == 'patch':
        old_text = req.get('oldText')
        new_text = req.get('newText')
        if not isinstance(old_text, str) or not isinstance(new_text, str) or not old_text:
            raise BridgeError('invalid_patch', path)
        replace_all = bool(req.get('replaceAll', False))
        original, current_fp = read_text(path)
        if req.get('fingerprint') != current_fp:
            raise BridgeError('changed', path)
        occurrences = original.count(old_text)
        if occurrences == 0:
            raise BridgeError('patch_not_found', path)
        if occurrences > 1 and not replace_all:
            raise BridgeError('patch_ambiguous', path)
        updated = original.replace(old_text, new_text) if replace_all else original.replace(old_text, new_text, 1)
        new_fp = atomic_write(path, updated, current_fp, force=False)
        diff_lines = list(difflib.unified_diff(
            original.splitlines(),
            updated.splitlines(),
            fromfile='a/' + os.path.basename(path),
            tofile='b/' + os.path.basename(path),
            lineterm='',
            n=2,
        ))
        return dict(
            path=path,
            fingerprint=new_fp,
            replacements=occurrences if replace_all else 1,
            diff='\n'.join(diff_lines[:200]),
        )
    if action in ('create', 'mkdir'):
        path = child_path(path, req.get('name'))
        if action == 'mkdir':
            os.mkdir(path)
        else:
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o644)
            os.close(fd)
        return dict(path=path)
    if action == 'symlink':
        target = str(req.get('target', '')).strip()
        if not target or '\0' in target:
            raise BridgeError('invalid_name', path)
        link_path = child_path(path, req.get('name'))
        no_conflict(link_path)
        os.symlink(target, link_path)
        return dict(path=link_path, target=target)
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
    if action == 'hash':
        if not os.path.isfile(path):
            raise BridgeError('not_file', path)
        md5, sha1, sha256 = hashlib.md5(), hashlib.sha1(), hashlib.sha256()
        total_size = 0
        with open(path, 'rb') as fh:
            while True:
                chunk = fh.read(65536)
                if not chunk:
                    break
                total_size += len(chunk)
                md5.update(chunk)
                sha1.update(chunk)
                sha256.update(chunk)
        return dict(
            path=path,
            size=total_size,
            md5=md5.hexdigest(),
            sha1=sha1.hexdigest(),
            sha256=sha256.hexdigest(),
        )
    if action == 'disk_usage':
        if not os.path.lexists(path):
            raise BridgeError('not_found', path)
        max_items = 50000
        seen = 0
        file_count = 0
        dir_count = 0
        link_count = 0
        total_bytes = 0
        truncated = False
        child_sizes = []
        if os.path.islink(path) or not os.path.isdir(path):
            st = os.lstat(path)
            return dict(
                path=path,
                totalBytes=st.st_size,
                fileCount=0 if os.path.islink(path) else 1,
                dirCount=0,
                linkCount=1 if os.path.islink(path) else 0,
                truncated=False,
                largest=[],
            )
        for entry in os.scandir(path):
            if seen >= max_items:
                truncated = True
                break
            entry_bytes = 0
            try:
                st = entry.stat(follow_symlinks=False)
                if stat.S_ISLNK(st.st_mode):
                    link_count += 1
                    seen += 1
                    entry_bytes = st.st_size
                elif stat.S_ISDIR(st.st_mode):
                    dir_count += 1
                    seen += 1
                    for base, dirs, names in os.walk(entry.path, followlinks=False):
                        for d in dirs:
                            seen += 1
                            dir_count += 1
                            if seen >= max_items:
                                truncated = True
                                break
                        for n in names:
                            seen += 1
                            full = os.path.join(base, n)
                            try:
                                fst = os.lstat(full)
                                if stat.S_ISLNK(fst.st_mode):
                                    link_count += 1
                                else:
                                    file_count += 1
                                entry_bytes += fst.st_size
                            except OSError:
                                pass
                            if seen >= max_items:
                                truncated = True
                                break
                        if truncated:
                            break
                else:
                    file_count += 1
                    seen += 1
                    entry_bytes = st.st_size
                total_bytes += entry_bytes
                child_sizes.append(dict(
                    name=entry.name,
                    path=entry.path,
                    size=entry_bytes,
                    isDir=stat.S_ISDIR(st.st_mode),
                ))
            except OSError:
                pass
        child_sizes.sort(key=lambda item: item['size'], reverse=True)
        return dict(
            path=path,
            totalBytes=total_bytes,
            fileCount=file_count,
            dirCount=dir_count,
            linkCount=link_count,
            truncated=truncated,
            largest=child_sizes[:15],
        )
    if action == 'archive_list':
        if not os.path.isfile(path):
            raise BridgeError('not_file', path)
        entries = []
        total_size = 0
        total_count = 0
        truncated = False
        if zipfile.is_zipfile(path):
            with zipfile.ZipFile(path, 'r') as zf:
                infos = zf.infolist()
                total_count = len(infos)
                for zi in infos:
                    total_size += zi.file_size
                    if len(entries) < 500:
                        entries.append(dict(name=zi.filename, size=zi.file_size, isDir=zi.is_dir()))
                    else:
                        truncated = True
        elif tarfile.is_tarfile(path):
            with tarfile.open(path, 'r:*') as tf:
                for member in tf:
                    total_count += 1
                    total_size += member.size
                    if len(entries) < 500:
                        entries.append(dict(name=member.name, size=member.size, isDir=member.isdir()))
                    else:
                        truncated = True
        else:
            raise BridgeError('unsupported_archive', path)
        return dict(path=path, entries=entries, total=total_count, totalSize=total_size, truncated=truncated)
    if action == 'archive_create':
        sources = req.get('sources')
        if not isinstance(sources, list) or not sources:
            sources = [path]
        norm_sources = [norm(s) for s in sources]
        for s in norm_sources:
            if not os.path.lexists(s):
                raise BridgeError('not_found', s)
        dest_dir = norm(req.get('destDir') or os.path.dirname(norm_sources[0]))
        if not os.path.isdir(dest_dir):
            raise BridgeError('not_directory', dest_dir)
        fmt = str(req.get('format', 'zip')).lower()
        if fmt not in ('zip', 'tar.gz'):
            raise BridgeError('unsupported_archive', dest_dir)
        archive_name = req.get('name') or (os.path.basename(norm_sources[0]) + ('.zip' if fmt == 'zip' else '.tar.gz'))
        dest = child_path(dest_dir, archive_name)
        no_conflict(dest)
        fd, staging = tempfile.mkstemp(prefix='.tfm-arc-', dir=dest_dir)
        os.close(fd)
        try:
            if fmt == 'zip':
                with zipfile.ZipFile(staging, 'w', compression=zipfile.ZIP_DEFLATED) as zf:
                    for src in norm_sources:
                        base_name = os.path.basename(src)
                        if os.path.isdir(src) and not os.path.islink(src):
                            for root, dirs, files in os.walk(src, followlinks=False):
                                rel_root = os.path.relpath(root, os.path.dirname(src))
                                if not dirs and not files:
                                    zf.writestr(rel_root.rstrip('/') + '/', '')
                                for f in files:
                                    full = os.path.join(root, f)
                                    if os.path.islink(full):
                                        continue
                                    zf.write(full, os.path.relpath(full, os.path.dirname(src)))
                        elif not os.path.islink(src):
                            zf.write(src, base_name)
            else:
                with tarfile.open(staging, 'w:gz') as tf:
                    for src in norm_sources:
                        tf.add(src, arcname=os.path.basename(src))
            no_conflict(dest)
            os.replace(staging, dest)
        finally:
            if os.path.exists(staging):
                os.unlink(staging)
        return dict(path=dest, size=os.path.getsize(dest))
    if action == 'archive_extract':
        if not os.path.isfile(path):
            raise BridgeError('not_file', path)
        dest_dir = norm(req.get('destDir') or os.path.dirname(path))
        os.makedirs(dest_dir, exist_ok=True)
        dest_real = os.path.realpath(dest_dir)
        overwrite = bool(req.get('overwrite', False))
        extracted = 0
        if zipfile.is_zipfile(path):
            with zipfile.ZipFile(path, 'r') as zf:
                infos = zf.infolist()
                for zi in infos:
                    if zi.filename.endswith('/'):
                        safe_member_target(dest_real, zi.filename.rstrip('/') or '.') if zi.filename.rstrip('/') else None
                        continue
                    target = safe_member_target(dest_real, zi.filename)
                    if not overwrite and os.path.lexists(target):
                        raise BridgeError('exists', target)
                for zi in infos:
                    if zi.is_dir():
                        clean = zi.filename.rstrip('/')
                        if clean:
                            os.makedirs(safe_member_target(dest_real, clean), exist_ok=True)
                        continue
                    target = safe_member_target(dest_real, zi.filename)
                    os.makedirs(os.path.dirname(target), exist_ok=True)
                    with zf.open(zi, 'r') as src_fh, open(target, 'wb') as out_fh:
                        shutil.copyfileobj(src_fh, out_fh)
                    extracted += 1
        elif tarfile.is_tarfile(path):
            with tarfile.open(path, 'r:*') as tf:
                members = tf.getmembers()
                for m in members:
                    if m.issym() or m.islnk() or m.isdev():
                        raise BridgeError('unsafe_archive', m.name)
                    if not m.name or m.name in ('.', './'):
                        continue
                    target = safe_member_target(dest_real, m.name)
                    if not m.isdir() and not overwrite and os.path.lexists(target):
                        raise BridgeError('exists', target)
                for m in members:
                    if not m.name or m.name in ('.', './'):
                        continue
                    target = safe_member_target(dest_real, m.name)
                    if m.isdir():
                        os.makedirs(target, exist_ok=True)
                    elif m.isfile():
                        os.makedirs(os.path.dirname(target), exist_ok=True)
                        src_fh = tf.extractfile(m)
                        if src_fh is not None:
                            with src_fh, open(target, 'wb') as out_fh:
                                shutil.copyfileobj(src_fh, out_fh)
                            extracted += 1
        else:
            raise BridgeError('unsupported_archive', path)
        return dict(path=dest_dir, extracted=extracted)
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
    except (ValueError, TypeError, KeyError, zipfile.BadZipFile, tarfile.TarError):
        return dict(ok=False, version=VERSION, code='invalid_request')


if __name__ == '__main__':
    try:
        request = json.loads(sys.stdin.read() or '{}')
        result = dispatch(sys.argv[1], request)
    except (ValueError, IndexError):
        result = dict(ok=False, version=VERSION, code='invalid_request')
    print(json.dumps(result, ensure_ascii=True, separators=(',', ':')))
