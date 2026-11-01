# Sol #314 — CDX1, dialogue and evidence

Status: isolated Draft PR #317; source integration with host/independent evidence.
No merge, signing, publication or Fold7 verification. The final source SHA and
its Render result are pinned in PR #317 and issues #314/#315; verify Git HEAD.

## Baseline and coordination

Fresh remote release HEAD: `97b5b9006f8c60f1a6fc4fb10981894330c55fe4`.
Historical main: `2949893a7c65bdc6a875bdcb28b7a550fd9d7c3a`.
Branch: `research/continuum-sol-cdx1-evidence-20261101`.
PRs #299, #301, #305, #309–#313 are open; #310 is stacked on #309.
Astra #315 already has streaming-import PR #316. Sol does not build a second
source store or worker. Shared QA script change only whitelists this branch.
The current user mandate supersedes historical v31 research-freeze notes.
EXP-04/05/06/07/12 remain compatible; existing checkpoint formats are unchanged.

## First reproduced defect and repair

On the actual CDX1 implementation, seed 91 / SWB1 seed 73, 120 synthetic updates:
train `Anna wohnt in Graz.` / `Wo wohnt Anna?` / `Graz`;
validation `Berta wohnt in Linz.` / `Wo wohnt Berta?` / `Linz`.
Training NLL 5.5531206598 → 4.5374142234;
validation NLL 5.5516778578 → 5.1859544910.
The baseline accepts and publishes an incorrect free answer of 48 `a` bytes.
This is a synthetic HOST reproduction, not the unavailable private Fold7
checkpoint whose reported prefix is `99ea2346547dae3d`.

The first-byte suffix is **`ntwort: `**: `Antwort: ` is nine bytes, not eight.
Different evidence/questions with equal conditioning give identical first-byte
distributions. The conditioning vector is the only remaining input distinction.
This observation alone does not prove the entire device collapse mechanism.

`DecoderGenerativeGate` now measures free sequences including EOS, exact factual
answers and explicit abstention separately. Promotion requires ≥20 supported
validation questions, ≥5 missing-evidence controls, ≥80% supported correctness,
all controls explicitly abstained, all sequences terminated, a strict paired
gain and no previously correct answer lost. Existing NLL/protected gates stay.
The published 25-case intelligence TEST is preserved and never used for selection.
Historical known-only validation cannot establish abstention and therefore no
longer promotes weights. Existing checkpoints are retained; direct experiments
can still train an isolated in-memory `TinyContextDecoder` candidate.

RED: new regression fails specifically at the old erroneous acceptance.
GREEN: actual Kotlin 2.0.21 / JRE21 / JUnit4.13.2, focused 28 tests, resources on
classpath. Not Android Gradle/Lint/APK/device evidence. The earlier resource-less
test invocation failed on missing fixture; rerun includes unchanged fixtures.

## Reproduction and continuation

Compile `LearningSnapshot`, `TinyContextDecoder`, `TrainableSubwordEncoder`,
`ContinuumPairedEncoderBinding`, `ContinuumAtomicPairedBundleStore`,
`ContinuumDecoderBridge`, `DecoderGenerativeGate` and the four JUnit classes
`DecoderGenerativePromotionTest`, `DecoderGenerativeGateTest`,
`ContinuumDecoderBridgeTest`, `TinyContextDecoderTest` with Kotlin/JUnit.
Run JUnitCore with `app/src/test/resources` on the classpath; Render runs these
through the original Android Gradle QA script on the exact pushed commit.

Independent review found and prompted corrections: free scoring now checks
cancellation/deadline before/between items and inside byte generation; the
shared budget is the Android runtime's 48 bytes. Structurally ineligible large
curricula return `INSUFFICIENT_VALIDATION` without training, retry or cursor
consumption. The added qualifying 20+5 bridge regression verifies that improved
NLL still cannot publish an incorrect generator and preserves an existing
checkpoint. Focused host suite: **30/30 passed**. No original tests removed.

