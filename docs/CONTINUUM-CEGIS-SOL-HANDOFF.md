# CEGIS-Synthesizer: überprüfbare Sol-Übergabe und sichere Integration

Stand: 18. Oktober 2026. Grundlage: der unveränderte M4-Review-Commit `f7308898a16eb3fa387bcc9984ec457f3d88b0d9` in PR #28.

## Stand und harte Abnahmegrenze

Sol meldete einen **ausschließlich lokalen**, nicht gepushten Commit:
`d0ff6e97857f40ea584af67cdcc65accd89fe852` auf Branch `feat/bounded-cegis-synthesizer`. Gemeldet: elf neue Dateien (fünf Module, fünf Testmodule, eine Dokumentation), 80/80 neue Tests mit Node 22/24, 411 erfolgreiche Continuum-Tests, 19 Backend-Tests, 23 Reparaturakzeptanztests sowie ein verifizierter Git-Patch. Diese Ergebnisse sind **nicht unabhängig bestätigt**.

GitHub kennt den angegebenen Commit und Branch zum Zeitpunkt der Prüfung noch nicht (Commit: 422; Branch: 404). Auch die lokale Git-Bundle- oder Patchdatei steht dieser Ausführungsumgebung nicht zur Verfügung. Weder der eigentliche Synthesizer noch seine Testergebnisse werden erfunden oder als integriert behauptet.

## Eingangskontrolle des Originalcodes

Der neue `.github/continuum/cegis-intake.mjs` kontrolliert schreibgeschützt ein **bereits lokal importiertes** Kandidaten-Git-Objekt. Er führt **keinen beigesteuerten Code** aus und bewegt keine Git-Refs:

- Checkout muss sauber und exakt auf dem angegebenen Review-Basis-Commit stehen.
- Der Kandidat muss unmittelbar von diesem Commit abstammen, ohne weitere Eltern.
- Exakt elf neue reguläre Git-Dateien; keinerlei Modifikation bereits vorhandener Dateien.
- Erlaubte neue Module und Tests nur unter `.github/continuum/`, Markdown nur unter `docs/`.
- Exakt fünf JavaScript-Module, fünf Testmodule und ein Dokument.
- Keine Symlinks, keine ausführbaren Dateien, keine binären oder übergroßen Dateien.
- Syntaxprüfung aller zehn JavaScript-Dateien mit Node; **keine Ausführung der Tests**.

Aus einem auf den Basis-Commit zurückgesetzten, sauberen **separaten Review-Checkout** mit bereits importiertem Git-Objekt:

```bash
node /pfad/zur/aktuellen/device-ai/.github/continuum/cegis-intake.mjs \
  /pfad/zum/separaten/sauberen/sol-review-checkout \
  f7308898a16eb3fa387bcc9984ec457f3d88b0d9 \
  d0ff6e97857f40ea584af67cdcc65accd89fe852
```

Das Kommando funktioniert erst, wenn Sols Commit im Git-Objektspeicher dieses Checkouts vorhanden ist. Ein Git-Bundle oder ein Branch-Push muss daher vorher über den dafür vorgesehenen sicheren lokalen Review-Vorgang übernommen werden. Ein anders lautender Commit ist **nicht automatisch** Sols überprüfte Arbeit.

Ein grüner Inspector-Bericht hat ausdrücklich `approved:false`, `testsExecuted:0` und `decision:SOURCE_REVIEW_REQUIRED`. Er beweist nur Commit-Herkunft, Dateiform, unveränderten Repair-Kern und Syntax; **nicht** Funktionsrichtigkeit, Sandboxing oder Sicherheit der enthaltenen Tests.

## Unabhängige Abnahme nach erfolgreicher Herkunftsprüfung

1. Zehn Modul-/Testsourcen und Dokumentation lesen, auf Netzwerk-, Prozess-, Datei-, Evaluierungs- und Prompt-Injection-Risiken prüfen.
2. Synthesizer-Tests in isolierter Laufzeit mit eingeschränktem Dateizugriff/Netzwerk ausführen. Testdaten und native Ausführung unabhängig halten; nicht einfach Sols Testzahlen übernehmen.
3. Das gemeldete Beispiel (268 Kandidaten, zwei Gegenbeispiele, neun abschließende Tests, darunter vier unbekannte) eigenständig reproduzieren; alle Zwischenzustände und finalen Git-Diff speichern.
4. Erst dann die tatsächlichen Export-Signaturen `synthesizeExpression`, `proposeExpressionRepair`, `applyExpressionProposal` anhand des *Originalcodes* prüfen. Keine geratenen Parameter oder Rückgabefelder übernehmen.
5. Die Synthese zunächst nur als **optionalen Vorschlagslieferanten** im isolierten Worktree einhängen; vorhandene `repair-critic.mjs`-, `applyEdits`-, Baseline- und Evidenzprüfungen bleiben unverändert verbindlich. Keine automatischen Test-/Sicherheitskern-Edits.
6. Auf einem absichtlich kaputten Fixture den gesamten realen Ablauf (Diagnose → Synthese → unabhängige Tests → verifizierter Patch) nachweisen und einen Negativlauf dokumentieren.
7. PR #28 bleibt Draft; `main` wird weder direkt verändert noch automatisch gemergt. Android-/APK-/Gerätetests sind separate Abnahmebedingungen.

## Tests des Übergabeprüfers

```bash
node --test .github/continuum/cegis-intake.test.mjs
```

Die vier vorhandenen Tests benutzen echte, lokale Git-Fixtures. Sie testen die unveränderte Branchspitze, exakten Eltern-Commit, geschützte Dateien, Symlinks, ausführbare Dateien, JavaScript-Syntax, fremde Dateien und einen unsauberen Checkout. Sie **führen Sols unbekannten Code nicht aus**.
