# Kommando 03: Fortsetzung der HTML-/Android-Anbindung

## Aktueller Prüfblocker und Fold-Korrektur

Auf Reparaturquelle `12c38b751ac1a2de1450f5df7c18f25445b603d3`
bestanden erneut 16/16 DOM-Tests ohne Skips, JavaScript-Syntax und alle 35
Originalasset-Pins. Der dritte echte Gradle-Lauf
`html-bridge-fixed-small-01` brach nach 121,133 Sekunden während
`compileDebugKotlin` mit verlorenem Daemon ab: keine Compilerdiagnosen,
null ausgeführte Android-Tests. Das ist kein Nachweis eines Quellfehlers
und keine bestandene Android-Prüfung. Eine OOM-Ursache ist nicht belegt.

Der vorhandene Compiler-Cache wurde korrekt in ein isoliertes Verzeichnis
kopiert und per SHA-256 geprüft. Gradle verwarf ihn aufgrund der im Verlauf
gespeicherten absoluten Ausgabeorte und verlangte einen vollständigen Compile.
Der nächste begründete Versuch verwendet deshalb den ursprünglichen
Build-Ausgabeort mit normalem Gradle, gesicherten historischen Nachweisen und
frischer, unveränderter Quelle. Keine alten Testergebnisse werden als neue
Nachweise übernommen; keine Compiler- oder Quellprüfung wird umgangen.

Ein zusätzlicher Quellbefund betrifft echte Breitenwechsel: Die Activity bleibt
bestehen, baut aber ihre nativen Container neu. Das bisherige bedingungslose
Schließen der HTML-Stage zerstört dabei die offene PDF und den DOM-Zustand.
Die begrenzte Korrektur hängt dieselbe WebView an den neuen Container und
schließt sie weiterhin beim ausdrücklichen Klassikwechsel oder Activity-Ende.
Gezielte Activity-Regressionen prüfen die Lebensdauer über 392/904/392 dp,
den tatsächlichen Uhrbefehl, Busy-Ablehnung und den asynchronen lokalen
Botwechsel mit einem währenddessen bearbeiteten Entwurf. Diese neuen
Android-Regressionen sind bis zu einem erfolgreichen echten Lauf `NOT_RUN`.

Die Gegenprüfung fand außerdem eine pauschale Busy-Sperre vor dem vorhandenen
nativen Sendeknopf. Sie blockierte auch bereits erlaubte Status-/Routinenbefehle,
Besitzerkorrekturen und Antworten auf eine konkrete Recherchefreigabe. Die
enge Korrektur lässt ausschließlich diese bestehenden Steuerpfade anhand der
vorhandenen Parser zum nativen Handler durch. Normale neue Aufträge bleiben
während laufender Arbeit abgelehnt; eine noch ausstehende HTML-Übergabe oder
ein Gesprächswechsel bleiben ebenfalls gesperrt. Die ergänzten Regressionen
verwenden echte Activity-Routen und eine rein lokale Freigabe ohne Netzaufruf.
Die gezielte HTML-Auswahl enthält jetzt zehn Activity- und vier WebView-Fälle.
Drei bestehende klassische UI-Fixtures wählen ihren beabsichtigten nativen
Modus ausdrücklich; ihre Assertions bleiben unverändert. Das neue HTML-Fixture
wählt weiterhin ausdrücklich die Original-HTML-Hauptbühne.

## Erster Reparaturstand

Die getrennten Android-Adapter erhalten nun die PDF-Projektion bei laufenden
Statusmeldungen, benutzen ausschließlich echte Messungen in den vorhandenen
Diagrammen und blockieren Beispielaktionen im Android-Betrieb. Die vorhandene
Szene erhält die wirklichen Zustände Arbeiten, Zuhören und Sprechen. Die 35
Originaldateien bleiben bytegleich.

Die Eingabe verwendet denselben Platz als mehrzeiliges Feld. Der bestehende
native Composer übernimmt Entwürfe und Auswahlpositionen. Befehlsbestätigungen
sind an Sitzung und Anfrage gebunden; Ablehnung oder ein verspätetes Ergebnis
löschen keinen neuen Entwurf. Die bestehende 11.500-Zeichen-/UTF-8-Byte-Grenze
bleibt erhalten, statt der zusätzlichen 2.000-Zeichen-Grenze der alten Bridge.
Lange Antworten werden bis zur vorhandenen Speichergrenze übertragen, bei
Transkripten in begrenzten Paketen statt mit der bisherigen 6.000-Zeichen-Kappung.
Verdeckte native Darstellung bleibt bei Resume und Layoutwechsel angehalten;
die Original-HTML-Darstellung bleibt der aktive Vordergrund.

Vor diesem Checkpoint frisch ausgeführt: 16 DOM-Verhaltenstests bestanden,
JavaScript-Syntax und Originalasset-Vertrag bestanden. Sechs isolierte Kotlin-
Vertragstests bestanden; OriginalStage und Vertrag wurden gegen das echte
Android-36-SDK kompiliert. Das ist noch kein erfolgreicher kompletter
AgentActivity-/Gradle- oder Geräte-Lauf. Zwei tatsächliche Gradleversuche auf
der unveränderten Ausgangsproduktion verloren ihren Daemon ohne
Compilerdiagnosen; der zweite lief 133,081 Sekunden und führte null Tests aus.
Sie gelten ausdrücklich nicht als funktionaler RED-Nachweis. Der DOM-RED-Lauf
reproduzierte sechs Fehler vor der Reparatur.

Nächste Prüfung: beide tatsächlichen Activity-/WebView-UI-Klassen auf einem
sauberen, eingefrorenen Quellstand; danach die relevanten bestehenden
Regressionen und der unsignierte lokale APK-Prüfbau. Der normale Gradle-
Compiler bleibt aktiv. Vorhandene geprüfte inkrementelle Compilerartefakte
dürfen als Buildcache dienen, niemals als Ersatz für frische Testergebnisse.

## Verbindliche Quelle und Integrationsziel

- Ausgangspunkt: `6987b3399dd3589805374f2d7d1d4965587e546c`, PR #851.
- Arbeitszweig: `fix/command03-html-native-validation-20261115`.
- Einziger Integrationszielzweig: `integrate/continuum-command03-android-20261115`.
- Koordination und Zusammenführung: Root; drei begrenzte Agenten für Kotlin/Activity,
  HTML-Adapter und unabhängige Android-Buildprüfung. Keine weiteren Agenten.
- Der erste Checkpoint `ff7e88338df747c0b4ea1c9eae8002ddd2fe3188`
  enthielt ausschließlich Wiederaufnahme und Tests auf unveränderter Produktion.

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

Aktuell `PORTIERT`: vorhandene HTML-Hauptbühne aus PR #851 und die oben
beschriebenen Adapterreparaturen. `VERIFIZIERT`: genannte DOM-, isolierte
Kotlin- und Assetprüfungen mit ihren Grenzen. `GERÄTEBESTÄTIGT`: NOT_RUN.
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
