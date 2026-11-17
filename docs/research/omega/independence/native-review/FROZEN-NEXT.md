# Independently frozen next cases — no native execution yet

S01 remains the failed consumed case documented atab87f3f1, including its incorrect
1536-token metadata description. Nothing below replaces that failure.

Two separately named cases were frozen before this reviewer inspected the pending
runtime/service fix or any resulting model output:

| Case | Purpose | Initial owner RED | Extra independent RED | Manifest SHA256 |
|---|---|---|---|---|
| independence-dev-u03 | KNOWN development diagnostic, original consumed u03 | 1 test /1 failure | No new holdout claim | `47ad18afd93048eb3f7cc6b50f9ecf5ec8884fd818870a66801ceb49b99afea2` |
| independence-s02 | New controlled raw-text escaping defect | 6 tests /4 failures | 4 tests /4 failures | `3a836f7cc14ee3afdcaa6a102a8396720fa4eaa90cace5b0725668c5bffcbbdb` |

The known task reuses original broken source/test bytes (only test import is
relocated) from u03. A previously successful repair is known, but no solution is
supplied to the local model. It is not unknown-task or learning performance.
S02 concerns a different algorithm from u03, median, rotation and prior A0 cases.
No expected fixed implementation is created. Its independent tests stay outside
the owner/model checkout and are separately executed on the exact returned patch.

Chief narrowed the proposed240000ms plan **before this freeze**: request exactly
`modelTimeoutMs=120000`, with unchanged persistent profile total deadline120000ms.
Effective per-call timeout is the minimum of requested120000ms, remaining
assignment lifetime and remaining canonical deadline, minus500ms. Native CPU
ceiling is `ceil(effectiveTimeoutMs/1000)`, at most120 CPU-seconds. Baseline,
lookup and tool work consume the original total budget. Do not silently enlarge
profile deadlines. Fixed actual launcher output-token ceiling1800, context8192,
two native threads; pin/model/runtime unchanged. Record actual admitted values.

Each separate case permits exactly one reserved model request. No cloud/model
change, supplied patch, retry, fresh-ledger replacement or test modification.
Existing isolated test limits remain256MiB/64PIDs/oneCPU/10s. Unchanged source,
canonical receipts, independent LINT and ECHO/report identity, fresh process
memory eligibility and no automatic replay remain required. If no candidate
exists, extra tests and success-dependent recall are marked NOT_RUN rather than
invented. Failure remains failure. A new result cannot retroactively make a
consumed case blind again or rewrite its budget.

These preflight RED runs use reviewer-authored controlled fixtures on the host,
not a service/model proof. Actual execution waits for accepted exact service
source, validated runtime configuration, verified reconciliation of the retained
S01 resource barrier and Chief's exclusive CPU grant. No service/model calls have
started for either case. Keys/grants remain private. Owner reported20% remaining;
continuation is limited to the necessary real coding-agent acceptance.
