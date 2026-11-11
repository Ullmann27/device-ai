# Übergabe an Claude: PDF-Arbeitsszene

## Verbindlicher Pausenstand

**Der Besitzer hat am 11.11.2026 ausdrücklich angeordnet aufzuhören.**
Entwicklung und Tests sind beendet. Der laufende Gradle-Prüfprozess wurde mit
Exit 130 gestoppt; alle drei begrenzten Helfer sind fertig. Danach wurden nur
Dokumentation und vorhandene Prüfergebnisse gesichert. Erst auf einen neuen
Besitzerauftrag weiterarbeiten.

Kanonischer Einstieg: **PR #761**, Branch `work/pdf-scene-20261111` im Repository
`Ullmann27/device-ai`. Nicht auf einem alten Helferzweig neu beginnen.

| Identität | Stand |
|---|---|
| Gepushter Code | `a39089fe8e7e2becfefcbccb0dec64f9172f9992` |
| Lokal geprüfter Code | `a8482e2f01b2326130500f187c0f62a451d2b61d` |
| Identischer vollständiger Quellbaum | `f680fde68c31588a156d7447e96478644f00b6ae` |
| Integrationsbasis #747 | `7b1d0f67b4838075303cb2b8fc2c99192d1c7cc3` |
| Zusätzlich integrierte Basis #757 | `0adcfe907baaaab9f53730fcbab67ef61418ebce` |

Der anschließende Speichercommit enthält ausschließlich Übergabe und Evidenz.
Sein SHA ist nicht der getestete Code-SHA. Unterschiedliche lokale und entfernte
Commit-IDs entstanden beim kontrollierten GitHub-Upload; die vollständige
Baumgleichheit wurde jeweils geprüft. Der Remote-Branch wurde ohne Force-Push
und mit erwarteter Vorgänger-SHA aktualisiert und danach zurückgelesen.

## Auftrag und tatsächlich ausgeführte Arbeit

Ziel ist eine erste vollständige PDF-Arbeitsszene: echte Datei auswählen und
lesen, das Dokument links einfliegen lassen, auf dem bestehenden lokalen
Modellpfad einen Entwurf erzeugen, das Ergebnis rechts einfliegen lassen und
Jarvis erst nach der tatsächlichen Ankunft persönlich nächste Schritte anbieten
lassen. Originalavatar und bestehende Filament-/Android-Architektur bleiben.

### 1. Dokumentauftrag und echter vorhandener Leseweg

Der Präsentationszusatz aus #760 wird vom bestehenden Dokument-Intent erkannt.
Negationen und zusätzliche nicht freigegebene Aktionen bleiben ausgeschlossen.
Der bestehende Android-Dateiauswahldialog, `PdfDocumentIntake`, Dokumentjournal
und lokale Entwurfspfad wurden verbunden und gegen überholte Rückmeldungen
abgesichert; es wurde kein zweiter PDF-Decoder oder Modellpfad eingeführt.

Auswahl und Verarbeitung tragen eine feste Auftragsidentität aus Sitzung,
Arbeitsgeneration, Besitzerantwort und Anfrage. Eindeutige Picker-Requestcodes
bleiben über Activity-Neuerstellung erhalten. Eine verspätete Antwort von
Auswahl A darf Auswahl B nicht verbrauchen. Im Hintergrund zurückgegebene URIs
warten auf den Vordergrund; widerrufene Leseaufträge beginnen keinen Zugriff.
Überholte dekodierte Bitmaps werden freigegeben.

### 2. Fensterankunft und persönliche Antwort

Dokumentfenster verwenden `Edge.LEFT`, Ergebnisfenster `Edge.RIGHT`.
`JarvisWorkWindow.doOnPresented` meldet die tatsächliche Ankunft einmalig.
Close, Replacement, Detach, STOP, Hintergrundwechsel und überholte
Besitzerantworten verhindern verspätete Angebote. Der vollständige Entwurf
bleibt verfügbar; die anschließende persönliche Ansprache verwendet den
vorhandenen Sprachpfad und keinen zusätzlichen Modellaufruf.

