# Primary-source decisions for Synapse Swarm

Audit date: 2026-11-03, 20:07–20:10 UTC. Research base:
`efe622dda00e620952483dedad19257efb3284e6`. This is literature/API inspection,
not implementation, independent product QA, model inference or device acceptance.
All 17 HTTP requests in `HTTP-RECEIPTS.json` returned 200. Content was inspected,
not accepted on HTTP status alone. Q5 redirects to `/research/`; Nature appended
cookie-error query parameters but returned the article. No unavailable primary URL
in this bounded set. Full third-party HTML is not redistributed. Digests identify
retrieved bytes, not publisher authenticity or immutable future content.

`Q1`–`Q7` below refer to the original Synapse request; later source additions use
explicit names/IDs to avoid renumbering collisions with the expanded request.

## Findings and concrete decisions

| Source and observed date/version | What the source supports | Continuum decision and boundary |
| --- | --- | --- |
| [Q1: Computer and apps](https://docs.x.ai/grok-bot/computer-and-apps), live documentation, publication date not shown | Account bots use one persistent computer; files, browser sessions and command-line credentials are shared. Separate screens are explicitly not security boundaries. Connector credentials stay in the connector backend; service permissions still apply. | Reuse the distinction between identity, work surface and computer. **Reject shared credentials/files as our isolation design**: the owner requires private bot scopes. Neither a separate tab nor a directory provides that boundary. The fetched title is “Use the computer and apps / SpaceXSI Docs”; record the returned page, without inventing product availability or a Continuum comparison result. |
| [Q2: Generative Agents](https://arxiv.org/abs/2304.03442), v2, 2023-08-06 | Observation, retrieval, planning and reflection support believable simulated behavior in a sandbox. Its ablations concern believability of that simulation. | Profiles and remembered observations can persist independently of individual runs. Do not turn personalities, simulated social behavior or reflections into competence, verified facts, feelings, or permission. No need for a second memory database. |
| [Q3: MemGPT](https://arxiv.org/abs/2310.08560), v2, 2024-02-12 | Virtual context management moves information between memory tiers within a finite model context; interrupts manage control flow. Evaluation covers documents and multisession chat. | Apply bounded retrieval/context packing around existing LEX3, context compiler and journals. “Neuro-OS” is resource/orchestration software, not an OS replacement or additional physical RAM. No unbounded prompt append. |
| [Q4: Reflexion](https://arxiv.org/abs/2303.11366), v4, 2023-10-10 | Linguistic feedback and episodic reflective text influence later trials without weight updates. Feedback may be external or internally simulated in that research. | Retain external, independently executed CCC1 feedback. Reject self-written success text as verification. Persist current-grant/source-bound historical strategies; recall alone is not learning benefit. Published benchmark numbers were not reproduced here. |
| [Q5: Anthropic research system](https://www.anthropic.com/research/multi-agent-research-system), 2025-06-13 | Orchestrator/worker parallel search benefits decomposable research. The article highlights token expense, duplication, end-state evaluation and limits for tightly coupled coding tasks. | Default to one useful worker, expand only on a bounded need. Account for all coordination/model/tool costs, not only worker latency. Use final executable state/receipts. No inference speedup inferred from parallel external API calls. |
| [Q6: Patterns and problems](https://www.anthropic.com/research/multiagent-systems), 2026-08-13 | Coordination, conformity, epistemic failures and goal conflicts need separate assessment. Its vulnerability-search comparison used different total token budgets and search scopes; it explicitly notes comparable token efficiency within a matched core scope. | Freeze independent challenger cases before sharing candidate answers. Equalize scope and total budgets. Consensus is not verification. No “swarm advantage” inferred from unnormalized raw result totals. |
| [Q7: CfC](https://www.nature.com/articles/s42256-022-00556-7), 2022-11-15; correction 2022-11-29 | Closed-form approximation avoids numerical ODE-solving overhead for continuous-time models. The article acknowledges approximation and possible vanishing gradients and discusses irregular temporal data/embedded tasks. | Defer integration. First obtain actual timestamped scheduler/resource traces and rule/classifier baselines. A potential tiny predictor cannot grant tools or replace exact budgets. The paper is not evidence of Fold7 energy savings, causal understanding or general reasoning. |
| [Scaling agent systems](https://arxiv.org/abs/2512.08296), v3, 2026-04-08 | Abstract reports controlled tools/prompts/compute across architectures; task structure changes the benefit, tool-heavy and sequential tasks can suffer coordination overhead. | Test architecture against task classes and actual budget. Do not transfer its fitted scaling model or headline results to small local Gemma. Scope of this inspection: current abstract/metadata, not full-method reproduction. |
| [Equal thinking budgets](https://arxiv.org/abs/2604.02460), v2, 2026-04-11 | Abstract reports single-agent matches/outperforms multi-agent multi-hop reasoning under matched reasoning-token budgets, with context-utilization assumptions and API-budget artifacts. | Measure emitted tokens and total input work, not merely requested token caps. Preserve A0. This does not prove every multi-agent workflow is worse; study domain and assumptions remain explicit. Abstract/metadata inspected; methods/code not reproduced. |

No external implementation was imported or executed. No method is adopted on the
strength of an abstract, a role name, a commercial system, or its reported score.

## Android feasibility gates from official documentation

| Source | Verified constraint | Required local behavior |
| --- | --- | --- |
| [Service manifest](https://developer.android.com/guide/topics/manifest/service-element) | `isolatedProcess=true` uses a special process with no permissions of its own; communication is through Service APIs. `android:process` alone merely chooses another process. | Investigate a narrow bound service with explicit capability-limited IPC. Do not equate a colon process, worktree, or profile with permission isolation. Actual model FD, ABI, lifecycle and cancellation integration remain device tests. |
| [AVF overview](https://source.android.com/docs/core/virtualization), [VirtualizationService](https://source.android.com/docs/core/virtualization/virtualization-service) | AVF provides protected VM isolation, supports ARM64; each VM uses crosvm and resources. VM lifetime follows `IVirtualMachine` references, including client death. | ARM64 is necessary, not proof the Fold7 exposes required APIs/permissions to this app. Probe device support and authorized API access before selecting AVF. No VM or hardware capacity was created by this audit. |
| [Target API29+ execution restriction](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission) | Untrusted apps targeting Android10 cannot directly `execve()` files in their writable home; native code is expected to be packaged in the APK. | A desktop subprocess demo is not an Android execution recipe. Preserve approved packaged/native or existing verified Termux transport. No target-SDK downgrade or writable-code workaround. |
| [UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener) | `onRangeStart` (API26) requires engine-supplied range timing and describes expected playback. `onAudioAvailable` (API24) arrives at synthesis time, potentially before playback. `onStop` (API23) covers interrupted/flushed speech. | Keep one TTS/AvatarDirector path, exact utterance IDs and immediate stop/reset. Word ranges are not phonemes, audio generation time is not playback time. Mark fallback estimates and calibrate on the device. |
| [Thermal API](https://developer.android.com/games/optimize/adpf/thermal) | Headroom/status behavior is device-dependent. Repeated headroom polling faster than ten seconds can yield NaN; support itself can be unavailable. | Use bounded sampling/status-driven quality changes and UNKNOWN for unavailable measurements. Temperature/headroom is not energy consumption. Never infer battery savings from fewer simulated operations. |
| [ProcessLifecycleOwner](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner) | Pause/stop dispatch is delayed to cover configuration changes; it is not millisecond precise and knows nothing about other application processes. | Use renderer/surface/activity/IPC-specific cleanup in addition to process lifecycle. Do not use this alone for immediate camera shutdown, speech stop, worker termination or cross-process run ownership. |
| [PRoot manual](https://github.com/proot-me/proot/blob/25dc6a3134891f98a79f57ce1c2c1b23ff15cad1/doc/proot/manual.rst), v5.5.0, 2026-10-02 | User-space `ptrace` translation of chroot/bind/binfmt operations; guest kernel is not involved and programs retain access to host resources such as devices/network. | Useful compatibility tooling, **not sufficient hostile-bot isolation**. Fake UID/root does not establish rights separation. No implicit network/process/resource sandbox or 20 independent computers. |

One loaded runtime with serialized generation is the starting policy, not a
completed shared-runtime proof. Immutable model weights may be shared where the
actual API supports it; conversational state/KV caches/permissions remain private.
Measure cancellation, reentry and context-leak probes before adding concurrency.

## License and provenance separation

* Q1/Q5/Q6: no open-source implementation license was established from these
  documentation/articles. Summarize concepts; do not copy a presumed implementation.
* Q2 arXiv v2 links the arXiv nonexclusive distribution license. That is not a
  general source-code grant. Author repository
  [`joonspk-research/generative_agents`](https://github.com/joonspk-research/generative_agents)
  at `fe05a71d3e4ed7d10bf68aa4eda6dd995ec070f4` has Apache-2.0 LICENSE
  blob `e0d8210e8009de93c35050c71db7af0d88c37660`.
* Q3 and Q4 current arXiv pages link CC-BY-4.0 for the papers. Current Letta
  repository `8816d3a8516893bfdc0fa14bfb1235226012e295` has Apache-2.0 LICENSE
  blob `f75c3422907f55d71685ae59afdc28498cde5a40`; this current successor is **not**
  a pinned reproduction of the 2023 MemGPT experiment. Reflexion repository
  `218cf0ef1df84b05ce379dd4a8e47f17766733a0` has MIT LICENSE
  blob `54d8671f77d5cf375a08c3c1e8bc06de4b8581c1`.
* Q7 article links CC-BY-4.0 and explicitly links its code availability to
  [`raminmh/CfC`](https://github.com/raminmh/CfC). Inspected code HEAD
  `3ebaa0307a280c5a9ee8261fdf733ad9bd5636ad` has Apache-2.0 LICENSE
  blob `261eeb9e9f8b2b4b0d119366dda99c6fd7d35c64`. Framework dependencies,
  task data, checkpoint licenses and Android export remain unaudited.
* Scaling2512 current paper links arXiv nonexclusive distribution; EqualBudget2604
  links CC-BY-4.0. No code-license or exact implementation reproduction accepted.
* Android pages link a [content-license policy](https://developer.android.com/license).
  Retrieved policy distinguishes Apache-2.0 API/reference/sample content from
  other site content under CC-BY-2.5, with module-specific exceptions. Do not
  blanket-label all Android/AOSP materials as one license. No sample code imported.
* PRoot README declares GPL-2.0-or-later at the pinned commit above. This is
  a separate licensing concern from Android app/source licensing; no vendoring.

Repository metadata and pinned LICENSE excerpts were read with the authorized
GitHub connector, 2026-11-03. Paper license, code license, model license and commercial
service availability are separate checks. A license header inspection does not
constitute dependency/security review or permission to redistribute every asset.
