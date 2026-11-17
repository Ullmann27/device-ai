# Second independent run: core green, Unicode storage blocker

Exact source is in red-unicode-fcea5908/candidate-sha.txt.
Unchanged controller groups 15/15 PASS; migration/storage 3/3 PASS; existing compiler
regressions 4/4 PASS. Supplemental resource/withdrawal/pressure groups 4/5 PASS.

Real remaining defect: repeated valid 1,400-emoji responses are 5,600 UTF-8 bytes,
below the 6,000-byte response bound. Bounded history counts UTF-8 bytes, while the
codec uses Java DataOutputStream.writeUTF (modified UTF-8: surrogate halves use six
bytes per emoji instead of four). The retained history can satisfy its 48 KiB
UTF-8 limit but exceed the codec's 64 KiB bound. encode then throws, refusing a
valid conversation despite remaining storage. Raw failure and source hashes kept.
This is a source reliability failure, not a neural ability or runtime-measurement
claim. Builder asked to correct actual encoded-size admission without weakening
the independent check or arbitrarily clipping individual turns.

The separate real object-pressure probe committed 90 synthetic long ASCII turns,
retaining 4,172,924 bytes under the 4 MiB limit. The next write failed closed and
preserved the last committed profile. Reset cleared the active history pointer,
including after reopening, while old immutable objects stayed on disk. This tests
withdrawal at capacity, not garbage collection or indefinite conversation length.
No CPU work is running after this candidate test; the shared model measurement
window is unaffected.
