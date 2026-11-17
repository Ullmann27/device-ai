# Bounded Fable avatar motion checks

Run from a complete checkout with JDK 17+ and Kotlin 1.9+:

```bash
bash tools/fable-avatar-motion-check/run.sh
```

These 26 executable checks compile and run the actual `AvatarMorphMapper`,
`AvatarContract`/reducer, and (when present) `AvatarIdleMotion` production files.
There is no fake result generator and no sleep-based timing. Frame timestamps
are supplied explicitly.

**Isolation boundary:** only `AvatarGlbContract.TARGETS` is copied into a generated,
test-only declaration fixture. The original `org.json` GLB parser is not compiled
or exercised. The fixture is created in a temporary directory and never enters
the app. This checker is NOT an Android build, the JUnit suite, asset validation,
Filament GPU execution, speech playback, visual reference comparison or a device
acceptance. Its PASS must never substitute for those gates.

The original #502 checker had three expected failures against its pre-motion
mapper. The integrated checker also includes #503's bilabial-closure and
equal-timestamp policies, so that historical count does not apply to this file.
To compare a pinned older mapper against the current integrated requirements:

```bash
tmp=$(mktemp -d)
git show 0e3685a3bc6cb3a4af61988450371f11e9cb0f3c:app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt > "$tmp/AvatarMorphMapper.kt"
CONTINUUM_AVATAR_MAPPER_FILE="$tmp/AvatarMorphMapper.kt" bash tools/fable-avatar-motion-check/run.sh
# Inspect every failure; the historical #502-only 23/3 count is not applicable.
rm -rf "$tmp"
```

The source must match the pinned baseline; do not reclassify another branch's
failures from this expected count. A normal candidate run should exit 0 with
26 PASS. `CONTINUUM_AVATAR_CONTRACT_FILE` can point to an explicitly disclosed
TARGETS declaration snapshot in an offline partial-source workspace. It does
not make such a workspace a full checkout.

The feature preserves the 70 ms vowel crossfade and existing 150 ms primary
blink. It adds bounded, 180 ms cosmetic gaze transitions with 3–5 s target
intervals and an earlier second blink in every fifth 4.6 s blink group. The
460 s clock wrap remains continuous. Actual observed gaze and non-idle task
gaze override cosmetic offsets. Reduced motion closes cosmetic blink and
mouth movement via the existing mapper guard.

The combined #503 speech contract gives active PP cues immediate lip closure,
including retirement of outgoing vowel opening. Repeated render timestamps leave
an ordinary vowel blend unchanged; a clock rollback still initializes a new
epoch. Two assertions from the original #502 host checker were updated to these
explicitly tested semantics; the other 24 checks were retained.

These checks do not claim clinical/naturalistic accuracy, lip-sync accuracy,
reference face likeness or FPS. Review the real rendering before integration.
