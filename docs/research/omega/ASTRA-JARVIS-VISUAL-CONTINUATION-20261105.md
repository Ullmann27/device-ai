# JARVIS Visual – Fortsetzung 05.11.2026

## Aktuelle Fortsetzung – zusammenhängender Arbeitsablauf

**WIP / UNVERIFIED auf integriertem Stand nach `a5723ac9`.** Die folgenden
historischen Startangaben bleiben zur Herkunft erhalten. Drei Fachbots arbeiten
jetzt nach dem erweiterten Besitzerauftrag: `avatar_motion` (ein Originalkopf),
`neon_flow` (Originalkugel, Glasfenster, Licht), `original_orb` (unabhängige QA).
Die Integrationsleitung hält gemeinsame Änderungen in `AgentActivity` klein;
`JarvisCockpitGrid` bleibt unverändert. Keine zusätzlichen Bots.

Das vorhandene Systemstatus-Fenster wird als erster real angeschlossener Ablauf
im START-Cockpit dargestellt: echte bestehende Statusabfrage, gemeinsamer
Shell-Zeittakt, geschützte Kopf-/Kugel-/Dialogbereiche, dedizierter Fensterblick,
Öffnen und Schließen. Kein neuer Gmail-/Dokument-/Coding-Erfolg wird simuliert.
Die originale Kugel war bereits korrekt transparent angeschlossen und bleibt.

Die parallel eingetroffenen Commits bis `a5723ac9` wurden gelesen und erhalten.
Überlappende Avatar-Intents werden zu einer einzigen revisions- und
fenstergebundenen Verarbeitung zusammengeführt; Legacy-APIs bleiben Adapter.
Ein Nicken wird nur an den letzten ausdrücklichen positiven Freigabeknopf
gebunden, nicht an einen eventuell automatisch erfolgreichen Web-Callback.

Letzter kombinierter Lauf vor dieser Zusammenführung: `2859f18d` plus
archivierter Quellpatch, identisch zum lokalen Checkpoint `cc2848bb`:
**127 Tests, 110 PASS, 17 FAIL, 0 SKIP**. Ursachen: 7 TTS-Fixture-Zeitfehler,
6 Draw-Hierarchiefehler und 4 native Pixel-/Skalierungsfehler. Sechs neue
isolierte Gegenproben danach: **0 PASS, 6 FAIL, 0 SKIP** auf `cc2848bb` plus
Testpatch. Dazu zählen Gesten bei wiederholtem Listening/reduzierter Bewegung,
Entfernen einer Ansicht während Draw und gebrochene Zielpixel.

Nächste Schritte: Draw-sichere Freigabe und Pixelrundung abschließen, integrierte
Avatar-/Window-/TTS-Tests ausführen, frische native Canvas-Aufnahmen aufnehmen,
semantische Ref5-Messung und Original-GLB-Replay an den finalen Quellcommit binden.
Der integrierte Stand ist bis zu diesen Läufen **nicht GREEN**.
Die Besitzerabnahme ist in
`docs/evidence/astra-jarvis-visual-20261105/FOLD7-OWNER-ACCEPTANCE.md` vorbereitet;
Filament-GPU, physischer Fold7 und echte TTS bleiben `NOT_RUN`.

## Quellen und Auftrag

- Arbeitsbranch: `claude/continuum-jarvis-cockpit-z7bo60`, Draft-PR #565.
- Geprüfter Ausgangscommit: `b294fc9139f1a458fccf815bb931eb61b2f7b0e8`.
- Übergabe: `CHATGPT6_CONTINUATION_JARVIS_VISUAL_20261105.md` und
  `docs/evidence/claude-jarvis-visual-20261105/README.md` gelesen.
- Besitzerklarstellung: **bestehenden Avatarkopf behalten**; nur die separate
  Gehirnkugel durch die alte animierte Continuum-Kugel ersetzen.
- Richtiger Renderer: `ContinuumHologramRenderer`, Originalkern und transparenter
  Hintergrund. PR #539 bezeichnet hingegen `ContinuumBrainOrbRenderer` als
  Original; diese Benennung ist keine Freigabe, ihn erneut einzubauen.
- Orb-Spender: #563, `17ee574abc021b8f1cf1edf93e96ef484ac0ec4d`.
- Randfluss-Spender: #549, `ea315130b990f4a1610d8efbc3f68f32d34162da`.

## Getrennte Arbeitspakete

1. `original_orb`: tatsächliche Renderverdrahtung, transparente Originalkugel,
   unabhängige Ringe und echte Puls-/Abrufsignale; Kopf und GLB bleiben erhalten.
2. `neon_flow`: vorhandenen Randfluss selektiv übernehmen, vorhandene Framezeit
   benutzen, reale Farben gegen die Referenz messen.
3. `avatar_motion`: eigener Fensterblick-Intent, Bestätigungsnicken und
   Denkbewegung; STOP-/TTS-Vertrag bewahren.
4. Integrationsleitung: minimale gemeinsame Anschlüsse, echte Gradle-/Robolectric-
   Läufe, unabhängiger Patchreview, Quellenbindung und Besitzerabnahmeplan.

Gemeinsame Dateien `AgentActivity` und `JarvisCockpitGrid` werden nicht breit
ersetzt. Erforderliche Anschlüsse erhalten einen getrennten kleinen C1-Handoff
für #519. Kein automatisches Übernehmen fremder PRs.

## Prüfstatus dieses Start-Checkpoints

`WIP / UNVERIFIED`: noch kein neuer Produktpatch freigegeben. Der eigene
Baseline-Lauf mit `ContinuumVisualEvidenceUiTest` und
`AvatarMotionEvidenceTest` wurde auf dem oben genannten Ausgangscommit gestartet;
sein Ergebnis ist noch offen. Historische Testzahlen werden nicht übernommen.

Lokale vorhandene Toolchain: JDK 17, Gradle 8.11.1, SDK 36, Kotlin 2.4.0;
separater Gradle-Cache, bestehender Build-Lock, Offline-Abhängigkeitsauflösung.
Robolectric-Aufnahmen zeigen Canvas und den 2D-Ersatzkopf, **keine** Filament-
GPU-Pixel. Fold7, echte TTS und reale Kamera: `NOT_RUN`.

Farbvergleich: `tools/measure_ref5_colors.py` besitzt feste Referenzkoordinaten
für 1448×1086. Andere App-Auflösungen brauchen eigene semantische Messboxen;
ein bloßes Strecken darf keine vermeintliche Farbtreue belegen.

## Grenzen und nächster Schritt

RED vor Produktänderung, GREEN auf eingefrorenem Quellstand, exakte Testfälle
und Commit-SHA. Keine neuen Modell-/Memory-Experimente; EXP-00/05/08/12 bleiben
kompatibel, ihre Abnahme wird durch diese Visual-Arbeit nicht verändert.
Keine Änderung an `main`, Paketkennung, Version, Signatur, Publisher,
Internetfreigabe, Besitzerdateien oder Originalmodellgewichten. Kein APK-Release,
keine Installation, keine kostenpflichtigen Dienste und keine Actions-Auslösung.

Nächster Einstieg: diesen Branch aktualisieren, eigene neueste Belege lesen,
danach nur die drei benannten Visual-Arbeitspakete weiterführen. Rücknahme erfolgt
pro abgegrenztem Produktcommit; historische Belege bleiben bestehen.
