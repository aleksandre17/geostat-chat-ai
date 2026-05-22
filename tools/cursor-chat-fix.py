"""Repair migrated Cursor composer after geostat-chat-bot -> geostat-chat-ai rename.

Run with Cursor fully closed (see restore-cursor-chat.ps1).
"""
from __future__ import annotations

import json
import shutil
import sqlite3
import sys
from datetime import datetime
from pathlib import Path

GLOBAL_DB = Path.home() / "AppData/Roaming/Cursor/User/globalStorage/state.vscdb"
OLD_WS_ID = "6631d101571969ef2f19ed19051d3769"
NEW_WS_ID = "f45c2a85e8c197de3d183d2b27ba565d"
OLD_ROOT = r"c:\Users\Test-User\CursorProjects\geostat-chat-bot"
NEW_ROOT = r"c:\Users\Test-User\CursorProjects\geostat-chat-ai"
MAIN = "f9d56514-11c3-4910-90d5-4bc1d507662e"

NEW_WS = {
    "id": NEW_WS_ID,
    "uri": {
        "$mid": 1,
        "fsPath": NEW_ROOT,
        "_sep": 1,
        "external": "file:///c%3A/Users/Test-User/CursorProjects/geostat-chat-ai",
        "path": "/c:/Users/Test-User/CursorProjects/geostat-chat-ai",
        "scheme": "file",
    },
}


def repl(obj):
    if isinstance(obj, str):
        return (
            obj.replace(OLD_ROOT, NEW_ROOT)
            .replace(OLD_ROOT.replace("\\", "/"), NEW_ROOT.replace("\\", "/"))
            .replace(OLD_WS_ID, NEW_WS_ID)
        )
    if isinstance(obj, list):
        return [repl(x) for x in obj]
    if isinstance(obj, dict):
        return {k: repl(v) for k, v in obj.items()}
    return obj


def main() -> int:
    if not GLOBAL_DB.exists():
        print(f"Missing {GLOBAL_DB}", file=sys.stderr)
        return 1

    backup = GLOBAL_DB.with_name(
        GLOBAL_DB.name + ".backup-" + datetime.now().strftime("%Y%m%d-%H%M%S")
    )
    shutil.copy2(GLOBAL_DB, backup)
    print(f"Backup: {backup}")

    conn = sqlite3.connect(GLOBAL_DB)
    cur = conn.cursor()

    cur.execute("SELECT value FROM ItemTable WHERE key='composer.composerHeaders'")
    row = cur.fetchone()
    if not row:
        print("composer.composerHeaders missing", file=sys.stderr)
        return 1

    data = json.loads(row[0])
    changed_headers: list[str] = []
    for i, composer in enumerate(data["allComposers"]):
        blob = json.dumps(composer)
        if (
            composer.get("composerId") == MAIN
            or OLD_WS_ID in blob
            or OLD_ROOT.lower() in blob.lower()
        ):
            fixed = repl(composer)
            fixed["workspaceIdentifier"] = json.loads(json.dumps(NEW_WS))
            data["allComposers"][i] = fixed
            changed_headers.append(fixed.get("composerId", "?"))

    cur.execute(
        "UPDATE ItemTable SET value=? WHERE key='composer.composerHeaders'",
        (json.dumps(data),),
    )

    kv_updates = 0
    status_resets = 0
    cur.execute("SELECT key, value FROM cursorDiskKV")
    for key, value in cur.fetchall():
        text = (
            value.decode("utf-8", errors="ignore")
            if isinstance(value, bytes)
            else (value or "")
        )
        if not text:
            continue

        needs_path_fix = OLD_ROOT.lower() in text.lower() or OLD_WS_ID in text
        needs_status_fix = key == f"composerData:{MAIN}" and '"status":"aborted"' in text.replace(" ", "")

        if not needs_path_fix and not needs_status_fix:
            continue

        if needs_status_fix:
            try:
                payload = json.loads(text)
            except json.JSONDecodeError:
                payload = None
            if isinstance(payload, dict) and payload.get("status") == "aborted":
                payload = repl(payload)
                payload["status"] = "none"
                payload["workspaceIdentifier"] = json.loads(json.dumps(NEW_WS))
                text = json.dumps(payload)
                status_resets += 1
            elif needs_path_fix:
                text = repl(text)
        else:
            text = repl(text)

        cur.execute("UPDATE cursorDiskKV SET value=? WHERE key=?", (text, key))
        kv_updates += 1

    conn.commit()

    cur.execute("SELECT value FROM ItemTable WHERE key='composer.composerHeaders'")
    check = json.loads(cur.fetchone()[0])
    for composer in check["allComposers"]:
        if composer.get("composerId") == MAIN:
            ws = composer.get("workspaceIdentifier", {})
            print("VERIFY workspace id:", ws.get("id"))
            print("VERIFY workspace path:", (ws.get("uri") or ws.get("configPath") or {}).get("fsPath"))
            repos = composer.get("trackedGitRepos") or []
            print("VERIFY first git repo:", repos[0]["repoPath"] if repos else "none")

    cur.execute("SELECT value FROM cursorDiskKV WHERE key=?", (f"composerData:{MAIN}",))
    row = cur.fetchone()
    if row:
        payload = json.loads(row[0])
        print("VERIFY composer status:", payload.get("status"))
        print("VERIFY context usage %:", payload.get("contextUsagePercent"))

    conn.close()
    print("Updated composer headers:", len(changed_headers))
    print("Updated cursorDiskKV rows:", kv_updates)
    print("Reset aborted status:", status_resets)
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
