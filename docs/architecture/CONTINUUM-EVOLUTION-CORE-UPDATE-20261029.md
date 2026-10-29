# Continuum Lokal · Evolution Architecture / Pre-release Gate
Date: 2026-10-29 | Owner: Device AI Continuum | Status: ISOLATED SOURCE CANDIDATE, NOT APPLIED

## Verified starting facts
Installed main package: `at.heinz.deviceai.continuumlocal`; currently reported 0.3.27, versionCode 28; installed code baseline `b2b2277162e346688da3eef551414108615f3cad`.
Existing original local signer SHA-256: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`. The owner keeps the actual P12 on the device; never copy, reset, recreate, or upload it.
The runtime already has `LocalModelRuntime`, LiteRT-LM 0.18.0, a hash-pinned imported Gemma 4 E4B, plus separate SWB1 4,113-parameter encoder; the latter is NOT a generative language model.
The reported SWB1 stage-1 rejection protected existing weights; one attempted stage must not be reset without explicit reviewed recovery.
The last web research used deterministic Wikipedia excerpts when the local model was not READY. A `Research response is too large.` failure is a bounded fetch rejection, not a reason to remove network limits.

## This PR's scope
Source-only preflight foundations, with pure Kotlin policies and JUnit tests:
- `ContinuumLanguageCoreSelection`: chooses only READY, independently evaluated compatible backend profiles; SWB1 is refused as generative core, while optional classification remains possible; owner-LAN opt-in required on both request and candidate. No model loading or execution.
- `ContinuumSignedUpdatePolicy`: strictly checks the owner-release metadata before any network/download: exact installed package, pinned original signer, increased versionCode, approved owner channel, trusted HTTPS origin and content-addressed bounded APK URL. **This is NOT a verified APK**, merely admission for a later byte/signature/installer gate.

These tests have not been executed on a host in this PR until a real test report is attached. No Android UI, download, credentials, installation, weight change, model backend integration or APK is claimed.

## Product architecture: two separately updatable planes
**Application code** (Kotlin/Android runtime, permissions, UI, native libraries): a new signed APK is required; the installed app should offer one action:
`System → Auf Updates prüfen → Neue freigegebene Version → Herunterladen → Android aktualisieren`.
Android's own installer must retain control and must be tested on the actual Fold7.

**Owner-controlled data** (source packs, knowledge-index, compatible embeddings, locally checked prompt recipes, model files, test suites): may be staged through separately authenticated/versioned packages, no APK rebuild, but **never execute code loaded from arbitrary internet content** and do not silently promote owners' memories or replace model weights.

## Complete trusted release path still required
1. Freeze and audit the integration tree, isolate Codex #230 and EWM research; preserve historical memories.
2. Implement a dedicated pinned HTTPS release channel. Choose and **compile** the exact host after verifying ownership. Never read `trustedHost` from untrusted downloaded metadata.
3. Expose only an actual, full owner-signed APK and its exact commit/hash/version and immutable manifest from original Fold7 signing workflow. Private GitHub branches, drafts and disposable QA files must not advertise as releases. Do not publish P12/password/model bytes.
4. Implement bounded streaming into an app-private staging file; enforce redirect target allowlist, Content-Length/actual max bytes, cryptographic SHA-256. Validate package name, greater versionCode and signer using Android `PackageManager` / APK verifier **from actual APK bytes**, not claimed manifest fields. Preserve the last verified archive.
5. Use the documented Android install confirmation mechanism (`PackageInstaller` or narrowly scoped `FileProvider` content URI with temporary read grant). User approval required; no privileged/silent update claim. Test Android 17 and Samsung-specific handling. Preserve private app data, model files and revoked owner facts.
6. Add visible states: current, no release, unsafe channel, auth/network/storage failure, checksum failure, signer mismatch, install cancelled, install confirmed. Never display `installed` based on download or installer launch alone.
7. Deploy a single bootstrapping owner APK with the fully working update button + trusted channel; prove a **second** signed version is offered and accepted as a same-package Android update. Without that second real device test, the phrase "one-tap update works" is prohibited.

## Replace the limiting SWB1 boundary
1. Today use the already imported Gemma 4 E4B as **primary language generator** after native READY and an actual direct response; keep SWB1 auxiliary only. Preserve SWB1's original snapshots, owner facts, CRE1/CDX1 experiments.
2. Put `LanguageCore` behind a typed adapter with `load / unload / stream / cancel / readiness / modelFingerprint`, bounded context, exclusive active native session, no tool invocation without existing `ToolGate`.
3. Use a frozen, content-free-evidence benchmark harness for the exact model/suite/task pair. A selection policy cannot manufacture a true benchmark score or a loaded backend.
4. Next add verified `llama.cpp` GGUF implementation in a separate isolated runtime; retain Gemma on failures. Do not mix model formats or bypass native compatibility, memory or thermal tests.
5. Optional 12B/26B/31B-class Gemma inference belongs on a **separately owned capable PC**, encrypted/authenticated local network with explicit owner opt-in; never assume such hardware exists. Keep smartphone entirely offline-capable.
6. Further research: source-anchored multi-document retrieval using official primary sources, licensed scientific papers/docs, provenance, freshness, contradictions, explicit uncertainty, and an indexed embedding model. The 48-item Wikipedia scheduler can remain a limited auxiliary research lane, but is not the entire knowledge strategy.
7. Evaluate German answers, coding, legal-source freshness, grounded retrieval, refusal of unsupported facts, long context, tool safety, token speed, thermal/energy behavior and data retention against the unchanged baseline. No assurance of frontier-model parity or unlimited local compute.
8. Autonomous coding/research worker must be sandboxed and submit reviewable changes; no unchecked direct production writes.

## References reviewed
- Official Gemma 4 E4B LiteRT support and MTP: https://developers.google.com/edge/litert-lm/models/gemma-4
- Official model sizes and memory tradeoffs: https://ai.google.dev/gemma/docs/core
- LiteRT-LM project and 0.18.0: https://github.com/google-ai-edge/LiteRT-LM
- Android arm64 llama.cpp build requirements: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
- Android PackageInstaller user action: https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams

## Current acceptance state
- This draft only establishes pure policy checks. **NOT INTEGRATED, NOT BUILT, NOT DEVICE-TESTED.**
- The original Fold7 APK is unchanged. No install, sign, signer extraction, Main merge or unattended learning change.
- Do not merge this PR without compilation/unit tests, integration tests, security review and explicit owner release approval.
