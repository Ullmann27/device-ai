# Emulator-QA, 23.10.2026

Basis: `93cd4c08bc961e07ea0dc2834e5b9b4d38a41195` (PR #81), isolierter Branch `astra/continuum-visual-engineering-20261023`.
Experimentbezug: EXP-00/EXP-07, unabhängige UI-Abnahme. Kompatibel mit bestehender Android-first-Toolchain; keine Änderung an Jobverträgen, Forschungsdaten, Gewichten oder Modellfreigaben. Status der QA-Logik: `MOCK_TESTED`; keine Geräte-/Emulatorabnahme dieser Änderung behauptet.

## Actions-Befund

[Run 39852367733](https://github.com/Ullmann27/device-ai/actions/runs/39852367733), Job `120081625213`, wurde **vor dem ersten Schritt** abgewiesen. Am 23.10.2026 gelesene Check-Annotation: “The job was not started because recent account payments have failed or your spending limit needs to be increased.” Steps waren leer; der Plugin-Logabruf lieferte `404 BlobNotFound`. Die Kontoeinstellungen wurden nicht verändert. Die folgende QA-Härtung behebt eigenständige Quellcodeprobleme und beseitigt diesen externen Startblocker nicht.

## Prüfumfang

- Gradle-Aufgaben verwenden fest `-PdeviceAiFeaturePreview=true`; erwartet wird ausschließlich `at.heinz.deviceai.featurepreview`. Die Activity-Klasse bleibt `at.heinz.deviceai.AgentActivity`.
- Ein verpflichtender Workflow-Schritt prüft vor dem Emulatorlauf Paketname, erfolgreichen `apksigner verify`-Exitcode und vorhandenen SHA-256-Signerfingerabdruck. Ein falsch adressiertes, ungültig signiertes oder unvollständig belegtes APK stoppt den Lauf. Es wird kein Besitzerzertifikat behauptet oder ersetzt.
- Das Skript verweigert physische adb-Ziele und prüft vor Installation das APK-Paket mit Android `aapt`.
- Phone, Fold-Cover, Fold-Innen und Fold-Innen quer erhalten jeweils 100-%- und 130-%-Samples: acht stillstehende Hauptansichten plus eine Animationsaufnahme.
- Phone und Fold-Innen prüfen bei beiden Schriftgrößen Start, Chat, Lernen, Entwickeln und Mehr: zwanzig gezielte Navigationen. Die Startprüfung verlässt zuerst den Startbereich. Chat verlangt einen fokussierten Editor, Zentren ihre echte Überschrift und jedes Ziel den Fokus im Preview-Paket. Keine Tools, Downloads, Trainings- oder Forschungsaktionen werden angetippt.
- Ungültige Dumps, fehlende/mehrdeutige Ziele, adb-Fehler und fehlende Zielansichten brechen ab. Hierarchien werden vor jedem Dump neu geschrieben. Screenshots allein zählen nicht als Navigationsnachweis.
- Der finale Actions-Gate verlangt erfolgreiche QA-, Node-, JVM-, Lint-, APK-, Paket-/Signatur- und Emulator-Schritte. Diagnostisches `continue-on-error` kann keinen grünen Abschluss erzeugen. Ein erfolgloser Evidence-Push meldet ebenfalls Fehler. Die Veröffentlichung verwendet ausschließlich `astra/continuum-visual-evidence-20261023`.

## Reproduzierbare lokale Checks

```sh
python3 -m unittest discover -s scripts -p 'test_continuum_emulator_screenshots.py' -v
/workspace/toolchain/node22/bin/node --test .github/continuum/learning-wave-animation.test.mjs .github/continuum/aaa-cockpit-and-effects.test.mjs .github/continuum/android-holographic-home.test.mjs
python3 -m py_compile scripts/continuum-emulator-screenshots.py scripts/test_continuum_emulator_screenshots.py
git diff --check
```

13 Python-Checks (einschließlich 21 negativer tatsächlicher Bash-Gate-Läufe und vier negativer Paket-/Signaturfälle mit simulierten SDK-Werkzeugen) und 17 vorhandene Node-UI-Checks bestanden. YAML wurde separat geparst; der Pflichtschritt liegt vor dem Emulatorlauf. Alle fünf Navigationsbeschreibungen, die Fold-Rail-Kurzlabels, die drei Zielüberschriften sowie die Home-Beschreibung wurden nochmals mit den aktuellen Kotlin-Quellen abgeglichen. Es wurde hier kein Gradle-Build gestartet und keine APK installiert.

Nächster Schritt: Workflow nach Behebung des externen Actions-Startblockers auf einem freigegebenen Weg ausführen; die acht Screenshots gegen Referenzen ansehen, `report.json` und zwanzig Zielnachweise prüfen. Displayüberschreibungen simulieren keine echte Scharniergeometrie, Samsung-Systemleisten oder Hardwareleistung. Ziele unter 48 dp werden berichtet, sind derzeit kein eigener Abbruch-Gate. Bildähnlichkeit, Animationstreue und physische Samsung-Abnahme bleiben getrennte Prüfungen.

Rollback: ausschließlich Änderungen an `.github/workflows/continuum-ui-screenshots.yml`, `scripts/continuum-emulator-screenshots.py`, dem neuen Python-Test und diesem Protokoll zurücknehmen. Keine Produktdatenmigration erforderlich.
