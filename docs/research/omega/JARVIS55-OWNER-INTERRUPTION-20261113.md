# Owner correction interruption

Source: `41306257ce03c1e22e998877bf769ce42b3a0486`.

## Confirmed gap

`AgentActivity.sendCurrentInput` accepted four exact computer stop strings. The owner's
“Stopp, das meine ich nicht.” matched neither those strings nor the document-reference
parser. A busy task rejected the turn before reaching cancellation. The hands-free
recognizer sends this compound sentence through the same chat path. Its dictation merge
could also append the correction to an existing unsent draft.

## Change

- Extend the existing `OwnerCorrectionIntent` with a bounded whole-sentence match for
  direct stop corrections. Quoted, reported, negated, conditional, ambiguous-question and
  additional-action sentences do not match.
- Route the correction before research replies and busy admission, using the existing
  `cancelLocalRequest` lifecycle. The Send click uses the same handler before its generic
  busy shortcut.
- Process a current hands-free correction before dictation merge. An unrelated composer
  draft remains untouched. Stale recognition still fails the existing turn-token check.
- Stop the controlled computer workflow, revoke its grant, clear queued visual work and
  stop reply speech through existing cancellation. Do not infer a replacement action.
  Ask “Verstanden. Was soll ich anders machen?”
- Keep prior conversation evidence and completed journal records. Append the owner's
  correction as a non-model context turn. Record it against an actual active document draft,
  never an unrelated older document episode. Existing transcript/journal writers persist it.

No tool permissions, model weights, action grants, new agent framework or renderer changed.

## Audit boundaries

The current `ChiefBotTeam` analysis/review lane retains the full owner request in both
handoffs, has empty tool scopes, `maxToolCalls=0`, and rejects every runner invocation.
`ChiefJobs` uses the existing bounded orchestrator and worker scope. The team cannot
turn “verändere aber nichts” into an automatic device change through this lane.

The local diagnosis path requires `Risk.READ_ONLY`; cleanup remains a separate explicit
confirmation. No bypass was demonstrated in these paths. A general typed prohibition
carried into hypothetical future tool-enabled specialists is outside this patch.

`CockpitWorkJournal` already records correction timestamps and marks old unfinished
drafts INTERRUPTED/tool outcomes UNKNOWN after restart. `ChiefBotTeam.initialize`
already interrupts retained active tasks rather than replaying them. No second restore
mechanism was added.

## Regressions added

- Direct stop corrections and quoted/negated/reported/conditional/question negatives.
- Hands-free compound correction remains SEND; reuse of its recognition token is ignored.
- A running production `OwnerComputerWorkflow` receives cancellation immediately after
  its first observed search edit. Its fake Android port records no contact click or draft
  input, result CANCELLED and no later DISPATCHED/COMPLETED cue. Earlier verified search
  evidence remains in the event record.

The workflow regression uses synthetic observations, not a physical Android app.

## Verification

Kotlin compilation, JUnit, full QA, native UI dispatch, actual Android computer abort,
Fold7 speech/capture/thermal tests: **NOT_RUN** in this connector-only work session.
The integrator must run OwnerCorrectionIntentTest, OwnerComputerWorkflowTest,
ContinuousConversationModeTest and the existing scoped/full QA on the integrated SHA.
Actual hardware acceptance must confirm the compound interruption during live work and
during a pending approval, no subsequent input, stopped speech/cues, and no late success.
An already dispatched external Shizuku effect is not claimed to be reversible.
