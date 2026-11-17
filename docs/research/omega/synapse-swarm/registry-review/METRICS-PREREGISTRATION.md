# Registry cost probe, preregistered before measurement

Candidate freeze: 2c5cd84a (resolve full SHA in run receipt). No optimization based
on measured output is allowed to retain a first-run label.

Hypothesis: twenty and one thousand persistent profiles can be loaded and paginated
within bounded host resources without resident model workers. This measures storage
metadata only; no intelligence, model throughput, OS isolation or energy conclusion.

Two synthetic stores (20 and 1000 entries), identical record shape, 512 MiB JVM heap.
Record exact primary snapshot bytes and total files/bytes. Use public APIs only.
Warm each in-process operation five times, then collect thirty samples for
`new store + get known final ID` and `existing store + first page(17)`.
Measure elapsed nanoseconds with System.nanoTime; report samples and nearest-rank
p50/p95, not a statistically guaranteed population percentile.
Separate JVM restart: two warmup launches, ten recorded process launches that load
and verify count/get. Report process wall time, explicitly including JVM startup.
Record Java version, source SHA, host CPU/memory limits and other active heavy
processes. Do not imply idle-hardware figures if concurrent work is present.
No hard speedup requirement or absent-feature percentage baseline. The pass gate is
correct identity count/bindings and each process bounded to 15 seconds; entire
probe timeout 180 seconds. Abort on corruption, excess budget or unbounded work.
No CPU/GPU/energy or Fold7 numbers are inferred from wall time. Resource usage from
`/usr/bin/time -v` may describe the test JVM including fixture creation only.
