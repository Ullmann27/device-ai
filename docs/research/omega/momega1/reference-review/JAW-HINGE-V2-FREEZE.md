# Explicit hinge-versus-mouth-landmark validation

This supplemental contract is frozen after inspection of the `62122c55` candidate
and its newly disclosed legacy-validator failure. It is not a blind pre-candidate
test and does not change the existing V1, V2 mapper or CCC1 tests.

The original builder at `0862cfc8` manually places `Jaw` at `(0,.227,.035)`.
It obtains the eyes from upstream geometry groups, but does not derive this jaw
axis from a measured anatomical joint. Its mouth-deformation mask independently
ends at canonical Y `.260`. The old Python validator uses the control's position
both as a joint bound and as the lower landmark for eye/mouth morph localization.
Moving a control axis must not move that localization boundary.

The actual unmodified V1 result on the packaged `6b199c59` asset is retained as
FAIL. No enlarged V1 range or claimed physiological/medical certification is
permitted. The following separate V2 requirements will be checked:

1. Parse the actual candidate through the original binary/graph checks. Keep
   actual parent hierarchy, finite transforms, independent eyes, mesh/skin/morph
   bounds and resource limits.
2. Check the **actual** hinge separately: it is a Head descendant; it does not
   skin the exterior face; it is inside the rest face bounds, centered between
   the eyes, behind the teeth, below the eye axes and above the authored oral
   deformation support. This describes a plausible stylized rigid-mouth control,
   not a measured human temporomandibular joint.
3. Keep the original anatomical mouth-region landmark at the pinned original
   rest location. For the unchanged V1 morph-localization/skin checks, construct
   an explicitly labeled diagnostic coordinate view that moves only the Jaw
   control back to that landmark and inversely compensates its rigid children.
   Actual neutral world transforms must be identical. This view is never shipped,
   rendered as the candidate, or reported as a V1 PASS for the actual asset.
4. Compare actual rigid-child neutral transforms with the pinned original asset.
   Reparenting, moved teeth, nonfinite values or arbitrary inherited scale cannot
   acquire permission through coordinate normalization. Materials and the
   separately reviewed eye/texture experiments do not authorize mouth changes.
5. Run actual-pose geometry separately at the actual hinge; the 119 existing
   sampled checks remain mandatory. Passing a normalized diagnostic view cannot
   replace those checks or the actual rendered images.

Frozen counterexamples for the V2 checker include: legacy V1's original malformed
controls; a hinge below active oral deformation; a hinge at/above the eyes; a
hinge in front of the teeth; a lateral hinge; missing child compensation; one
translated tooth; changed Jaw parent; nonfinite hinge; skinned Jaw dependency;
changed eye order; altered mouth morph outside the original lower-face region;
an upper-face blink mutation; and a changed or unpinned original landmark source.
They must be rejected, including cases whose other neutral geometry remains intact.

The original asset is a V1 control, not an expected V2 hinge success: its actual
reachable mouth defects are already reproduced. The current functional correction
is a V2 candidate, not an automatic expected PASS. If the stronger actual-hinge
constraints fail, the candidate remains held and the failure is retained.