Render first-commit QA: service `srv-dbjatfetlv9c73cmufs0`, deploy
`dep-dbjatfmtlv9c73cmugig`, source `b7ca85ba9a05f8ff626dfea9e71fa1c2bd2adfc9`;
started 2026-11-01 03:15:10 UTC, PASSED. Later `6bc99e13` also passed.
Last independently accepted memory source:
`8c40d111c557e9ebb0d19f807a098c1f5d196966`, deploy
`dep-dbjb9plh3lcs738aao40`, live 2026-11-01 03:53:54 UTC.
HTTPS STATUS confirms BUILD_AND_TEST_GREEN_UNSIGNED_NOT_INSTALLABLE, package
at.heinz.deviceai.continuumlocal, QA code35, signed/published FALSE.
The final closeout source needs its own exact-SHA QA. The static site serves
audit text only; it is not an active worker.

Completed:27 controlled context/conditioning/seed ablations, all0/20 and0/5.
See `docs/evidence/sol314-20261101/CDX1-ABLATION.md` and both JSONL reports;
the initial incomplete larger-context run is retained separately. Larger
context requires a checkpoint-format migration and delivered no gain here.
Model/device tests remain unavailable until actually run. Rollback is to drop
this isolated branch; no owner data migration or model checkpoint replacement.

## Named-owner role / temporal retrieval candidate

Actual runtime path: AgentActivity.runContinuumDialogue → CognitiveDecisionBridge
→ ContinuumCognitiveRuntime → ContinuumLearningCore.replyWithEvidenceScoped →
ContinuumMemoryDialogue.replyInConversation/rank. The change runs in this existing
path; it does not enable the optional EWM2 brain switch or create a new ledger.

RED: three new actual-runtime-class regressions exposed wrong-person property
citations, superseded residence ordering and unknown-person borrowing.
The first EWM filter passed these three, but independent first-run TEST-v1
showed **8/50→33/50 exact evidence sets** with dangerous remaining errors and a
material resource regression. That candidate was rejected before commit. V1 is
now consumed for debugging; it cannot be relabelled as an untouched final TEST.

Replacement: `OwnerAttributeEvidenceScope` uses explicit named-attribute grammar,
an exact grammatical holder and predicate, single-assertion safeguards, latest
owner assertion / explicit reactivation time, negative/revoked invalidation and
tie/first-name ambiguity abstention. Only eligible confirmed owner citations may
survive; public source text cannot answer these personal attributes. At most16
matching assertions ≤512 chars reach the existing EWM reader, after cheap bounded
header filtering. No all-ledger quadratic EWM graph per query. The existing lexical
ranker remains unchanged outside this grammar; this is no general semantic parser.

`Und Annas?` rewrites an explicit genitive in the prior supported question within
the same existing persisted conversation. Missing context and unsupported shapes
abstain. No temporary example is promoted into personal memory, no checkpoint or
history format changes. This does NOT yet implement temporary scenario assertions,
key swapping, arbitrary long-context understanding or a general temporal database.

Host closeout gate: **53 actual Kotlin/JUnit tests passed**, including eleven
role/dialogue tests, imported perspective tests, excerpt integrity and retained
decoder/encoder tests. Reproduce with
`scripts/continuum-sol314-host-check.sh` and documented classpath environment vars.
Independent40-case TEST-v2, hash
`2bf6acb02afc33a92422994d590b1289c187ee85ffc55a3148e057b5571ad8c0`,
ran ONCE on8c40d111: **6/40→37/40 exact evidence sets**,31 paired wins/0 losses;
18/20 answerable exact,19/20 abstentions. Declared grammar35/36,
18/19 answerable and17/17 abstentions. Reverse ownership wording abstains;
historical/future questions remain broader limits. Relevant-hit recall can fall
when ambiguous forms now abstain, even with zero exact-set losses. Do not equate
retrieval evidence-set scores with neural answer accuracy.

At256 short alphabetic-name owner records, rank P50/P95 baseline12.890/28.865ms
→13.234/24.911ms; correct owner selected alone. Long assertions >512 chars
fail closed in this scope; that stress case is NOT successful long-fact retrieval.
All original/failed/consumed/fresh outcomes, frozen corpora, metric definitions,
source hashes and portable no-binary replay tools are committed under
`docs/evidence/sol314-20261101/independent-review`. V1/v2 are now consumed.

## Final targeted changes and remaining verification

- `OwnerMemoryPerspective` and its original tests were selectively reused from
  PR #309 (4396d924), after reading the actual patch. Display uses Dein/Deine
  for a confirmed owner statement beginning Mein/Meine; original evidence and
  ledger bytes stay unchanged. Both normal dialogue and EWM ON use this formatter.
  Direct regression: Mein Testkürzel lautet NEBEL-731 → Dein Testkürzel, with
  the original literal quotation retained.
