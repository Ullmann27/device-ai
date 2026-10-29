# Generation16 read-only rescue handoff

Status: WIP / UNVERIFIED; process tests NOT_RUN.
EXP-12 (continuity), EXP-00 / EXP-09 (Termux recipe).
Baseline: b47f4fac043b2124a9ef4dd1cfb469a3fde88bc6 (PR #216, includes #215).
Isolated branch: codex/generation16-readonly-rescue-20261029.
Draft: https://github.com/Ullmann27/device-ai/pull/219
Source/test checkpoint: 62201ed05b9dd2bd1c8cacd6a12bea972d9ffc01.
No main merge, app/Kotlin changes, APK build, installation, or phone access.

## Scope and existing schema
Only new scripts/continuum-generation16-rescue.sh and its .test.mjs plus this handoff.
Historical package: at.heinz.deviceai.mentorpaired.
Only source path: files/learning-core-v2/experiences-v2.bin.
Read: AGENTS.md, docs/CONTINUUM-EXPERIMENTVERTRAG.md,
docs/CONTINUUM-EXPERIMENTE.json and Issue #41 at the baseline.
Actual source: app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningExperience.kt.
LEX2 magic 0x4c455832; max256 records; max512KiB; SHA256 payload trailer.
Records have enum tags, bounded strings, content-derived ID/hash, timestamps and unique IDs.
The older experiences-v2.lkg can predate owner revocation/correction and is never restored or read.

## Implemented source behavior (not device-verified)
Bash delegates to built-in Node modules (Node20+, Bash and existing adb; no added dependencies).
The owner runs the helper locally. It accepts only loopback ADB server 127.0.0.1
(default5037, optional --adb-server-port), checks that a server is already listening, and
never explicitly starts/configures a server, connects/pairs, roots, installs, restores or changes permissions.
Environment overrides for remote ADB servers and implicit devices are removed.
No serial selects exactly one listed device; --serial explicitly selects one listed authorized device.
USB/emulator identifiers and owner-connected loopback transports are accepted.
Non-loopback network and mDNS device serials are conservatively refused.
Thus customary Termux _adb-tls-connect._tcp and 192.168.* wireless endpoints are unsupported
even when the owner paired them earlier. An owner-authorized USB connection or an already
connected loopback adbd endpoint must appear in the local server's device list. The helper
never converts mDNS/WiFi transports or runs adb connect/pair on the owner's behalf.
The server socket probe and later adb call are separate operations, not an atomic server lease.

run-as is an OS permission boundary: a non-debuggable package is expected to fail.
Successful run-as establishes permitted sandbox/debuggable access, not original signing identity.
This preservation-only helper does not verify an APK signing fingerprint and cannot authenticate
the historical package publisher. No APK replacement is attempted or required.
Android Keystore identity, signing material and device-bound keys are neither read nor exported.
Fixed adb commands probe run-as PACKAGE id -u, pwd and stat; run-as starts in the
package private directory, so files/... is relative to that sandbox.
Probe requires application uid, the historical package private cwd, two real directories
and a regular source file. No shell -c or user-provided remote command is constructed.
Two exec-out run-as PACKAGE cat PATH captures are bounded (512KiB plus one byte, 15s per call),
retained privately, compared byte-for-byte, and independently parsed against the actual LEX2 layout.
Success requires the file trailer checksum, every record hash/ID and the expected256 records.
Content-free auditCounts metadata records all type/status totals plus canonical-shaped owner
USER_FACT totals/CONFIRMED/REVOKED counts. Owner facts require USER_CONFIRMED + OWNER_CONFIRMED,
no annotation/sourceRef, and CONFIRMED/LEARNED/REPLAY_PENDING/REVOKED status, matching the
existing PersonalLearningTransfer review filter. These remain unauthenticated claimed labels,
not restored owner consent. Console prints only aggregate confirmed-owner/revoked counts,
record/byte totals, checksum and private output directory; it never prints facts or record IDs.
Strict UTF8 / null flags may conservatively refuse malformed historical archives; captured bytes survive.

The current user must be non-root and HOME an owned private directory.
Output: HOME/.continuum-generation16-rescue/capture-* (0700), files0600, exclusive creation,
fsync before metadata publication. No shared storage, overwriting an earlier capture, uploads,
personal-content stdout/stderr, raw adb error logging, signing/key access or source writes.
Both captures retain .unverified.bin names intentionally; only metadata.json status
CAPTURED_VALIDATED identifies a fully verified local capture.
A nonzero exit or FAILED/UNVERIFIED metadata never means a rescued ledger.
Partial/mismatching/corrupt captures are retained; failure never removes or restores source data.
Two equal observed copies do not prove the application stopped changing afterwards.
This is preservation only: no import, memory promotion, learning or release approval.

## Verification and next entry point
Executed through GitHub connector: baseline ancestry/PR216 metadata and required instructions read;
actual store/schema inspected; published source/test read back.
Executed in functions V8 isolate: JavaScript syntax compilation of the helper's embedded Node body
and test source (ESM imports removed only for that syntax check). PASS.
These are static checks, not Bash parsing, Node process execution, Android or device tests.

NOT_RUN / environment UNAVAILABLE:
- bash -n scripts/continuum-generation16-rescue.sh
- node --check scripts/continuum-generation16-rescue.test.mjs
- node --test scripts/continuum-generation16-rescue.test.mjs
- real adb/run-as, Samsung/Termux, APK, import and restore checks.

The 24-case synthetic process suite uses a strict fake-adb command allowlist and a fake loopback listener.
It checks256-record success, device selection/authorization, nonlocal serial refusal, run-as failure,
uid/cwd/symlink probes, partial/mismatching/truncated/oversized output, checksum and record identity,
wrong record count, mixed record/status/owner eligibility counts, private output permissions, no content leakage and unchanged primary/older backup.
Root-running CI drops child uid/gid to65534; it must be allowed to execute its Node binary.
The suite never falls back to real adb. Test HOME is an isolated child environment fixture.
Next reviewer runs the exact commands above in a clean isolated checkout with Bash/Node20+.
No GitHub Actions trigger or remote phone access is introduced.

After review and successful local tests only, the owner may choose to invoke:
bash scripts/continuum-generation16-rescue.sh --serial OWNER_LOCAL_SERIAL
Do not run this command through a remote phone shell in this session.
If run-as is refused, retain the installed APK and original data unchanged. Assess an already
supported owner-controlled app export separately; never install a debug/re-signed replacement to gain access.

Rollback: stop using/discard this isolated draft branch. No source-data rollback is necessary.
