# Nullpunkt 17.11.2026 – Übergabe an den Integrations-Bot

- Branch: `grok/nullpunkt-20261117` (Basis `00b3c8b8412da4b4a6a98f8236dcfd11543c1f5a` = Quelle der Code62-Pre-release, Run 44035893856)
- Draft-PR: #860 (Base `integrate/continuum-command03-android-20261115`, nicht mergen ohne Heinz)
- Bitte in `code52/grok-integration-all-20261117` mitnehmen.

## Commits
1. `dc18451f5` fix(cockpit): RecallHits (MEMORY_TURN) ersetzt keine offene Dokumentwelt mehr + Test `openedDocumentIsNotReplacedByUnscopedRecallHits`.
2. `8f0d169b5` ci: volle Exact-Source-QA (`continuum-code57-voice-full-qa.yml`, unsigniert) läuft auch auf diesem Branch.
3. `55a3fe015` fix(settings): Datenschutz-Zusage „nicht automatisch trainiert“ im Personalisierungsdialog wieder sichtbar (`setCustomTitle(choiceDialogHeader(...))`). Lokal: critical QA PASS (android-source-contracts 66/0).

## QA
- Lauf 44073545347 auf `8f0d169b5`: FAILURE.
  - unsigned-arm64 und renderer-preflight: grün.
  - node-backend: rot (nexus-beginner-ui-Vertrag, durch Commit 3 behoben).
  - native-runtime: rot.
    - `native_hologram_evidence.py:246`: Aktionsknopf ist nicht SCHLIESSEN/NEIN.
    - `native_routines.py:642`: Insufficient process time for periodic wait.
    - „Landscape input/navigation unreachable after scrolling“.
  - full-android: rot (Full suite + Skip/Code59/Code60-Coverage-Gates). Die Einzeltests sind noch nicht ausgewertet.
- Für Code60–62 gibt es keinen grünen vollen QA-Lauf. Code62 wurde nur fokussiert geprüft (44035212452 auf `3fe1ae65`, gleicher Tree).

## Offen
- Die native- und full-android-Fehler auf der Code62-Linie.
- Legacy-Dateien: video3 (#487, 7 Dateien) und 10 (d)-Dateien (#264/#273/#326) fehlen. ui-fix `fbe35a7de` ist nicht drin.
- #802 ist teilweise nicht enthalten (Live-Scroll/Input).
- Signieren: Der Signer verlangt einen workflow_dispatch-QA-Lauf auf demselben Branch und SHA. Das Code60-Profil baut versionCode 60, das liegt unter dem installierten 62. Ein Update ≥63 braucht eine Pipeline-Entscheidung von Heinz.
