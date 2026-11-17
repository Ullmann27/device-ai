# CONTINUUM – JARVIS UNIFIED CINEMA, ANDROID, ACCOUNTS & LEARNING
Owner master / 11 Nov 2026 · WIP execution specification · NOT an implementation claim

## 0. Non-negotiable product definition
Continuum is a living, embodied 3D JARVIS inside one coherent, cinematic, perspective-correct sci-fi workspace. The user watches JARVIS operate *real* PDFs, apps, webpages and device tools on its spatial holographic monitors. A rectangular chat/progress dialog with glow is NOT acceptance. The user's five visual reference images remain the target for art direction, not proof of implemented hardware rendering.

This product uses the already approved Android/Filament renderer, the original owner GLB/avatar and original Continuum energy orb. Do not add a second engine, show fake tool actions, silently downgrade to legacy UI or permanently disable Cinema to work around heat.

On-screen activity must follow real owner commands, tool states, OS permissions, verifiable outputs and timing. The user may ask naturally by voice or tap. A cinematic world with real content, not a simulated recording, is mandatory.

## 1. Current verified implementation and limitations
- Target repo: Ullmann27/device-ai; package: at.heinz.deviceai.continuumlocal; Samsung Galaxy Z Fold7, Android 17.
- Installed original-signed Code53 / versionCode 53 / versionName 0.3.52, tested application source commit 050a57cb019dcc1b7fe6cfa2fb5c11176b048e05. This source and its signed release must remain reproducible and unmodified.
- Existing integration owner: draft PR #747, integration/continuum-final-visibility-20261111. Cinema depth-glass WIP: PR #757, claude/cinema2-depth-light-20261111 (stacked on #747, not yet device-certified). Coordinate rather than overwrite.
- Owner device verification: ElevenLabs Maya voice works; lip amplitude varies (#764). Original avatar movement and orb draw; *not* a full 3D workplace (#638). Direct WhatsApp opening works after owner manually enabled Android's accessibility/screen observation (issue #765). Full multistep app automation is NOT proven.
- The current AccessibilityService is primarily window observation/bounded screen reading, not a general tap/typing automation engine. Native app opening exists and has verified-foreground postcondition. Screen reading requires real grants; not all app screen fields are accessible. Direct arbitrary third-party app UI embedding does not exist.
- PDF viewer is owner-selected via Android ACTION_OPEN_DOCUMENT; the spoken PDF phrase with 'und zeige mir jeden Arbeitsschritt' was incorrectly routed to local Gemma (#760).
- Full system cleanup does NOT exist: code currently cleans a bounded set of Continuum-owned diagnostic-cache records with preview/owner consent/limited undo, not all third-party caches. User-friendly full diagnostic route is incomplete.
- Team mode on Code53 selects chief Iris and analysts Archiv/Atlas; first task CANCELLED, second saved Archiv ANALYZE but overall FAILED, Atlas REVIEW not verified (#670). The profiles are not independent model instances and have no demonstrated self-training of Gemma weights.
- Serious Fold7 thermal warning: LocalModelThermalGuard stops at Android SEVERE >=3; no disabling thermal guard or recommending freezer/ice packs. Quantified thermal bottleneck remains unprofiled (#569).
- Owner-memory multi-correction question failed at final turn (#758), and previous context/time/completion logic requires tests.
- No Google/Oberbank connection, new OAuth app authorization or continuous third-party account session is proven in Code53. Treat as NEW WORK, never an existing capability.

## 1A. Canonical PDF Work Scene handoff — PR #761 (mandatory, preserve existing work)

**Owner handoff supplied 11 November 2026. Real GitHub status verified before this addition:**
- Official PR: https://github.com/Ullmann27/device-ai/pull/761 — `[WIP] PDF work scene: real document, spatial presentation and personal result reply`, open/draft, **not merged**.
- Canonical working branch: `work/pdf-scene-20261111`. Latest verified remote HEAD: `8505c88504149f7de576b13350fea88dcb223b44`. **Last actual application-code commit:** `a39089fe8e7e2becfefcbccb0dec64f9172f9992`; the later HEAD adds handoff/evidence only. Tested local code `a8482e2f01b2326130500f187c0f62a451d2b61d` shares exact tree `f680fde68c31588a156d7447e96478644f00b6ae` with the pushed code.
- Mandatory current handoff: `docs/research/omega/CLAUDE-PDF-SZENE-UEBERGABE-20261111.md` and `docs/research/omega/PDF-SCENE-PAUSE-STATE-20261111.json` on PR761's HEAD. Also `docs/research/omega/PDF-SCENE-HANDOFF-20261111.md`, `docs/research/omega/PDF-SCENE-JUNIT-JOURNAL-20261111.md`, and `docs/evidence/pdf-scene-20261111/`. Read these and repository `AGENTS.md` **before touching application code**.
- Integration lineage **already includes** the Code53 runtime from #747, the Cinema2 focus-depth/glass code from #757 at `0adcfe907baaaab9f53730fcbab67ef61418ebce`, and the reviewed PDF flow/scene contributions from #762/#763. **Never cherry-pick or recreate #757, #762, #763 into #761 blindly.** Compare actual file/tree SHAs first; preserve everything already integrated.
- The PDF implementation already contains: speech command with a bounded display instruction; real owner-authorized Android PDF picker + `PdfDocumentIntake`; session/generation-scoped stale picker protection; genuine PDF from **left**; a derived five-mesh `app/src/main/assets/cinema3d/document-workspace.glb` with actual page bitmap as texture via `FilamentDocumentStage`; real scroll/layout/geometric refresh; native-glass/3D visibility fallback; result from **right**; personal Maya-capable follow-up only after physical window arrival; STOP/close/background/no-duplicate completion safeguards. Original GLB avatar and energy orb remain unchanged. This is **working code claimed and partly source-tested**, not yet a proven Fold7 experience.
- Latest code review: Gitar found two issues (depth-glass gating assertion and loss-of-stage-readiness propagation); both marked **fixed** in PR761. CodeRabbit draft review was skipped. Neither review is proof of a full Android/GPU acceptance.

**Exact QA status — do not upgrade partial results to PASS:**
- Production Kotlin/Java and Android test sources compiled. Focused Node/source checks: **17 passed, 0 failed**.
- Android 18-class core scope was **interrupted on owner's prior explicit stop, exit 130**: **11 completed PASS, 1 narrowly defined SKIP, no completed suite_end**. A running case `anotherStartedTaskRejectsAnAlreadyObservedPdfFailure` was not completed; another pending case also not established. Complete all remaining methods.
- Strict synthetic-PDF/provider/native decoder probe: **1 FAIL** with `NoSuchMethodError: java.io.FileDescriptor.getOwnerId$()` inside Android `IoUtils.acquireRawFd` / `PdfProcessor.create` on the host JVM, *before* page decode. The optional explicit `-PcontinuumPdfAllowMissingHostNative=true` converts **only that exact host linkage issue** into a **SKIP after provider/open assertions**, never into successful PDF decoding. Strict test remains default, find a suitable Android environment for authoritative native decoding.
- Current full 25-class PDF scope (18 core + 7 visual), complete Node regression, complete Android suite, Lint, new APK, real 3D Filament render, real PDF file, Maya audible timing, battery/thermal/frame times on physical Fold7: **NOT COMPLETED / NOT RUN**, unless later separately verified on new exact SHAs. 25 prior host-capture frame inventory uses scripted Canvas inputs, not physical Filament or real 3D film-quality evidence. No Code54 APK was created, signed or installed from PR761.

**Mandatory Opus continuation and integration order:**
1. On new owner-authorized continuation, inspect latest remote PR761 HEAD/tree/review and the above handoffs, then work **on the canonical PDF branch**, not a new copy. Do not restart finished agent tasks, reimplement the five meshes or discard existing tests.
2. Complete interrupted **core** Android tests on one frozen code tree, preserving red failures and the mandatory strict native probe; use the existing `tools/pdf-scene-review/focused.init.gradle`, then the **visual** seven-class scope, complete Node suite, main Android regression/Lint. Keep distinct PASS/FAIL/SKIP/NOT_RUN, exact commands/source SHA and end-of-suite receipts. Reviewer fixes must remain covered. Avoid redundant parallel heavy Gradle runs.
3. Repair only reproducible defects with minimized source diffs, including token-sensitive/no-duplicate work, scrolling, 3D fallback, missed window arrival/gaze, render-loss recovery and owner STOP. Every consistent working checkpoint **commit + push**, unfinished WIP also **commit + push** with truthful evidence, no force pushes.
4. Coordinate a controlled integration of #761 into the existing #747 integration lineage, accounting for shared #757 files. Do not overwrite concurrent master changes including this spec. Existing Code53 APK stays the owner release baseline until a new signed candidate passes normal gates.
5. Run actual Fold7 owner tests using a real permitted PDF (not mock file browsing), actual Filament GPU scene with measured transform/first-page bitmap/scroll, evidence-bound left→read→verified steps→right→voice, original avatar gaze and Maya sound. Evaluate temperatures, frame times and interruption protection. Only then consider APK/owner-signing release pipeline.

**Completion contract:** The first end-to-end PDF scene is the model for other tools (WhatsApp/Chrome/device diagnosis); preserve the **film-quality 3D office** goal throughout. No second renderer, no fake video/screenshot UI, no new package or certificate. A report listing compiled source, partial tests and host Canvas is **WIP**, never "shipped". The user's instruction to **commit, push, integrate, and not leave finished work abandoned** is a release-process requirement, not permission to bypass review, thermal protection, OS consent, banking security or owner APK-signing gates.

## 2. One spatial 3D engine, not screenshot animation
Retain the original GLB, 21 morph targets, the verified Filament head asset, energy orb, existing HollywoodFui/JarvisWorkWindowHost, AvatarDirector, CockpitBus, and task/tool journal. Assemble a real scene graph with transform hierarchy:
  WorldRoot → CinematicEnvironment → Floor/Deck/Stage/LightRig → AvatarRig → Orb → SpatialScreens/ContentTextures → WorkTools → ResultScreen.
Everything has 3D world transforms (position, rotation, scale), emissive/physical materials, depth/occlusion, readable text as native or offscreen rendered textures, real camera moves/zoom, motion tween tracks tied to finite state, stable foldable aspect/viewport layouts.

Visual language: convincing sci-fi office/command room, restrained but rich magenta/cyan accents, luminous physical materials, smoke glass, dynamic reflections/light response, anchored perspective, depth-of-field only when legible, no overdraw that hides real documents. Avoid copy/pasting a single huge HUD bitmap. Preserve original Orb/Avatar even in low-power presentation.

A real device/app job updates a single explicit scene state machine:
  REQUEST_ACCEPTED → PERMISSION_REQUIRED? → TOOL_STARTED → TOOL_PROGRESS/EVIDENCE → VERIFIED_RESULT or FAILED/UNKNOWN/CANCELLED → OWNER_DIALOG.
Animate only real state changes. No fictitious file listings, fake clicking, mocked browser results or invented 100% indicators. Keep a durable receipt/replay ledger so a fast genuine step can still be watched as a faithful accelerated visualization without delaying/redoing side effects.

Camera and avatar gaze must point at the actual spatial monitor/document and target panel *from its start through fly-in*, then yield to speech/user gaze. For PDF, genuine page images/text and document metadata; for web, real loaded URL/content; for apps, genuine permitted observation/capture. If OS blocks observation, state it and show a real OS-app handoff, never a fabricated mirrored screen.

Performance acceptance on inner/cover Fold7: frame-time histograms, GPU/CPU load where measurable, memory, thermal state and battery trends; adaptive resolution/shaders/particles while preserving semantic Cinema and essential mouth/eye movement. No thermal-protection bypass; 60 FPS may be an aspiration, not a falsely asserted result.

## 3. Exact owner scenarios (end-to-end)
### PDF and local files
Owner: 'Continuum, öffne PDF Argon 4 Forschungsexperiment und zeig jeden Arbeitsschritt.'
- Recognize compound presentation request without rejecting PDF intent. Show real, authorized folder/document picker; if owner has not granted a file, do NOT invent or search inaccessible files.
- On explicit user selection, display the actual file name, page images and text where extractable. Gaze shifts to file shelf/real document monitor. Scroll only over content genuinely being scrolled/read. Reveal authentic read/analysis receipts, then project a result panel and personally ask 'Zusammenfassen, ausarbeiten, prüfen, widerlegen?'
- No local Gemma inference for merely selecting/opening a document. When text analysis is requested, use real bounded input with thermal admission, cancellation and evidence.

### Android apps, WhatsApp, Facebook
Owner: 'Öffne WhatsApp.' → find exact installed launcher, request necessary owner permission, launch and verify foreground. JARVIS' spatial app monitor must show only a real permitted app state; show no invented contacts.
Owner: 'Wem kann ich schreiben?' → if accessible/approved, read visible contacts or use an official contact picker, display accurate identities and ask whom. Draft text from explicit input. Never send/post/delete/like/pay with a mere read or open authorization.
Owner: 'Öffne Facebook.' → actual Facebook app/web account as authorized, real owner feed if legitimately accessible; private and redacted areas must not leak into logs/screenshots.
Interacting with external apps may require real accessibility tree operations and per-step verification; MediaProjection can show actual on-screen pixels only after session-specific Android approval. Android window transitions may temporarily put the target app in the foreground; do not claim simultaneous native UI embedding if unsupported.

### Web browser / research
Owner: 'Recherchiere X in Chrome.' When requested browser is unambiguous, choose it. If ambiguity remains, offer the actual available browsers once and remember preference where permitted. Load real URLs/results, show genuine source titles and traceable events. Link browser surface vs projection correctly; no simulated browser history. Re-ask only for missing essential permissions or materially ambiguous acts.

### Device diagnosis and cleanup
Owner: 'Schau mein Handy an; was macht es heiß, was belegt Speicher und was können wir verbessern?'
- Show real Android status + source/precision limitations. Measure CPU/own process where possible, RAM, battery/thermal, storage; no inaccessible cross-app diagnostics invented.
- A *read-only* assessment can run without destructive confirmation. Show realistic diagnosis stations within the spatial world.
- Present scoped cleanup candidates with preview, counts and bytes, exact provenance; never erase personal files, WhatsApp, model weights, backups or accounts without appropriate owner consent. For permitted actions present a transaction-safe plan, precise verification/undo constraints and measured before/after. Distinguish logical deleted bytes from OS free-space delta.
- Current Code53 cleanup is app-owned technical cache only, not a complete Android system cleaner. Expand only within OS/publicly supported access or explicitly granted document scopes.

## 4. Persistent multi-account connection hub – a required new module
Build one clear 3D + accessible conventional UI for 'Meine Verbindungen': separate accounts/cards for Gmail (including multiple Google identities), Contacts, Calendar, Drive, supported clouds, browser/provider integrations, and Oberbank. Each account individually lists: provider, masked account identifier, grants/scopes, read vs write privileges, connection state, last verified operation, token/consent expiry, and two-way revoke/delete.

Authentication: provider-hosted OAuth 2.0 authorization-code with PKCE when supported for native installed clients, state/nonce, verified redirect/deep link, least-privilege incremental scopes. Never store or ask for passwords in Continuum, never scrape password fields or OTPs, never hardcode secrets or inject credentials into model context. Secure long-term granted credentials at rest with Android Keystore-backed encryption and an appropriate provider-safe token lifecycle; use backend only for integrations that legally/technically require it. Isolate permissions by identity/provider/tool/action. Refresh expired access tokens while valid refresh consent permits, transparently fall back to official reauthorization for expired/revoked grants.

**Realistic persistence contract:** one-time login normally persists until revoked, expired, invalidated or provider requires new consent/strong authentication. 'Never log in again' and universal perpetual bank access are not promised. No account is automatically inherited from ChatGPT connector logins. User may manually revoke at any time; app must stop pending actions and clear local tokens.

Gmail: optional message browse/search/read, drafts, sending with distinct explicit approval and correct narrow scopes. Google restricted Gmail scopes may require OAuth verification/security assessment before production distribution. Never silently enable unrestricted mail.read/write. Support distinct multiple Google accounts and clearly identify sender before sends.

Oberbank: use official Oberbank XS2A/PSD2 via a duly authorized account-information partner/TPP or an approved integration route. Bank login and SCA remain on the bank's official flow; show bank balances/transactions only with lawful scope/valid consent; any transfers or durable financial changes require fresh explicit transaction approval/bank SCA. A normal personal Android app cannot assume unlimited regulated banking access merely by saving bank username/password. No bank credentials in prompts, logs or screen projection.

WhatsApp/Facebook/other third-party accounts: prefer official APIs when approved and available, otherwise owner-approved Android app UI observation for on-device tasks within service permissions, respecting provider restrictions. Preserve signed-in state of the provider's own app, do not import passwords/cookies. Send/post/delete/transfer always separately approved.

Security acceptance: revoke stops access; no network use in offline mode; no personal email/contact/financial payload leaves device except to specifically consented provider path; scrub secrets and protected screens from telemetry, evidence screenshots, crash logs and AI prompts. Some explicit OS grants (especially Android MediaProjection capture) must be repeated per session; do not bypass.

Official references:
- Google OAuth offline refresh: https://developers.google.com/identity/openid-connect/openid-connect
- Gmail scopes and verification: https://developers.google.com/workspace/gmail/api/auth/scopes
- Oberbank XS2A: https://www.oberbank.at/xs2a-schnittstelle
- Android MediaProjection session consent: https://developer.android.com/about/versions/14/behavior-changes-14
- Android AccessibilityService: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

## 5. Bots, real learning and controllable autonomy
Preserve existing personas, chief role and two sequential specialists, task journals, skill library, offline worker limits. Do not imply 3 simultaneous independent LLMs or actual model-weight training from profile selection. Finish the live failed two-specialist workflow (#670), holdout/negative/transfer tests, and correct status receipts.

Add capability-aware agent selection; permit each named agent access ONLY to explicit, scoped skills/tools and approved account grants, never automatic global access from profile name. Model proposals are non-authoritative. Maintain clear skill versioning, evidence, sandbox tests, prompt injection barriers, token/compute budgets, cancel and rollback. Separate (1) stored experience/retrieval, (2) measured improvements in parsing/routing, (3) any verified adapter/weight training, with baselines and holdout evaluation; rollback bad updates. Owner controls local-only autonomous background jobs and can pause/revoke them.

Do not obscure incomplete tasks; no silent infinite retries; interrupted/uncertain tool effects require review before resubmission.

## 6. Three coordinated dev workstreams (if actual agent orchestration exists)
**Agent A – cinema/technical art**: high-quality GLB scene assets, room/environment, transform animation/timeline, real spatial windows and projected textures, measured avatar head/eye/hand gestures, shaders and thermal-aware rendering. Build on #747/#757, not a duplicate engine. Provide before/after renders with exact source SHAs and device GPU evidence.

**Agent B – Android execution & tool plumbing**: PDF route repair #760, real app/browser/file sessions, Android accessibility/MediaProjection consent, event-to-scene receipt bridge, safe system analysis/cleanup and thermal profiling. Show genuine WhatsApp/Chrome/PDF workflows, including negative permission tests.

**Agent C – account/brain/agent platform**: encrypted multi-account OAuth grants, Gmail/Drive/Calendar read-then-draft flows, lawful Oberbank investigation/integration, safe consent/revoke UI, chief/specialist task recovery, personal memory and learning regression; independent security and privacy review.

A single integrator owns merge ordering, shared event contracts, lockfile/dependency policy, test gates, build/QA, signature and release. If independent agents are unavailable, execute the three workstreams sequentially; do NOT assert background agents were started.

## 7. Integration/commit/push rules – NEVER orphan finished work
- At beginning check live remote HEADs, compare to source Code53 and #747/#757. No branch may be silently discarded or force-pushed.
- Work in meaningful, reviewable changes on existing integrated product architecture. Every coherent passing milestone is COMMITTED and PUSHED immediately; label unfinished changes WIP, with exact SHA, passing/failed/not-run tests and ownership. Avoid untracked local-only 'done' reports.
- Maintain one integration ledger mapping every PR/branch/commit to INCLUDED/REJECTED/WIP/NOT_YET_TESTED and owner-cited rationale; **do not confuse a PR stacked on another branch with an already merged/released feature**.
- Integrate approved production-ready changes on the shared integration lineage, resolve merges without clobbering the other agents' contributions. Run relevant JVM/unit/Android instrumentation/tests, lint, actual build, sign with the existing owner's original protected signing flow only after normal gates. Never weaken environment reviews, replace original P12, create second package or claim APK installable before verification.
- Preserve key working features: Maya, hands-free resume, original avatar/orb, code53 real WhatsApp launch, memory, existing tools, manual/automatic voice stop semantics. No Cinema-wide off switch as heat workaround.
- When graphics build/test not run, label NOT_RUN; when Fold7 cannot be tested, report that honestly. A beautiful mockup with 100%-progress is never evidence of tool completion.

## 8. Release acceptance, ordered slices
A. Fully functional & safe real PDF task in the cinema office; capture and test actual document page and event timeline, gaze/lighting, result dialogue, no thermal stop on normal short use.
B. Real Android WhatsApp-open, actual verified screen state and owner-contact choice; no false contacts/sending, proper OS consent, truthful 3D projection or explicit handoff.
C. Real Chrome research, cited authentic sources and actual browser/window session, staged in same Cinema world.
D. Account hub: two Google identities with different scopes, one-time provider sign-in and refresh, deliberate expiry/revocation, Gmail read-only, draft+send approval; optional Oberbank *sandbox first*, then regulated production only if authorized.
E. Device assistant: measurable heat/CPU/RAM/storage diagnosis, preview/limited cleanup with explicit consent and logical bytes/free delta, rollback when possible.
F. Agent team: Iris/Archiv/Atlas two completed distinct receipts (ANALYZE+REVIEW) after failure recovery; learning/holdout/rollback demonstration.
G. Full Fold7 internal/external display performance, thermal, battery, signed update continuity, accessibility, readable typography. Preserve filmic identity even under reduced frame budget.

For each slice publish exact text of command, UI video/frames if safely captured, real tool receipts, failures/unknowns, code SHA, test command and Android/Fold7 validation. Any missing item -> NOT READY, not 'implemented'.

## 9. Owner project expectations
The owner needs one integrated JARVIS product, not independent demo apps, loose branches, mock videos or repeated login forms. Maintain a directly visible owner-facing progress ledger: DONE (proved), INTEGRATED (commit/merge proven), WIP, BLOCKED, NOT_RUN. Do not ask the owner to re-explain this vision. This master spec is a committed requirement, NOT a statement that Gmail/Oberbank/3D office have been coded.
