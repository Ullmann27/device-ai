# Build-Reparatur-Bot: frühe QA-Versionsprüfung

Stand: 04.11.2026. Ausgangspunkt: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`
(PR #491). Lokaler geprüfter Codecommit:
`287fd417152e46b76bcc3f1441c58b7b5060a26e`. Der Hauptagent führt die
Veröffentlichung und Integration im gemeinsamen Draft-PR #505 aus.
Dieser lokale Commit allein behauptet keinen bereits erfolgten Remote-Push.

## Befund und Reparatur

`scripts/render-continuum-owner-continuous-qa.sh` unterstützt den
Besitzer-QA-Modus und den gewöhnlichen, isolierten QA-Modus. Beide übergeben
`CONTINUUM_RELEASE_VERSION_CODE` an Gradle, APK-Versionsprüfung und QA-Beleg.
Ein Standardwert ist im Script nicht vorgesehen.

Auf dem Ausgangsstand prüfte nur der Besitzer-Modus die Version. Das echte
Script ließ im gewöhnlichen Modus fehlende, leere, zu kleine, zu große und
nichtnumerische Werte bis zum ersten Downloadversuch gelangen. Der
Besitzer-Modus gab bei `039` eine Bash-Oktalfehlermeldung aus und ließ
`18446744073709551655` durch seine arithmetische Bereichsprüfung gelangen.
Diese Zahl läuft in der getesteten Bash-Integerarithmetik über.

Die gemeinsame Prüfung erfolgt jetzt unmittelbar nach der Git-SHA-Prüfung,
vor Zweigwechsel, QA-Verzeichnissen, Werkzeug-Downloads oder Tests. Erlaubt
bleiben kanonische dezimale Ganzzahlen von 39 bis 9999. Führende Nullen,
fehlende Werte und überlange Zahlen werden vor arithmetischer Auswertung mit
dem bestehenden Fehlercode 69 zurückgewiesen. Es gibt keinen neuen
Standardwert und keine geänderte App-Version.

## Tatsächlich ausgeführte Prüfung

- Neue Regression gegen das ursprüngliche Script: **32 Tests, 20 PASS,
  12 FAIL, 0 SKIP**. Die zehn gewöhnlichen QA-Fälle sowie der Oktal- und
  Überlauffall im Besitzer-Modus reproduzieren die Lücke.
- Nach Reparatur auf dem oben genannten sauberen Codecommit: **70 Tests,
  70 PASS, 0 FAIL, 0 SKIP**. Das umfasst 32 neue Preflight-Fälle und 38
  bestehende Katalog-, Publisher-, One-Tap- und Host-Preflight-Prüfungen.
- Gültige Werte 39, 42 und 9999 passieren in beiden Modi die Vorprüfung.
  Fehlende Besitzerfreigabe, falscher Host/Dienst, ungültiger Modus und
  Signiermaterial in QA werden weiterhin abgewiesen.
- Das tatsächliche QA-Script wird jeweils unverändert in einem frischen,
  temporären Git-Checkout ausgeführt. Ein lokales `curl`-Canary beendet den
  Lauf beim ersten Downloadversuch. Es finden keine Netzwerkzugriffe und
  keine Gradle- oder Signiervorgänge statt. Dies ist eine Preflight-Abnahme,
  keine vollständige Render-/Android-Abnahme.

Originalausgaben, Befehle, Quell-/Beleg-Hashes und Werkzeugversionen:
[`build-preflight/receipt.json`](../../evidence/three-repair-bots-20261104/build-preflight/receipt.json),
[`red.tap`](../../evidence/three-repair-bots-20261104/build-preflight/red.tap),
[`green.tap`](../../evidence/three-repair-bots-20261104/build-preflight/green.tap).

## Umfang und Übergabe

Geänderte ausführbare Quellen:

1. `scripts/render-continuum-owner-continuous-qa.sh`
2. `scripts/continuum-owner-continuous-qa-preflight.test.mjs`

Betroffene Verträge: EXP-00 und EXP-09, vorhandene Build-/QA-Rezepte.
Android-first-Architektur, Paketname, Originalsignatur, Freigabe-SHA,
Updatekanal, Besitzerbestätigung und lokale Daten bleiben unverändert.
Keine Modell-/Gewichtsforschung, kein Main-Merge und kein Release.

Der nächste Einstieg ist die unabhängige Prüfung dieser zwei Dateien und
derselbe im Beleg hinterlegte Node-Testbefehl nach der Integration.
Gradle, vollständiger Publisher, signierter Download und Fold7 sind für
diese Reparaturrunde **NOT_RUN**. Ein Rückfall erfolgt durch gezieltes
Revert des Reparaturcommits auf dem Review-Zweig; bestehende Fremdarbeit
wird nicht zurückgesetzt.
