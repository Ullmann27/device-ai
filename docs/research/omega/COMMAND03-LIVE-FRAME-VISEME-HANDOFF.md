# Command03: echte Computerbilder und native Sprachbewegung

## Ergebnis und geprüfter Quellstand

Stand: **16.11.2026**. Der vorhandene Android-Computerbildstrom und die bestehende native Sprachpose sind an das freigegebene Original-HTML angeschlossen. Auf dem folgenden unveränderten Quellstand bestehen **153 frische lokale Tests, ohne Fehler oder Skips**. Der reguläre unsigned Release-Bau und die unabhängige APK-Prüfung bestehen ebenfalls. Die Gerätebestätigung bleibt offen.

- Geprüfter Commit: `b56a614b290977684b50910fac3cc8d14e4294c0`.
- Produktionsunterbaum `app/src/main`: `8ead90327b0a99898ac75632b8f83621d4f77d0a`.
- Originalvertrag: **35 Assets / 15.864.219 Bytes bytegleich**. Der Android-Einstieg ergänzt weiterhin genau die beiden lokalen Adapter-Includes. Originalgestaltung, Avatar und Renderer bleiben erhalten.

## Tatsächliche Anbindung

### Computerbild im ursprünglichen Arbeitsfenster

`OwnerComputerSessionController.setFeed` liefert denselben bereits zugelassenen `AndroidLiveComputer.feed` an `Command03ComputerProjection`. Der Adapter übernimmt dessen echte Frames; Quellenprüfung, Schutz gegen eigene gespiegelte Fenster und geschützte Inhalte sowie die vorhandene Aktionsfreigabe bleiben beim bestehenden nativen Computerpfad.

Der Transport ist auf JPEGs mit höchstens **640 Pixeln Kantenlänge**, **131.072 Bytes** und mindestens **200 ms Abstand** begrenzt. Das ist eine Obergrenze von fünf Bildern pro Sekunde, keine gemessene Bildrate. Gleichzeitig bleiben höchstens ein Encoderauftrag und ein noch unbestätigtes Bild ausstehend. Das Bildalter berücksichtigt die Kodierung und das Warten auf die lokale Seite; die Frist beträgt höchstens 1.500 ms.

Der HTML-Adapter zeigt die Pixel im vorhandenen projizierten Arbeitsfenster. Zum Öffnen verwendet er dessen ursprüngliche 950-ms-Bewegung; laufende Bildwechsel starten diese Bewegung nicht erneut. PDF und Chat verwenden weiterhin ihre vorhandenen Bereiche und Zustände.

### Sichtbarkeit und Frische vor einer Aktionsfreigabe

Jedes Paket ist an Gespräch, Generation, Sequenz und native Quelle gebunden. Veraltete oder verspätete Antworten, unzulässige Größen und ungültige Nutzdaten werden verworfen. Abgelaufene beziehungsweise nicht mehr aktive Bilder werden entfernt.

Die Bestätigung erfolgt in mehreren Schritten: Das HTML prüft das fertig decodierte aktuelle Bild, dessen Sichtbarkeit einschließlich Vorfahren, Viewport und Überdeckung sowie die abgelaufene Öffnungsbewegung. Erst danach sendet es die genaue Bildkennung. Android verlangt anschließend `postVisualStateCallback` **und ein darauf folgendes sichtbares, hardwarebeschleunigtes `onDraw`**. Erst nach erneuter nativer Prüfung von Quelle und Alter darf `LiveMonitorSubmission` das Bild als dargestellt übernehmen. Senden, Decodieren oder der Visual-State-Callback allein erzeugen keinen gültigen Darstellungsbeleg.

PDF-Überdeckung, Schließen, Stopp, verborgene Fenster, Gesprächswechsel und Lifecycle-Wechsel entwerten die betroffenen Belege. Wird beim Verdecken gerade ein Ersatzbild geladen, werden das sichtbare und das ausstehende Bild berücksichtigt. Späte Decoder- und Draw-Callbacks können die geschlossene Darstellung nicht wieder öffnen. Sichtbarer, bereits erlaubter Computerbetrieb im geteilten Bildschirm bleibt beim Fokuswechsel zur Ziel-App erhalten.

