# Final compiled-production probes

Source: `7859d5d212d61829fabc7dfdbd3ca7587852388d`. The original Java programs in
`../baseline-probes/` were recompiled with JDK 17 against the final Gradle-produced
classes, Kotlin stdlib 2.4.0 and Android SDK 36 signatures. Compilation and both
programs exited 0. Inputs are synthetic; no view, native GPU or device was used.

The same AA→PP probe now has extra jaw 0.0 and total common opening 0.6267853.
Replayed audio leaves energy unchanged at 0.25599998. A registry exception no
longer escapes and one renderer callback is queued. After replacement, obsolete
callbacks reach neither the detached renderer nor the newly registered renderer.
The behavioral JUnit tests independently assert normal current delivery.

One JVM emitted an informational shared perf-data lock warning; it is retained
in the raw output. It did not change the exit code or any probe value.
