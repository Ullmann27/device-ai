# Device AI: bounded learning implementation

Base inspected live on 2026-10-13: `cd3fb3bde7301ee6efc4a3323a2aa58ec00fa57c`, PR #19, branch `copilot/pr-12-working-first-vertical-slice`. Its workflow 38421725703 was independently checked as successful. This is evidence for the base only; check the new commit's workflow separately.

## Decision and audit

The actual `sendCurrentInput()` path calls `BootstrapBrain`, not the optional model-driven `ContinuumIntelligenceCoordinator`. Existing bootstrap memory stores 32 explicit text facts, 24 approved exact command mappings and feedback counts in app-private SharedPreferences. Feedback counts do not train a model. Existing `CognitiveCore` defines interfaces; `MemoryEcology` contains provenance, evidence policies, retrieval and graph relationships, but not a typed inference engine. `ExperienceCompiler` groups distinct successful tool experiences into reviewable proposals. `Deliberation` coordinates bounded model calls; it is not independent symbolic reasoning. `ContinuumState` provides immutable typed content objects; the existing bootstrap memory is separate from the signed Continuum archive.

This change extends the existing bootstrap dialogue/persistence, preserves the signed identity/archive and ToolGate, and adds no replacement general agent framework. Structured reasoning is rebuilt from the current confirmed facts on every query; corrections cannot leave a cached old conclusion. Existing archive/model/research text is NOT automatically treated as confirmed premises. Consolidating the separate bootstrap and Continuum archives needs an explicit migration design and is not claimed here.

## Productive knowledge and online learning

Use the existing chat (one command per message):

```
Merke dir: Fakt: Nova | ist | Mobilgerät
Merke dir: Regel: Mobilgerät | besitzt | Betriebssystem
Prüfe Wissen: Nova | besitzt | Betriebssystem
Korrigiere: Fakt: Nova | ist | Mobilgerät => Fakt: Nova | ist | Sensor
Widerrufe: Regel: Mobilgerät | besitzt | Betriebssystem
```

`Fakt` is one signed triple. `Regel: C | R | V` means every explicitly known member of C has R/V. `ist` is membership; a rule concluding `ist` permits another hop. `nicht R` is explicit negation. This is a small controlled language, not natural-language understanding, arbitrary first-order logic, or a planner. Facts are confirmed user assertions, not independently verified world truth. Output labels that distinction and lists content-hash source references and derivation steps.

The engine returns supported, refuted, conflict, unknown or limit. Missing facts are not false. Opposite literals conflict; a conflict in an intermediate premise taints its descendants. The engine conservatively abstains when its selected proof uses a conflict, even if an alternative clean proof might exist. Both opposing sources are shown. Corrections/revocations require exact old content and an Android confirmation; other facts are preserved.

`OnlineNaiveBayes` performs Laplace-smoothed incremental multinomial learning. It has separate predict/learn/evaluate/save/restore/reset functions. Production rebuilds the bounded derived counts from the existing approved command corpus, avoiding a second independently mutable command database. Unknown phrases may receive a **suggestion only**; no probabilistic output creates a `DEVICE_TOOL` decision. Exact approved commands still use the existing read-only ToolGate path. Scores are not calibrated probabilities; unknown vocabulary/weak scores abstain. A numerical boundary at 0.60 uses a 1e-12 tolerance.

## Integrated neural experiment

The learner is reachable directly from the chat and the existing Lernlabor button:

```
Neuron Demo
Neuron: Hallo
Neuron Status
Neuron lerne: Servus mein Assistent => Gruß
Neuron lerne: Wie lernst du dazu => Frage
Neuron zurücksetzen
```

Demo, teaching and reset each show an explicit confirmation. Training runs on `localAgentExecutor`; the existing Stop button requests cancellation. Cancelled/partial jobs do not publish weights. Existing custom lessons prevent silently rerunning the demo over them. Read-only predictions do not train anything. Reset touches only the experiment. Damaged persisted state is rejected rather than silently replaced; explicit experiment reset remains available.

The original Kotlin MLP has deterministic `java.util.Random(seed)` initialization, bounded inputs, 8 tanh hidden units, one binary output, numerically stable binary cross entropy and analytic backpropagation/SGD. The conversation experiment uses 32 hashed word features, 273 parameters and seed 42. It learns to distinguish greetings from questions; responses are explicitly labelled **prewritten**, not generated language. No claim of consciousness, autonomous intelligence or cloud-level ability is made. A separate 2-input nonlinear XOR experiment tests the same training implementation without increasing model complexity.

Snapshots contain weights, seed, update count and the approved corpus, are versioned/size-bounded/checksummed, and are stored under new keys in the existing SharedPreferences. Checksums detect corruption, not an attacker who can rewrite app-private storage. Existing fact/command/identity keys are retained. Content remains app-private, without new content encryption.

## Reproducible measurements and limits

Run `gradle :app:testDebugUnitTest` and `gradle :app:assembleDebug`. New tests are under `app/src/test/java/at/heinz/deviceai/learning/`. No new Gradle dependencies, pretrained weights or data assets are needed.

Local JVM check used OpenJDK 17, Kotlin compiler 2.0.21, JUnit 4.13.2 and an Android compile API jar for the unchanged command interpreter. This is NOT an Android device test or the project's Kotlin 2.4/Gradle build. 27 tests passed (9 existing bootstrap tests + 18 new tests). Initial new reasoning and integration assertions were observed failing on the previous behavior before implementation; absent new learner APIs initially failed compilation. No claim that every test individually followed a red/green cycle.

