# Kommando 03: Fortsetzung der HTML-/Android-Anbindung

## Verbindliche Quelle und Integrationsziel

- Ausgangspunkt: `6987b3399dd3589805374f2d7d1d4965587e546c`, PR #851.
- Arbeitszweig: `fix/command03-html-native-validation-20261115`.
- Einziger Integrationszielzweig: `integrate/continuum-command03-android-20261115`.
- Koordination und Zusammenführung: Root; drei begrenzte Agenten für Kotlin/Activity,
  HTML-Adapter und unabhängige Android-Buildprüfung. Keine weiteren Agenten.
- Dieser Checkpoint enthält eine Wiederaufnahme, noch keine behauptete Reparatur.

## Reproduzierbarer Ausgangsbefund

Die 35 ursprünglichen HTML-, CSS-, JavaScript-, Font- und Modellassets aus PR #850
bleiben unverändert. `node tools/command03-native-qa/check-visual-source.mjs`
bestand auf der Ausgangsquelle: 35 Assets, 15.864.219 Bytes.

Der erste tatsächliche lokale Android-Kompilierungsversuch auf exakt dieser Quelle
endete nach 50,825 Sekunden mit `gradle_exit=1` und einem verschwundenen
Gradle-Daemon. Der Quellstand blieb sauber und unverändert. Der Lauf beweist weder
einen Kotlin-Quellfehler noch einen erfolgreichen Compile. Eine OOM-Ursache ist
nicht nachgewiesen. Receipt: `html-original-compile-01.json` im lokalen, vom
Quellcheckout getrennten QA-Ausgabeverzeichnis.

## Eng begrenzter Reparaturumfang

1. PDF-Projektion über wiederholte Status-/Chataktualisierungen sichtbar halten.
2. Befehle und Entwürfe zwischen HTML und bestehendem Android-Composer ohne
   vorzeitiges Löschen, stille 2.000-Zeichen-Grenze oder Verlust von Zeilenumbrüchen
   übertragen. Bestehende Owner-Eingabegrenzen und native Freigaben weiterverwenden.
3. Ursprüngliche Beispielszenen in der Android-Oberfläche von echten Aktionen und
   Messwerten trennen; keine simulierten Rechercheergebnisse als Live-Arbeit zeigen.
4. Native Hintergrunddarstellung bei HTML-Präsentation in allen Wiederaufnahme-
   und Fensterwechselpfaden anhalten; echte Messungen an das sichtbare HTML senden.
5. Gezielte DOM-/Android-Regressionen, Kotlin-Compile und einen lokal gebauten
   unsignierten Prüfstand an eine unveränderte Quelle binden.

## Beleg- und Sicherheitsgrenzen

Aktuell `PORTIERT`: vorhandene HTML-Hauptbühne aus PR #851. `VERIFIZIERT`:
ausschließlich der oben genannte Asset-/Quellvertrag. `GERÄTEBESTÄTIGT`: NOT_RUN.
Der alte Code60-Build aus `533eb104` belegt diesen HTML-Stand nicht.
Keine Signierung, Veröffentlichung, Installation oder Main-Integration ist Teil
dieses Checkpoints. Kein zweiter Renderer, ToolGate, Gesprächsspeicher oder
Orchestrator wird eingeführt. EXP-07 und EXP-11 bleiben kompatibel; ein Hostbuild
belegt keinen EXP-00-On-device-Build.

## Nächster Einstieg und Rückweg

Zuerst aktuellen Branch-HEAD und Testbelege prüfen. Kotlin- und Web-Agent stimmen
den gebundenen Eingabe-/Entwurfsrückkanal ab; Build-Agent prüft nur eingefrorene
Quellen. Reparaturen anschließend in dieselbe PR-851-Integrationslinie übernehmen.
Die bestehende bestätigte Rückkehr zur klassischen Ansicht bleibt verfügbar.
Einzelne Reparaturcommits lassen sich auf dem Entwicklungszweig zurücknehmen;
Originalassets und bestehender Code59-Release bleiben identifizierbar.
