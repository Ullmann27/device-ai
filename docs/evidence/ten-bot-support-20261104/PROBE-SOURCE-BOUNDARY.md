# Source binding for the synthetic weight-resume probe

The diagnostic executes the reviewed runner's module definitions. Its claims
about not constructing an experiment or starting a model are valid only for
those reviewed bytes. The CLI previously accepted an arbitrary Python source
path without enforcing that boundary.

The coordinator added a SHA-256 check before compilation or module execution.
The only accepted runner is the previously inspected source with SHA-256
`6b14f23ef9b595008099b16d81f1121783fea0bb67e7f3d942e44eed6405e425`.
Any changed runner requires a separate review and explicit update of this
diagnostic; this is not a general-purpose runner validation tool.

## Actual verification

```sh
python3 -m unittest discover -s tools/support_assist_20261104 \
  -p test_weight_phase_resume_probe.py -v
```

- RED before the check: one test, one error, exit 1. The unreviewed fixture's
  module code ran; the expected source rejection was absent, and subsequent
  access to the fixture's missing `json` member raised `AttributeError`.
- GREEN after the check: one test passed, exit 0. The changed source receives
  the expected `ValueError`, and its marker file is not created.
- An attempted diagnostic against the owner's moving checkout was rejected
  before execution. Its newly observed SHA-256 was
  `e75bbafd7585bccaa3e9ccffb6be6b94bd09e42479331de7012335de8978a36e`.
  That rejection is not evidence for or against the changed runner's behavior.
- The previously reviewed runner was then extracted from the owner's commit
  `88d7b05072bdec6250990efaa9914a1e6744c29b` into a temporary local copy, with its
  hash verified before use. The bound diagnostic again returned exit 1 with the
  original interrupted-write and three silent-resume findings. No owner file
  was changed. Raw result: `bot05-bound-resume-probe.json`.

The original weight finding remains tied to its exact source hash. The owner's
new source has not been behaviorally validated by this probe. Raw RED/GREEN logs
and the bound diagnostic result accompany this note.