### Sprachpose am Originalavatar und Rückweg zur Klassik

Der bestehende `AvatarDirector` und `AvatarPoseReducer` liefern die aktuelle Sprachpose an die öffentlichen Original-APIs `ContinuumAvatar.setSpeechFrame` und `clearSpeech`. Die 15 vorhandenen Mundformen werden direkt verwendet. Die Pose stammt aus dem laufenden nativen Sprachablauf; die Viseme beruhen weiterhin auf der bestehenden `GermanVisemeApproximation`.

Die Herkunft der Ansteuerung bleibt ausdrücklich unterscheidbar: `PCM_ENVELOPE`, `WORD_RANGE_FALLBACK`, `TEXT_PACE_FALLBACK` oder unbekannt. Damit wird eine Wortbereichs- oder Texttakt-Näherung nicht als gemessene PCM-Energie ausgewiesen. Generation, Sequenz, aktuelle Äußerung, gültige Gewichte und kurze Gültigkeitsfristen schützen vor verspäteten Mundbewegungen. Stopp und Lifecycle-Ende setzen die Sprachpose zurück; eine Demo-Sprechanimation ersetzt die nativen Daten nicht.

Der Rückweg zur klassischen Oberfläche bleibt erhalten. Dort bekommt der `AvatarDirector` wieder den ursprünglichen direkten Sink. Nur der HTML-Modus ergänzt die Sprachweiterleitung; ein späterer Wechsel zurück zum HTML bindet diese neu an. Die initiale Director-Erzeugung fragt keinen noch nicht angebundenen Activity-Context ab.

## Frische Verifikation auf `b56a614b`

Die folgenden Läufe melden unveränderte Quellen vor und nach der Ausführung. Frühere oder unvollständige Läufe werden nicht mitgezählt. Die versionierten Belege liegen unter [`command03-live-qa-b56a614b/`](command03-live-qa-b56a614b/README.md). Die folgenden relativen Nachweispfade beziehen sich auf dieses Verzeichnis; ursprüngliche Laufpfade bleiben in den JSON-Belegen erhalten.

| Prüfgruppe | Ergebnis | Fachliche Abdeckung | Nachweis unter der Nachweiswurzel |
|---|---:|---|---|
| Android-Medien-, Bridge- und Stage-UI | **22/22 PASS** | Native Anbindung, tatsächlicher JSON-Vertrag, Sitzungs- und Lifecycle-Verhalten, Sichtbarkeits- und Draw-Grenzen | `receipts/live-media-ui-07.json` |
| Fokussierte Unit-Tests | **71/71 PASS** | Computerprojektion, Sprachprojektion, Bridge-Vertrag, bestehende Frame-/Dispatch-/Submission-Guards, Sprachzulassung und Reihenfolge | `receipts/live-media-unit-02.json` |
| Klassische UI-Regressionen | **32/32 PASS** | Navigation, Avatarbindung, Geometrie, Entwurfserhalt, Tastaturänderungen, Beschriftung und Trefferflächen | `receipts/live-media-classic-ui-02.json` |
| DOM und verpflichtende native Interoperabilität | **28/28 PASS** | 27 DOM-Fälle plus ein tatsächlich aus der frischen Kotlin-Stage serialisiertes Paket | `web/verification.json` |
| **Gesamt** | **153/153 PASS** | Lokale Fokusprüfungen; keine Geräteprüfung | Keine Fehler, keine Skips |

Die 27 regulären DOM-Fälle decken Eingabe/Entwurf/Sendebestätigung, PDF/Verlauf/Bedienelemente, echte Telemetrie- und Szenenzustände, Computerbild-Lifecycle sowie Sprachpose ab. Ein Fall lädt die unveränderte `avatar3d.js` und prüft deren wirkliche öffentliche API im PNG-Fallback. Der 28. Fall verwendet das aus `ContinuumCommand03MediaStageUiTest` exportierte synthetische Bildpaket der echten Kotlin-Serialisierung. `COMMAND03_REQUIRE_NATIVE_PACKET=1` macht ein fehlendes Paket zum Fehler. Der dabei geprüfte `captureSession`-Wert ist ausdrücklich eine Zeichenkette, damit die native Long-Kennung unverändert transportiert wird.

