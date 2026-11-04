# MASTER-EXP: begrenzte Operator-Komposition – Forschungsstand 30.10.2026

## Ziel und wissenschaftliche Grenzen

Dieses Experiment prüft die Kombination aus Programmkomposition, priorbasierter Reihenfolge, Gegenbeispielprüfung und Makrovorschlag. Es ist **keine** universelle Levin-Suche und kein Nachweis einer lokal universellen Superintelligenz.

Die Implementierung experimental/mastercode-shadow/search.mjs nutzt eine streng typisierte Mini-Domäne mit nur zwei erlaubten Reparaturen: Korrektur eines Slice-Endes um eins und eine Untergrenze von null für negative Quoten. Externer JS-Code wird nicht ausgeführt.

Endliche Kandidaten (Tiefe maximal 2) werden nach einer **heuristischen** Kostenfunktion geordnet:

C(p) = -log2(P(p)) + lambda * L(p) + log2(G(p))

P ist eine normierte, manuell vorgegebene Operator-Priorverteilung, L eine bewusst gewählte Surrogat-Beschreibungslänge und G ein festes Gas-Kostenmaß. **NTW1 ist nicht angeschlossen.** Der Score ist weder echte Kolmogorov-Komplexität noch die vollständige Levin-Zeitplanung und garantiert keine mathematisch optimale Zeit.

## Tatsächlich implementiert

- Zwei fest definierte, rein funktionale und typisierte Operationen. Keine eval-, vm-, Shell-, Dateisystem-, Android- oder Gerätetools.
- Endliche Suche mit maxDepth=2, gas und höchstens 16 Kandidatenbewertungen; ungültige Priors und Operatoren werden zurückgewiesen.
- Unabhängig formulierte mathematische Spezifikation zur Prüfung der sichtbaren und zusätzlichen Fälle.
- Zwei Einzel-Bug-Trainingsbeispiele sowie ein synthetischer Doppel-Bug. **Diese Beispiele sind offengelegt; die zusätzlichen Fälle sind keine unabhängige geheime wissenschaftliche Holdout-Studie.**
- Erfolgreiche zusammengesetzte Operatoren erhalten nur den Status SHADOW_REVIEW_CANDIDATE und niemals Schreibrechte auf CCC1-Ledger, NTW1 oder Produktions-Worktrees.
- SHA-256 der Beispieldaten bedeutet Integritätsvergleich, nicht Eigentümer- oder Hardware-Signatur.

Lokale Vorprüfung am 30.10.2026 unter Node 22.16.0: **12/12 gezielte Tests bestanden**. Ausführungsbefehl:

    node --test experimental/mastercode-shadow/search.test.mjs

Dies sind Tests auf einer lokalen äquivalenten Arbeitskopie. Ein vollständiger Android-, CCC1-, GitHub-HEAD- oder Fold7-Test ist nicht ausgeführt und wird nicht behauptet.

## Festgestellte Risiken im ursprünglichen eingesandten Node-Entwurf

1. Statement-genaues Textmatching überträgt gelernte return-Statements nicht automatisch auf unterschiedlich strukturierte const-Zuweisungen.
2. Ein spawnSync-Subprozess ist nicht automatisch eine OS-Sandbox. Node.js dokumentiert ausdrücklich, dass node:vm keine Sicherheitsgrenze für fremden Code darstellt.
3. Das Demo-Problem enthält Slice- und Clamp-Fehler, keinen experimentell nachgewiesenen Division-durch-null-Fix.
4. Die Makro-Beschreibungslänge wird im Original künstlich mit 0,65 multipliziert, statt tatsächlich kodiert und gemessen.
5. Endliche adversariale Proben widerlegen einzelne Kandidaten, beweisen aber keine universelle Korrektheit.
6. Die beschriebene Suche berücksichtigt keine echte ausgeführte Rechenzeit und kann daher nicht als zeitoptimale Levin-Suche bezeichnet werden.

## Nächster Anschluss an Continuum

**E1:** Separaten, vor den Änderungen versiegelten adversarialen Blinddatensatz von einem unabhängigen Prüfer erstellen; keine nachträgliche Anpassung.

**E2:** Bestehende CCC1-Worktrees, erlaubte Testprofile, isolierte Ausführung und den M9-Reparaturdatensatz verwenden. Einen geprüften Parser-/AST-Adapter schaffen, der **reale** Multi-Hunk-/Multi-Fault-Patches vorschlagen darf; keine neue Produktions-Reparaturpipeline.

**E3:** Gleichverteilte Priors, nachweislich verifizierte M9-Erfahrung und – nur sofern lokal wirklich vorhanden – NTW1-Priors bei identischen Aufgaben und Budgets vergleichen. Metriken: exakte Fixes, false successes, Pass@1, CPU-/Speicherverbrauch und Zeit.

**E4:** Versionierte Makro-Abhängigkeiten mit Widerrufs-Kaskade, Nachprüfung und unabhängigen Bestätigungen konzipieren. Selbst nach zwei erfolgreichen Dev-Prüfungen darf kein Makro automatisch produktiv werden.

**E5:** Gerätübergreifende Präsenz mit PR #246 und #247 abstimmen: Die beiden JPI1-Implementierungen haben denselben Kotlin-Klassennamen und dürfen **nicht blind zusammengemergt** werden. Für Smart-Home- und Audioverhalten bleiben Freigaben und echte Geräteabnahmen Pflicht.

## Sicherheitsstatus

Branch-Basis: PR #245 (05e6aa9a). Rein SHADOW. Keine Änderungen an main, CCC1-Ledger, Modellgewichten, Besitzerrechten, Gemma oder JPI1 und keine neue APK. Das Feld promoted ist immer false; ein geplanter Makro-Vorschlag verändert kein ausführbares Operatorvokabular.
