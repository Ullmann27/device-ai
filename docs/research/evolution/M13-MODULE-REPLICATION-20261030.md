# EXP-M13 · modulbasierte Replikationsprüfung

Stand: 30.10.2026 · PR #249 (SHADOW) · Forschungsbasis PR #245 und PR #248.

## Was jetzt unmittelbar angeschlossen ist

Die bestehende M13-Datei-CLI `experimental/repair-transfer-lab/m13-evidence-cli.mjs` führt nach der vorhandenen Quellstand-, Siegel-, Zähler- und Teststatistikprüfung zusätzlich `auditM13ModuleReplication` aus. Bei widersprüchlichem Originalbericht wird die zusätzliche Auswertung **nicht** ausgeführt. Sie benötigt keine neue Datenbank, keinen separaten Worker, keine Netzwerkverbindung und keine Modellgewichte.

Die neue Datei `m13-module-replication.mjs` benutzt **Module**, nicht einzelne Reparaturkandidaten, als statistische Vergleichseinheit. Innerhalb jedes DEV-Moduls wird die Erfolgsrate der untersuchten Diagnose-/Strategie-Kombination mit den anderen Diagnosen derselben Strategie verglichen. Nur Module, in denen beide Gruppen vorkommen, zählen. Gleiche Erfolgsraten zählen als Gleichstand, nicht als Erfolg.

Es wird ein einseitiger exakter Vorzeichentest auf Modulebene berechnet, nur wenn mindestens sechs unterschiedliche vergleichbare Module mit eindeutiger Differenz vorliegen. Bei mehreren gemeldeten Hypothesen werden die p-Werte nach Holm korrigiert; **auch ursprünglich nicht bestätigte Hypothesen zählen für die Multiplikitätskorrektur**. Die Auswertung bleibt `MODULE_REPLICATED_RESEARCH_ONLY` und lässt ausdrücklich keine automatische Weitergabe an CCC1, NTW1, ToolGate oder `main` zu.

## Warum die Ergänzung notwendig war

Das ursprüngliche M13-Labor behandelte mehrere aus demselben Modul stammende Kandidaten in seinem Binomialtest als einzelne Versuche. Ein einzelnes Modul mit sehr vielen ähnlichen Treffern kann dadurch scheinbar extrem kleine p-Werte produzieren, obwohl keine Replikation über unabhängigen Code gezeigt wurde. Die bisherige Prüfung bleibt erhalten, um die originale Aufzeichnung zu rekonstruieren; der **zusätzliche Modulbefund** verhindert die Gleichsetzung mit belastbarer Verallgemeinerung.

Diese neue Methode hat selbst eine wesentliche Voraussetzung: Auch verschiedene Module können gemeinsame Testbibliotheken, Fehlerursachen, Vorlagen und Entwicklungsabstammung besitzen. Die Annahme voneinander unabhängiger Module wird nicht nachgewiesen. Die Auswertung ist deshalb **Forschungsevidenz unter Annahmen**, keine kausale Gewissheit. Sechs Module und p < 0,05 sind keine universelle Qualitätsfreigabe.

## Reproduzierbarer Testlauf

Auf einem lokalen Node.js 22.16.0 wurden auf bytegenau zu GitHub passenden Quellen ausgeführt:

    node --test experimental/repair-transfer-lab/m13-evidence-cli.test.mjs experimental/repair-transfer-lab/m13-module-replication.test.mjs

**15 PASS · 0 FAIL · 0 SKIP · Exit 0.**

Roh-TAP ist im Repository: `docs/evidence/evolution/m13-module-replication-node22.tap`.

Git-Blob-Prüfsummen der ausgeführten Quellen:

- Modulprüfung `2ce86a42a2be9a4aac0d2778ac446c130b4d2ae5`
- Modulprüfungstests `d5b752ea5259098f0e7dd59054985f314f81e4f3`
- M13-CLI `422ddb7089219bd361c6d6c9f40d730439868e78`
- Bestehender M13-Prüfvertrag `d5a7d558b20371566d4a546f2cbf4a9e6492a6e6`
- CLI-Tests `09d460d51893e7adfc5bac53983ce794c77bda6a`
- Roh-TAP `f715c3affefe626191ddf3064c5d6dd75cd65b10`

## Noch offen

1. M13 selbst wurde in dieser Runde nicht mit echten Kandidaten aus dem vollständigen Repository ausgeführt. Sein abgekoppelter früherer Prozess ist aus GitHub nicht nachweisbar abgeschlossen.
2. Die neuen Tests sind lokal ausgeführt. Vollständige CCC1-/Worker- und Android-/Fold7-Regressionen für PR #249 bleiben offen; keine CI-Statusprüfung wurde für diesen HEAD gemeldet.
3. Bei tatsächlich aufgenommenem M13-Lauf müssen vollständige Rohbelege, genaue Versionen, unabhängige Modul-Holdouts und die neu berechneten Modul-p-Werte veröffentlicht werden.
4. Erst ein fairer Vergleich zum unveränderten M9-adaptive-Baseline-Modell auf unabhängigen historischen Fehlern kann einen realen Intelligenzgewinn belegen.

Der Code hat keine Berechtigung, Operatoren zu promoten oder zu löschen, Modelldaten zu trainieren, Quellbranches zu mergen oder Geräte zu steuern.
