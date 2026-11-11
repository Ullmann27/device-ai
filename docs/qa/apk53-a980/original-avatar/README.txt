ORIGINALAVATAR CODE53 — ISOLIERTES CPU-RENDERING
App-Quellstand: a980a19ed3ab44323009ae27eb299d322ff11679
Original-GLB unveraendert; SHA256 c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a.
Acht unveraenderte Pose-Stichproben aus AvatarMotionEvidenceTest des vollstaendig bestandenen Android-Hostlaufs. Kopf, Hals, Augen, Kiefer und alle21 Morphgewichte wurden gegen die exportierte Zeitreihe verglichen.
Blender4.3.2/Cycles/CPU,512x640,16Samples,1Thread. Bestehender Renderer prueft Skinning und Gelenktransformationen numerisch gegen die kanonischen glTF-Transformationen.
Studio-Licht und Glow sind kein Filament-Pixelnachweis. Keine neue Avatar-Geometrie, kein Ersatzmodell, keine Bildgenerierung.
Mikrofon, Kamera, TTS-Wortbereiche und Arbeitsereignisse sind geskriptete Testeingaben, keine echten Audio-/Kameraaufnahmen. Fold7/Filament/GPU/Thermik/FPS: NOT_RUN.
Reproduktion: bash replay-original-avatar.sh /path/to/exact-a980-repository /path/to/this-directory /path/to/new-output
Die benoetigte Renderimplementierung ist im Repository unter tools/avatar-3d/reference-atlas/render-production-poses.py bereits enthalten. Keine APK-/App-Aenderung fuer diesen Nachweis.

All eight original PNGs are committed alongside pose-replay-manifest.json.
Each PNG must match its frame imageSha256. These are Blender CPU renders,
not Filament/device acceptance. The owner download package is an additional copy.