### 3. 3D-Dokumentfläche in der vorhandenen Grafikarchitektur

Der vorbereitete Baukasten wurde geprüft. Aus dessen GLB entstand ein
reproduzierbares Dokument-Asset mit fünf Meshes, korrekten Normalen und UVs
sowie einer Fläche für das tatsächliche erste PDF-Seitenbild. Generator und
Quellasset sind unter `tools/cinema-document/` gesichert; das Laufzeitasset liegt
unter `app/src/main/assets/cinema3d/document-workspace.glb`.

`FilamentDocumentStage` teilt Engine, Renderer, Loader und Bildtakt mit dem
vorhandenen Avatar-Renderer. `DocumentStageFrame`, `DocumentStageView` und
`DocumentStagePageGeometry` übertragen die tatsächliche native Seitengeometrie
einschließlich Perspektive. Puffer und Texturauflösung sind begrenzt; identische
Frames und Bitmaps erzeugen keine ständigen Neu-Uploads. Verdeckte oder
ungültige Seiten werden nicht weiter gezeichnet. Keine zweite Spiel-Engine,
kein neuer Event-Bus und keine zusätzliche Renderuhr wurden eingebaut.

Ein konkret gefundener Darstellungsfehler wurde korrigiert: Native Glasflächen
und das ImageView hätten die 3D-Seite vollständig verdeckt. Erst ein bestätigter
gültiger 3D-Frame übernimmt diese Pixel; Layout, Text und Bedienbarkeit bleiben
nativ. Bei Ausfall, Clipping, Close oder Replacement erscheint der native
Fallback wieder. Scroll-, Layout- und Draw-Ereignisse aktualisieren auch ein
bereits angekommenes Fenster. Der Originalavatar wurde nicht ausgetauscht.

### 4. Pause, Fehler und Abbruch

Ein tatsächlich beobachteter Fehler beim Wiederöffnen einer bekannten PDF darf
als einmalige UI-Rückmeldung eine normale Pause überstehen. Dabei wird die Datei
nicht nochmals gelesen. Ein vor dem Zugriff abgebrochener Auftrag erzeugt keine
erfundene Dateifehler-Erholung. STOP, eine neue Auswahl, ein neuer Leseauftrag
und zwischenzeitlich gestartete andere Arbeit verwerfen die alte Rückmeldung.
Ein Folgekommentar unterdrückt die ältere Sprachausgabe. Ein vorgeschalteter
Journalcallback prüft den Widerruf, bevor er einen neuen Zugriff starten darf.

Die ursprünglich zwei auffälligen Tests blockierten vor dem Dateizugriff und
erwarteten dennoch einen Dateifehler. Die Fixtures wurden auf einen wirklich
beobachteten Providerfehler mit gezähltem Zugriff umgestellt; die bisherigen
Recovery-Assertions blieben bestehen. Acht zusätzliche Fälle unterscheiden
Abbruch vor Zugriff, echte Fehler und neue beziehungsweise fremde Arbeit.

## Was wirklich geprüft wurde

| Prüfung | Tatsächlicher Stand |
|---|---|
| Produktions-Kotlin und Java | Kompiliert; letzter Produktionscode bereits auf `2a967b3af` erfolgreich kompiliert, danach unverändert |
| Alle Android-Testquellen | Auch auf dem pausierten Stand erfolgreich kompiliert |
| Gezielte Node-Quellprüfungen | **17 PASS**, 0 FAIL, auf `a8482e2f` mit unverändertem Quellbaum |
| Aktueller Android-Core-Lauf | **11 PASS**, 0 beobachtete FAIL, **1 SKIP**, danach Besitzerabbruch; **unvollständig** |
| Strikter echter PDF-Probelauf | **1 FAIL**, 0 PASS, 0 SKIP; konkreter Hostfehler unten |
| Frühere isolierte Kotlin-Routertests | **13 PASS** nach dokumentiertem vorherigem Rotlauf; eigener älterer Quellstand |
| Vollständiger aktueller Grafik-Scope | **NOT_RUN** |
| Vollständige Node-Regression | Früherer Lauf unterbrochen; **kein vollständiger Erfolgsbeleg** |
| Physischer Fold7, Filament-GPU, hörbare Stimme, echte Modellqualität, Wärmemessung | **NOT_RUN** |

