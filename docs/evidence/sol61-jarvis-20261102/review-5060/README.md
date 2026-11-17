# Independent RED evidence and repairs

Reviewed source: `5060ec4470f49e3f9aa217189cd69adbb0f42f1a`, not the subsequent fix.
Raw SDK/Node failures and independent counterexamples are preserved here.
`raw-reports.zip` contains the complete original Node receipts/XML and Android XML.

Probes are read-only host diagnostics. Native probes use the supplied explicit
catalog/native API stand-ins, Kotlin2.4/JDK21; they do not load LiteRT weights.
Cognitive probes use exact reviewed sources and actual Gradle-compiled project
dependencies. Raw commands preserve original workspace paths; recreate the source
at the pinned SHA and substitute checkout/compiler/classpath locations. The
existing `scripts/continuum-test-classpath.init.gradle` exports a real SDK test
classpath. No host stub result certifies Android/native scheduling or Fold7.

Current regression commands (real SDK plus fixed source):

```sh
./gradlew --no-daemon -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=38 :app:testDebugUnitTest
node --test scripts/continuum-qa-evidence.test.mjs scripts/continuum-atomic-acceptance.test.mjs .github/continuum/continuum-local-dialogue-quality-wiring.test.mjs scripts/continuum-architecture-proof.test.mjs
```

The fixed-source focused tests are initial host checks only; see the canonical
handoff and fresh source-bound acceptance for the full Android/Node outcome.
