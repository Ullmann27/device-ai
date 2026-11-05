# Independent verification, declared before execution

The frozen JVM study completed all twenty predefined arms. The next check
recomputes the same 240 forward passes in a separate NumPy implementation.
It uses the original source formulas and actual frozen weights; it does not
import the diagnostic forward code or its result parser. No new prompts,
interventions, checkpoint selection or optimizer updates are introduced.

Before this recomputation, fix these tolerances for Float32 accumulation
differences between scalar JVM kernels and NumPy matrix multiplication:

- next-token probability: maximum absolute difference `1e-5`;
- EOS and best-other logit: absolute difference `1e-4`;
- activation value RMS and maximum replica range: absolute difference `1e-4`;
- activation relative RMS spread: absolute difference `2e-6`.

The original native W0/S0 parity gate remains `1e-5`. Do not revise a tolerance
after observing a failure. Preserve any failed comparison and stop additional
experimental execution until its cause is resolved. Exact bitwise trace parity
with the unchanged JVM model is a separate, already recorded requirement.
NumPy is not expected to reproduce every roundoff-dependent tied-bit count.

Additionally verify all 240 probability vectors and EOS ranks, 1,440 trace
identities, 48 native generation records, all receipt/log hashes, and decoded
input tokens against the twelve fixed texts. Recompute all aggregate findings
from raw outputs. Complete-resume must start zero JVM processes and change no
run file; malformed/source-changed/partial receipts must start zero processes.

This is an independently implemented computation by the same root session,
not a claim of another human reviewer or newly spawned helper.
