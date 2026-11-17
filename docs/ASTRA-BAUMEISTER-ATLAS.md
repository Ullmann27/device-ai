# Astra Continuum Baumeister – Workshop, Academy, Atlas

## Purpose

This is the experimental local engineering assistant for **Device AI Continuum**. It extends the existing teamleader, canonical development backlog and M4 test gate; it must not become a second unsynchronized repair system.

It has three independently auditable layers:

1. **Workshop** (`.github/continuum/continuum-workshop.mjs`): Doctor, status, one supervised Teamleader cycle, bounded recurring cycles, fixed M4 Android release verification.
2. **Academy** (`.github/continuum/worker-academy.mjs`): Rotates through fixed, approved source/test pairs, executes real Node test suites, records source SHA-256, test SHA-256 and pinned Git HEAD in an atomically written local ledger. Only executed passing tests count as validated recipe evidence.
3. **Atlas** (`.github/continuum/worker-atlas.mjs`): Content-addressed, small web reference index. Research uses existing public Wikipedia, Crossref and OpenAlex HTTPS adapter with fixed destinations; only bounded bibliographic leads and provenance are retained. Nothing is silently promoted as factual memory or model training material.

## Interaction from Android

The Mentor Pair application recognizes:

- `Worker` or `Baumeister` – local instructions.
- `Worker Status` – local Teamleader/Backlog audit; this does **not** prove a host worker is connected.
- `Worker Aufgaben` – persistent shared backlog view.
- `Worker Werkstatt` – existing Android development drafting dialog.
- `Worker: Erstelle eine Kotlin Notizen-App` – saves a **local draft goal** only. It does not run arbitrary code or send the request to a remote model.

The Android app does not yet have a transport for the external Workshop live status or for build submission. The future host-channel integration must pair with a separate, narrowly scoped identity, explicit job ownership and a durable queue. Groq Mentor is a text-only, per-question approved service and must never become a backdoor to device tools or repository writes.

## Local developer commands

Execute from a **clean trusted checkout** on a dedicated developer workstation:

```bash
node .github/continuum/continuum-workshop.mjs --mode doctor
node .github/continuum/continuum-workshop.mjs --mode status
node .github/continuum/continuum-workshop.mjs --mode learn
node .github/continuum/continuum-workshop.mjs --mode research
node .github/continuum/continuum-workshop.mjs --mode once
node .github/continuum/continuum-workshop.mjs --mode loop --interval-minutes 60
node .github/continuum/continuum-workshop.mjs --mode verify-android
```

Persistent state defaults to `~/.local/state/device-ai-continuum`, outside the source checkout. The `loop` mode reuses the existing Teamleader recovery/repair engine, performs one Academy lesson, and then attempts a bounded Atlas research cycle (Atlas cooldown: 4 hours). It remains alive only while the developer host process is actually running; a stopped PC cannot execute tasks.

The `verify-android` gate uses the existing M4 independent-worktree proof. Its reports and built APKs are **not signed publisher release attestations**. The separate Render Android preview process signs the Mentor Pair package and verifies the configured preview certificate.

For direct component tests:

```bash
node --test .github/continuum/continuum-workshop.test.mjs .github/continuum/worker-academy.test.mjs .github/continuum/worker-atlas.test.mjs
```

## Autonomous public research service

The already existing `backend/src/continuum-daemon.js` and `backend/src/public-research.js` can run in public-source-only mode on the existing Render mentor service:

- `CONTINUUM_DAEMON_ENABLED=true`
- `CONTINUUM_RESEARCH_PROVIDER=public`
- `CONTINUUM_RESEARCH_INTERVAL_MS=21600000`
- `CONTINUUM_DAILY_CYCLE_LIMIT=4`
- Disable `CONTINUUM_BRAIN_FACTORY_ENABLED` and `CONTINUUM_HYPOTHESIS_TOURNAMENT_ENABLED` unless their separate model, cost and audit contracts have been explicitly evaluated.

This runs metadata research, **not** generative model training. Render's ephemeral filesystem may reset its local state after replacement/restart, and a free service may sleep. Persistent multi-device knowledge requires a separately verified durable data store and signed handoff protocol.

## Integrity and threat model

- NEVER issue Git commits or merges to `main` automatically.
- NEVER use a user's free-form chat text as a shell command.
- All new Academy facts are only *test-evidence observations*; a passing unit test is not generalized competence.
- Atlas entries remain `UNVERIFIED_WEB_LEAD`; source access alone does not provide extraction rights or a verified scientific claim.
- API keys, phone memories and backend device tokens are not Academy inputs.
- Existing code/build scripts may still execute arbitrary repository-supplied programs during testing. Run them only in a dedicated unprivileged OS/container sandbox with limited file/network access.
- iOS/Swift needs a supported macOS/Xcode signing toolchain, while Flutter/Godot require verified platform export recipes. Architecture recognition is not a claim of completed app builds.
- A major language model is **not** trained by the Academy or Atlas. Local generative inference still needs a compatible LiteRT-LM or PC-based model, its model license, source and evidence-backed verification.
