# Cinema render QA handoff

Integration owner: Cinema root integrator. Target:
`cinema/fluid-light-stage-20261110`. This branch only adds evidence and reuses
existing native test tasks; production renderer and research #718 are untouched.

Start with [coverage and missing frames](../../evidence/cinema-render-coverage-20261110/README.md)
and [portable capture commands](../../../tools/cinema-native-qa/README.md).
The audited source is `a7b4d1a090ab7117f29f6fcc3440b60b863395a5`, equivalent
remote `5457351a7585eada2393f80aa81db4bff5cc8897`.

- `PORTIERT`: scratch capture invocation to a configured repository runner.
- `VERIFIZIERT`: Python compilation/help, seven configuration rejections,
  read-only inventory of 126 PNGs, 50 declared PNG hashes verified.
- `GERÄTEBESTÄTIGT`: NOT_RUN. Wrapper Gradle execution and two-phase execution
  are also NOT_RUN. Existing dock captures retain two failed tests.

Next: obtain the integrator's single build/render lease, use the same source
and cache for compile then capture, and retain exact-source receipts including
failures. Priorities are fixed dock pressure in both viewports, current-source
work/return and missing cover STOP. Do not substitute Java2D/Blender for native
or physical Fold7 evidence. No dependency installation, APK, signing or main merge.

Rollback removes this handoff, `tools/cinema-native-qa/` and
`docs/evidence/cinema-render-coverage-20261110/`; no app behavior changes.
