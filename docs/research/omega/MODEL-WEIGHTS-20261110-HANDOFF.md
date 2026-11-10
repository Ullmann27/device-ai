# Device AI Modellgewichte — Fortsetzung 2026-11-10

Status: RESEARCH_SHADOW / Zwischenstand, neue Modellversuche NOT_RUN.

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
