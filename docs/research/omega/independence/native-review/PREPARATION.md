# S01 preparation, still zero model calls

After freeze630f77c6, reviewer inspected the in-progress service CLI contract.
Candidate fd51d648 is not yet independently accepted. Execution still waits for
the Chief's accepted exact source and exclusive CPU window. No service was
initialized/started and no grant or model request exists for S01.

Materializer d24076f6 created owner fixture866678a7/tree cf49e839 with only the
original broken source and owner tests. Only the relative test import changed;
frozen input and materialized hashes are recorded. The four additional tests
remain outside the builder checkout. Extra-check helper b1416de1 is syntax
checked, not executed; it labels its eventual evidence as separate reviewer
fixed-profile execution, never a canonical bot receipt.

The typed task contract limits each acceptance item to300 characters. Before
any service call, the original318-character first requirement was losslessly
split at sentence boundaries into130/87/99 characters. The second256-character
requirement remains intact; joining all requirements yields the unchanged
575-character goal. `typed-task.json` passes the existing pure schema validator.
Frozen requirements, source, assertions and maxModelCalls1 are unchanged. This
is input-shape preparation, not a candidate-conditioned experiment alteration.

Native S01 will use actual CLI init/prepare/submit/start/status/evidence/stop and
fresh-process reads. Chief agreed not to cancel/retry this single frozen task.
The separate service challenger owns cancellation/crash fixtures without native
calls. After terminal S01, controlled service restart must not replay it.

New reduced profile classpath pending Chief validation:
`/workspace/independence-profile-773fbc94/runtime-classpath.txt`. Prior1595 full
classpath is not substituted silently. No Docker/Gradle/model work starts while
the service challenger owns the CPU window. Quota NOT_VISIBLE.
