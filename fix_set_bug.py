#!/usr/bin/env python3
"""
fix_set_bugs.py

Fixes two bugs in GymTracker:

  1. RunRoutineScreen (RoutineScreens.kt):
     "Rest button adds an extra set" / "+ Set does nothing until Rest is tapped".
     Cause: `mutableStateMapOf<Long, MutableList<RunSetDraft>>` — the outer map
     is observable but the inner MutableList is not, so `drafts.add(...)` never
     triggers recomposition. Only a Rest-state change forces a redraw.
     Fix: use SnapshotStateList (mutableStateListOf) for the inner list.

  2. AddWorkoutScreen (Screens.kt):
     "Custom reps per set don't type correctly".
     Cause: OutlinedTextField's `value` was derived from the parsed Int, so
     clearing/typing a partial number collapses to 0 and fights the user.
     Fix: keep a local remembered text state; push parsed value up on change.

Run from project root:
    python fix_set_bugs.py

Backups written as <file>.bak-<timestamp> next to each modified file.
"""
from __future__ import annotations

import re
import shutil
import sys
import time
from pathlib import Path

ROOT = Path(".")
ROUTINE = ROOT / "app/src/main/java/com/example/gymtracker/ui/RoutineScreens.kt"
SCREENS = ROOT / "app/src/main/java/com/example/gymtracker/ui/Screens.kt"


# ---------- helpers ----------
def backup(path: Path) -> Path:
    ts = time.strftime("%Y%m%d-%H%M%S")
    b = path.with_name(path.name + f".bak-{ts}")
    shutil.copy2(path, b)
    return b


def once(text: str, old: str, new: str, label: str) -> str:
    n = text.count(old)
    if n == 0:
        print(f"  [SKIP] {label}: pattern not found (maybe already applied)")
        return text
    if n > 1:
        sys.exit(f"[FAIL] {label}: pattern occurs {n} times — refusing to guess")
    print(f"  [OK]   {label}")
    return text.replace(old, new, 1)


def once_re(text: str, pattern: str, new: str, label: str) -> str:
    rx = re.compile(pattern)
    hits = list(rx.finditer(text))
    if not hits:
        print(f"  [SKIP] {label}: pattern not found")
        return text
    if len(hits) > 1:
        sys.exit(f"[FAIL] {label}: pattern matched {len(hits)} times")
    print(f"  [OK]   {label}")
    return rx.sub(lambda _m: new, text, count=1)


def ensure_import(text: str, imp: str) -> str:
    if re.search(rf"^import {re.escape(imp)}$", text, re.M):
        return text
    m = list(re.finditer(r"^import androidx\.compose\.runtime\.[A-Za-z0-9_.]+$",
                         text, re.M))
    if not m:
        print(f"  [WARN] could not auto-insert import: {imp}")
        return text
    end = m[-1].end()
    return text[:end] + "\nimport " + imp + text[end:]


# =====================================================================
# 1) RoutineScreens.kt  — make the inner set list observable
# =====================================================================
print(f"== {ROUTINE}")
if not ROUTINE.exists():
    sys.exit(f"[FAIL] file not found: {ROUTINE}")
r = ROUTINE.read_text()
bak_r = backup(ROUTINE)

r = once(
    r,
    "val actual = remember { mutableStateMapOf<Long, MutableList<RunSetDraft>>() }",
    "val actual = remember { mutableStateMapOf<Long, SnapshotStateList<RunSetDraft>>() }",
    "actual map value type -> SnapshotStateList",
)

r = once(
    r,
    """            r.exercises.forEach { ex ->
                actual[ex.id] = MutableList(ex.sets) {
                    RunSetDraft(reps = ex.reps, weight = ex.weight)
                }
            }""",
    """            r.exercises.forEach { ex ->
                actual[ex.id] = mutableStateListOf<RunSetDraft>().apply {
                    repeat(ex.sets) { add(RunSetDraft(reps = ex.reps, weight = ex.weight)) }
                }
            }""",
    "LaunchedEffect body -> mutableStateListOf",
)

r = once(
    r,
    "val drafts = actual[ex.id] ?: mutableListOf()",
    "val drafts = actual.getOrPut(ex.id) { mutableStateListOf() }",
    "drafts fallback -> observable list",
)

r = ensure_import(r, "androidx.compose.runtime.mutableStateListOf")
r = ensure_import(r, "androidx.compose.runtime.snapshots.SnapshotStateList")

ROUTINE.write_text(r)
print(f"  backup: {bak_r.name}")


# =====================================================================
# 2) Screens.kt — fix per-set reps/weight text fields
# =====================================================================
print(f"== {SCREENS}")
if not SCREENS.exists():
    sys.exit(f"[FAIL] file not found: {SCREENS}")
s = SCREENS.read_text()
bak_s = backup(SCREENS)

# 2a. Reps field (exact text, known from grep)
old_reps = (
    "value = draft.reps.toString(),\n"
    "                onValueChange = { onChange(draft.copy(reps = it.toIntOrNull() ?: 0)) },"
)
new_reps = (
    "value = repsText,\n"
    "                onValueChange = { raw ->\n"
    "                    repsText = raw.filter { it.isDigit() }\n"
    "                    repsText.toIntOrNull()?.let { onChange(draft.copy(reps = it)) }\n"
    "                },"
)
s = once(s, old_reps, new_reps, "reps OutlinedTextField -> local text state")

# 2b. Weight field (regex so we tolerate minor whitespace differences)
weight_pat = (
    r'value = draft\.weight\.toString\(\),\s*\n'
    r'\s*onValueChange = \{ onChange\(draft\.copy\(weight = it\.toFloatOrNull\(\) \?: 0f\)\) \},'
)
new_weight = (
    "value = weightText,\n"
    "                onValueChange = { raw ->\n"
    "                    weightText = raw\n"
    "                    raw.toFloatOrNull()?.let { onChange(draft.copy(weight = it)) }\n"
    "                },"
)
s = once_re(s, weight_pat, new_weight,
            "weight OutlinedTextField -> local text state")

# 2c. Inject local text states at top of SetDraftRow body
if "var repsText by remember" not in s:
    pat = re.compile(r"(private fun SetDraftRow\([^)]*\)\s*\{\s*\n)")
    def _inject(m: re.Match) -> str:
        return m.group(1) + (
            "    var repsText by remember { mutableStateOf(draft.reps.toString()) }\n"
            "    var weightText by remember { mutableStateOf(draft.weight.toString()) }\n"
        )
    s, n = pat.subn(_inject, s, count=1)
    if n == 1:
        print("  [OK]   inserted repsText / weightText local state")
    else:
        print("  [WARN] could not locate SetDraftRow body — insert manually:")
        print("         var repsText by remember { mutableStateOf(draft.reps.toString()) }")
        print("         var weightText by remember { mutableStateOf(draft.weight.toString()) }")

# 2d. Ensure required runtime imports are present
for imp in (
    "androidx.compose.runtime.getValue",
    "androidx.compose.runtime.mutableStateOf",
    "androidx.compose.runtime.remember",
    "androidx.compose.runtime.setValue",
):
    s = ensure_import(s, imp)

SCREENS.write_text(s)
print(f"  backup: {bak_s.name}")

print()
print("Done.")
print("Inspect changes with:")
print(f"  git diff -- {ROUTINE}")
print(f"  git diff -- {SCREENS}")
print()
print("Then rebuild:")
print("  ./gradlew assembleDebug")
