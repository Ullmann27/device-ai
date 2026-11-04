# Actual context recall observation

The normal conversation path compiles context through AiContextCompiler; the
existing event-emitting retriever was not a production caller on the inspected
578bdfd1 base. This hook reports only memories actually included by a successful
compile to the existing per-app memory hub. Omitted memory groups yield zero
hits; failed context compilation yields no success event. The query is hashed
with the existing app salt; no memory body or raw query enters the event.

RED: six behavioral tests compiled and failed because the observer/publisher
APIs were absent. GREEN: **26 tests passed**, including those six, context
boundary/integrity suites and the existing event queue suite. The publisher
makes no salt file and starts no worker without listeners. An observer failure
cannot prevent the local answer. These are Kotlin/JUnit host checks against the
frozen real Android baseline classpath, not Activity or device validation.

The Activity owner must instrument its real compiler with
`withRecallObserver(ContinuumMemoryRecallPublisher(filesDir))`. The lifecycle
adapter must bind the same app directory. Actual factory/Activity wiring is a
separate combined integration gate, not implied by this result.
