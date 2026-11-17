# Hinge V2 result and reproduction

**ACCEPT_HINGE_V2_SEMANTIC_RESOLUTION_FOR_6b199** applies to the actual packaged
rig at source `62122c55077644de5bb1993347b7749c7e53610b`. The unchanged old V1
validator still rejects that actual asset, with a newly repeated real CLI exit 1.
The original `f39a89fc` control still passes V1. Neither result is rewritten.

The actual hinge is at Y 0.267 m, above the actual oral-deformation support
(maximum Y 0.259786 m) and below the eye axes (Y 0.288414 m). Its Z 0.035 m is
behind every actual tooth vertex (minimum Z 0.136677 m). All other control/child
neutral world transforms stay within `2.78e-17` of the original. These are useful
stylized-rig constraints, not a measured human temporomandibular-joint claim.

The old morph-region checks pass with their original anatomical landmark fixed.
The diagnostic coordinate view moves only the control back to that landmark and
compensates its rigid children; it never replaces the actual asset, actual-hinge
geometry runs or rendered images. Original hierarchy, malformed-data, morph,
skin and resource checks remain active. No V1 boundary is enlarged.

All **16 distinct adversarial cases pass**. Cases 1–13 completed before the full
batch reached its 60-second process budget; unchanged cases 14–16 subsequently
completed in a separate bounded run, exit 0. The full single-process run is not
claimed to have exited 0. Seven original malformed-control examples remain
rejected, along with shifted teeth, bad hinges, skin coupling and out-of-region
mouth/eye deformations. Complete case identities and raw outcomes are retained.

The initial checker itself confused rigid node origins with baked tooth-vertex
positions. That original 15/16 outcome is preserved. The correction tests the
actual transformed vertices and keeps the same behind-teeth requirement. A later
CPU-reservation interruption is also retained; no interrupted case is counted.

Reproduce using the pinned original GLB from `26555aa6`, the candidate GLB from
`62122c55`, and the unchanged legacy validator from the candidate checkout:

```bash
export PYTHONDONTWRITEBYTECODE=1
export HINGE_LEGACY=/path/to/62122/tools/epsilon-avatar-contract/validate_glb.py
export HINGE_ORIGINAL=/path/to/26555/app/src/main/assets/avatar3d/continuum-avatar.glb
export HINGE_CANDIDATE=/path/to/62122/app/src/main/assets/avatar3d/continuum-avatar.glb
python3 tools/jarvis-reference-review/test_hinge_v2.py
python3 tools/jarvis-reference-review/validate_hinge_v2.py \
  "$HINGE_LEGACY" "$HINGE_ORIGINAL" "$HINGE_CANDIDATE" /tmp/hinge-v2.json
```

Use one CPU. On a constrained host, invoke individual named unittest methods in
bounded groups; preserve all identities and process exits. The CLI's exit 0 means
the explicit V2 check succeeds; its JSON separately retains actual V1 FAIL.
`hinge-v2-final-receipt.json` binds source, checker, test, raw-log and asset hashes.

This result resolves the narrow legacy-control semantic conflict. It does not
approve experimental HDR materials, final likeness/naturalness, actual speech,
native Filament performance or Fold7 execution. Original raw reference media is
not included in the repository.
