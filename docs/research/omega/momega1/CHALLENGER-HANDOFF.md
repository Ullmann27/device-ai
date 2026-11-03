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

Freeze checkpoint: 071ef7f3, pushed. Draft PR #416.
Frozen provider baseline: 9 tests, 6 PASS / 3 FAIL, exit 1. Failures are explicit
forged verifiedModelExecution=true claims accepted as shadow proposals; baseline
still returns verifiedModelExecution=false. This is strict malformed-receipt
handling, NOT demonstrated promotion to trusted execution.

Independent namespace capability: environment-probe.py exit 0. Real bwrap child
net namespace differs, socket interface enumeration returns only lo, synthetic
sentinel write fails EROFS and content remains unchanged. No remote connection.
This is HOST_CAPABILITY_ONLY and does not certify any candidate's sandbox.
Synthetic C runtime modes freeze subprocess probes before candidate source reads;
these binaries only print fixed JSON and cannot be counted as actual inference.
