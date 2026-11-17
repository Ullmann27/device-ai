# Continuum MASTER: überprüfbarer M4-Entwicklungsmeilenstein

Basis: `b842d471183fcf181f392d522a0d0a8f5936a649`, am 23.10.2026 erneut über GitHub und `git ls-remote` geprüft.
Auftrag: autonome Weiterentwicklung ausschließlich von `Ullmann27/device-ai`; eigene Branch- und Draft-PR-Kette,
kein Main-Merge, keine Besitzer-APK und keine Installation. EXP-00/02/08/09/13.

## Entscheidung und Alternativen

Priorität haben A/B: vollständige Regression und ein echtes separates APK samt unabhängiger Prüfung.
Die bestehende deterministische M4-Pipeline wird erweitert. Ein neuer LLM-Generator hätte ohne Sandbox und
Qualitätsbeleg keinen Vorteil; eine parallele Build-/Reparaturpipeline würde CCC1 umgehen. Beide Alternativen
werden deshalb zurückgestellt. Die konkrete Umsetzung wählt Sol im ausdrücklich erteilten autonomen Auftrag.

1. Vorhandene M4-Tests tatsächlich ausführen, Integrationsfehler reproduzieren und reparieren.
2. Nur erwartete Quellpfade zulassen; zusätzliche Gradle-/Kotlin-Eingaben müssen vor dem Build blockieren.
   Generierte Build-/Cache-Verzeichnisse bleiben getrennt von Quelleneingaben. Kein OS-Sandbox-Versprechen.
3. Android-Termux ist der Standard. Ein ausdrücklich gewähltes Host-SDK-Profil liefert separate Host-Belege;
   es darf nie ARM64 oder einen Gerätelauf vortäuschen. Offline-Build bleibt der normale Buildpfad.
4. Ein versionierter, streng begrenzter Projektplan beschreibt Identität, Screens/Navigation, Daten,
   Berechtigungen, Ressourcen, Tests, Artefakte und Budgets. Zwei harmlose deterministische Rezepte sind das Ziel.
   Unbekannte Felder/Versionen, freie Pfade, Shell oder Modellquellen werden abgelehnt.
5. Buildbelege enthalten tatsächlich ausgeführte Tests, Lint, Paket, Signatur und SHA-256 derselben APK-Bytes.
   Der bestehende CCC1-Verifier erhält einen zusätzlichen Prüfeingang; keine zweite Reparaturpipeline.
6. Persistierte Projektbelege ermöglichen eine lesende Wiederaufnahme nach verlorenem Android-Callback.
   Historische Belege werden beim Lesen erneut gegen Quellen und Artefakt geprüft. Ein alter Build ist kein
   neuer Lauf, ein Host-Build kein Android-Gerätenachweis. Abbrüche/Fehler dürfen keinen Erfolg hinterlassen.

## Abnahme und Grenzen

Die unveränderte Ausgangssuite und alle neuen Negativtests werden protokolliert. SDK-/Gradle-/Lint-Prüfungen
werden soweit verfügbar wirklich ausgeführt. Mindestens eine getrennte APK wird gebaut, durch einen separaten
Verifier erneut geprüft und mit Logs/Hashes gesichert. Besitzergewichte, Signierschlüssel und Premium-Haupt-UI
bleiben außerhalb dieses Meilensteins. Geräteinstallation/Termux-Callback brauchen später den Besitzer.

Dialogforschung S1–S5 und der aktive 30-Sekunden-Übungsmodus sind nachgeordnete Teilprojekte. Sie werden nur
begonnen, wenn A/B gesichert sind; andernfalls verbleiben konkrete Anschlussaufträge im Handoff. Keine bloße
Übungswiederholung wird als Gewichtetraining oder verbessertes Sprachverständnis ausgegeben.