Der zuletzt laufende Fall war
`anotherStartedTaskRejectsAnAlreadyObservedPdfFailure`; er besitzt kein
abgeschlossenes Ergebnis und darf nicht als bestanden gezählt werden.
`aNewOwnerMessageKeepsQueuedPdfFailureVisible` wurde im letzten Lauf ebenfalls
noch nicht abgeschlossen. Der zuvor auffällige normale Pausenfall ist dagegen
jetzt tatsächlich bestanden.

Unter den elf abgeschlossenen Erfolgen sind die einmalige persönliche Ansprache
erst nach der Fensterankunft, ihre Unterdrückung nach STOP, die einmalige
Fehlerzustellung nach einer Hintergrundphase, das Verwerfen alter Recovery nach
neuem Picker sowie der verhinderte Dateizugriff eines widerrufenen Journalauftrags.
Sprachbelege sind erfasste Host-TTS-Aufrufe, keine gehörte Gerätestimme.

### Konkrete Host-Grenze beim echten PDF

Der strikte Test öffnete eine synthetische echte PDF über ContentProvider und
ParcelFileDescriptor. Androids `IoUtils.acquireRawFd` ruft beim
`PdfProcessor.create` die auf diesem Host fehlende Methode
`java.io.FileDescriptor.getOwnerId$()` auf. Ergebnis: `NoSuchMethodError`, vor
Seitenrendering und Textextraktion. Der vollständige fehlgeschlagene Lauf auf
lokal `b1b794358` / remote `3554271e` bleibt unverändert erhalten.

Der optionale Host-Skip akzeptiert ausschließlich genau diese Fehlermeldung
und die beiden beobachteten Android-Stackframes. Zuvor müssen ein realer
Provider-Query und ein realer Open bestätigt sein. Andere Linkagefehler bleiben
Fehler. Ohne ausdrücklich gesetztes
`-PcontinuumPdfAllowMissingHostNative=true` bleibt der Test strikt. SKIP bedeutet
keinen bestandenen PDF-Decoder. Auf einer anderen Umgebung zunächst strikt
prüfen; den Host-Skip nicht pauschal übernehmen.

### Bildbelege und ihre Grenzen

Im früheren, am Zeitlimit beendeten Lauf auf `f0349f4c` entstanden 25 generische
native Canvas-Bilder für Innen-/Coverformat und STOP. Das Inventar enthält ihre
Hashes; ein repräsentativer Frame und seine ursprünglichen Metadaten sind
gesichert. Die übrigen temporären Bilder sind keine garantierte dauerhafte
Dateiablage und müssen bei Bedarf neu erzeugt werden. Außerdem ist ein
Blender-Bild des abgeleiteten Assets mit synthetischer Seitentextur gesichert.
Keines dieser Bilder zeigt einen geprüften Filament-Frame auf dem Fold7.
Vor dem Besitzerstopp entstand im aktuellen Core-Lauf kein PDF-spezifischer
Screenshot. Keine Filmqualitäts- oder Gerätefreigabe behaupten.

## Gesicherte Evidenz und wichtige Dateien

Unter `docs/evidence/pdf-scene-20261111/`:

- `core-partial/`: sofort geschriebene Einzelresultate, Rohlog und Abbruchbeleg.
- `node-focused/`: 17 konkrete Fälle, Kommando, Quellidentität und Loghash.
- `native-strict/`: vollständiger fehlgeschlagener Decoder-Probelauf.
- `run-history/`: ursprüngliche Fehler und Unterbrechungen; Rohlogs als gzip.
- `flow/`: frühere isolierte Router-Rot-/Grünbelege.
- `visual-samples/`: klar eingegrenzte Bildbeispiele und 25-Frame-Inventar.

Maschinenlesbarer Pausenstand:
`docs/research/omega/PDF-SCENE-PAUSE-STATE-20261111.json`.
Weitere technische Details stehen in `PDF-FLOW-HANDOFF-20261111.md`,
`PDF-SCENE-HANDOFF-20261111.md` und `PDF-SCENE-JUNIT-JOURNAL-20261111.md`.
Diese Pausenübergabe ersetzt dortige ältere Aussagen wie „Lauf läuft noch“.

