# Selected feedback lifecycle source review

Base: b1d9556c74df9aa3c6e05defc9b4fb7d94181f17. Branch: integration/continuum-feedback-review-20261026. Source copied selectively from interactive-lab frozen d7df7a45ea8002d8adaf2b45745b373416ae96dd; ResearchFeedback.kt is byte-identical. Full lab/failed candidates and raw measurements remain on research/continuum-interactive-intelligence-lab-20261026 / PR174.

Only B's synthetic research feedback lifecycle is selected: uses existing FileLearningExperienceStore, digest-bound explicit review, versioned source turns, rejected/revoked exclusion, same-process atomic stale-review rejection, contradictory-version exclusion and active-training fingerprint. No new memory codec, USER_FACT write, owner-provenance promotion, automatic training or UI hook. Natural-language classification never confirms permission.

Four native JUnit checks passed on the exact saved source hashes: durable restart/revocation, stale digest, conflicting revisions, rejected-candidate resurrection, eight concurrent reviews (one winner), and model/question/storage-request separation. Compiler is existing Kotlin2.2.0/JVM17 on Java21; full Android/Gradle/device NOT_RUN. No dependencies downloaded. Evidence in docs/evidence/interactive-feedback-review-20261026/.

This is a SOURCE REVIEW, not production-ready owner feedback. Caller authentication must use existing owner/ToolGate paths; this research class has only SYNTHETIC provenance and a separate research subdirectory. It is single-process; cross-process coordination is not implemented. A changed active fingerprint invalidates old checkpoints but does not claim unlearning their weights. No production checkpoint loader is connected.

The dialogue selector failed4/8adversarial references and is deliberately excluded. Trained SWB/CRE weights showed no quality gain and are excluded. No C–G research mechanisms are activated.

Reproduce:
```bash
git switch integration/continuum-feedback-review-20261026
CONTINUUM_FABRIC_JVM_DEPS=/absolute/kotlin-junit-jars \
  bash scripts/continuum-feedback-review-jvm.sh /absolute/new-output
```
The jar set already used by scripts/continuum-fabric-jvm-check.sh is sufficient. No signing/installation/main merge. Current v25 release source gate must continue rejecting these additional unattested sources; do not edit the frozen release inventory to make this review appear owner-approved.

Next: independent review of this small delta; explicit owner-reviewed candidate UI/API design in existing app; real SDK compilation and adversarial consent tests before any productive use. Keep private user content out of commits. Revert/selectively omit these three source/test/runner files for rollback; no existing state migration is necessary.
