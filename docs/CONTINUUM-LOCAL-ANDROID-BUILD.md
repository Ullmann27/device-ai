# Continuum: lokales Android-M4-Gate ohne GitHub Actions

Stand: 17. Oktober 2026. Zweck: Ein reproduzierbarer, fehlerschließender Build-Nachweis aus einem **vollständigen lokalen Git-Checkout**, auch wenn die GitHub-hosted Runner nicht verfügbar sind.

## Derzeitiger GitHub-Actions-Blocker

Die GitHub-Weboberfläche für Workflow-Run [39009473864](https://github.com/Ullmann27/device-ai/actions/runs/39009473864) zeigt: "The job was not started because recent account payments have failed or your spending limit needs to ...". Build und Repair haben keine Steps ausgeführt. Dies ist ein **Account-/Abrechnungs- oder Budgetblocker**, kein Beweis eines Compilerfehlers. Ob eine fehlgeschlagene Zahlung oder ein Limit konkret ausschlaggebend ist, bleibt anhand der abgeschnittenen Nachricht offen. Die Warnung zur Ubuntu-Image-Migration ist nicht ursächlich.

Nur der Kontoinhaber kann unter [GitHub Billing](https://github.com/settings/billing) den genauen Befund prüfen und ggf. beheben. Budget nicht ohne bewusste Kostenentscheidung erhöhen. Neue Workflow-Runs lösen diesen Blocker nicht; keine Änderung an YAML oder Android-Code als vermeintliche Reparatur dafür durchführen.

## Voraussetzungen für einen lokalen Prüflauf

Ein unveränderter, vollständiger Checkout des zu prüfenden Commits, Git, Bash, Node.js 20/22, JDK 17, Gradle **8.11.1**, Android SDK Platform **36** und Android Build-Tools **35.0.0**. Dazu alle von Gradle benötigten Abhängigkeiten. Der erste lokale Build kann Netzwerkzugriff für Maven-Artefakte benötigen; "ohne GitHub Actions" ist **nicht automatisch** "ohne Internet".

Kontrollieren:

```bash
java -version
gradle --version
node --version
git status --short
git rev-parse HEAD
```

Die lokale Umgebung muss `ANDROID_HOME` oder `ANDROID_SDK_ROOT` auf das vollständig installierte SDK setzen. `JAVA_HOME` muss, sofern gesetzt, auf JDK 17 zeigen.

## Ausführung

Aus dem Root des Device-AI-Checkouts:

```bash
EXPECTED_SHA="$(git rev-parse HEAD)"
bash scripts/local-m4-android.sh "$EXPECTED_SHA"
```

Für eine externe Übergabe sollte die erwartete SHA **vorab aus dem freizugebenden GitHub-Commit** übernommen und nicht blind aus einem beliebigen lokalen HEAD gelesen werden. Das Skript verweigert unsaubere Checkouts, falsche Versionen und fehlende SDK-Dateien. Es ruft `.github/continuum/m4-local-gate.mjs --android` auf und führt nach Erfolg zusätzlich dessen unabhängige `--verify`-Prüfung gegen den erzeugten Bericht aus.

Der M4-Lauf nutzt einen separaten Git-Worktree und schreibt Nachweise standardmäßig unter `${XDG_STATE_HOME:-$HOME/.local/state}/device-ai-continuum/proofs`. `gate-report.json` und die APK werden durch die bestehende Gate-Implementierung verknüpft. Das SHA-256-Feld des Gate-Berichts ist ein Integritätscheck, keine digitale Signatur.

## Neuer verpflichtender Reparatur-End-to-End-Nachweis (M4 v2)

Nach erfolgreicher Node-Prüfung startet das lokale Gate innerhalb eines isolierten Worktrees den separaten, zeitlich begrenzten `runRepairE2e()`-Prüflauf. Dazu führt es die realen Dateien `repair-loop.test.mjs` und `repair-cycle-safety.integration.test.mjs` in einem eigenen Node-Prozess aus. Die drei festgelegten Reparaturfälle müssen nachweislich ausgeführt und bestanden sein. Ergebnisse mit fehlenden, übersprungenen oder manipulierten Pflichtfällen werden abgewiesen.

Das Gate speichert `repair-e2e/repair-e2e-junit.xml` außerhalb des Checkouts, referenziert den SHA-256-Wert sowie die tatsächliche Anzahl ausgeführter Tests und prüft den Bericht **vor Beginn der Android-/Gradle-Phase** erneut. Im abschließenden `--verify`-Schritt werden Datei und Prüfsumme erneut unabhängig eingelesen. Ein fehlender oder nachträglich veränderter JUnit-Nachweis verhindert `releaseReady: true`, selbst wenn die Android- und APK-Felder grün aussehen.

`gate-report.json` verwendet deshalb jetzt `version: 2` mit `repairE2e`, `summary.repairE2ePassed` und `summary.executedRepairE2eTests`. Ältere `version: 1`-Berichte sind **kein gültiger Release-Freigabenachweis**. Dies ist eine absichtliche Fail-Closed-Migration; historische Berichte bleiben archiviert und werden nicht umetikettiert.

Gezielte Vertrags- und Integrationstests:

```bash
node --test .github/continuum/m4-mandatory-e2e-gate.test.mjs .github/continuum/m4-repair-e2e.test.mjs .github/continuum/m4-local-gate.test.mjs
```

Diese Prüfungen sind nicht gleichbedeutend mit einer erfolgreichen kompletten Gradle-/Android-Abnahme; der Android-SDK-/JDK-17-Build muss weiterhin real durchlaufen.

## M4-Nachweiskonsistenz: ein einziger geprüfter JUnit-Dateistand

Der eigenständige Reparatur-Nachweisprüfer hashte die XML-Datei früher und ließ sie anschließend vom separaten JUnit-Dateiparser **erneut öffnen**. Durch ein konkurrierendes Austauschen hätte sich die Hashprüfung somit auf andere Bytes beziehen können als die Auswertung. Der M4-Reparaturprüfer verwendet deshalb jetzt einen einmalig geöffneten regulären Dateideskriptor, verweigert Symlinks (`O_NOFOLLOW`), prüft Geräte-/Inode-/Größenübereinstimmung sowie vollständiges Lesen und verarbeitet anschließend **dieselbe Bytefolge** in `verifyJUnitXml()` und `verifyMandatoryCases()`. Die SHA-256-Prüfung bezieht sich auf exakt diese Bytefolge.

Die bisherige `verifyJUnitReport(file)`-Schnittstelle bleibt für bestehende Node-/Backend-Berichte erhalten. Neu ist `verifyJUnitXml(xml)` für bereits gelesene, unveränderte XML-Dokumente. Der E2E-Report wird für diese Prüfung nicht erneut von der Festplatte gelesen. Diese Absicherung ist ein Schutz gegen Dateiaustausch und zufällige Inkonsistenzen, **keine digitale Signatur oder ein Ersatz für eine Betriebssystem-Sandbox**.

Gezielte Regression:

```bash
node --test .github/continuum/m4-junit-memory.test.mjs .github/continuum/m4-repair-e2e.test.mjs
```

Beide Module wurden lokal mit Node 22 geprüft: **9/9 Tests bestanden**, ohne Fehler oder Skips. Darunter sind echte Node-JUnit-Reporter-Ergebnisse, verschachtelte Tests, fehlende Pflichtfälle, manipulierte Hashes, Symlink-Negativtests und die Prüfung auf genau einen Datei-Lesevorgang.

## M4-Schutz gegen fingierte XML-Testfälle

Ein adversarialer Regressionstest hat einen **falsch positiven** Reparaturnachweis reproduziert: Ein JUnit-Bericht enthielt drei Pflicht-Testnamen ausschließlich als Text innerhalb eines XML-Kommentars und manipulierte dazu passend die Summenkommentare. Der alte Prüfer erkannte fälschlich \`passed: true\` und drei ausgeführte Fälle. Eine zweite Variante versteckte angebliche Testfälle innerhalb eines \`<system-out>\`-Elements. Beide Varianten ließen sich zuvor mit formal passendem SHA-256-Beleg akzeptieren; der Hash beweist nur die Inhaltsübereinstimmung, **nicht die Authentizität einer Testausführung**.

Die zentrale \`verifyJUnitXml(xml)\`-Implementierung verwendet nun \`structuralJUnitMarkup(xml)\`, damit XML-Kommentare keine Testfälle bzw. Testsuites vortäuschen können. CDATA, DTD und unsichere XML-Deklarationen werden ausgeschlossen. Eine begrenzte, deterministische Strukturprüfung zählt \`<testcase>\` und \`<testsuite>\` nur unter den zulässigen JUnit-Elternelementen. \`verifyMandatoryCases()\` arbeitet ebenfalls mit dem kommentargefilterten Dokument. Der alternative \`verifyM4AcceptanceEvidence()\`-Pfad verwendet für Pflichtfallnamen und Zusammenfassung denselben Datei-Lesevorgang.

Die Prüfung ist bewusst ein streng begrenzter Validator für die von Node erzeugte JUnit-Struktur und kein universeller XML-Parser. Sie liefert **keine kryptographische Bestätigung, wer die Testdatei erstellt hat**. Die bereits bestehenden Isolations-, Worktree-, Testausführungs- und Freigabegrenzen bleiben deshalb verpflichtend.

Gezielter Regressionstest:

\`\`\`bash
node --test .github/continuum/m4-junit-memory.test.mjs .github/continuum/m4-repair-e2e.test.mjs
\`\`\`

Auf Node.js 22: **16 von 16 Tests bestanden, null Skips, null Fehler**. Enthalten sind echte JUnit-Reporter-Ausgaben, geschachtelte Tests, fehlerhafte XML-Strukturen, manipulierte Kommentare, unzulässige Testcase-Elternelemente sowie vorhandene Pfad-/Hash-/Symlink-Negativtests. Die vier im GitHub-Branch geänderten Modul- und Testdateien wurden durch Vergleich der Git-Blob-SHAs mit der lokal ausgeführten Fassung bestätigt. Die vollständige Repository-, Kotlin- und APK-Abnahme muss separat real ausgeführt werden.

## Welche Abnahme dadurch **nicht** ersetzt wird

Das aktuelle M4-Gate führt seine vorgesehenen Node-/Backend-/Kotlin-Profile und einen Debug-APK-Build aus. **Eine erfolgreiche M4-Prüfung allein beweist noch nicht:** Android-Lint `gradle :app:lintDebug`, vollständige Kotlin/Node-Paritätsrunden mit ihren Pflicht-Umgebungsvariablen, einen signierten Release-Build, die korrekte Integration des LiteRT-LM-Modells oder die Ausführung auf einem realen Samsung-Gerät.

Diese Prüfungen sind gesonderte, weiterhin verbindliche Abnahmebedingungen. Kein Merge in main/production, keine Behauptung "APK auf Gerät erfolgreich", solange die echten Protokolle fehlen.

## Prüfnachweis für den Startmechanismus

```bash
node --test .github/continuum/local-m4-android-gate.test.mjs
```

Die Tests verwenden ausdrücklich simulierte Java-, Gradle-, Node- und SDK-Dateien. Sie testen die **Fail-Closed-Vorbedingungen des Startskripts**, nicht das Vorhandensein einer tatsächlich gebauten APK.