- Temporal regression preserves `Mira wohnte früher in Wien.` while the current
  question cites only `Mira wohnt jetzt in Graz.` No general temporal parser or
  automatic permanent memory for temporary examples is claimed.
- `ContinuumEvidenceWindow` replaces the fixed first350-character archive
  prefix with a bounded exact lexical quote, source/snippet SHA and UTF8 offsets
  in the canonical archived excerpt. Missing complete anchors/hash mismatch
  abstain. Three original prefix regressions and the independent overlong-anchor
  failure were observed before fixes. Source text remains unverified data.
- AgentActivity and optional EWM source answers use the same quotation function;
  EWM reopens the full verified existing record via `readSource`, because the H5
  index can contain truncated text with the full-source hash. No new source store.
- EWM ON needed a final invalidation gate: `brainAnswerView` reads the same
  conversation's resolved question plus all ledger invalidators, so a different
  active-only path cannot resurrect an older fact. Added explicit and two-turn
  possessive revoked-latest integration regressions. These Core/Brain tests require
  the final Render Android run; pure host53 is not proof of that integration.

New/modified production files: ContinuumDecoderBridge, DecoderGenerativeGate,
TinyContextDecoder, ContinuumCdxLargeLab, ContinuumMemoryDialogue,
OwnerAttributeEvidenceScope, OwnerMemoryPerspective, ContinuumEvidenceWindow,
ContinuumKnowledgeArchive, ContinuumLearningCore, ContinuumBrain, AgentActivity.
Corresponding JUnit, causal-ablation/host scripts, branch-only QA whitelist and
evidence/handoff files are in this PR. No model/checkpoint/schema/signing migration.

## Exact continuation for Astra / next Sol

1. Fetch this branch and verify PR317 HEAD, clean checkout and the source_commit
   in `https://continuum-sol314-cdx1-evidence-qa.onrender.com/STATUS.txt`.
   Require final exact-SHA Android JUnit/Lint, Node/backend and unsigned APK/AAB
   PASS; do not reuse8c40d111 for subsequent source changes. Inspect queued/failed
   deploys before triggering another one. All full QA remains on Render, not Actions.
2. Review the committed15-PR/57-patch integration matrix under `integration-review`.
   PR198/203/205/214/285 are represented by ancestry;281/267 by selective commits;
  224 was productized later. Do not integrate221's unresolved native-lock rewrite.
   Pending ARGON/claim/timeline experiments are not silently productive.
3. Integrate selectively with305/309/310/311/313 and Astra316. Reconcile shared
   Activity/MemoryDialogue/QA hunks; perspective309 is already reused here. Retain
   long-input offline opt-out310 and original caller-side consent/ToolGate rules.
   No competing file store or worker was added. Quote offsets reference archived
   canonical UTF8 text; Astra's100-MiB raw-file chunks must preserve that distinction.
4. Next CDX scientific question: why does evidence+question fail to determine
   the FIRST generated byte? Use one frozen decoder+its pinned SWB1 bundle. Compare
   evidence-only/question-only permutations, pairwise16D distances, first-token
   full probabilities/gold rank, entropy/EOS, per-position teacher-forced NLL and
   free-generation divergence. Raw8-byte seed91 had mean conditioner distance
  0.016798, first-byte2/25, entropy4.067867nats and EOS0.0009953. Do not rerun27
   scaling variants without a new falsifiable mechanism. Keep Gemma, SWB1, CDX1
   and CSX research identities separate. The private device bundle is unavailable
   here; synthetic host checkpoints do not prove its complete causal diagnosis.
5. Remaining bugs/limits: temporary scenario facts/key swaps, general anaphora,
   reverse ownership, compound/multiclause or >512-char owner facts, broad historical
   and future questions; normal grounded `Erkläre …` can still suppress Gemma due
   the narrow synthesis router. Keep these open; no further feature expansion was
   attempted after the owner's final-budget instruction.
6. Final owner release remains separate: package at.heinz.deviceai.continuumlocal,
   versionCode>35, original certificate6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23,
   independent APK bytes/hash/signature/package readback AND real Fold7 tests.
   No owner data, backups, keys, weights, archive or permissions were changed.
