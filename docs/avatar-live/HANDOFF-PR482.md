# Handoff Draft-PR #482 – Jarvis-Kopf (jarvis-grafik/avatar-ref-match)

- Kerns Verträge #481 @17e1db2b gemergt; `JarvisHoloAvatar : JarvisAvatarRenderer` (attach/onAvatarState/onAnimationEvent/detach/onAvatarEvent als override).
- Registrierung `JarvisAvatarSlot.register(JarvisHoloAvatar())`: noch kein Aufruf – im Cockpit-Setup von #481 ist kein Ort dafür vorgesehen (Kern entscheidet).
- `CockpitAnimationEvent.CockpitEntered` → headIconBurst: noch nicht umgesetzt, kommt mit Kerns nächstem Push.
- GLB continuum-avatar.glb: Herkunft offen (CC0 laut NOTICE, Hash passt nicht).
- Rote Layout-Tests (ContinuumAdaptiveCockpitLayoutTest, ContinuumCoreMirrorTest): übernimmt Kern.
- Kein Körper (Ganzkörper macht Jarvis Lückenfüller).
