<!-- coderabbit-ai-generated:v1 -->

# Reliability sprint checkpoint

Base PR351 `057474de37d6e20d993bd64f166b6585bc4a357a`; EXP00/02/08/11. Scope: fail-closed check supervision and PID identity; no Android or signer changes.

Two synthetic regressions reproduced and fixed: success despite unconfirmed cleanup; root-PID reuse advancing descendant discovery floor. Process25/25 and storage/coding/architecture32/32 pass on Node24.21.0/Ubuntu26.04.1. Broad suite outstanding. Historical owner-abort and storage failures remain unproven as current runtime defects. Storage HTTP deadline regression confirms a refused write cannot commit metadata and a queued healthy write survives restart.

WIP: remaining full-suite validation and final handoff pending. Rollback: revert this sprint only. No merge or release.