Der separate Originalasset-Abgleich und die JavaScript-Syntaxprüfung bestehen ebenfalls. Sie sind nicht in die 153 Tests eingerechnet.

### Kotlin-Kompilierung und Ressourcen

`live-media-compile-06` besteht als regulärer tatsächlicher **inkrementeller** Gradle-Lauf von `:app:compileDebugKotlin` auf genau diesem Quellstand. Zuvor wurde der kohärente, vollständig gehashte Compilerzustand des ursprünglichen 533-Standes wiederhergestellt. Gradle bewertete die aktuellen Eingaben neu und baute sieben Produktklassen neu. Der Nachweis besteht damit aus tatsächlicher Kompilierung und frischen Tests; ein erfolgreicher Cold-Build ist nicht belegt.

Nachweise: `receipts/live-media-compile-06.json`, `provenance/compiler-state-recovery-533.json` und `provenance/recovery-provenance.json`.

Die 32 klassischen UI-Fälle liefen in **sieben sequenziellen Testworkern mit jeweils unverändertem 1.024-MiB-Heap**. Das ausdrückliche Runner-Opt-in verwendet `forkEvery=1` und `maxParallelForks=1`. Die ausgewählten klassischen Testquellen und Assertions wurden für diesen Wiederholungslauf nicht verändert oder abgeschwächt; die Hash- und JUnit-Prüfungen bleiben aktiv.

## Erkannte und behobene Befunde

Die zunächst fehlenden HTML-Medieneinstiegspunkte wurden durch neue fehlschlagende DOM-Fälle sichtbar. Der echte Kotlin-/JavaScript-Vertragsabgleich beseitigte anschließend die falsche JSON-Typisierung von `captureSession`. Die bestehende Klassik-Auswahl deckte eine echte Regression des direkten Avatar-Sinks auf; der Produktionsfix begrenzt die zusätzliche Weiterleitung auf den HTML-Modus.

Die neuen Stage-Prüfungen legten außerdem unzureichende Fenster-/Layout-Vorbedingungen des Robolectric-Hosts offen. Die gezielten Testfixtures stellen den echten Android-Layoutpfad und den passenden NativeGraphics-Hostmodus her; positive Sichtbarkeits- und Draw-Assertions bleiben erhalten. Frühere Daemon-Abbrüche und ein Worker-Exit 137 lieferten keinen vollständigen Testnachweis. Eine bestimmte OOM-Ursache wurde daraus nicht abgeleitet. Die obigen frischen Läufe ersetzen diese Zwischenstände.

## Aussagegrenzen und Abnahmestatus

| Stufe | Status | Belastbare Aussage |
|---|---|---|
| **PORTIERT** | **Ja** | Vorhandene native Computer- und Sprachproduzenten sind an das Original-HTML angeschlossen; Klassikrückweg erhalten. |
| **VERIFIZIERT** | **Lokal** | Tatsächlicher inkrementeller Kotlin-Compile, 153 frische Fokusprüfungen, native JSON-/JS-Interoperabilität, unveränderte Originalassets sowie regulärer unsigned Release-Bau und unabhängiges APK-Audit. |
| **GERÄTEBESTÄTIGT** | **NOT_RUN** | Android-Hardware, sichtbare WebView-/GPU-Pixel und hörbare Sprachsynchronität wurden in diesem Schritt nicht geprüft. |

Die Android-UI-Prüfungen laufen auf dem Host mit Robolectric. Auch dessen NativeGraphics-Modus belegt keine Darstellung auf einem realen Telefon. Im DOM-Test bleiben Bilddecoder, Layout-/Trefferprüfung und Zeitsteuerung kontrollierte Test-Schnittstellen. Der echte Kotlin-Interop-Nachweis verwendet ein synthetisches JPEG; der Originalavatar-API-Fall verwendet den PNG-Fallback. Daraus folgt kein Nachweis einer tatsächlichen Bildschirmaufnahme, des GLB-/WebGL-Renderings oder phonemgenauer Lippensynchronität.