| Evaluation | Before | After | Data separation |
|---|---:|---:|---|
| Two-hop knowledge questions | 0/12 correct, 12 abstentions | 12/12 correct | Fresh object/category/resource names; no answer triples supplied |
| New command paraphrase suggestions | 0/2 | 2/2 | Four approved training phrases, two distinct query phrases |
| Neural synthetic XOR | training loss 0.7122959282 | 0.0050508142; holdout 16/16, loss 0.0674383950 | 16 training points, 16 disjoint test points |
| Neural greeting/question | holdout 2/8; training loss 0.7010074443 | holdout 8/8, loss 0.0090170785; training loss 0.0009741803 | 16 authored training strings, 8 distinct held-out combinations |

The reasoning baseline was the actual `BootstrapBrain.kt` read from the pinned base snapshot, compiled alongside the new code under `OriginalBootstrapBrain`; only its object name and duplicate type declarations were changed. It was not simulated by a hand-written dummy baseline. Its existing `BootstrapTraining` dependency does not participate in the fact-only queries. Both the knowledge and suggestion comparisons proposed **zero** device commands. Zero wrong answers in these tiny positive fixtures is not an estimate of general error rate; contradiction, unknown, malformed, cancellation and corruption cases are covered separately.

Single warmed JVM run for 12 reasoning queries: old 2,976,392 ns / 137,840 allocated bytes; new 1,276,192 ns / 939,088 allocated bytes. These are one-run thread allocation and latency observations, not retained heap, peak RAM, Android battery measurements or a speedup claim. New inference allocates more because it builds proofs and source hashes. Neural greeting training took about 25–30 ms for 4000 updates on this host. Device measurements remain open. XOR snapshot: 320 bytes; dialogue snapshot after demo: 2566 bytes. New learning Kotlin sources: approximately 27 KB. APK delta must be measured from comparable CI artifacts; JVM class bytes are not a DEX/APK measurement.

Hard ceilings: 32 facts × 320 characters; 256 derived literals; 4096 rule checks; 16 derivation levels; bounded token/field input. Naive Bayes: 6 labels, 256 vocabulary terms, 256 examples, 32 tokens/example. Neural: input width 1–32, 8 hidden units, batches ≤128, steps ≤5000/call, wall limit ≤5 s (app uses 2 s), ≤100000 cumulative updates, 64 dialogue examples. Weights must be finite and bounded; malformed snapshots and oversized inputs are rejected. Finite-difference tests independently check every gradient on a small network. No new network, background service, tool permissions or identity mutations.

## References and licensing checked

No third-party source was vendored or translated. Mathematical methods were implemented specifically for this project; these projects are references only.

| Reference | Checked primary license/source | Decision |
|---|---|---|
| AIMA Java | MIT, `AIMA3e/LICENCE.md`, blob `330e89b7fb83cd0e699262e9cbe0a6d5d017623f`; `FOLFCAsk.java` | Bounded forward chaining, much narrower language; no AIMA dependency |
| River | BSD-3-Clause, `main/LICENSE`, blob `c3a351f973d0b1b38eadc6ae72917bfc5ce24f8e`; `river/naive_bayes/multinomial.py`, blob `ddb4e127f21879e6148b71f0546f4d0e84ec74d6` | Small original Kotlin multinomial NB; no Python/numpy/scipy runtime |
| Micrograd | MIT, `master/LICENSE`, blob `3d89960112452062baf36bb13d43a92028fc0f0e`; `micrograd/engine.py`, blob `afd82cc573777421cfec2437a402539a82b70203` | Chain-rule reference; direct dense backprop instead of a scalar autograd graph |
| MicroGPT | MIT, explicitly declared by author `karpathy` in gist comment 5999841; verified via GitHub's public comments API page 3 | No code copied; transformer deferred, not needed for the first measured network |
| JSoar | BSD-3-Clause, `maven/LICENSE.txt`, blob `ec95ba9ee989cf622f0b451cd642420f00cb3ae3` | Decision-cycle reference; no whole architecture dependency |
| EJML | Apache-2.0, `SNAPSHOT/LICENSE-2.0.txt`, blob `e454a52586f29b8ce8a6799163eac1f875e9ac01` | Arrays suffice for 273 parameters; no EJML modules added. Current Android compatibility/APK overhead not tested |

MicroGPT author declaration: https://gist.github.com/karpathy/8627fe009c40f57531cb18360106ce95?permalink_comment_id=5999841#gistcomment-5999841 .

Primary URLs: https://github.com/aimacode/aima-java ; https://github.com/online-ml/river ; https://github.com/karpathy/micrograd ; https://karpathy.github.io/2026/02/12/microgpt/ ; https://gist.github.com/karpathy/8627fe009c40f57531cb18360106ce95 ; https://github.com/soartech/jsoar ; https://github.com/lessthanoptimal/ejml . Any future source copying needs preserved notices and exact revision licensing review.

## Handoff

1. Check the new commit's real CI logs, not this base-CI reference or the PR description.
2. On an Android device run the above commands; restart the app and repeat `Neuron: Hallo`; confirm fact correction/revocation, cancellation and experiment reset preserve other memory. TTS/UI execution has not been device-tested here.
3. Add a larger frozen out-of-distribution language benchmark before expanding vocabulary or advertising conversational quality. This tiny split is a development fixture, not an independent benchmark.
4. Measure Android latency, peak heap, battery cost and comparable APK size. No transformer or EJML addition until the small experiment passes those checks.
5. Future archive unification must preserve the explicit-confirmation boundary, revocation semantics and provenance; no automatic promotion of model/web outputs. Do not merge main as part of this task.
