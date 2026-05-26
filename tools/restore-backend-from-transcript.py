#!/usr/bin/env python3
"""
Rebuild apps/backend from agent transcripts — pre-unauthorized-delete state.

Per file: chronological replay across all parent transcripts (subagents excluded),
last op wins. Restoration sessions after the catalog delete (18774ebf) are excluded.
Tracked files never touched in transcripts are filled from git (unchanged on disk pre-delete).
"""
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRANSCRIPT_ROOT = Path(
    r"C:\Users\Test-User\.cursor\projects\c-Users-Test-User-CursorProjects-geostat-chat-ai\agent-transcripts"
)
MANIFEST = ROOT / "apps/backend/ops/restore-manifest.sha256"

# Sessions that ran AFTER unauthorized catalog delete — must not override pre-delete state.
EXCLUDE_TRANSCRIPTS: frozenset[str] = frozenset(
    {
        "18774ebf-7075-40ed-ba98-d2523a0a15ef.jsonl",
    }
)

JDBC_SKIP_OLD_PREFIX = "    @Override\n    public List<DerivedCatalogLink> findPortalLinks"

PHASE8_WORKING_TREE_DELETED: tuple[str, ...] = (
    "apps/backend/src/main/resources/catalog/topics.yaml",
    "apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/YamlTopicCatalog.java",
    "apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/TopicCatalogLoader.java",
    "apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/SpecificLinkLoader.java",
    "apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/NewsCategoryLoader.java",
    "apps/backend/src/main/java/com/geostat/chat/application/chat/ResponseBuilder.java",
    "apps/backend/src/main/java/com/geostat/chat/domain/catalog/TopicStyleCatalog.java",
)


@dataclass(order=True)
class Op:
    sort_key: tuple[float, int, int]  # mtime, file_index, line_no
    transcript: str = field(compare=False)
    line_no: int = field(compare=False)
    name: str = field(compare=False)
    path: str = field(compare=False)
    payload: dict = field(compare=False)


def norm_key(path: str) -> str:
    p = path.replace("\\", "/")
    i = p.lower().find("apps/backend/")
    return p[i:].lower() if i >= 0 else p.lower()


def rel_path(path: str) -> Path:
    p = path.replace("\\", "/")
    i = p.lower().find("apps/backend/")
    if i >= 0:
        return Path(p[i:])
    parts = Path(path).parts
    if "apps" in parts:
        return Path(*parts[parts.index("apps") :])
    return Path(path)


def is_backend(path: str) -> bool:
    return "apps/backend/" in path.replace("\\", "/").lower()


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


def collect_ops() -> tuple[list[Op], list[Path]]:
    files = sorted(
        (
            p
            for p in TRANSCRIPT_ROOT.rglob("*.jsonl")
            if "subagents" not in p.parts and p.name not in EXCLUDE_TRANSCRIPTS
        ),
        key=lambda p: (p.stat().st_mtime, str(p)),
    )
    ops: list[Op] = []
    for fi, tf in enumerate(files):
        mtime = tf.stat().st_mtime
        for line_no, line in enumerate(tf.read_text(encoding="utf-8").splitlines(), 1):
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
                inp = part.get("input", {})
                path = inp.get("path", "")
                if not path or not is_backend(path):
                    continue
                ops.append(
                    Op(
                        sort_key=(mtime, fi, line_no),
                        transcript=tf.name,
                        line_no=line_no,
                        name=name,
                        path=path,
                        payload=inp,
                    )
                )
    ops.sort()
    return ops, files


class State:
    def __init__(self) -> None:
        self.content: dict[str, str | None] = {}
        self.rel: dict[str, Path] = {}
        self.last_op: dict[str, Op] = {}

    def set_path(self, path: str) -> None:
        self.rel[norm_key(path)] = rel_path(path)

    def ensure_loaded(self, path: str) -> None:
        k = norm_key(path)
        if k in self.content:
            return
        rel = rel_path(path)
        self.rel[k] = rel
        self.content[k] = git_show(rel) if git_tracked(rel) else None

    def force_deleted(self, rel: str) -> None:
        k = rel.lower()
        self.rel[k] = Path(rel)
        self.content[k] = None


