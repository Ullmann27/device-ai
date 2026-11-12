# Historical object prompts — follow-up to PR #769

## Integration checkpoint

- Root integrator: PR #769, `integrate/omega-apk54-20261111`.
- Follow-up: [PR #779](https://github.com/Ullmann27/device-ai/pull/779), `work/omega-memory-history-20261111`.
- Frozen base: `c36258a853bbc6dacd8fbfa0dbae64f0f95a0d14`.
- Tested production: local `99a015b5c29e1460af5c09c31be086956f28347b`, published as `f1efba8f3ab0898fb5bb4a7da11c5dbbbd3eae76`; identical tree `73ba02654260267b7d3183bc78ca26d871e4e008`.
- Review: <https://github.com/Ullmann27/device-ai/pull/769#discussion_r4540082835>.
- Scope: EXP-06 conversation provenance, EXP-12 session continuity; no new database, model, authority or persistence.

## Cause and repair

The compact projection omitted retained history for retrospective correction
summaries, questions about past changes, `vorhin`, and singular inflected
`Verlaufs`/`Verlaufes`. The three reported requests correctly avoided deterministic
recall but consequently reached the model without historical evidence. `zuvor`
already worked through `Mode.PREVIOUS` and is explicitly protected.

The 32-line production addition changes only prompt history selection. Clear
read commands and past-change questions can select retained history; checked
planning requests and current-state descriptions keep the compact projection.
Old attribute values and before-correction references are explicit, so an old
object adjective alone does not request its history. A bare after-correction
phrase frames the current state; it is removed only from change-topic matching.
Explicit comparisons with old values take precedence over the current-value
compact view. Past-state question starts extend projection selection only.

`answer()`, owner-event acquisition, bounded internal events, uncertainty,
unresolved references, truncation markers, the full renderer and prompt budget
gates are unchanged. In particular, `vorhin` is not mapped onto an invented
single previous value. Large historical requests still must satisfy the
existing budget gate; no silent deletion or budget bypass was added.

## Exact validation

| Run | Source | Result |
|---|---|---|
| New regression RED | Unchanged `c36258a8` production; twelve tests from local `b08e22882` | 12 run / 3 PASS / 9 FAIL / 0 SKIP, exit 1 |
| Repair and neighbors GREEN | Production `99a015b5c`; byte-identical new tests plus existing memory/budget classes | 95 run / 95 PASS / 0 FAIL / 0 SKIP, exit 0 |
| Saturated ordinary prompt | Same six-object, 24-correction budget fixture | 1,595 UTF-8 bytes; mandatory frame fits |
| Independent source review | `omega-extensions` on exact production SHA256 | Narrow review OK |
| Actual Activity regression | Fourth case in `OwnerObjectStateConversationUiTest` | NOT_RUN here; root owns shared Android gate |

All nine RED failures are missing historical prompt evidence. The three RED
controls pass: planned/current requests remain compact, unrelated change requests
stay unrelated, and existing `zuvor`/current deterministic recall stays correct.
The unchanged-source RED and patched GREEN ran serially under
`flock /tmp/continuum-omega-gradle.lock` after the existing lint holder released
it. Kotlin 2.4.0/JUnit 4.13.2/JDK21 used one CPU and fresh selected source
compilation against cached real app dependencies. No Gradle was started here.

The independent review supplied three additional boundaries: old/current value
comparisons, descriptions after a correction, and past-state questions before a
correction. All are now covered. The twelve new tests also cover uncertainty,
unresolved references, historical truncation and non-mutation of the ledger.
No assertions were weakened or skipped.

Evidence: `docs/evidence/omega-memory-history-20261111/RESULT.json`, the paired
source manifests, compiler logs, JUnit logs and exit files. The exact host-specific
harness is archived as `harness-used.py`; manifests retain complete commands.
Local test commit `b08e22882` and published `f1efba8f3` contain identical new test
bytes, as recorded by SHA256. Subsequent evidence commits do not alter production
or test sources.

## Android integration gate

Add `at.heinz.deviceai.cognitive.OwnerObjectStateLedgerHistoryPromptTest` to the
root focused class list. The existing class
`at.heinz.deviceai.OwnerObjectStateConversationUiTest` now includes
`historicalChangeRequestsReachModelWithRetainedHistory` as its fourth case. It
seeds the existing session context, expires the original turns from the recent
window, and submits the three reported requests through the real send button.
It asserts actual inference handoff, full owner requests, historical count/place
evidence and unchanged persistent memory. It is not an on-device inference test.

Root integrates the full follow-up and runs its common compile/Activity gate.
No device, signing, installation or release result is claimed. Rollback: revert
this follow-up; no stored-data migration is needed.
