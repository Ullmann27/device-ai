# MΩ-1 independent runtime / reliability challenger

Baseline ca93fb4f; branch codex/momega1-challenger-20261103.
Exclusive writes: tools/momega1-challenger, docs/evidence/momega1/challenger,
this handoff. No feature code. Frozen criteria and initial executable provider
contract tests created before any candidate inspection. Tests are synthetic
CONTRACT_TEST, not model or device proof. Initial checkpoint WIP/UNVERIFIED.

Device observation: adb absent from PATH, no adb daemon, no /dev/bus/usb,
zero local USB nodes; tailscale absent. Policy vpn_configured=false, no TCP grants.
Environment status current with enforced unrestricted HTTP; this does not establish
private device access. No guessed remote endpoints, no installation, no download.
FOLD7_NOT_RUN. Host cgroup memory.max=17179869184, cpu.max=400000 100000,
pids.max=max. These are shared host limits, not per-model containment.

Next: run frozen baseline, push checkpoint/draft, then adapt only to narrow public
runtime API and independently assess frozen candidate. Preserve failing evidence.
Rollback: omit this tests/evidence-only branch. No owner data touched.
