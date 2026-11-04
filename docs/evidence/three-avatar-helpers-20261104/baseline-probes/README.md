# Independent production-baseline probes

The coordinator ran these Java probes against the freshly compiled production
classes of d352bab6855db2a44def0bbf60fd93202b4e3066. The actual Gradle baseline
checkout was a65cfcac63c794ea9953034349989b4a45f6ac73 (documentation-only child).
Compilation and both probe processes exited 0. These are defect observations,
not passing acceptance tests and not native Android/GPU execution.

## Observed failures

- AA to PP after 16 ms, measured jaw 0.6: residual rendered AA contributes
  0.6267853 of the common opening, but extra JawOpen remains 0.6. Total common
  opening is 1.2267853, illustrating the target-cue/crossfade mismatch.
- A stale audio sample at 200 ms, replayed after the 250 ms sample, increases
  displayed speech energy from 0.25599998 to 0.5136.
- A registry sink exception escapes the dispatch adapter and queues zero events
  for the independent renderer.
- After registering a new renderer, the previously queued event still reaches
  the old renderer (old=1, current=0).

## Reproduce

First run the focused Gradle baseline documented in the handoff. Compile these
Java files with JDK 17 against app/build/tmp/kotlin-classes/debug,
Kotlin stdlib 2.4.0 and Android SDK 36 android.jar. Run each resulting main class
with that same classpath. Android supplies type signatures only; these probes
do not instantiate views or claim a device render. All inputs are synthetic.

The eventual Kotlin regression tests assert these behaviors and their required
corrections through the production APIs. The two text outputs are the original
unmodified probe output.
