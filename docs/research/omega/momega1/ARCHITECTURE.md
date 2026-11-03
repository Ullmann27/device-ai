# MΩ-1 call paths and decisions

Inspected source baseline: ca93fb4f7a17ff7a2a13debd93197ba485594f06 (production equivalent to Chief786436f5). Work in progress, not end-to-end acceptance.

## Existing central architecture

AgentActivity.runLocal constructs its own AgentModel adapter within BoundedAgentOrchestrator. The adapter compiles AiContextCompiler context, local conversation and owner timeline, calls LocalToolContinuation over the process-local LocalModelRuntime, checks LocalTurnModelBinding before/after generation, and returns AgentModelOutput(response, ToolProposal). The orchestrator applies the existing ToolGate, confirmation, execution records, AgentEvaluator and experience/evidence stores. ContinuumLocalAgentModel exists but is not this main adapter. CognitiveWorkState already contains UNDERSTANDING, PLANNING, WAITING_FOR_APPROVAL, EXECUTING, VERIFYING, BLOCKED, COMPLETED; no replacement state machine is justified.

The main model tool registry contains no coding-task dispatch. showCodingTaskDraftDialog independently validates a manually entered ContinuumCodingTask through ContinuumCodingConversation. Its owner-confirmed full task goes through ContinuumTypedCodingBridge, the internal BuilderActivity, fixed Termux RUN_COMMAND and scripts/continuum-typed-coding-bridge.mjs. Task hashes bind exact bytes, not owner identity. The current full prepare review is separate from a narrower run confirmation; DELTA is closing full-snapshot persistence and execution review.

The Node bridge already requires owner-supplied external OS-isolation configuration. runCodingTask → CCC1 journal → canonical mobile repair job → localTeamleaderDriver → executeOwnerRepairTask → scopedRepairSession is the execution path. scopedRepairSession returns actual diagnostics/history for bounded model retries. Its final independent owner check occurs after that loop; currently its counterexamples cannot trigger an in-scope revision. BETA owns this missing connection and test-identity/receipt binding.

The standalone ccc1-redgreen-verifier retains identities after PR400 and has consumed adversarial evidence from PR407. Its active CLI/tests are not proof of being reached by the typed worker. New review defects include noncanonical TMPDIR roots and self-closing suite ancestry; embedded receipt source/hash contradictions also remain. All must be reproduced and reevaluated before integration.

ExperienceLog currently stores terminal summaries from canonical CCC1 journals. It has no provider retrieval caller, and its hash chain alone is not proof of independent success. GAMMA extends this same journal, with a distinct technical-experience proof contract and bounded retrieval; owner facts, source observations, attempts, verified strategies and authorized capabilities stay separate. No Kotlin LearningExperienceType reinterpretation or new fact database is authorized by this design.

## Model admission decision

LocalModelHost owns a process-local Android LiteRT singleton; its imported files live in app-private storage. Termux runs under another UID. Existing code exposes neither authenticated request IPC nor an endpoint that shares this engine. A READY Android model therefore does not provision coding inference.

For this host implementation the smaller viable connection is an explicitly owner-provisioned separate GGUF model and pinned native llama runtime, reached directly through a confined local process. No model is downloaded or chosen by this task. ALPHA implements request/output/model/runtime observations and failure admission. The preferred shared-LiteRT path remains blocked pending a reviewed authenticated, replay-resistant, revocable, bounded Android IPC design and actual device lifecycle testing; no new exported component/server is added.

A runtime observation or per-request MAC proves only the trusted runner's transport/process observations. It is not scientific proof that an arbitrary binary executed the claimed mathematical model; independent runtime/provenance observation and real local benchmark remain separate gates. verifiedModelExecution must not become true from a model response or self-description.

## Host/device boundary

User confirmed: **no device access; continue host work**. FOLD7_NOT_RUN. No ADB daemon/binary, USB nodes or configured VPN/device TCP channel were observed. No installed version, signature, Android17/ARM64 identity, Gemma hash, thermal/battery/PSS or Fold7 performance result is available.

Host managed Docker28.4 and bwrap namespaces are available. Shared cgroup limit is4 CPUs and16GiB; GPU capacity is not established. Separate Git worktrees provide source isolation only. Actual containers/namespaces are counted only after execution and observed confinement.

## DAN Ω / JARVIS workforce supplement

The user's additional order authorizes8–10 real specialized sessions in waves, with MΩ-1 priority. Current platform maximum remains7 active sessions including Chief; initial six specialists are runtime, loop, memory, Android, independent QA and runtime challenger. Subsequent workspace builder, workspace challenger, memory reviewer and Android reviewer use released slots, each recorded only after spawning.

Reuse ChiefWorkerPool, ChiefBrainWiring, routines.AgentMailbox, cognitive.ComputeFabric and existing isolated-executor. The first infrastructure increment must execute an actual scoped fixed profile in each authorized worker's isolated workspace, with CPU/RAM/process/time caps and no network, and return evidence to the existing coordination contract. No free shell from model text, second scheduler, virtual GPU claim or new independent mailbox database. Durable CCC1 journals remain recovery authority; uncertain execution blocks replay. The book scenario is inspiration only, not evidence of intelligence or hardware capacity.

## Integration authority

Chief owns central Activity/ToolGate/runtime contracts and source integration. DELTA exclusively owns BuilderActivity, BuildResultReceiver and TypedCodingBridge for the reviewed snapshot repair. BETA additionally owns repair-loop and backlog-repair-worker. Other file allocations are in PLAN.md. Independent reviews target exact frozen source; old test counts do not transfer to new commits.
