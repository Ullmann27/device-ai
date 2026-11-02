# Dialog VI — Three rounds × three code milestones
Date: 2026-11-02. Parent: PR #346 at a69e88937361b01cd7ebb78baa588e4fbf330fcd.
Branch: research/continuum-dialog-vi-nine-gates-20261102 (draft PR #347).
Scope: Device AI Continuum only. Source changes, not an owner APK or completed device QA.

## Round A: Android-intent and verified read-only action
1. **M1: German utterance policy:** new pure-Kotlin \`OwnerAppLookupIntent\` recognizes narrow commands and refuses negation, compound actions, script-like payloads, URLs and ambiguous free-form conversation. It never grants app-launch permission.
2. **M2: Real chat integration:** \`AgentActivity.sendCurrentInput\` now runs explicit name-search requests through the existing \`executeTool → ToolGate → find_app → PackageManager\` path, not through a second app or a raw shell command. Output is accepted only with the native \`CONTINUUM_APP_DISCOVERY_V1\` schema and bounded candidate list. Owner gets a readable German result; actual \`launch_app\` remains separate and confirmed.
3. **M3: Registry and lifecycle gate:** strict \`find_app\` arguments (\`2..80\` characters, no control data); unique per-turn call IDs; a cancelled or backgrounded read-only lookup discards late results and releases UI busy-state. PackageManager returns only visible launcher activities, not proof of global installation.

## Round B: JARVIS-style offline voice and owner interruption
4. **M4: Spoken output data hygiene:** closed, incomplete and inline code sections are excluded from TTS; Markdown and hyperlinks do not read out raw code or URL secrets. Existing offline de-AT/de German voice with previous pitch 0.86 and rate 0.93 is retained. No cloned actor voice or third-party audio model.
5. **M5: Barge-in:** a new owner-entered chat turn stops the previous TTS utterance and speech animation before processing. It does not auto-submit ASR transcripts, trigger background microphones or bypass ToolGate.
6. **M6: Microphone lifecycle:** \`onPause\` cancels and revokes the full speech input scope, including delayed system permission callbacks. Previous original-session/generation/foreground checks remain in place.

## Round C: Owner context continuity and reproducible QA
7. **M7: Consent-revoked response abstention:** if consent changes during a model turn or a web session is revoked, the model output remains blocked, while the original owner's wording is retained only as unconfirmed ephemeral session context (not SWB1/EWM2 fact and not an authorization). Screen-derived turns are excluded.
8. **M8: F08 failure deduplication:** repeated failed-turn callbacks for the same owner request cannot displace distinct earlier owner statements from the four-turn context window. Added a specific JUnit negative regression.
9. **M9: Reproducible JVM host contracts:** \`scripts/continuum-dialog-vi-host-check.sh\` compiles three production pure-Kotlin files and \`tools/dialog-vi-host/ContractChecks.kt\`, then runs bounded runtime checks. Explicitly emits Android/Device NOT_RUN rather than treating a JVM run as an APK or Fold7 success. New JUnit cases cover app-intent parsing, tool policy, TTS confidentiality and repeated owner evidence.

## Actual local execution evidence (not a full Android build)
- Machine: OpenJDK 21, \`kotlinc-jvm 1.9.0\`.
- Built a standalone JVM executable from byte-identical copies of these **three production source blobs** verified against GitHub:
  - \`NativeAppLookup.kt\`: **cf10ecfa022f18394ba7a509d7a6bb9dedf347a6**
  - \`JarvisVoiceProfile.kt\`: **c39ef9a9776895fef542e0e7ac03b1e6b071412a**
  - \`OwnerAppLookupIntent.kt\`: **52fe61624ae1eec84475bacac53818b7cfaad03d**
- Local 43-case pure Kotlin runtime harness result: **43/43 PASS**, after compiling those exact three product sources. 7 German intent positives, 14 intent refusals, 13 NativeAppLookup cases, 9 voice/sanitization cases.
- The committed runnable contract file has equivalent cases but is **not** claimed as byte-identical to the locally used harness; the committed GitHub script must still be executed in a proper repository checkout.
- Additional local direct harness for \`OwnerAppLookupIntent\`: **19/19 PASS**.
- Source-exact two-file voice/lookup tests before these changes: 23/23 historical, superseded by the 43-case host run.
- The new Android/JUnit tests, \`AgentActivity\`, \`ToolRegistry\`, and other Android-bound sources have **NOT** been successfully compiled as a complete Android build on the new head.
- \`GRADLE_NOT_RUN\`; \`LITERT_NOT_RUN\`; \`LINT_NOT_RUN\`; \`RENDER_NOT_RUN\`; \`FOLD7_DEVICE_NOT_RUN\`; \`MODEL_TOOL_SELECTION_NOT_RUN\`.
- No audio waveform or physical-device timbre quality was verified. No automated tapping or arbitrary app execution was added.

## Reproduction and follow-up
From an authorized full checkout:
\`\`\`bash
bash scripts/continuum-dialog-vi-host-check.sh
\`\`\`
Then run the complete existing Android Gradle/JUnit/LiteRT/Lint suite on the **exact final branch HEAD** in the established Render Android-QA environment. Prior Synapse III Render success does not cover Dialog VI.

Review \`AgentActivity\` lifecycle and every new JUnit test before approving. After full Android compile, complete explicit Fold7 opt-in device tests for voice availability, starting/stopping recognition, interrupted AppLookup, consented app launch, screen postcondition, and F08 memory corrections. No main merge, owner release, original signing or private model mutation without owner approval.
