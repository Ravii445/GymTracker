#!/usr/bin/env python3
"""
fix_setdraftrow.py — insert the missing local text buffers into SetDraftRow.

Run from project root:  python fix_setdraftrow.py
"""
from __future__ import annotations

import re, shutil, sys, time
from pathlib import Path

SCREENS = Path("app/src/main/java/com/example/gymtracker/ui/Screens.kt")
if not SCREENS.exists():
    sys.exit(f"[FAIL] not found: {SCREENS}")

s = SCREENS.read_text()

if "var repsText by remember" in s and "var weightText by remember" in s:
    print("[SKIP] already present — nothing to do")
    sys.exit(0)

# Find `private fun SetDraftRow(` then the matching closing `)` of the
# parameter list, then the following `{`.
m = re.search(r"private fun SetDraftRow\s*\(", s)
if not m:
    sys.exit("[FAIL] could not find `private fun SetDraftRow(`")

i = m.end()          # just after '('
depth = 1
while i < len(s) and depth > 0:
    c = s[i]
    if c == "(":
        depth += 1
    elif c == ")":
        depth -= 1
    i += 1
# i is now just past the matching ')'
brace = s.find("{", i)
if brace == -1:
    sys.exit("[FAIL] could not find opening brace of SetDraftRow body")

insert_at = brace + 1
# Preserve whatever newline style / indentation follows the brace
tail = s[insert_at:]
indent = "    "
injection = (
    f"\n{indent}var repsText by remember {{ mutableStateOf(draft.reps.toString()) }}"
    f"\n{indent}var weightText by remember {{ mutableStateOf(draft.weight.toString()) }}"
)

new_s = s[:insert_at] + injection + tail

bak = SCREENS.with_name(SCREENS.name + f".bak-{time.strftime('%Y%m%d-%H%M%S')}")
shutil.copy2(SCREENS, bak)
SCREENS.write_text(new_s)
print(f"[OK] inserted repsText / weightText into SetDraftRow")
print(f"     backup: {bak.name}")
