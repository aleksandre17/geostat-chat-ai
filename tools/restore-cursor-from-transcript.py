#!/usr/bin/env python3
"""
Rebuild .cursor/ from agent transcripts — optimized pre-delete state.

Includes:
  - 3226f32b dedup / cross-ref optimization (README hubs, owner-no-domain-hardcode, …)
  - 18774ebf owner-requested § No degradation updates

Per file: chronological replay across parent transcripts (subagents excluded), last op wins.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRANSCRIPT_ROOT = Path(
    r"C:\Users\Test-User\.cursor\projects\c-Users-Test-User-CursorProjects-geostat-chat-ai\agent-transcripts"
)
MANIFEST = ROOT / ".cursor/ops/restore-manifest.sha256"

# Owner-requested no-degradation patches (before unauthorized catalog delete in same chat).
NO_DEGRADATION_TRANSCRIPT = "18774ebf-7075-40ed-ba98-d2523a0a15ef.jsonl"
NO_DEGRADATION_MAX_LINE = 15

# Not part of 3226f32b optimization bundle.
SKIP_PREFIXES: tuple[str, ...] = (
    ".cursor/templates/",
    ".cursor/projects/",
)


@dataclass(order=True)
class Op:
    sort_key: tuple[float, int, int]
    transcript: str = field(compare=False)
    line_no: int = field(compare=False)
    name: str = field(compare=False)
    path: str = field(compare=False)
    payload: dict = field(compare=False)


def norm_key(path: str) -> str | None:
    p = path.replace("\\", "/")
    marker = "/geostat-chat-ai/.cursor/"
    i = p.lower().find(marker)
    if i < 0:
        i = p.lower().find(".cursor/")
        if i < 0:
            return None
        rel = p[i:]
    else:
        rel = p[i + len("/geostat-chat-ai/") :]
    if rel.lower().startswith(".cursor/projects/"):
        return None
    for prefix in SKIP_PREFIXES:
        if rel.lower().startswith(prefix):
            return None
    return rel.lower()


def rel_path(path: str) -> Path | None:
    k = norm_key(path)
    return Path(k) if k else None


def is_cursor_repo(path: str) -> bool:
    return norm_key(path) is not None


def git_show(rel: Path) -> str | None:
    try:
        raw = subprocess.check_output(
            ["git", "show", f"HEAD:{rel.as_posix()}"],
            cwd=ROOT,
            stderr=subprocess.DEVNULL,
        )
        return raw.decode("utf-8")
    except (subprocess.CalledProcessError, UnicodeDecodeError):
        return None


def git_tracked(rel: Path) -> bool:
    return (
        subprocess.run(
            ["git", "ls-files", "--error-unmatch", rel.as_posix()],
            cwd=ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        ).returncode
        == 0
    )


def collect_ops(*, include_no_degradation: bool) -> tuple[list[Op], list[Path]]:
    files = sorted(
        (p for p in TRANSCRIPT_ROOT.rglob("*.jsonl") if "subagents" not in p.parts),
        key=lambda p: (p.stat().st_mtime, str(p)),
    )
    ops: list[Op] = []
    for fi, tf in enumerate(files):
        if include_no_degradation:
            if tf.name != NO_DEGRADATION_TRANSCRIPT:
                continue
        else:
            if tf.name == NO_DEGRADATION_TRANSCRIPT:
                continue
        mtime = tf.stat().st_mtime
        for line_no, line in enumerate(tf.read_text(encoding="utf-8").splitlines(), 1):
            if include_no_degradation and line_no > NO_DEGRADATION_MAX_LINE:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            for part in obj.get("message", {}).get("content", []):
                if part.get("type") != "tool_use":
                    continue
                name = part.get("name", "")
                if name not in ("Write", "StrReplace", "Delete"):
                    continue
                path = part.get("input", {}).get("path", "")
                if not path or not is_cursor_repo(path):
                    continue
                ops.append(
                    Op(
                        sort_key=(mtime, fi, line_no),
                        transcript=tf.name,
                        line_no=line_no,
                        name=name,
                        path=path,
                        payload=part.get("input", {}),
                    )
                )
    ops.sort()
    return ops, files


class State:
    def __init__(self) -> None:
        self.content: dict[str, str | None] = {}
        self.rel: dict[str, Path] = {}
        self.last_op: dict[str, Op] = {}

    def ensure_loaded(self, path: str) -> None:
        k = norm_key(path)
        if k is None or k in self.content:
            return
        rel = Path(k)
        self.rel[k] = rel
        self.content[k] = git_show(rel) if git_tracked(rel) else None


def replay(ops: list[Op], st: State | None = None) -> tuple[State, int, int]:
    if st is None:
        st = State()
    applied = 0
    skipped = 0
    for op in ops:
        k = norm_key(op.path)
        if k is None:
            continue
        rel = Path(k)
        st.rel[k] = rel
        st.last_op[k] = op
        if op.name == "Write":
            st.content[k] = op.payload.get("contents", "")
            applied += 1
        elif op.name == "Delete":
            st.content[k] = None
            applied += 1
        elif op.name == "StrReplace":
            st.ensure_loaded(op.path)
            content = st.content.get(k)
            if content is None:
                skipped += 1
                continue
            old = op.payload.get("old_string")
            new = op.payload.get("new_string")
            if not old or old not in content:
                skipped += 1
                continue
            if op.payload.get("replace_all"):
                st.content[k] = content.replace(old, new)
            else:
                st.content[k] = content.replace(old, new, 1)
            applied += 1
    return st, applied, skipped


def fill_git_baselines(st: State) -> int:
    filled = 0
    try:
        listed = subprocess.check_output(
            ["git", "ls-files", ".cursor"],
            cwd=ROOT,
            stderr=subprocess.DEVNULL,
        ).decode("utf-8")
    except subprocess.CalledProcessError:
        return 0
    for rel_str in listed.splitlines():
        if not rel_str.strip():
            continue
        k = rel_str.lower()
        if k in st.content:
            continue
        content = git_show(Path(rel_str))
        if content is None:
            continue
        st.rel[k] = Path(rel_str)
        st.content[k] = content
        filled += 1
    return filled


def apply_to_disk(st: State) -> tuple[int, int]:
    written = 0
    removed = 0
    for k, content in sorted(st.content.items()):
        rel = st.rel.get(k)
        if rel is None:
            continue
        out = ROOT / rel
        if content is None:
            if out.exists():
                if out.is_dir():
                    import shutil

                    shutil.rmtree(out)
                else:
                    out.unlink()
                removed += 1
            continue
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(content, encoding="utf-8", newline="\n")
        written += 1
    return written, removed


def purge_unexpected(st: State) -> int:
    """Remove .cursor files on disk that are not in replay state (except ops manifest dir)."""
    removed = 0
    cursor_root = ROOT / ".cursor"
    if not cursor_root.exists():
        return 0
    expected = {k.lower() for k in st.content if st.content[k] is not None}
    for path in list(cursor_root.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT).as_posix().lower()
        if rel.startswith(".cursor/ops/"):
            continue
        if rel not in expected:
            path.unlink()
            removed += 1
    return removed


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def write_manifest(st: State) -> int:
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    for k in sorted(st.content):
        rel = st.rel.get(k)
        if rel is None:
            continue
        content = st.content[k]
        if content is None:
            lines.append(f"DELETED  {rel.as_posix()}")
        else:
            lines.append(f"{sha256(content)}  {rel.as_posix()}")
    MANIFEST.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(lines)


def verify(st: State) -> list[str]:
    bad: list[str] = []
    for k, expected in st.content.items():
        rel = st.rel.get(k)
        if rel is None:
            continue
        out = ROOT / rel
        if expected is None:
            if out.exists():
                bad.append(f"SHOULD_BE_DELETED {rel.as_posix()}")
            continue
        if not out.exists():
            bad.append(f"MISSING {rel.as_posix()}")
            continue
        if out.read_text(encoding="utf-8") != expected:
            bad.append(f"DIFF {rel.as_posix()}")
    return bad


def main() -> int:
    _, files = collect_ops(include_no_degradation=False)
    base_ops, _ = collect_ops(include_no_degradation=False)
    patch_ops, _ = collect_ops(include_no_degradation=True)
    print(
        f"transcript_files={len(files)} base_ops={len(base_ops)} "
        f"no_degradation_ops={len(patch_ops)} (<=L{NO_DEGRADATION_MAX_LINE})"
    )
    st, applied1, skipped1 = replay(base_ops)
    st, applied2, skipped2 = replay(patch_ops, st)
    applied = applied1 + applied2
    skipped = skipped1 + skipped2
    git_filled = fill_git_baselines(st)
    written, removed = apply_to_disk(st)
    purged = purge_unexpected(st)
    manifest_lines = write_manifest(st)
    bad = verify(st)

    print("\nFinal .cursor files:")
    for k in sorted(st.content):
        if st.content[k] is None:
            continue
        op = st.last_op.get(k)
        src = f"{op.transcript} L{op.line_no}" if op else "?"
        print(f"  {st.rel[k].as_posix()} ({len(st.content[k].splitlines())} lines) <- {src}")

    print(
        f"\napplied={applied} skipped={skipped} git_filled={git_filled} "
        f"written={written} removed={removed} purged={purged}"
    )
    print(f"manifest_lines={manifest_lines} verify_failures={len(bad)}")
    for b in bad[:20]:
        print(f"  {b}")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