Für die Gerätebestätigung bleiben reale freigegebene Computerquellen, verdeckte und veraltete Frames, Pause/Wiederaufnahme, PDF-Überdeckung, Phone/Fold/IME, hörbare TTS-Ausgabe und PCM-/Fallback-Verhalten zu prüfen. Bildrate, Latenz, Ressourcenverbrauch und thermisches Verhalten müssen auf Hardware gemessen werden. Die bytegleichen Originalassets belegen die Quelltreue, nicht bereits die abschließende visuelle Gleichheit auf jedem Gerät.

## Release-Artefakt und unabhängige APK-Prüfung

`live-media-unsigned-02` besteht mit regulärem `:app:assembleRelease` auf dem unveränderten Commit `b56a614b290977684b50910fac3cc8d14e4294c0` in **12,704 Sekunden**. Die tatsächliche Release-Kotlin-Kompilierung war bereits im vorherigen Lauf abgeschlossen. Dessen Paketbau scheiterte an Duplikaten in abgeleiteten `CompressAssets`-Zwischendateien. Nach Sicherung der 97 betroffenen Einzel-ZIPs wurden diese regulär neu erzeugt; die Quellen blieben unverändert.

| Merkmal | Geprüfter Wert |
|---|---|
| Artefakt im separaten Prüfpaket | `app-release-unsigned.apk` |
| Dateigröße | **111.421.502 Bytes** |
| SHA-256 | `00def8e94f0f2d26257d25eeb5176f4e7d007bf33013c48a92ee6f8874d57e4c` |
| Paket | `at.heinz.deviceai.continuumlocal` |
| Version | Code **60**, Name **0.3.59**, `arm64-v8a` |
| Build-Ergebnis | **PASS_LOCAL** |
| Unabhängige APK-Prüfung | **PASS_INDEPENDENT_LOCAL_ARTIFACT_REVIEW** |
| Signaturstatus | **UNSIGNED_NOT_INSTALLABLE**, wie für diesen lokalen Bau vorgesehen |

Die unabhängige Prüfung bestätigt die bytegleiche Übernahme aller **38 HTML-Paketassets** einschließlich Android-Einstieg und Adapter, gültige ZIP-CRCs, eindeutige sichere Paketpfade, fünf ARM64-Bibliotheken mit 16-KiB-ELF-Ausrichtung sowie bestandenes 16-KiB-`zipalign`. Der fehlende Signaturblock ist bestätigt; `apksigner verify` liefert für dieses ausdrücklich unsignierte Artefakt erwartungsgemäß Exit 1. Das wird nicht als erfolgreiche Signaturprüfung ausgegeben.

Nachweise: `receipts/live-media-unsigned-02.json` und `apk/independent-apk-review-b56a614b.json`. Diese Struktur- und Quellprüfung ersetzt keine Installation oder Geräteabnahme. Eine Main-Integration, Originalsignierung oder produktive Veröffentlichung ist mit diesem Entwicklungsschritt nicht dokumentiert.

## Fortsetzung und Integrationslinie

Die Entwicklung wird über [`integrate/continuum-command03-android-20261115`](https://github.com/Ullmann27/device-ai/tree/integrate/continuum-command03-android-20261115) und den bestehenden [Draft-PR851](https://github.com/Ullmann27/device-ai/pull/851) fortgeführt. [PR853](https://github.com/Ullmann27/device-ai/pull/853) enthält diesen Medien-Schritt. Die nachfolgende Sicherung der Prüfbelege verändert ausschließlich Dokumentation; der geprüfte gesamte `app`-Baum bleibt `2ca2e8189ca574f2637424cc3c2db59e8ea34d91`.

Der Laufstand `b56a614b` bleibt die genaue Quelle von Tests und APK. Originalsignierung, Installation, Geräteabnahme und eine Main-Integration sind eigenständige spätere Schritte. Die bisherigen HTML-Basisnachweise auf `f79d5754` bleiben in `COMMAND03-HTML-VALIDATION-HANDOFF.md` als historischer Stand erhalten.
