# Shell execution integrity repair — WIP

Base/last complete proof: PR179 3f39172f983e71c735903dc2b19176374e6bcaa3; tested code8cf8c05f. This continuation stays on the existing editable own integration branch/PR. EXP01/07/11/12; contract/register and current Issue41 reviewed. No production release/main/model change.

Root causes read from actual sources: DeviceToolExecutorRunner treats all callback strings as success; DeviceToolExecutor converts missing bridge/argument errors into strings; ShellUserService loses the typed exit status and starts its90s wait AFTER synchronous output reading. A silent nonterminating process or descendant holding the pipe bypasses that deadline; truncating without status also permits incomplete successful evidence.

Implement/test inline: (1) pure Kotlin bounded process result and strict versioned transport codecs; actual exit/nonzero/EOF/deadline/output/interrupt tests; (2) preserve the existing AIDL execute(String):String ABI, encode typed requests/results on that channel, reject old responses for tools, retain human display for old console callers; (3) DeviceToolExecutor returns Result<String> to the existing runner, checked bridge budget15s within ToolGate20s, failures never converted to success strings; (4) include native cases in the existing worker JVM hook, full native regressions and publication.

No claims of cancellation of all privileged descendants or rollback of already-started external side effects. Such receipts remain ambiguous and must not replay automatically. Full Android/SDK/Shizuku-device execution still NOT_RUN. Performance changes require measured latency/output/memory bounds, no speculative neural retraining. Progress, exact commits and raw evidence append below.
