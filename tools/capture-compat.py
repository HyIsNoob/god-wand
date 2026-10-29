"""Capture an explicitly edited generated API adapter; not part of normal builds.
Usage: python tools/capture-compat.py PROFILE java/net/godwand/SomeFile.java
Never use this to capture a gameplay difference; edit common source for that.
"""
import difflib
import json
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
profile, key = sys.argv[1:]
if profile not in {"fabric-1.20.1", "forge-1.20.1", "mc-26.3"}:
    raise SystemExit("Unknown supported profile")
if not key.startswith(("java/", "resources/")) or ".." in Path(key).parts:
    raise SystemExit("Expected a relative java/ or resources/ path")
folder = root / "compat/profiles" / profile
manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
base = (root / "common/src/main" / key).read_text(encoding="utf-8")
target = (root / "build/shared" / profile / "src/main" / manifest["rename"].get(key, key)).read_text(encoding="utf-8")
a, b = base.splitlines(keepends=True), target.splitlines(keepends=True)
changes = []
for context in (3, 6, 12, 24, 48, 100000):
    groups = difflib.SequenceMatcher(None, a, b, autojunk=False).get_grouped_opcodes(context)
    changes = [{"before": "".join(a[g[0][1]:g[-1][2]]), "after": "".join(b[g[0][3]:g[-1][4]])} for g in groups]
    if all(c["before"] and base.count(c["before"]) == 1 for c in changes):
        break
else:
    raise SystemExit("Could not make unique adapter anchors")
patch = folder / (key + ".json")
patch.parent.mkdir(parents=True, exist_ok=True)
if changes:
    patch.write_text(json.dumps({"reason": "Explicit version/loader API adapter", "changes": changes}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
elif patch.exists():
    patch.unlink()
print(f"Captured {profile}/{key}: {len(changes)} API hunks")