## Einstieg nach einem neuen Weiterauftrag

1. Repository-`AGENTS.md`, diese Übergabe und den tatsächlichen Kopf von PR #761
   lesen. Kanonischen Branch verwenden; keine Helfer-PRs erneut blind integrieren.
2. Core-Scope vollständig auf einem während des Laufs unveränderten Quellstand
   ausführen. Fehlende Fälle und echte Fehler bearbeiten; vorhandene Assertions
   nicht abschwächen. Der Test `anotherStartedTask...` ist nicht abgeschlossen.
3. Danach den vollständigen Visual-Scope auf demselben Code ausführen und echte
   PDF-/Ergebnisbilder einschließlich Coverformat, STOP und Fallback ansehen.
4. Offene gesamte Node-Regression nachvollziehbar abschließen. Den historischen
   unterbrochenen Lauf nicht in einen Erfolg umdeuten.
5. Erst danach einen konkreten Geräteprüfstand vorbereiten. Native PDF-Lesung,
   tatsächliche 3D-Projektion, Sprachzeitpunkt, Lebenszyklus und Wärme auf dem
   Fold7 separat prüfen. Für Originalsignierung und Geräteinstallation gelten
   die bestehenden ausdrücklichen Besitzerfreigaben aus `AGENTS.md`.

### Reproduktionshinweise

Verwendet wurden JDK 21.0.13+9, Gradle 8.11.1, Android API 36,
Build-Tools 35.0.0 und die unveränderten Projektabhängigkeiten (Kotlin 2.4.0,
Filament 1.56.0, Robolectric 4.14.1). Kein Bibliotheksupgrade ist Teil der Arbeit.

Gradle-Aufgabe: `:app:testDebugUnitTest`. Bestehende QA-Flags:
`-Pkotlin.compiler.execution.strategy=in-process`, `-PdeviceAiMentorPreview=true`,
`-PcontinuumOwnerArm64=true`, `-PcontinuumStandaloneVersionCode=53`,
`-PcontinuumLocalStandaloneQa=true`, `-PcontinuumUiWidgetTests=true`.
Init-Skripte: `tools/capture/force_capture.gradle` und
`tools/pdf-scene-review/focused.init.gradle`.

`-PcontinuumPdfQaScope=core` wählt 18 Klassen, `visual` sieben; ihre disjunkte
Vereinigung entspricht dem unveränderten `all`-Scope mit 25 Klassen. `decode`
wählt nur den echten Decoder-Probetest und ersetzt ihn nicht in `core`.
Keine CLI-`--tests` verwenden: Die bestehende Aufgabenfreigabe weist sie zurück.
`-PcontinuumUiEvidenceDir` auf ein neues Verzeichnis außerhalb des Checkouts
setzen; `CONTINUUM_CAPTURE_SOURCE_COMMIT` aus dem tatsächlichen `git rev-parse
HEAD` beziehen. Ein abgeschlossenes `suite_end` und passende Quellmetadaten
verlangen. Native Canvas-Fälle sind langsam; getrennte Scopes und ausreichendes
Zeitbudget verwenden. Jeweils nur einen schweren Gradle-/Gesamttest betreiben.

## Freigabe- und Produktstand

**PORTIERT:** ja, im gemeinsamen Branch und den vorhandenen Aufrufpfaden.
**VERIFIZIERT:** teilweise, mit den obigen echten Ergebnissen und offenen Fällen.
**GERÄTEBESTÄTIGT:** nein.

Kein Main-Merge, keine APK-Veröffentlichung, keine Originalsignierung, keine
Installation und kein Wechsel von Paketname, Signatur oder APK-Version wurden
vorgenommen. An der installierten Besitzer-App wurde durch diese Arbeit nichts
geändert. Die PDF-Szene ist ein gesicherter Implementierungsstand mit teilweiser
Hostprüfung, noch keine vollständig abgenommene Geräteversion.
