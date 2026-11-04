# Head surface correction — WIP / UNVERIFIED

Owner request: match the right Nachher head from the newly supplied comparison, preserve the rest, then deliver an actual render of the resulting GLB. No generated replacement or device-screenshot claim.

Base: official PR #521 commit `653a885435d95a19f341a80a89be1e9fbeccf1f1`; local twin `a85cd420540d66c581f3607d5d8b65bf8f634548`, tree `7c77cd3bebcdf89b28993f18642ebb45f4ed03d8`.

Baseline asset: SHA-256 `70b93bfdfb10c65662f6d37857e4a6a403dfb6ad950a9220acf91cb4145ab145`.
Owner reference: SHA-256 `f70460e42e7b89db5dd4c1f94228d3b7afa7c54a06774ebd57132ab27ecad7a4`.

Diagnosis: the single frontal projection and compressed continuation coordinates stretch the crown pattern across the ear, temple and jaw. The prior geometry/UV conflict checks did not establish likeness.

Change boundary: locally reproject the owner correction onto the existing unique head atlas; preserve raw geometry, skin, rig, 21 morph payloads and all existing app controls. Preserve lower torso surface texels.

Verification planned: inspect actual GLB renders from quarter/front and speaking/blink; compare accessor bytes; run existing avatar provenance/contract/motion gates. Native Filament/Fold7 NOT_RUN.

Status: correction not accepted or packaged at this checkpoint. Rollback is the exact prior asset/runtime pin/NOTICE from the base commit. No release, installation, main merge, paid service or new background agent.
