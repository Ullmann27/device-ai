# Continuum brain checkpoint — 2026-10-25

Official branch `sol/continuum-infrastructure-repair-20261025`, Draft [PR #150](https://github.com/Ullmann27/device-ai/pull/150). Owner requested a clean stop with all useful work committed and pushed. No main merge, owner-data change, weight promotion, signing or installation.

## Source and measured results

Brain implementation freeze: `bc33e09d7f25339b9d7617e90c124a8341726de1`. Research protocol/evaluator checkpoint and final Android test source: `c3ebad7ced76b5edff0721a883f1e332044755a6`. Subsequent closure changes archive evidence/handoff only.

| Item | Actual outcome | Evidence |
|---|---|---|
| Single explicit mixed letter/digit identifier grounding | Wrong IDs no longer borrow another identifier's memory/source; exact positives and original weights/facts retained | `identifier-node-before.txt`, `identifier-node-after.txt`, Kotlin before/after logs |
| Focused Node regressions | 30 pass; all 8 newly introduced identifier cases failed before repair | `identifier-node-after.txt` |
| Focused Kotlin actual SDK-class regressions | 17 pass; both new tests failed before repair | `identifier-kotlin-after.txt` |
| Independent after-freeze identifier benchmark | 100→244 exact result sets /288; wrong-hit queries144→0; previous positive retrieval74/96 unchanged | `identifier-independent-holdout-v1.json` |
| Final Android SDK build of brain source | BUILD SUCCESSFUL; 17/17 focused tests,0skip; Kotlin/Java compilation; configured NewApi lint0issues | `android-focused-c3ebad7c.txt`, focused summary, JUnit XML and lint XML |
| Actual selected Gemma CPU inference | 4/4 frozen short synthetic probes correct; official native LiteRT-LM API0.18.1 | `gemma-host-probe-v1.json`, native log and runtime provenance |
| CDX1 fixed paired-training generation | Final train1/4, consumed legacy DEV0/2, independent0/16; no promotion | `cdx-baseline-v1/report.json`, manifest and four synthetic model checkpoints |

The independent identifier result includes rejection cases; it does not mean244 successful positive answers. Twenty-two existing positive misses remain. Multiple identifiers and general name/relation entailment remain unresolved, as recorded by separate negative controls. This repair changes deterministic evidence eligibility in the real memory dialogue, not neural model capacity.

Gemma used the fully SHA-verified original `gemma-4-E4B-it.litertlm` (3659530240 bytes, SHA256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`). Two CPU threads, context2048, output128, no tools or private data. Engine load4.96s after hashing warmed the file cache; short answers1.69–2.00s; peak RSS3083192KiB. This is a bounded host smoke, not Fold7 compatibility, broad intelligence, coding or throughput validation. Android still uses runtime0.18.0.

CDX1 has7746 parameters and eight causal context bytes. Initial evidence/question distinctions depend on its frozen16D conditioner because the common answer prefix occupies the causal context. At120/600 updates all six observed train/legacy-DEV generations were48 spaces without EOS. At6000 updates all six became `Nora ist Ärztin.` with EOS, and all16 independent probes returned that same wrong sentence. Teacher-forced training loss fell5.54939→0.25201, while legacy DEV loss ended4.17223 and transfer accuracy remained0/16. This confirms free-generation collapse on this synthetic protocol; it does not reproduce the unavailable Fold7 checkpoint or establish an n-specific tokenizer defect. No generation repair is claimed. All four new checkpoints are preserved without touching existing weights. The independent fixture was authored before predictions and opened only after final weights froze; now consumed, it must not become a hidden test after tuning.

## Reproduction

Use the corresponding pinned source with the tool versions and system CA settings in `../sol-infrastructure-20261025/README.md`. Existing full infrastructure checks belong to source `31e26ca48d411a73af42ec7d752af9195fd4adc8`; they are not silently relabelled as a full suite of later brain changes.

```bash
node --test .github/continuum/retrieval-*.test.mjs .github/continuum/neural-dialogue-audit.test.mjs
```

Independent identifier evaluator: `scripts/continuum-identifier-independent-holdout.mjs` takes official-checkout path and a new output-file path. It reads the two frozen Git revisions and verifies candidate identity. Complete cases/results are already archived.

Gemma probe: install SHA-verified official `litert-lm-api==0.18.1` in an isolated toolchain, use the catalog's actual original file, a clean source checkout at the probe's frozen SHA, `CONTINUUM_PROBE_SOURCE_SHA` equal to that HEAD, and `python scripts/continuum-gemma-host-probe.py MODEL_FILE NEW_REPORT_JSON`. Keep the external240s timeout and native limits; report files must be new. Original model bytes are not duplicated into Git.

CDX reproduction on actual SDK classes (requires Kotlin/JDK17, org.json20240303):

```bash
kotlinc experimental/brain/Cdx1GenerationProbe.kt \
  -module-name DeviceAI_app_debug -jvm-target 17 \
  -cp "app/build/tmp/kotlin-classes/debug:$JSON_JAR" \
  -include-runtime -d "$SCRATCH/cdx-probe.jar"
CONTINUUM_PROBE_SOURCE_SHA=c3ebad7ced76b5edff0721a883f1e332044755a6 \
java -Xmx768m -cp "$SCRATCH/cdx-probe.jar:app/build/tmp/kotlin-classes/debug:$JSON_JAR" \
  at.heinz.deviceai.learning.Cdx1GenerationProbe \
  "$SCRATCH/new-output-directory" \
  app/src/test/resources/continuum-neural-swb1.base64 \
  config/research/cdx-independent-transfer-v1.json
```

The recorded process exited successfully and saved all stage weights before test labels were read. `report.json` and base64 snapshots are byte-preserved; text logs normalize trailing whitespace only. Manifest records file hashes and the binary model hash remains in the raw report.

## Resume and limits

Continue from `docs/research/omega/SOL-BRAIN-HANDOFF.md`. Next bounded work is CDX generation-collapse repair with new independent quality evidence, then reasoning-aware main-dialogue routing through existing evidence/privacy/tool gates. No blind activation of CTX3/LM1 or new parallel framework. Gemma is not yet imported/validated on Fold7; DeepSeek remains metadata-only with no allocated inference GPU. Free-language coding→new installable APK is unverified. The working session is ending, not the long-term project.

Final Android command and exact test/source scope are recorded in `android-focused-summary.json`. This removes the pending SDK compilation blocker for the identifier repair. It does not replace the earlier whole-suite run or physical-device acceptance.
