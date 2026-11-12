# Cockpit: reproducible host scene evidence

This directory renders the original packaged avatar and the new owned cockpit mesh. It does not launch Android,
Filament, MediaProjection, WhatsApp or another owner app. The exported PNGs are labelled host scene excerpts.

## Inputs

- `app/src/main/assets/avatar3d/continuum-avatar.glb`: original avatar; never rewritten by these tools.
- `app/src/main/assets/cinema3d/cockpit-workstation.glb`: deterministic mesh from `build_scene.py`.
- Original Kotlin sphere renderer, avatar contract and Java2D painter: compiled by `export_host.sh`.
- Camera/layout parameters in `render_host.py`: mirror the named production source at capture time.
- Original packaged Roboto fonts: captions only.

## Reproduce

Provide a JDK 17 executable (`SCENE_JAVA`), a Kotlin compiler library directory (`SCENE_KOTLIN_LIB`), its standard
library JAR (`SCENE_STDLIB_JAR`), and the `org.json` JAR (`SCENE_JSON_JAR`). The checked run used Kotlin 2.0.20,
Blender 4.3.2 / Cycles CPU, Pillow and NumPy. Denoising is disabled because this host lacks OpenImageDenoise support.

```sh
bash tools/cockpit-workstation/export_host.sh /tmp/cockpit-host
blender -b -t 2 --python tools/cockpit-workstation/render_host.py -- "$PWD" /tmp/cockpit-host
python tools/cockpit-workstation/compose_host.py "$PWD" /tmp/cockpit-host docs/evidence/astra55-cockpit-20261112
```

`build_scene.py` only regenerates the owned room mesh; its output SHA must also match `FilamentCockpitStage`.
The original avatar validator rejects a different avatar. The exporter records source hashes and commit.
Changed production layout/framing sources stop rendering until the mirrored parameters are revalidated.
The compositor retains the original alpha, applies 0.62 avatar opacity and the native layer's bottom/side fades.

## Limits

Cycles lights and shadows are host inspection settings. Android Filament pixel parity is not asserted. The
layout is mirrored explicitly, not produced by an Android layout traversal. These images prove the actual mesh,
original avatar identity, intended camera/framing and component composition. Native UI screenshots and device
capture tests are separate evidence. The monitor is empty because no real app capture exists on this host.

Never label these renders LIVE, Fold7 tested, an Android screenshot or a successful app action.
