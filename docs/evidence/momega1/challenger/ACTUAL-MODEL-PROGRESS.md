# Actual-model review checkpoint

Approved model full SHA+size independently matched, clean official runtime source
and build tools verified. Author official binary hash independently matched:
c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a.
Independent second build in progress; no final reviewed-pair approval yet.

First challenger blind native invocation used frozen37e16cef sandbox/prompt API,
actual approved model/runtime retained FDs, fresh synthetic prompt. Result RED:
exit137 after74368ms; raw generated output contains markdown JSON plus repetition,
strict parse fails. No bounded accepted proposal and no normal terminal decode
counter. Hard120CPU-second limit is the likely termination cause; the wall watchdog
did not fire. This first run does NOT satisfy normal-completion acceptance.
Pins unchanged before/after. All raw synthetic artifacts retained in
actual-native-first/. No raw private data or secrets.

Initial own sampler peakRssKiB=924 observes only bwrap and misses nested native
process; this is NOT model RSS. Do not publish it as memory performance. Python RUSAGE_CHILDREN also undercounts the bwrap inner process in this environment
(8448KiB for a64MiB child), so it is NOT validated as native telemetry. Follow-up
uses executable-inode-matched /proc samples, calibrated with a64MiB native child:
66100KiB observed and one matching process. Source/review scripts stay frozen or versioned; no failed result erased.

Brief overlap with author's second smoke happened despite prior coordination;
author stopped further inference and performance is marked contended. Reviewer
source/task/expected tests remain unchanged. Native author is adapting upstream
counter/trailer syntax and generic structured-output format using own prompts;
challenger did not disclose its task content or accepted patch.