def replay(ops: list[Op]) -> tuple[State, int, int]:
    st = State()
    applied = 0
    skipped = 0
    for op in ops:
        k = norm_key(op.path)
        st.set_path(op.path)
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
            if k.endswith("/jdbcderivedcatalogreader.java") and old.startswith(JDBC_SKIP_OLD_PREFIX):
                skipped += 1
                continue
            if op.payload.get("replace_all"):
                candidate = content.replace(old, new)
            else:
                candidate = content.replace(old, new, 1)
            if k.endswith("/jdbcderivedcatalogreader.java"):
                lines = candidate.splitlines()
                if not lines or not lines[0].startswith("package "):
                    skipped += 1
                    continue
                if candidate.count("public class JdbcDerivedCatalogReader") != 1:
                    skipped += 1
                    continue
            st.content[k] = candidate
            applied += 1

    for rel in PHASE8_WORKING_TREE_DELETED:
        st.force_deleted(rel)
    for k in list(st.content):
        if "/chatbot/" in k:
            rel = st.rel.get(k)
            if rel is not None:
                st.force_deleted(rel.as_posix())
    return st, applied, skipped


def fill_git_baselines(st: State) -> int:
    """Restore tracked backend files that transcripts never modified (pre-delete disk copy)."""
    filled = 0
    try:
        listed = subprocess.check_output(
            ["git", "ls-files", "apps/backend"],
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
        if rel_str in PHASE8_WORKING_TREE_DELETED:
            continue
        rel = Path(rel_str)
        if rel.suffix.lower() in {".jar", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".woff", ".woff2"}:
            continue
        content = git_show(rel)
        if content is None:
            continue
        st.rel[k] = Path(rel_str)
        st.content[k] = content
        filled += 1
    return filled


def last_write_from_end(ops: list[Op], suffix: str) -> Op | None:
    suffix = suffix.lower()
    for op in reversed(ops):
        if op.name == "Write" and op.path.replace("\\", "/").lower().endswith(suffix):
            return op
    return None


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
                out.unlink()
                removed += 1
            continue
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(content, encoding="utf-8", newline="\n")
        written += 1
    return written, removed


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


def purge_orphans() -> None:
    for p in [
        ROOT / "apps/backend/src/main/java/Chatbot",
        ROOT / "apps/backend/src/test/java/Chatbot",
        ROOT / "apps/backend/src/main/java/chatbot",
        ROOT / "apps/backend/src/test/java/chatbot",
    ]:
        if p.exists():
            shutil.rmtree(p)
    for base in (ROOT / "apps/backend/src/main/java", ROOT / "apps/backend/src/test/java"):
        if not base.exists():
            continue
        for p in list(base.rglob("*.java")):
            if p.name == p.name.lower() and any(c.isalpha() for c in p.stem):
                p.unlink()


def main() -> int:
    ops, files = collect_ops()
    print(f"transcript_files={len(files)} (parent only, pre-delete, chronological) backend_ops={len(ops)}")
    if EXCLUDE_TRANSCRIPTS:
        print(f"excluded_transcripts={sorted(EXCLUDE_TRANSCRIPTS)}")
    for f in files:
        print(f"  {f.parent.name}/{f.name} mtime={f.stat().st_mtime:.0f}")
    st, applied, skipped = replay(ops)
    git_filled = fill_git_baselines(st)
    purge_orphans()
    written, removed = apply_to_disk(st)
    manifest_lines = write_manifest(st)
    bad = verify(st)

    keys = [
        "YamlCatalogLinkBuilder.java",
        "ChatService.java",
        "JdbcDerivedCatalogReader.java",
    ]
    print("\nLast Write (newest transcript scan from end):")
    for suffix in keys:
        lw = last_write_from_end(ops, suffix)
        if lw:
            lines = lw.payload.get("contents", "").count("\n") + 1
            print(f"  {suffix}: {lw.transcript} L{lw.line_no} ({lines} lines)")
        else:
            print(f"  {suffix}: (no Write — built from StrReplace chain)")

    print("\nFinal state last op source:")
    for suffix in keys:
        needle = f"/{suffix.lower()}"
        for kk, op in st.last_op.items():
            if not kk.endswith(needle):
                continue
            c = st.content.get(kk)
            n = len(c.splitlines()) if c else 0
            print(f"  {suffix}: {n} lines — last {op.name} in {op.transcript} L{op.line_no}")
            break

    print(f"\napplied={applied} skipped={skipped} git_filled={git_filled} written={written} removed={removed}")
    print(f"manifest_lines={manifest_lines} verify_failures={len(bad)}")
    for b in bad[:15]:
        print(f"  {b}")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
