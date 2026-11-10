# Device AI Modellgewichte — Fortsetzung 2026-11-10

Aktueller Status: RESEARCH_SHADOW / WIP. PR #728 bleibt offen.
Branch: `research/continuum-model-weights-20261110`. HEAD immer neu lesen.

## Aktuelle Fortsetzung und Besitzer-Zwischenanweisung

Der unterbrochene HEAD `46816f6a232b1b9f5ecf6ce95af8a977af305da8` wurde
wiedergefunden; alle 72 damaligen PR-Dateien stimmen mit den GitHub-Blobs überein.
Gesicherte Schritte dieser Fortsetzung:

- `dcd5729f3faf9c61a2aff5f6981e5d38d3c16cb5`: 15/15 eingefrorene Eingaben geprüft.
- `491798a6d56f700edfff49128f4510538f5cb9cf`: unabhängiger Antwort-Gewichtsaudit.
- `d296507071b0242b32704f2810b198dd66494755`: ursprünglicher Replay-Arm ausgeführt,
  echte Gewichte und Rohdaten veröffentlicht.
- `605a81fd08a3ba740c8bf728ac3d43f5cfd64133`: beide Auswertungen/Paaranalysen,
  13 Evaluator- und 9 Paartests bestanden.

| Messung | Original | Antwort | Replay |
| --- | ---: | ---: | ---: |
| Exakte DEV-Antworten | 0/64 | 38/64 | 35/64 |
| Vollständig richtige Gegensatzpaare | 0/32 | 6/32 | 3/32 |
| Öffnen/Suchen, Vierfachauswahl | 32/64 | 0/64 | 0/64 |
| Text-Bits pro Byte | 2.801409 | 2.889189 | 2.646510 |
| Textproben mit 32 Tokens | 2/4 | 0/4 | 0/4 |

Je 128 echte Updates; alle 132.160 Gewichte verändert. Beide Arme verfehlen
das ursprüngliche DEV-Gesamtkriterium. Exakte Retention 0→0 ist ein Bodeneffekt.
135 Baseline-Messzeilen beider Arme sind byte-identisch. Eigene kleine
LM1-Forschungsgewichte, keine Gemma-LoRA oder Verbesserung der installierten App.

Bereits begonnen: `training/distill-v1/` mit genau einem KL-Erhaltungsziel gegen
das unveränderte Originalmodell, Faktor 1.0 auf 16 vorhandenen Replaytexten.
Aktueller Zwischencheckpoint: WIP, 15 native Verlust-/Gradientenprüfungen und
Null-Update-Inspektion bestanden, Training NOT_RUN. Runner/Registrierung und
unabhängige Auswertung müssen vor dem einen begrenzten Lauf fertig werden.
Keine weitere Forschungsrunde danach beginnen. EXP-05 und EXP-12 bleiben aktiv.

Die neue Besitzeranweisung verlangt sofortige Sicherung auch unfertiger Teile
und APK-Reife. Dieses Team hält sämtliche Modellgewichte außerhalb der App;
keine aktive Modellwahl, Produktverdrahtung oder Promotion. APK-Integration muss
die ungeprüften Kandidaten ausschließen. Android-Build/Fold7/Signierung:
NOT_RUN in diesem Auftrag. Kein Main-Merge, Force-Push oder Branch-Löschen.

Aktueller Einstieg: `experimental/weight-space-20261110/evaluation/contrast-audit-v1/README.md`.
Die echten Modelle liegen im dortigen Forschungsordner unter `artifacts/`.
Rückfall: Forschung nicht integrieren, Originalmodell behalten.

## Historischer Startcheckpoint

Die folgenden Abschnitte dokumentieren den Beginn vor den obigen Ergebnissen.

## Auftrag
Heinz autorisiert ausschließlich Arbeit an Device-AI-Modellgewichten mit drei
Unterstützern, kleinen wiederholbaren Experimenten und sofortiger Sicherung
jedes Ergebnisses. Kein Main-Merge, Produktgewichtswechsel oder bezahlter Compute.
Dieser aktuelle Auftrag autorisiert die isolierte Gewichteforschung trotz
historischer allgemeiner Release-Prioritäten.

## Ausgangspunkt
- Technische Basis: PR #530, `4b8fa96effd5a390bd5fd83ed84b81ac850b1d5b`.
- Eigene LM1 Float32-Gewichte; nicht Gemma-LoRA.
- #530 belegt nichtnull Trainingsfortsetzung auf 2.632 Parametern.
  Sein 16M-Roundtrip umfasst Null Updates und frische Nullmomente.
- Historische Qualitätsabnahme #508 bleibt FAIL; alte Finaldaten sind verbraucht.

## Erste offene Befunde
Der unabhängige Evaluator meldet einen zu schwachen Negativnachweis:
`weights_only_restart_diverges` vergleicht komplette Zustände mit verschiedenen
Schrittzählern; `moment_reset_alone_diverges` ebenfalls nur komplette Zustände.
Direkte Gewichtsvergleiche und eine Metadaten-Negativkontrolle werden separat
vorregistriert und ausgeführt. Bis dahin ist dies ein Quellenauditbefund,
kein neu gemessener Trainingserfolg.

Der Datenprüfer reproduzierte den historischen Intent-Datengenerator; seine
quantitative Längenprüfung wird als eigener nächster Checkpoint gesichert.

## Zuständigkeiten
1. Training: gepinnte native LM1-Quellen, reproduzierbare Gewichtsupdates/Resume.
2. Daten: frische kontrastive Entwicklungsdaten mit kontrollierten Längen.
3. Evaluation: direkte Tensorvergleiche, negative Kontrollen und Nachprüfung.
4. Koordinator: Protokollfreeze, Integration ausschließlich im Forschungsordner,
   veröffentlichte Teilstände und ehrliche Messwertauswertung.

## Ablauf
Quellen/Ein- und Ausgaben pinnen → Protokoll vor Modelllauf veröffentlichen →
begrenzten Lauf durchführen → Rohdaten/Checkpointidentitäten prüfen →
Ergebnis sofort committen und veröffentlichen. Neue Daten heißen DEV,
solange keine wirklich unabhängige Bestätigung vorliegt.

## Grenzen / Rückfall
Produktquellen, aktive Modellwahl, App, Avatar, Sprache, APK, Signierung und
main werden in dieser Runde nicht verändert. Rückfall: Forschungsordner
nicht integrieren. Alte Artefakte bleiben erhalten.
