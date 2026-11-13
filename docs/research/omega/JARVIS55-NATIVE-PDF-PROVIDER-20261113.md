# Native PDF provider process isolation

Base: `84268a0b868468074bf9ac904e3682ed885bf058`.

The exported synthetic provider lives in the instrumentation APK's application process. Its previous Kotlin implementation depended on Kotlin runtime classes. The test APK may not carry dependencies already present in the target APK; the actual DEX contents were not available after the executor outage, so no reproduced ClassNotFoundError is claimed.

Move only that provider to Java using Android and Java platform classes. Preserve its fully qualified name, authority, immutable PDF content, metadata, read-only path, query/open counters and static NAME/bytes API. The instrumented Kotlin probe remains in the target process and retains every assertion. No production reader, manifest, helper, workflow or QA gate changes.

Static review: Java abstract overrides, provider identity, read-only checks, ASCII PDF object/xref offsets and Kotlin-to-Java static call compatibility inspected. Unknown URI queries now reject with IllegalArgumentException; openFile retains FileNotFoundException.

Compilation: NOT_RUN (executor unavailable).
Native Android execution: NOT_RUN.
Required validation: existing full-app native-pdf job on the exact integrated SHA. Keep the strict host PDF gate until equivalent native evidence has actually passed.
