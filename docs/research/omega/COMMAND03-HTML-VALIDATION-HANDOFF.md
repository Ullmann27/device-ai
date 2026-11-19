# Kommando 03: Original-HTML in der Android-App — verifizierter Reparaturstand

## Ergebnis und verbindliche Quelle

**Geprüfte Produktions- und Testquelle:**
[`f79d57546516b92788bd83c6039ce474955da8ea`](https://github.com/Ullmann27/device-ai/commit/f79d57546516b92788bd83c6039ce474955da8ea).
Tree: `0a3f4ecc2e42052d741a83340ce2019aaeb89bae`.

Der reguläre Android-Compile, 66 gezielte Gradle-Tests, 16 DOM-Verhaltenstests
und der reguläre unsignierte Release-Bau sind auf dieser Quelle erfolgreich.
Alle Gradle-Läufe bestätigen eine saubere und während des Laufs unveränderte
Quelle. Die nachfolgende Evidenzablage ändert ausschließlich Dokumentation.

Ausgangsbasis war `6987b3399dd3589805374f2d7d1d4965587e546c` aus PR #851.
Root führt die Reparaturen aus PR #852 auf den einzigen Zielzweig
`integrate/continuum-command03-android-20261115` zurück. Genau drei Agenten
bearbeiteten Kotlin/Activity, HTML-Adapter und unabhängige Buildprüfung.
Der veröffentlichte Code59 und `main` sind nicht das Integrationsziel.

## Tatsächlich repariertes Verhalten

1. **PDF und Chat:** Eine geöffnete PDF-Projektion bleibt bei wiederholten Status-,
   Messwert- und Chataktualisierungen sichtbar. Die beiden Ansichten erhalten
   eindeutige Sichtbarkeitszustände.
2. **Eingabe:** Das vorhandene Eingabefeld verwendet im Android-Adapter ein
   mehrzeiliges Textfeld am selben Platz. Zeilenumbrüche, Entwürfe und Auswahl
   erreichen den bestehenden nativen Composer. Die zusätzliche alte
   2.000-Zeichen-Grenze entfällt; die vorhandene Grenze von 11.500 Zeichen und
   UTF-8-Bytes bleibt. Abgelehnte oder verspätete Bestätigungen löschen keinen
   neueren Entwurf. Sitzungs- und Anfrage-IDs verhindern doppelte Ausführung.
3. **Asynchrone Übergabe:** Der bestehende native Bot-Lookup darf seinen
   Originaltext bis zur tatsächlichen Übernahme lesen. Zwischenzeitliche neue
   Texte bleiben als Entwurf erhalten. Ein Lookup-Start zählt noch nicht als
   angenommenes Kommando.
4. **Bestehende Steuerbefehle:** Während laufender Arbeit erreichen exakt
   erkannte Besitzerunterbrechungen, Chief-Status, Routine-Liste/Stop und
   Antworten auf ein bereits offenes Rechercheticket den vorhandenen nativen
   Handler. Normale neue Aufträge bleiben abgewiesen und lösen keinen
   versehentlichen Abbruch aus. Freigaben bleiben beim bestehenden ToolGate.
5. **Lange Antworten:** Gespeicherte und neue Nachrichten werden bis zur
   bestehenden Antwortgrenze übertragen; größere Transkripte in begrenzten
   Paketen. Die frühere 6.000-Zeichen-Kappung entfällt. Verspätete Historienleser
   ersetzen keine neueren Live-Nachrichten.
6. **Tatsächliche Zustände:** Die vorhandenen Instrumente erhalten gemessene
   Android-CPU-/RAM-Werte mit Messzeit und Lücken bei unbekannten Werten.
   Beispielaktionen werden im Android-Betrieb abgefangen. Die vorhandene Szene
   erhält die Zustände Arbeiten, Zuhören und Sprechen.
7. **Lebensdauer und Rückweg:** Der verdeckte native Renderer bleibt bei Resume,
   Gesprächsänderung und Reflow angehalten. Breitenwechsel hängen dieselbe
   WebView an den neuen Container; offene Seite, Dokument und Request-Belege
   werden dabei nicht zerstört. Der ausdrückliche Klassikwechsel und
   Activity-Ende schließen sie weiterhin. Der Entwurf bleibt beim Rückweg erhalten.

Die 35 Originaldesign-Dateien aus PR #850 bleiben bytegleich. Der Android-
Einstieg bleibt die Originalseite plus zwei lokale Includes. Die Anpassungen
verwenden die bestehende Activity, denselben Sendeknopf, Gesprächsspeicher,
ToolGate, Orchestrator und WorkspaceMotion-Controller. EXP-07 und EXP-11 bleiben
kompatibel. Ein Hostbuild belegt keinen EXP-00-On-device-Build.

## Frische Prüfergebnisse

| Prüfung | Tatsächliches Ergebnis | Dauer |
|---|---:|---:|
| Reguläres `compileDebugKotlin` | PASS, keine Compilerfehler | 37,142 s |
| HTML-Activity und WebView-Transport | 14/14, 0 Fehler/Skips | 34,320 s |
| Eingabevertrag, Instrumente, Gesprächsverlauf | 20/20, 0 Fehler/Skips | 19,982 s |
| Bestehende klassische UI-Regressionsauswahl | 32/32, 0 Fehler/Skips | 37,573 s |
| DOM-Verhalten im Original-HTML mit Adapter | 16/16, 0 Fehler/Skips | 2,220 s |
| JavaScript-Syntax und Originalasset-Vertrag | PASS, 35 Dateien / 15.864.219 Bytes | — |
| Reguläres `assembleRelease` | PASS, keine Compilerfehler | 80,310 s |

Die 14 neuen Android-Fälle prüfen unter anderem den tatsächlichen
WebView-Client → Activity → Sendeknopf-Pfad, Geräteuhrantwort und Replay,
Busy-Ablehnung, unveränderte laufende Arbeit bei Statusabfragen, Besitzerstopp,
das konkrete Rechercheticket, verzögerte Botzuordnung, Entwurfsrückweg und
wirklichen Root-Wechsel bei 392 → 904 → 392 dp mit derselben fertigen WebView.

Drei bestehende klassische UI-Fixtures wählen ihren beabsichtigten nativen
Modus ausdrücklich. Ihre Assertions bleiben erhalten. Das HTML-Fixture wählt
weiterhin ausdrücklich die HTML-Hauptbühne. Insgesamt liegen zwölf frische,
vor nachfolgenden Läufen separat gesicherte JUnit-XML-Dateien für 66 Gradle-
Tests vor. Die 16 DOM-Tests sind zusätzliche Prüfungen, keine Geräteframes.

**Persistente Nachweise:**
[Verifikationsbericht](command03-html-qa-f79d5754/verification.json),
[zwölf JUnit-Dateien](command03-html-qa-f79d5754/junit/) und
[originale Web-Testausgabe](command03-html-qa-f79d5754/web-tests.txt).
Der Bericht enthält die unveränderten erfolgreichen Gradle-Receipts,
Quell-/Testdateibindung, Original- und neue Artefaktzuordnung, Prüfsummen und
die unabhängige APK-Prüfung. Die Befehle und gezielten Auswahlen bleiben in
`tools/command03-native-qa` reproduzierbar.

## Tatsächlich gebautes Artefakt

| Merkmal | Wert |
|---|---|
| Paket | `at.heinz.deviceai.continuumlocal` |
| Version | Code 60 / 0.3.59 |
| Architektur | ausschließlich `arm64-v8a` |
| Android | minSdk 26 / targetSdk 36 |
| Größe | 111.401.278 Bytes |
| SHA256 | `803026bf44efdb93974a03c92f68bb7efeddeb351305d0b297a7221f6af2ad8c` |
| Status | `UNSIGNED_NOT_INSTALLABLE` |

Root prüfte das neue Artefakt zusätzlich unabhängig: ZIP-CRC, eindeutige sichere
Archivpfade, alle 38 HTML-/Adapterdateien bytegleich zur geprüften Quelle,
Paket/Version/SDK, nicht debuggable, fünf ARM64-Bibliotheken mit insgesamt
16 ELF-LOAD-Segmenten mit mindestens 16-KiB-Ausrichtung, `zipalign -P 16`
und die erwartete fehlende Signatur. Die Originalavatar-Prüfsumme besteht.

Die neue APK liegt getrennt unter dem eindeutigen Laufnamen
`html-original-path-unsigned-01`. Der historische Release-Ausgabeordner wurde
nach dem Bau exakt wiederhergestellt. Deshalb zeigt der ursprüngliche lokale
APK-Pfad im unveränderten Raw-Receipt anschließend wieder auf die alte Datei;
das beigefügte Wrapper-Receipt bindet den neuen Archivpfad an denselben neuen
APK-Hash (`archive_matches_raw_receipt=true`). Für den HTML-Stand ist nur der
oben genannte neue Hash maßgeblich. Die alte Code60-APK aus `533eb104` belegt
den HTML-Stand nicht.

## Buildproblem und dokumentierte Fehlversuche

Die ersten drei tatsächlichen Hauptkompilierungen in einem neu angelegten
Buildpfad verloren ihren Gradle-Daemon ohne Compilerdiagnose und ohne Testlauf:

| Lauf | Quelle | Dauer | Ergebnis |
|---|---|---:|---|
| `html-original-compile-01` | `6987b339` | 50,825 s | Daemonverlust, 0 Tests |
| `html-bridge-red-01` | `ff7e8833` | 133,081 s | Daemonverlust, 0 Tests |
| `html-bridge-fixed-small-01` | `12c38b75` | 121,133 s | Daemonverlust, 0 Tests |

Ein kopierter Compiler-Cache wurde von Gradle wegen seiner absoluten alten
Ausgabeorte ordnungsgemäß verworfen. Die normale Wiederverwendung des
ursprünglichen Quell- und Buildpfads erhielt den tatsächlichen inkrementellen
Verlauf. `AgentActivity` wurde neu kompiliert; OriginalStage und BridgeContract
wurden neu erzeugt. Alle erfolgreichen Läufe nutzten 768 MiB Heap / 256 MiB
Metaspace und getrennte JVM-Prozesse. Die Release-Metaspace-Warnung führte
zu keinem Fehler. Eine OOM-Ursache der früheren Prozessverluste ist nicht belegt.

Der erste klassische UI-Aufruf ließ die beiden bestehenden Helper
`CockpitHostFramePump` und `JarvisHomeTestRoutes` aus seiner fokussierten
Auswahl aus und führte null Tests aus. Die vorhandene Auswahl
`--selection command03-ui` enthält beide echten Dateien; mit ihr bestand
Lauf 02 vollständig. Dafür wurde kein Produktionscode und keine Assertion
verändert. Der vorausgehende DOM-RED-Lauf hatte sechs konkrete Fehler
reproduziert; die abgebrochenen Android-Läufe sind kein funktionaler RED-Nachweis.

## Status und nächster Einstieg

- **PORTIERT:** genannte HTML-/Android-Reparaturen, expliziter Klassikrückweg.
- **VERIFIZIERT:** regulärer Compile, 66 Gradle-Tests, 16 DOM-Tests, unveränderte
  Originalassets und unabhängige Prüfung der tatsächlich gebauten unsigned APK.
- **GERÄTEBESTÄTIGT:** `NOT_RUN`.

Offen bleiben echte Android-WebView-/GPU-Darstellung, IME-Verhalten und
Faltbewegung auf Hardware, visueller Vergleich zur Originalvorlage sowie
GPU-/Thermik-/Sprachsynchronisationsprüfung. Die darüber hinaus in PR #851
verzeichneten Computer-Liveframes im HTML-Arbeitsfenster und die native
Viseme-Anbindung sind durch diese Reparatur nicht erledigt. Vor einer neuen
Originalsignierung bleiben die vollständige Release-QA und die erneute
Design-/Quellfreigabe erforderlich.

Beim Wiedereinstieg zuerst den tatsächlichen Integrations-HEAD und diesen
geprüften Quell-Commit abgleichen. Nachfolgende reine Nachweisdokumentation
ändert die getestete Produktion nicht. Die bestehende bestätigte Rückkehr zur
klassischen Oberfläche bleibt verfügbar; einzelne Reparaturcommits lassen sich
auf dem Entwicklungszweig gezielt zurücknehmen. Keine Main-Integration,
Signierung, Veröffentlichung einer Besitzer-APK oder Installation wurde ausgeführt.
