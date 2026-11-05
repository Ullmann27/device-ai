# R13: LM1 compile + synthetic call-path profiling prototype

**Status: TOOLING_HOST_CHECKS_PASS; FULL_SOURCE_KOTLIN_NOT_RUN.** No performance optimization claimed or adopted.

This source archive contains three complete files: `tools/tuning-r13/compile-and-profile-lm1.mjs`, `compile-and-profile-lm1.test.mjs`, and `Lm1CallProbe.kt`. The four UTF-8 Base64 pieces form one gzip tar archive. The complete local archive was recreated and its 12 Node tests passed on Node v22.16.0; all remote piece Git-blob SHA-1 values must match this manifest before reconstruction.

Expected full archive SHA-256: `18613f6c765d7d499d318622d9650dabd97bd139d6030077e35dcb58894a9ddb`.
Expected archive bytes: 5007. Pieces: 00=4c81ea9648ca5ed3a74ea7a34ee61419e89de1e7 (1700 chars), 01=ec500a0b9811b366aaeccca9667d73a40fb20887 (1700 chars), 02=cb67213e84fc157519a995c3ab07046c13de99d1 (1700 chars), 03=f59ce5ae3372ddb96bb82e17734da8ccc6151ce5 (1576 chars).

Reconstruction from a **trusted checkout** (no remote code execution):

```sh
P=docs/evidence/tuning-r13-20261105
cat "$P"/probe-part-{00,01,02,03}.b64 | base64 --decode > /tmp/r13-lm1-probe-sources.tar.gz
printf '%s  %s\n' 18613f6c765d7d499d318622d9650dabd97bd139d6030077e35dcb58894a9ddb /tmp/r13-lm1-probe-sources.tar.gz | sha256sum -c -
tar -tzf /tmp/r13-lm1-probe-sources.tar.gz
mkdir -p /tmp/continuum-r13-probe
tar -xzf /tmp/r13-lm1-probe-sources.tar.gz -C /tmp/continuum-r13-probe
node --test /tmp/continuum-r13-probe/tools/tuning-r13/compile-and-profile-lm1.test.mjs
HEAD_SHA=$(git rev-parse HEAD)
node /tmp/continuum-r13-probe/tools/tuning-r13/compile-and-profile-lm1.mjs --repo "$PWD" --expected-head "$HEAD_SHA"
```

The tool rejects a wrong HEAD, changed/symlink/missing LM1 source, unexpected instrumentation anchors, output differences, malformed traces, and counter inconsistencies. It compiles all six LM1 Kotlin sources, once unchanged and once with three counters inserted **only in a temporary copy** of `LmRuntime.kt`, using tiny synthetic in-memory parameters; the source itself is unchanged. Counts cover LM1 synthetic request execution, **not** production/Android Gemma caller frequencies. No Kotlin full-graph compile was possible in this run because the sandbox cannot check out the repository (`github.com` DNS unavailable). `NOT_RUN` is mandatory until executed against a source-bound authorized checkout. No APK, Android, Fold7, weight changes or training.

Rollback: omit these four proof pieces and research document; original R1/R3 production code remains unchanged.
