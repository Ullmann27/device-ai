# Generation16 read-only rescue handoff

Status: WIP / UNVERIFIED. EXP-12 (continuity), EXP-00 / EXP-09 (Termux recipe).
Baseline: b47f4fac043b2124a9ef4dd1cfb469a3fde88bc6 (PR #216, includes #215).
Branch: codex/generation16-readonly-rescue-20261029. No main merge.

Scope: only a new owner-invoked read-only local ADB helper and synthetic process tests.
Historical source package: at.heinz.deviceai.mentorpaired.
Only source path: files/learning-core-v2/experiences-v2.bin.
No device connection or personal content has been read in this session.
No root, APK installation/update, permission changes, signing/keystore access, package restore,
learning/fact promotion, remote device connection, pairing, or ADB server start is authorized here.

Read before editing: AGENTS.md, docs/CONTINUUM-EXPERIMENTVERTRAG.md,
docs/CONTINUUM-EXPERIMENTE.json, Issue #41.
Actual schema: app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningExperience.kt:
LEX2 magic 0x4c455832, max 256 records, max 512KiB, SHA256 payload trailer.
The older .lkg can predate owner corrections/revocations; this helper never restores it.

Checks: repository source/schema/PR #216 baseline read through authorized GitHub connector.
Shell syntax, Node process tests, Android/Termux/device checks: NOT_RUN / execution environment UNAVAILABLE.
Next: implement the bounded helper and synthetic fake-adb process suite, then run
bash -n scripts/continuum-generation16-rescue.sh
node --test scripts/continuum-generation16-rescue.test.mjs
in an isolated checkout with Bash and Node20+.
Real device execution requires the owner invoking the helper locally after inspecting its draft.
Rollback: discard this isolated branch; source APK and source package data are never modified.
