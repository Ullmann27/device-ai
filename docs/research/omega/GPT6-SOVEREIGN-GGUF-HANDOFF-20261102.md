# GPT-6 Sovereign GGUF / Termux Handoff — 2026-11-02

Status: WIP / SOURCE_CHECKPOINT. Source baseline: PR #354 `41e1897b9099b911286d312a53c8c19307a49484`. Independent research branch. EXP-05 and EXP-12. No changes to Android screen/click path, ToolGate, main, owner signing, model weights or local owner data.

Objective: Upgrade existing opt-in Termux `llama-server` start path to require an owner-supplied provenance manifest and real bounded SHA-256/GGUF admission **before** starting the second local model engine. Reuse `scripts/continuum-gguf.mjs`, `scripts/continuum-gguf-smoke.mjs` and `scripts/continuum-local-llama-termux.sh`. Do not introduce duplicate model registry or claim full Android inference integration.

Until implementation/test evidence lands: WIP / NOT_TESTED / FOLD7_NOT_RUN. A matching SHA-256 is local file identity, **not** publisher authenticity, licensing clearance, server loaded-model proof, or protection against post-check file replacement. No automatic download, no model promotion, no API secret output. Safe rollback: switch to original Termux launcher from parent commit. Next: implement CLI + guarded start, regression tests, verify and update this handoff.
