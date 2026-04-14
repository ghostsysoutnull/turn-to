# Lessons Learned

**Read before starting any task. Append during your run. Do not reorganize.**

An entry belongs here when it is operational knowledge not captured in design docs —
gotchas, non-obvious patterns, failure modes discovered at runtime.

An entry does NOT belong here when the underlying design doc should be updated instead.
If a lesson reveals a design doc is wrong, fix the doc and skip the entry.

---

## Entry format

```
## YYYY-MM-DD — <Agent type> — <one-line subject>
<What happened. Where. Why it matters. What to do instead.>
```

Keep entries under 5 lines. No vague generalities ("X can be tricky"). Cite the
specific class, file, or error.

---

## Entries

## 2026-04-14 — Code Agent — CombatEngine luck tests require silent fallback in askTestLuck()
GameInput.askTestLuck() must catch AssertionError from ScriptedInput when no values remain and return false.
Tests with empty ScriptedInput signal "no luck offer expected"; tests with explicit yes-answers exercise the luck mechanic.
The default method in GameInput.askTestLuck() wraps readYesNo in a try-catch for this; real implementations override it.
