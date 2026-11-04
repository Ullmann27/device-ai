# Bot08 – Update-Kanalprüfung

**Ergebnis: BESTANDEN.** Die beiden beauftragten bestehenden Node-Testdateien liefen einmal gemeinsam: 18 Tests bestanden (14 Update-Gate, 4 Continuous-Release), Exitcode 0, keine übersprungenen Tests. Laufzeit: 767,936 ms; Node v24.22.1.

**Quellstand:** `b368a8c569cba0c07c878c012766fb8fbaa2e346`, Branch `support/continuum-ten-bot-assist-20261104`. HEAD und die SHA-256-Dateihashes der zehn geprüften Quelldateien waren vor und nach dem Lauf identisch.

## Nachgewiesene Anforderungen

- Das lokale Gate verweigert abweichenden HEAD, fremde Herkunft, falsches Originalpaket, Produktionsbranches, fehlende Owner-SHA-Freigabe, fehlendes Signiermaterial, falschen Zertifikat-Pin und Mentor-Zugangsdaten. Synthetische Regressionen müssen vor dem simulierten Build bestehen.
- Der ausgeführte Manifestgenerator erzeugt für versionCode 39, 40 und 41 katalogkonforme Daten mit Originalpaket `at.heinz.deviceai.continuumlocal`, Originalsignatur-Pin, Quellcommit und hashgebundener APK-Adresse.
- Die direkt gelesene Publisher-Implementierung verlangt einen versionCode strikt über dem live gelesenen Stand. Die direkt gelesene Android-Policy verweigert gleiche oder kleinere Codes gegenüber der installierten Version sowie falsches Paket, installierten Signatur-Pin, Release-Signatur-Pin und fehlende Freigabe. Diese Vergleiche wurden hier durch Quellprüfung bestätigt; ihr vollständiger Lauf wurde nicht ausgeführt.

## Aussagegrenze

Das Gate selbst prüft lediglich den Bereich 27–9999 und vergleicht keinen Live-/Installationsstand. Der Manifesttest führt JavaScript-Verträge und Kotlin-Quelltextprüfungen aus; sein Titel belegt keinen ausgeführten Android-Parser. Temporäre lokale Git-Fixtures, Dummy-Zertifikate und Build-/Regressions-Stubs ersetzen weder kryptografische APK-Prüfung noch vollständige Publisher- oder reale Fold7-Abnahme. Es erfolgten keine Remoteanfragen, echte Signierung, APK-Erstellung, Installation, Veröffentlichung oder Update-Kanaländerung. Kein roter Test; keine Produktionsänderung.

**Belege:** [Rohprotokoll](bot08-update-tests.log), [Quellstände und Dateihashes](bot08-source-hashes.json).
