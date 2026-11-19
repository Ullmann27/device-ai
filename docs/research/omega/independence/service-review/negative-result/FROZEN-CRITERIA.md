# Independent terminal-failure and resource-reconciliation gate

Frozen before viewing a new builder candidate or its API. Date: 2026-11-03.
Reported native service source: `8fd58d00c53c6c2257a102922243f413afb6bac1`.
Review branch starting point: `d2ae042fb95298ccb849a4ba05c9410f74675006`.
This is a supplemental safety gate, not a new blind model-quality experiment.

Chief reports S01 ended with canonical `ABSTAINED`, model CLI exit 1 without
output, delivery `UNKNOWN`, no public ECHO/result snapshot, and a retained global
`UNRESOLVED` barrier. A separate native verifier reportedly found no descendants
or containers. These observations remain unconfirmed by this reviewer until
their exact raw evidence and inspection implementation are examined.

## Hypothesis and smallest accepted change

A service may publish a bounded, provenance-bound negative report from its
actual canonical terminal state even when local-model inference was unobserved.
Reporting a failure does not resolve execution uncertainty, establish successful
inference, admit a repair, teach a successful strategy, or permit another task.

Resource reconciliation is a separate trusted operator operation. Direct worker
death, a failed CLI exit, an empty `ps` text report, or an LLM statement cannot by
themselves establish teardown of intermediate wrappers, process groups or OCI
work. Until independently adequate teardown evidence is bound to the exact
execution, the durable resource barrier must remain effective across restart.

## Frozen acceptance and counterexamples

| ID | Trigger | Required observation |
|---|---|---|
| N01 | Canonical terminal failure and local-model observation unavailable | A negative report may say what failed and what remains unknown; no neural success, independent repair or positive experience is invented. |
| N02 | Same terminal failure but unresolved wrapper/native/OCI execution | Negative reporting preserves the global barrier and prevents another queued task or alternate configuration from starting. |
| N03 | Direct worker exits normally/nonzero or is killed while an intermediate process remains | Exit status/PID death alone never clears the barrier; status retains uncertainty. |
| N04 | Authenticated preflight refusal before any executable model/tool work | Only the existing trusted no-execution path may resolve its own preflight intent; an untrusted boolean cannot imitate it. |
| N05 | Positive claim, malformed model metadata, copied snapshot, fabricated verifier status or unsigned metrics | Refuse promotion. Negative reporting remains attached to the actual canonical journal, exact task/source, model-attempt identity and available trusted observations. |
| N06 | Grant revoked, source changed, journal advanced or different task/instance requested during reporting | Do not silently report the old evidence as current authority. If historical evidence is offered, label it historical and non-executable. |
| N07 | Restart after a terminal failure and interrupted report delivery | Preserve one task identity, original observations and report provenance. Do not repeat a model/tool attempt or erase an unresolved barrier. |
| N08 | Reconciliation proposal targets another task/state/source/instance, stale PID identity or a replaced barrier | Refuse. Inspection must match the exact durable barrier and execution identities, and recheck before any mutation. |
| N09 | Process listing/permission failure, absent inspector, failed Docker query, or ambiguous descendant association | Mark incomplete/blocked. Missing data or an empty failed command must never mean teardown confirmed. |
| N10 | A matching intermediate/native process, surviving process group or associated OCI container is present | Do not clear uncertainty. No guessed mass-kill, unrelated PID signal or unrelated container cleanup. |
| N11 | Independent inspection supports completed teardown for this exact S01 | Produce a concrete owner/operator reconciliation proposal with evidence, attribution limits and rollback implications. This reviewer does not remove the barrier. |
| N12 | Reconciliation happens after a report or canonical failure | It may free a proven-idle resource; it must not change `ABSTAINED` into success, restore an expired grant, replay the attempt or admit a learned repair. |

## Method and budget

- Freeze this file and push its SHA/hash before candidate/API inspection.
- Review original sources and existing canonical/report/runtime/cleanup paths.
- Run only read-only artifact inspection and small explicit contract fixtures
  until Chief coordinates any service or model execution window.
- Do not run another model, start a service, clear a global barrier, or remove
  actual S01 state in this reviewer lane without a specific later task.
- All candidate results stay `NOT_RUN` until actually executed. Existing S01
  observations do not become a successful neural repair through new wording.
- Keep raw failures and distinguish canonical task status, report delivery,
  verified inference, physical resource teardown and operator authorization.

Candidate/API inspection: `NOT_RUN` at freeze. Independent actual S01 artifact
assessment: `NOT_RUN` at freeze. Production changes: none.
