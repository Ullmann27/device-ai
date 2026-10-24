# Continuum M5 – Entwurfs- und Bestätigungsgrenze, 24.10.2026

## Ausgangspunkt
- Repository: `Ullmann27/device-ai`, ausschließlich Device AI Continuum.
- Ausgangscommit: `144ab574b4c06e9bed926f78158a7183e6dfb013` (Sol, Draft-PR #109).
- Isolierter Branch: `chatgpt/continuum-m5-draft-approval-guard-20261024`.
- Betroffene Experimente: EXP-00 (Android-Projekt), EXP-08 (sicherer Auftragskanal), EXP-09 (festes Buildrezept), EXP-12 (Zustands-/Freigabekontinuität).
- Unverändert: zwei M4-Projektvorlagen, CCC1/Termux, Besitzer-APK/Signierung, Gewichte, CTX3-TEST3, Main, UI-Layout.

## Nachvollziehbarer Fehler
`ContinuumM5LabActivity` prüfte eine Projektvorschau beim Öffnen eines Bestätigungsdialogs gegen die aktuelle `ContinuumCodingConversation`. Eine **nachträglich bearbeitete, noch nicht abgesendete** Eingabe änderte diese Gesprächsrevision aber nicht. Der Bestätigungscallback prüfte den Entwurf ebenfalls nicht. Damit konnte eine alte Dialogbestätigung an den Builder weiterleiten, obwohl im Eingabefeld bereits eine Korrektur stand. Nach Activity-Wiederherstellung konnte eine gültige rekonstruierte Planvorschau trotz wiederhergestelltem, noch nicht abgesendetem Entwurf aktiv sein.

## Enge Behebung
- `TextWatcher` deaktiviert die Übergabe bei jeder Entwurfsänderung, auch wenn die Änderung danach gelöscht wird.
- Beim Öffnen des Bestätigungsdialogs wird die Entwurfsrevision festgehalten; Bestätigung erfordert dieselbe Revision und ein leeres Eingabefeld, zusätzlich zu den bisherigen Activity-, Gesprächs-, Katalog-, Planrevision- und SHA-Gates.
- Initialer Buttonzustand nach SavedState-Replay erfordert ein leeres Eingabefeld; selbst ein programmatischer Klick mit einem ungesendeten Entwurf kann keinen Dialog öffnen.
- Keine automatische Generierung, Installation oder Ausführung. Erzeugen und Bauen benötigen weiterhin eigene Freigaben im existierenden Builder.

## Tests und ehrlicher Status
- Zwei neue Robolectric-Regressionsfälle im bestehenden `ContinuumM5UiTest`:
  1. Entwurfsänderung nach Öffnen des Dialogs verwirft die alte Freigabe und lässt sie durch bloßes Löschen nicht zurückkehren.
  2. Activity-Recreation mit wiederhergestelltem Entwurf stellt keine Übergabefreigabe wieder her.
- Quellvergleich mit GitHub: Baseline plus vier Implementierungs-/Testcommits; nur die beiden genannten Kotlin-Dateien geändert. Schlüssel- und Shellpfade unverändert.
- Statische Bedingungen im tatsächlichen GitHub-Quelltext nach dem Push überprüft. **Neue Robolectric-Tests NICHT ausgeführt.** Das aktuelle Ausführungsumfeld hat Java 21/Kotlin 1.9, aber kein Android-SDK, Gradle oder Robolectric-Testclasspath. Daher keine Aussage `SDK_TESTED` oder `DEVICE_TESTED`.
- Sols frühere 1.085/1.085 Node, 60/60 Backend und 913/913 SDK-JUnit belegen ausschließlich den älteren M5-Quellstand. Sie zählen nicht als Test dieses Patches.
- Keine Besitzer-APK gebaut, keine Tests am Samsung Fold7.

## Nächster zwingender Prüfschritt
Auf einem frischen Checkout **dieses** PR-HEAD mit vorhandener Android-SDK36-/Gradle8.11.1-Toolchain:
```sh
gradle --offline --no-daemon --max-workers=2 --console=plain -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 backend/test/*.test.js
```
Bei einem fehlschlagenden Gate: kein Merge, stattdessen Fehlerbeleg und eigener Reparaturcommit. Erst nach grünem vollständigem Gate die Integration gegen den neuesten Opus-/Sol-Reviewstand und Besitzer-Release-SHA prüfen. Nicht automatisch nach `main` übernehmen.

## Verbliebene unabhängige Probleme
- Sols zuletzt sichtbarer Remote-Durchlauf meldete einen Fehler. Der Screenshot allein liefert keinen reproduzierbaren Fehlerstack. Die GitHub-Actions-Läufe `Continuum Hourly Watch` auf `main` schlagen mehrfach vor den lesbaren Schritten fehl (Beispiel: run 40058942949, Job ohne Step-Daten; Log-Download nicht verfügbar). Das ist nicht nachgewiesen die Ursache des Sol-Remote-Fehlers.
- Physische Android-Chat→Termux→Build→Callback-Abnahme auf dem Fold7 fehlt.
- CTX3 ist nach unabhängigem TEST3 explizit nicht promotet und bleibt Shadow/Off.
- Besitzer-Update 0.3.18/code19 ist eine getrennte freigabepflichtige Integration, kein Teil dieses Patches.

## Rollback und Fortsetzung
Nur die beiden Kotlin-Dateien und diese Dokumentation sind Gegenstand des isolierten Patches. Durch Rücknahme der Commits auf diesem Branch wird Sols HEAD `144ab574...` wiederhergestellt; weder Force-Push noch Main-Eingriff erforderlich. Nächste Arbeit: echten SDK-Test auf PR-HEAD ausführen, Ergebnisse committen, dann erst konfliktbewusst in die nächste Integrationskette übernehmen.
