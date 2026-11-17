# Dental/lip failure reachability review

Result: the original adverse stress pose is real, but it is not a pose the current runtime mapper can emit. No app repair is justified by this sampled comparison. Appearance remains NOT_ACCEPTED; this is not a complete collision, phonetic, or Android GPU validation.

Reviewed app source: Chief853a8ee0. `AvatarMorphMapper.kt` is byte-identical to EPSILON7caef9b6, SHA256 d12224172eb34e96fb198454f7329b42858bf0504616bbcf4b43a5d1eaddb394. Accepted asset f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e, unchanged.

The mapper enforces `JawOpen=q` and inner `Jaw` rotation=`12*q` degrees for finite clamped q∈[0,1]. A's anchor24 instead sets AA=1, JawOpen=0 and innerJaw12.6°. At JawOpen0 the runtime rotation must be0°; even maximum input cannot produce12.6°. A's failure is preserved verbatim as `A-unreachable-stress-AA1-JawOpen0-Jaw12_6.png` (SHA25696cc173fc945df3983ba1e8177b3d803190d6a6cb422c242376615b6be38fdb0). Its source renderer is A81b22f54, exact imported runtimeGLB, all other morphs/head/eyes zero.

Before rendering, `preregistration.json` froze the three runtime control tuples and the adverse criterion: any lower dental geometry visibly outside/below the lower lip in a sampled reachable pose counts as failure. No criterion was relaxed after inspection.

| AA | JawOpen q | Inner jaw pitch | Runtime relation | Observed lower-lip protrusion |
|---:|---:|---:|---|---|
|1|0|12.6°|Unreachable A stress pose|Present; retained failure|
|1|0|0°|Reachable mapping|Not observed|
|1|0.5|6°|Reachable mapping|Not observed|
|1|1|12°|Reachable mapping|Not observed|

All three new frames were independently inspected. The exterior lower lip opens together with the inner jaw in the reachable samples, leaving the dental band inside the mouth opening. The mouth is exaggerated/stylized at high combined AA/JawOpen; this does not change the previously unaccepted visual/identity status.

The maximum q=1 is reachable from the existing reducer after three level1 audio events: smoothed energy0.4→0.64→0.784; `(0.784−0.025)*1.5` clamps to1. AA cue weight1 is emitted by the existing director. No new TTS listener, state machine, audio source or speech text is involved in the replay.

Execution: one authorized Blender4.3.2 process, CPU2, nice10, 8samples, three558×780 frames (2× logical279×390 native viewport). Geometry came from actual glTF import; camera focus came from exact Kotlin preflight. Head remained at neutral, so world BlenderX rotation about the imported Jaw hinge equals the runtime's canonical local+X rotation. The script resets every imported Face/NeuralThreads morph before setting AA and JawOpen. Other controls remain zero. `runtime-family-manifest.json` records exact controls and image hashes; `BLENDER-LOG.txt` records actual render completion/exit. QA was notified immediately when Blender exited, before visual inspection; no timing overlap with its later JVM gate.

Reproduce with Blender and the script in this directory:

```sh
OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=2 nice -n 10 blender -b -t 2 \
  --python docs/evidence/epsilon-android-20261103/runtime-family-review/render_runtime_family.py -- \
  app/src/main/assets/avatar3d/continuum-avatar.glb /tmp/epsilon-runtime-family \
  docs/evidence/epsilon-android-20261103/A-V2-ANDROID-PREFLIGHT.json
```

A separate stale Node assertion still searched for `canAnimate()` after the intentional FrameLayout-safe rename. The one leased assertion now checks the actual private `canAnimateFrame` predicate, foreground/attachment/visibility conditions, animator setting, power-save and thermal guards. Focused file result21/21 PASS. No Android app/asset source was changed by this diagnosis.
