# Issue340 M2 pointer candidate — FROZEN, author DEV only

No Blind-C input, labels, cases, or held-out directory were read. Curator freeze notice was received before this final candidate/checkpoint freeze. Do not tune source/weights after unblinding. No app source, production checkpoint, legacy/Blind-A/B data, branch, commit, push, or cached main classes were modified by this agent.

Source/checkpoint/corpus/report digests: FREEZE.sha256. Candidate source SHA256 9c7d33b64d7fb1b13e3ceb22f2b538f3e4e62608d477f7e9868ce1f3b79c5f2e. Checkpoint SHA256 629c8319ede2aba3cd60f0448b6469034c8550530e0326739b79c26bfc40939d, 910 bytes. All four author sources are new tools files.

Reproduce from repo root:
```bash
bash tools/continuum-span-research/cdx2-pointer-dev.sh /workspace/continuum-checks/issue340-pointer-reproduce
```
Uses installed Kotlin2.0.21 + JDK21, existing cached app classes read-only, -module-name app/-Xfriend-paths. New classes go only into destination/classes. It generates TRAIN240 seed17 and DEV100 seed29; fixed four trials20/60 epochs x rate .05/.15; DEV NLL selects60/.15. TRAIN/DEV subjects Na-/Li-; locations Te-. No verifier result or gold answer enters runtime features. Runtime prediction reads only question/source fields even when supplied JSON contains other fields.

CLI protocol:
```bash
CP=/workspace/continuum-checks/issue340-pointer-dev/frozen/classes:/workspace/continuum-checks/post-fable-final-harness/main:/workspace/continuum-toolchain/json.jar:/workspace/continuum-toolchain/stdlib.jar
java -cp "$CP" at.heinz.deviceai.learning.span.Cdx2PointerCandidate predict /workspace/continuum-checks/issue340-pointer-dev/frozen/checkpoint.json REQUEST.json FRESH_OUTPUT.json
```
Request: {"question":"Wo ist Li-Vara?","sources":[{"sourceId":"DEV-cli","revision":7,"text":"...","sha256":"64-lowercase-hex"}]}. Output RAW/VERIFIED/RULE each {decision:NOANSWER} or {decision:SPAN,text,sourceId,revision,sha256,start,endExclusive,offsetEncoding:UTF-16}. RAW fixed before existing verifier and rule baseline. Contract checks hash/source/revision/UTF16range. No new DB or app caller.

Observed RED: initial zero-output scaffold exited1 with RED_COPY_AND_CHANGED_VALUE; final zero-weight model red-copy.log reproduces same failure. Overflow-model RED exited1, then bounded finite weights fixed it. Final GREEN: controls.log, 38 behavioral/finite-difference-gradient/loss/immutability/negation/time/unknown/model-validity checks. Finite-difference error tolerance1e-7. A failed partial harness-only compilation had cross-module Kotlin smart-cast errors; authoritative runner compiles all three Kotlin files together and succeeds. JSON protocol test passed exact hand-checked tuple Te-Tal at39:45; initial ad-hoc expected37:43 was a hand-count error corrected to39:45, with no source change. Three independently generated trained checkpoints were byte-identical.

DEV: RAW100/100 exact full tuples, VERIFIED100/100, RULE100/100. Supported45/45; NOANSWER55/55; unsupported positives0; false-source assignments0. TRAIN classifier NLL1.3935530535947087 -> .0028133692902478657; DEV classifier NLL .0027843158600500124. This is span/no-answer classifier NLL, NOT byte-generation NLL. Training grid wall781.007446ms and main-threadCPU739.448081ms; JVM other-thread CPU is not included. 11 learned parameters. Full per-case data in dev-report.json and TRAIN/DEV.json. Cold single-request RAW204.900605ms includes class/regex warmup; warm DEV latency is reported in dev-report.json.

Limits: handcrafted German question/entity/relation/static-location/risk grammar is strong prior knowledge. Shared weights learn a narrow decision distribution; no free byte generation, general learned language understanding, production promotion, Android/Fold7 benchmark or legacy improvement claim. Coordinates capitalized1..3-word spans and bounded4-source/4096UTF16 requests. Source-wide risk flags conservatively reject unrelated risk words. DEV grammar and labels were authored by same implementer, so100/100 is development fit only. Independent Blind-C remains NOT_RUN by this agent. Root owns independent review/evaluation/commit and full project tests. git diff --check passed; no full project suite run by this agent.
