# Independent first-welcome increment review

Status: WIP / UNVERIFIED. Separate from accepted readiness source070d2c64 and its immutable17-case evidence. Author test-only baseline3e755780; no increment implementation inspected before this case freeze.

Frozen challenges: an explicitly saved muted preference wins, including late offline-TTS readiness; no greeting while background, destroyed or guide dismissed; newly configured first-open welcome requires actual offline readiness and occurs once; delayed initialization, repeated resume/refresh, replacement Activity and duplicate callbacks cannot replay a consumed welcome; unavailable/network voice cannot produce a successful greeting claim; no microphone/camera/service/tool dispatch from welcome; successful speak-return alone cannot enable owner hearing confirmation. Existing configured preferences and owner facts remain unchanged. Inspect actual callback wiring to prevent the old generic greeting and new welcome from producing two utterances. Test actual Activity/widgets/TTS entry where practical; distinguish controlled-callback host evidence from complete onCreate or real audible playback.

No modification of accepted readiness tests/evidence or production sources. Compile scheduling remains QA-owned. Device camera, microphone, audible voice and Fold7 remain NOT_RUN.
