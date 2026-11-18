# Development-bot contribution provenance

The PR #19 development-bot integration and accompanying hardening are project-specific Kotlin code. No code from OpenHands, aider or their third-party forks has been copied into this contribution. Those projects were considered only as architectural references. This document does not replace license obligations for the application's existing dependencies or assert a completed dependency-license audit. Any future copied code requires exact upstream revision/file provenance, license compatibility review and preservation of required notices.


# Bounded learning contribution

Structured reasoning, online classification and the neural experiment are original project-specific Kotlin implementations. AIMA Java (MIT), River (BSD-3-Clause), Micrograd (MIT), JSoar (BSD-3-Clause), and EJML (Apache-2.0) were reviewed as references; no upstream code or weights are bundled. MicroGPT (MIT, author declaration in gist comment 5999841) was studied without copying code. Exact inspected license/source hashes and adoption decisions are in `docs/CONTINUUM_LEARNING.md`. No application dependencies were added.


# Optional Claude brain: Anthropic Java SDK

The owner-selectable cloud brain (`localmodel/ClaudeModelRuntime.kt`) uses the official Anthropic Java SDK as a regular Gradle dependency, `com.anthropic:anthropic-java:3.3.0` (Apache License 2.0 since SDK 3.0.0; its license files are packaged unchanged under `META-INF/licenses/anthropic-java*/`). No SDK source code is copied into this repository. The SDK brings these artifacts transitively, each under the license published with it: `anthropic-java-core` and `anthropic-java-client-okhttp` 3.3.0, Jackson 2.19.4 (`jackson-core`, `jackson-databind`, `jackson-annotations`, `jackson-datatype-jdk8`, `jackson-datatype-jsr310`, `jackson-module-kotlin`), `com.github.victools:jsonschema-generator` 4.38.0 with its Jackson and Swagger modules, `com.fasterxml:classmate` 1.7.0, `io.swagger.core.v3:swagger-annotations` 2.2.50, `io.opentelemetry:opentelemetry-api` 1.62.0 (tracing is switched off in the client), `com.standardwebhooks:standardwebhooks` 1.2.0, `org.slf4j:slf4j-api` 2.0.16 and `com.google.errorprone:error_prone_annotations` 2.50.0. OkHttp 4.12.0 and `kotlin-reflect` were already dependencies of the app. The API key is the owner's own and is never part of the repository, the build or the APK.
