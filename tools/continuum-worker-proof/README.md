# Canonical worker proof (Synapse II M3)

`run-m3.mjs` is a disposable-fixture evidence harness. It supplies no repair proposal,
model output, test replacement, worker, scheduler or command-profile override. It imports
the existing CCC1 worker, `repair-loop.mjs` fixed profiles, verifier and publisher.

From the research branch, write a fresh proof directory:

```sh
node tools/continuum-worker-proof/run-m3.mjs /tmp/continuum-m3-new-proof
```

The harness creates a tiny separate Git repository under `/tmp`, commits a known-good
arithmetic module and a typo regression, and submits the explicitly authorized fixture
repair to CCC1. The existing worker diagnoses the real failing tests and generates the
patch. The existing worker independently applies and checks that patch in a second
fresh worktree. `publishRepair({push:false})` validates and commits the candidate only
in the disposable repository. A further fresh checkout of that committed candidate
runs the original assertions. No official checkout patch, push, merge or install occurs.

The existing reasoner requests a fixed 25-minute budget; the harness grants a 30-minute
deadline and one CCC1 round. The existing owner repair worker limits candidate attempts
to three. The completed record used one attempt. All fixture tests are finite and small.

The tracked result is in `docs/evidence/synapse-ii-20261102/m3/README.md`.
This is a Linux host proof using `HOST_LEGACY` execution with isolated Git worktrees;
it does not establish an OS container sandbox, model generation, Android build,
Termux execution or Fold7 acceptance (`DEVICE_NOT_RUN`).
