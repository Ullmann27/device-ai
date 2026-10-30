# EXP-AUTO-REPAIR-EVOLUTION · Continuum SHADOW

Stand: 30.10.2026. Ausgangsbasis: PR #248, Commit 2e33dad91a9a393f00996084c5538955bf14ca92. Bestehende reale Forschung: CCC1 / Repair Loop / M9-M16 aus PR #245.

## Implementierung

experimental/self-evolution-shadow/evolution-core.mjs enthält reine Funktionen für kontrolliertes Self-Play, Autopsie, neutrale Infrastrukturfehler, unveränderliche Forschungsepisoden, flüchtige Operator-Prioren und Makro-Review-Vorschläge.

experimental/self-evolution-shadow/mastercode-adapter.mjs verwendet den VORHANDENEN MASTER-EXP-Sucher und die VORHANDENE Validierung aus .github/continuum/repair-ledger.mjs. Es wird keine zweite produktive Reparaturpipeline angelegt. Die Verbindung ist ein Aufrufpfad im isolierten Forschungsmodul und noch KEIN Android- oder autonomer Worker-Aufruf.

## EXP-SELF-1 · Adversariales Self-Play

Eine Mutation verändert ausschließlich die zwei typisierten Parameter der vorhandenen MASTER-EXP-Modelldomäne, niemals beliebigen Quelltext mittels globalem String-Replacement. Bis zu drei Aufgaben pro Durchlauf; 1-50 als experimentelle Zyklenummer; höchstens 16 Kandidatenbewertungen pro Aufgabe; keine Endlosschleife. Der Sucher bekommt keine originalen Blindfälle. Die zweite Auswertungsfunktion prüft die tatsächliche Kandidatenkette anhand zurückgehaltener Fälle und eines mathematischen Referenz-Orakels.

Start nur bei explizit positivem Besitzer-Opt-in, Idle, Ladung, freiem Worker, vertrautem Host, Batteriestand mindestens 40 Prozent, thermischem Headroom mindestens 0,5 und Zeitfenster. Diese Eingangssignale werden im Prototyp nicht vom Android-Betriebssystem authentisiert; daher keine Produktivfreigabe aus dieser Policy ableiten.

## EXP-SELF-2 · Negative Autopsie statt blindem Gradienten

Ein belegter negativer Fall benötigt ausgeführte fehlgeschlagene Tests, einen konsistenten Exit-Code, vollständige Evidenz und SHA-256-Bindung an Aufgabe/Kandidat/Testsatz. Prozessabbruch, Timeout, fehlende Prüfung oder null Tests bleiben NEUTRAL. Autopsien enthalten keine Quelltexte und dürfen NTW1 nicht verändern.

Für nachprüfbar einzelne Operatoren erzeugt der Kern Laplace-geglättete flüchtige Prioren. Mehrstufige Fehlschläge werden nicht willkürlich einzelnen Operatoren zugeschrieben. Das ist keine NTW1-Backpropagation, sondern eine gefahrlose Forschungspriorität. Die Kennzeichnung einer Auswertungsfunktion als independent stellt keine kryptografische Isolation dar. Manipulationsresistente Bestätigung muss später über den bestehenden CCC1-Isolated-Executor und externe, unveränderliche Prüfbelege erfolgen.

## EXP-SELF-3 · Makro-Kristallisation als Vorschlag

Eine Operatorenkette benötigt mindestens zwei verschiedene erfolgreiche Aufgaben, um als SHADOW_REVIEW_CANDIDATE zu erscheinen. Niemals direkte Speicherung in Omega, NTW1-Training oder automatisches Löschen. Vor realer Nutzung sind versionierte Beweisbeziehungen, Widerruf abhängiger Makros und Owner-Review erforderlich. Erfolgsquote unter 30 Prozent rechtfertigt keine automatische Löschung.

## Messstand

Lokale Node.js 22.16.0 Kernprüfungen: 13 PASS, 0 FAIL, 0 SKIP für evolution-core.test.mjs. Eine weitere Testsuite mastercode-adapter.test.mjs enthält vier geschriebene Aufrufketten-Vertragstests für den vorhandenen Sucher und echten Ledger-Validator; sie ist NICHT auf dem GitHub-HEAD ausgeführt. Keine vollständige Node-, CCC1-, Kotlin-, Android-, Gemma- oder Fold7-Abnahme auf diesem Zweig; keine produktive APK.

In einem vollständigen Checkout prüfen:

    node --test experimental/self-evolution-shadow/evolution-core.test.mjs
    node --test experimental/self-evolution-shadow/mastercode-adapter.test.mjs
    node --test experimental/mastercode-shadow/search.test.mjs

Anschließend die in AGENTS.md hinterlegten existierenden CCC1- und Worker-Gates ausführen und Rohbelege mit dem exakten HEAD sichern.

## Weiterer Forschungsplan

1. Vollständigen Checkout mit exakt diesem HEAD testen, Fehler reparieren, committen und pushen.
2. Prüfen, ob M13 tatsächliche abgeschlossene Ergebnisse auf GitHub hinterlassen hat. Fehlende Ergebnisse nicht als Erfolg darstellen.
3. Echte Multi-Hunk-Reparaturen und historische Bugs mit der vorhandenen CCC1-/M9-Reparaturpipeline untersuchen. Der hier entwickelte Typ-Sucher kann solche Quelltextänderungen noch nicht generieren.
4. Baseline, M9-Erfahrungsstrategie und flüchtige Self-Play-Prioren auf demselben eingefrorenen Holdout vergleichen; Erfolgsrate, Fehl-Erfolge, Pass@1, CPU-/Speicherlast und tatsächliche Testkosten protokollieren.
5. Erst nach nachgewiesener Reproduzierbarkeit einen opt-in-SHADOW-Einzelzyklus an die existierende lokale Worker-Planung hängen. Kein paralleler Worker und keine selbststartende Endlosschleife.
6. Jede Änderung an Modellgewichten, Operatorbibliothek, Produktiv-App und main benötigt explizite Review-Freigabe.

Kritik am ursprünglich gelieferten Beispiel: globales Ersetzen von Vergleichsoperatoren kann Quelltext beschädigen, ein vom Solver selbst gemeldeter Erfolg ist keine unabhängige Verifikation, ein inkrementierter Makrozähler beweist keine echte Kompression und eine unbegrenzte Hintergrundschleife gefährdet Ressourcen.

Gesamtstatus: Getesteter SHADOW-Kern, zwei vorhandene Forschungsbausteine über einen neuen Adapter verbunden, keine Produktivaktivierung und keine unabhängige Fold7-Messung.
