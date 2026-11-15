# Device AI Continuum – Bunker V04

## Installation failure and Code59 signing preparation — 2026-11-15

The owner reported “App not installed”. The Code59 files saved to Drive are
**unsigned QA artifacts, not installable APKs**. Downloading them again cannot
repair the missing signature. No original signing key was read, exported or used.
The existing application must remain installed to retain its data.

Integration owner: current Continuum V04 implementation. Canonical branch remains
`fix/code59-context-complete-20261114`; no main merge or alternate application ID.

The registered `continuum-code50-sign-release.yml` now prepares Code59 in addition
to the unchanged Code51/55 routes. Code59 requires the full green source-bound run
and the separate green context run. It checks all five full jobs and the context
job, six artifact identities, latest attempts, ZIP digests, actual JUnit and lint,
Node XML and source inputs, real native lifecycle/frames/routines/offline evidence,
and the ARM64 APK's contents, original assets and 16 KiB alignment. The original
Code55 archive validator is retained byte for byte; Code59 supplies a temporary
asset projection with face models independently verified against the source lock.

Original-signing approval is **not recorded for this source**. The workflow still
requires explicit `owner_approved_sha`, the `Ullmann27` required reviewer and an
exact single branch policy in `continuum-original-signing`. No environment policy
was changed. Approval includes signing and publication as latest release, followed
by draft and public download verification. No workflow was dispatched to sign.

The original Code55 predecessor was downloaded and verified independently:
`continuum-local-code55-889e06d47fffd2230fa5b062dd1f31b65992e728`,
103,803,882 bytes, APK SHA-256
`2ba5cbf91f151833e204d54a6ac6435d9cf4081051de1cb25d5fd89a203a07b9`.
It has the original package and certificate
`6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.

Parent `95dbfa85ec7174ef2668a74adf0a9e1dcd789c4d`, full run `43682795173`:
unsigned build, Node/backend, renderer and full Android jobs passed. The downloaded
Android artifact `14150777921` was verified by size, SHA-256 and ZIP CRC. Actual
JUnit: 5,834 cases, 5,832 passed, two declared skips, no failures or errors.
Lint has no errors and 292 warnings. Context run `43682795141`
passed all 400 tests. Native artifact `14148798551` has a passing real application
probe, including the repaired landscape composer, restart, offline flow and live
hologram frames/resizes. Its routines probe **failed** before sending a command:
ADB requested `Routinen`, while actual XML recorded `Rioutnen`. This is not a
confirmed production input fix and is not a passing routine result.

The routine probe now records and injects individual characters sequentially with
50 ms between characters. It still requires the exact observed command before
Send and never repairs, retries or submits altered text. This changes only test
input delivery; the production routine, scheduler interval, constraints, clock,
approval and text editor remain unchanged from the parent. The new exact source
must complete its own native test before any signing.

The preceding `37493320` artifact recorded an Android scheduler window with
earliest execution about 15 minutes and latest about 30 minutes after the initial
run; `TIMING_DELAY` was still unsatisfied when the old 17-minute observation
expired. The probe now permits 32 minutes of observation, bounded by a 40-minute
process budget with four minutes reserved for result verification and stopping.
The 15-minute minimum, two distinct actual tool receipts, unchanged owner approval,
real host/device clocks, unchanged chat and normal job removal remain mandatory.
No clock, production interval or scheduler constraint is changed. A satisfied
`run -s` test request remains explicitly distinguished from natural scheduling.
This is a longer test observation, not a promise of exact device execution time.
Primary reference, checked 2026-11-15:
https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long)

Local verification: 20 new signing-evidence/preflight tests passed, including
rejection of false approval, missing reviewer, foreign source, failed native job,
stale/tampered archives and green metadata without real evidence. The 26 existing
Code51/55 contract/preflight tests passed. Routine evidence/input tests: 43 passed.
The preserved validator's inherited gates passed. The new APK verifier separately
rechecked the actual parent APK and the actual context/renderer evidence. These
checks do not constitute a green final Code59 release run or a physical device test.

Additional local check on preparation commit
`4d6059eeeffbcb764de5d92d803b9b9686d0d56b`: the complete Node suite passed 2,475
cases with 37 declared skips, and critical checks passed 137 cases. Both actual
reports were accepted by the corrected Node proof verifier after full source-file
hash comparison. Six tests ensure that the report's explicit out-of-scope
Gradle/APK/device declarations stay `NOT_RUN`, and cannot be invented as successes.
The initial local full run failed because the sparse checkout omitted historical
test fixtures; the complete checkout was restored before the passing repeat.

Status: `PORTIERT` for signing preparation and routine test input; local tests
`VERIFIZIERT`; complete exact-source CI pending; original signing and installation
`NOT_RUN`; Fold7/smartphone/tablet `GERÄTEBESTÄTIGT = NO`.

Next: publish this tested preparation on the common branch, inspect the new complete
source-bound runs, then obtain the protected exact-SHA owner approval. Never label
an unsigned artifact as the finished installation APK.

## Querformat-Eingabe und vollständige Vorläufer-QA, 15.11.2026

Der abgeschlossene Lauf 43678716957 auf
`37493320a7d535350ace78f9b74d9a3aeaeaadf2` bestätigt die vollständige Android-
Suite: 5.830 Fälle, davon 5.828 bestanden und zwei deklarierte Host-Container-
Auslassungen; keine Fehler. Lint: keine Fehler, 292 Warnungen. Renderer 32/32,
Node 2.474 bestanden mit 37 erklärten Auslassungen, kritisches Profil 137/137,
Backend 64/64. Die Archive wurden heruntergeladen, nach Größe/SHA-256 geprüft
und die JUnit-XMLs erneut ausgezählt.

Die reale native 3D-Ausgabe und der komplette Offline-Web-Pfad bestehen auf
diesem Stand. Die App-Abnahme fand jedoch einen echten Querformatfehler: Bei
geöffneter Tastatur waren nur Startkopf und Modusreihe sichtbar; Editor und
Senden lagen unter der Tastatur. Zwei neue Tests gegen die unveränderten
Releaseklassen reproduzierten beide den abgeschnittenen Editor.

Die tatsächliche nutzbare Fensterhöhe steuert jetzt eine kompakte Gesprächs-
ansicht. Der vorhandene Editor und dieselbe Senden-Schaltfläche bleiben in
einer Zeile. Zusatzfunktionen und Moduswahl werden über ein natives Menü mit
ihren bestehenden Klickpfaden bedient; Diktat, Stimmauswahl und Entwicklung
behalten ihre bisherigen Langdruckaktionen. Deaktivierte Aktionen bleiben
gesperrt, auch nach dem Öffnen des Menüs. Entwurf, Auswahl und Eingabefokus
bleiben bestehen. Beim Vergrößern werden dieselben Ansichten und ursprünglichen
Layoutparameter wieder eingesetzt. Die Schrifteinstellung bleibt erhalten;
die Senden-Schaltfläche misst ihren Platz auch bei doppelter Schriftgröße.
Der Originalavatar und sein Controller bleiben bestehen. Der vorhandene 2D-
Avatar wird nur im sehr niedrigen Gesprächsbereich ausgeblendet und anschließend
wieder angezeigt; der native 3D-Avatar bleibt im bisherigen Home-Container.

Lokale Abnahme: vollständiger ARM64-Releasebau bestanden; 64 JVM-/Robolectric-
Fälle gegen die vollständig kompilierten Releaseklassen bestanden (SDK28),
einschließlich vier neuer UI-Fälle und bestehender Routinen-, Avatar-, Monitor-,
Dashboard-, Insets- und Entwurfsprüfungen. Kein Test wurde ausgelassen.
Workflow-YAML und Python-Abnahmeblock wurden erfolgreich geparst.
Die vier neuen UI-Fälle sind zusätzlich im verpflichtenden Voll-QA-Register
enthalten. Die reduzierte Emulatorauflösung garantiert lediglich gleiche
nominelle Display-dp; Systemeinrückungen und nutzbare App-Höhe unterscheiden
sich. Die Metadaten verwenden dafür jetzt den präzisen Namen
`nominal_display_dp_preserved`.

Der periodische Routinenjob wurde in Lauf 43678716957 innerhalb der begrenzten
17-Minuten-Beobachtung nicht erneut ausgeführt. Der erfolgreiche natürliche
Start des Vorlaufs b0fd4492 nach 976.638 ms bleibt ein anderer Quellbeleg.
Android garantiert für `JobInfo.Builder.setPeriodic` keine exakte Startzeit
innerhalb des Intervalls (Primärquelle: https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long)).
Die neue Korrektur ändert den Scheduler nicht. Kein Timer wurde vorgedreht,
keine unerfüllte Bedingung erzwungen und kein fehlgeschlagener Lauf als bestanden
ausgegeben. Der nächste echte Lauf des geänderten Appcodes muss den gesamten
nativen Pfad erneut belegen. Originalsignierung bleibt separat offen.

## Nachgewiesener nativer Fortschritt und letzter Web-Prüferbefund

Stand `b0fd4492db12526eff239b93b609a979eca9a038`, Lauf `43676184729`:
Die unveränderte reale 3D-Abnahme besteht bei 540 × 1200 Pixeln / 210 dpi.
SurfaceTexture-Zeitstempel schreiten fort, PixelCopy und TextureView enthalten
sichtbare Pixel, alle vier Größenwechsel bestehen. Die tatsächliche Aufnahme
wurde geöffnet: Originalavatar, Bunker, Monitor und Arbeitsbereich sind sichtbar.
Der Routinenlauf besteht ebenfalls, einschließlich natürlichem Android-
Schedulerstart nach 976.638 ms realer Wartezeit, erneuter Zustellung in denselben
Chat, Prozessneustart und Entfernung des periodischen Jobs nach Besitzerstopp.
Keine Geräteabnahme auf einem physischen Fold7 wird daraus abgeleitet.

Die restliche App-Abnahme stoppte am letzten Offline-Web-Validator: Er erkannte
den tatsächlichen leeren Eingabefeld-Hinweis `Was soll ich auf deinem Gerät tun?`
nicht. Die identischen aufgezeichneten Eingaben, Zustimmungen, Netzwerkzustände,
Quittungen und Ergebnisse bestehen mit der korrigierten Prüfung vollständig.
Der Hinweis ist am vorhandenen `AgentActivity.input.hint` gebunden; 33 Webfälle
bestehen einschließlich neuer Ablehnungen für vorhandenen Auftragstext und ein
deaktiviertes Eingabefeld. Die Korrektur ändert ausschließlich die Prüfung.
Allgemeine App-Rotation/Navigation nach diesem Webschritt wurde im alten Lauf
noch nicht erreicht und muss im neuen echten Lauf bestätigt werden. Der alte
Gesamtlauf bleibt fehlgeschlagen; die unabhängige Nachprüfung ändert seine
ursprünglichen Belege nicht.

Die APK von b0 wurde lokal und unabhängig in GitHub bytegleich erzeugt:
104.422.309 Bytes, SHA-256
`9ab603aa017b3b6703465b8badfefef639e4d5850102cf3105285fccbcf657bf`.
Sie ist in Drive gespeichert und vollständig zurückgelesen/geprüft, weiterhin
unsigniert. Renderer: 32/32, kritisches Node-Profil: 137/137, Node-Vollprofil:
2.474 bestanden und 37 erklärte Auslassungen, Backend: 64/64. Die vollständige
Android-Suite war bei Erstellung dieses kleinen Prüfpatches noch aktiv. Avatar,
Assets und alle produktiven APK-Einträge sind gegenüber b80 identisch; nur die
eingebettete Git-Revision unterscheidet sich. Originalsignierung bleibt offen.

## Native Nachprüfung des gebauten Code59, 15.11.2026

PR #849 ist in `fix/code59-context-complete-20261114` integriert. Der gemeinsame
Stand `b80ce6dad82608534434b5885195131b6f28f9f3` erzeugt lokal und in GitHub eine
byteidentische ARM64-APK: 104.422.309 Bytes, SHA-256
`96423410880694dd65d13251124737c2b056430887d74d85ae19b52cc2e0560a`.
Die ausdrücklich unsignierte Datei wurde im beauftragten Drive-Ordner gespeichert
und vollständig zurückgelesen; Größe und SHA-256 stimmen überein. Sie ist kein
installierbares Besitzerupdate. Alle 47 eingebetteten Quelldateien, die fünf
nativen Bibliotheken und die ursprünglichen Avatar-/Orb-/Material-Pins wurden
geprüft. Renderer-Vorprüfung: 32 bestanden. Node-Vollprofil: 2.474 ausgeführt und
bestanden, 37 deklarierte Auslassungen. Die vollständige Android-QA lief zum
Zeitpunkt dieses Prüfpatches noch; kein Gesamterfolg wird behauptet.

Die native QA von b80 meldet drei konkrete offene Ergebnisse: kein konsumierter
erster Filament-Frame innerhalb des Beobachtungsfensters, ANR beim Schließen der
Einrichtung vor Beginn der Routinenprüfung und ein falsch abgewiesener sichtbarer
Android-Listeneintrag. Der Web-Probe erreicht inzwischen die Internetfreigabe.
Die aufgezeichnete XML zeigt einen aktivierten `CheckedTextView` in
`android:id/select_dialog_listview`; Android delegiert dessen Klick an die Liste.
Der gemeinsame Resolver für Bedienung und Evidenzprüfung akzeptiert nun genau
diesen sichtbaren, vollständig im aktivierten Dialogcontainer liegenden Eintrag.
Ausgeschaltete, mehrdeutige, außerhalb liegende oder abgetrennte Einträge bleiben
unzulässig. Die vorhandenen Zustimmungs-, Modus-, Quell- und Ergebnisprüfungen
bleiben erhalten. 172 Host-Helferfälle bestehen, darunter 30 Webfälle. Die reale
Dialog-XML ist als reproduzierbare Fixture enthalten. Der Workflow führt auch
die bisher nur lokal ausgeführten Web-Navigationsfälle aus.

Der einzige zusätzliche Emulatorversuch verwendet 540 × 1200 Pixel bei 210 dpi
statt 1080 × 2400 bei 420 dpi. Die logische Fläche bleibt rechnerisch gleich;
die Pixelzahl sinkt auf ein Viertel. Originalkonfiguration und tatsächliche
Startauflösung werden geprüft und als Evidenz gespeichert. Anlass sind die
beobachtete Software-GPU-Last und ein historischer erster Frame erst nach etwa
86 Sekunden. Das ist eine begrenzte Infrastrukturdiagnose, keine bewiesene
Ursachenbehebung und kein Fold7-Leistungsnachweis. Renderer, Avatar, Szene,
90-Sekunden-Beobachtung, PixelCopy, konsumierte Zeitstempel, vier Größenwechsel,
Routinenlauf und Web-Zustimmung bleiben unverändert. Erst der neue Lauf kann
die Wirkung belegen; bis dahin bleibt native QA offen.

Originalsignierung ist für Code59 noch nicht ausführbar: Der registrierte
Signiervertrag unterstützt Code51/55 und verlangt exakte grüne Quell-QA,
SHA-spezifische Besitzerfreigabe und den geschützten Besitzerreview. Es wurden
keine Schlüssel gelesen, keine Signiergrenzen geändert und kein Release
veröffentlicht. Einstiegspunkt bleibt dieser gemeinsame Branch; nächste
Abnahme sind die unveränderten nativen Prüfungen des neuen Quellstands.

## Fortsetzung: gemeinsamer Code59-APK-Bau, 15.11.2026

Integrationsziel bleibt `fix/code59-context-complete-20261114` über PR #849.
Der veröffentlichte Integrationscommit
`e006e10681c472269485f6f7f406edabf3dc9460` enthält V04, den aktuellen
Kontext-/Laufzeitstand `b391409efd3a3080d70de5a798fc01a514ea6a8c`
und die native Startkorrektur `43e67f671fcb3becac8288453faa769f9860732f`.
Sein Baum `1d4f8fcfe89747caedcedd1666e861f47c3c7ef7` wurde exakt mit dem
lokalen Merge verglichen. Alle 29 Xvfb-Fälle beider Eltern bleiben erhalten.

Der vollständige lokale ARM64-Releasebau hat erstmals erfolgreich kompiliert,
gedext und eine APK gepackt: `:app:assembleRelease`, 46 Tasks, Exit 0.
Ein erneuter vollständiger Bau einschließlich der Routinenkorrektur bestand
ebenfalls. Beide verwenden Code59 / 0.3.58 und das ursprüngliche Paket
`at.heinz.deviceai.continuumlocal`, im ausdrücklich unsignierten Transportmodus.
Das ist kein signiertes Besitzerupdate. Der abschließende veröffentlichte
Quellstand muss anschließend erneut gebaut und separat geprüft werden.

Die Zustellung normaler Routinenberichte war reproduzierbar defekt: Ein
gespeicherter INFO-Bericht weckte den passiven UI-Empfänger nicht, weil dieser
am nur für URGENT ausgeführten Mailbox-Hook hing. Ein separater Aufnahme-Hook
benachrichtigt nun den vorhandenen UI-Empfänger auf dem Hauptthread. Priorität,
Warteschlangengrenzen, Duplikatsperre, Zustimmung, Sitzungsbindung und die
laufende Besitzerantwort bleiben unverändert. Der Vorher-Test lief mit zwei
Fällen und einem Fehler; danach bestehen alle 55 ausgewählten JVM-/Robolectric-
Fälle gegen die vollständig kompilierten Releaseklassen. Enthalten sind die
neue passive Zustellung, die tatsächliche Activity-Zustellung während einer
laufenden Antwort, Dashboard-Sichtbarkeit, native Monitorpixel/-Lebenszyklus,
Bunker-Integrität und Monitortransformationen. SDK28 / Robolectric ersetzt
keinen Test auf einem Fold7. Die bisherige Quellvertragsprüfung der alten
Monitorrückseite berücksichtigt jetzt sowohl den V04-Pfad als auch den
unveränderten Rückfall auf die alte Rückseite.

Der Offline-Web-Probe wartet zuerst auf den wirklichen Paneltitel und sucht
die Internetfreigabe danach mit höchstens sechs nachgewiesenen Scrollschritten.
Eindeutiger Container, sichtbares Ziel, echte Geometrieänderung, konstante
Fenster-/Prozessidentität und aufgezeichnete Eingabebefehle bleiben zwingend.
168 native Host-Helfertests bestehen; darin 26 Webfälle einschließlich neun
neuer Navigationsfälle. Dies bestätigt keine erfolgreiche native Webrecherche.

Die zuvor heruntergeladene native Basis-QA meldete weiterhin einen fehlenden
ersten konsumierten Filament-Frame. Einreichungszähler allein reichen nicht.
Dieser Befund bleibt bis zum echten Lauf des gemeinsamen Kandidaten offen;
es wurde kein GPU-PASS erfunden und keine Prüfung abgeschwächt.
Originalavatar, Orb, Monitor-Material, GLBs und die fünf bereits in Drive
geprüften Cycles-Bilder sind unverändert. Die nachfolgenden Abschnitte halten
den historischen Stand der ersten V04-Übergabe fest.

## Grundlage

Der vorgegebene Workflow Continuum_Cinematic_Home_Blender_Workflow.md wurde
zuerst gelesen. Grundlage ist Continuum_Mission_Desk_V3.glb, SHA-256:
0e6dd56445d5c96f40c228101329f4583ef305de4c33d0491279211ab16e3ee1.
Beide ausgewählten Home-Referenzen wurden visuell geprüft. Die inzwischen im
Drive-Ordner vorhandenen ZIEL-PNGs zeigen dieselben Entwürfe.

Android-Basis: PR #848, Commit b8c31554ce7416540692f1d30b559be3712495d7.
Lokaler Branch: chatgpt/continuum-bunker-v4-20261114.
Der Basisbranch wurde abschließend erneut geprüft und stand unverändert auf
obigem Commit. Main wurde nicht verändert.

## Erstellte 3D-Dateien

| Datei | Bytes | Umfang |
| --- | ---: | --- |
| Continuum_Bunker_Stage_V04.glb | 3.979.204 | 329 Nodes, 320 Meshes, 53 Materialien, 101.824 Dreiecke |
| Continuum_Bunker_Stage_V04_Android.glb | 3.027.184 | 58 Nodes, 55 Meshes, 23 Materialien, 70.020 Dreiecke |
| Continuum_Bunker_Stage_V04.blend | 25.254.592 | Bearbeitbare Szene mit Kamera, sieben Flächenlichtern und separatem Originalavatar |

Die Bunkerhalle besitzt gegliederten Boden, Wände, Träger, Deckenrippen, Tür,
sechs Serverracks, Lüftung, Kabelkanäle und Leitungen. Der Mission Desk bleibt
als Grundlage erhalten und wurde proportional zum Raum verkleinert.
Monitore besitzen flachere Gehäuse und echte Rückseiten mit Kühlrippen,
Serviceblock, Anschlüssen, Gelenkarmen und Kabeln. Graphit, Navy, Titan und
Beton ergänzen Cyan links und Violett rechts.

Die vollständige GLB erhält alle vier V3-Animationsclips. Zeit- und
Rotationsaccessoren wurden nach dem Blender-Export bytegenau übernommen und
verglichen. Die Android-GLB enthält Raum, Tisch und Monitorkonstruktion als
getrennte Wurzeln. Avatar, Orb, Live-Pixel und Animationen bleiben in der
bestehenden App-Laufzeit. Die sieben Flächenlichter gehören zur Blender-Datei;
Android nutzt seine vorhandene Filament-Beleuchtung mit den neuen Materialien.

## Selbst gerenderte Ansichten

Blender 4.3.2, Cycles CPU, AgX. Alle fünf Bilder wurden geöffnet, visuell geprüft
und vollständig dekodiert.

| Ansicht | Auflösung | Samples |
| --- | --- | ---: |
| Front | 1440 × 900 | 64 |
| Rear | 1440 × 900 | 64 |
| Top | 1120 × 1120 | 32 |
| FoldWide | 1440 × 880 | 32 |
| MonitorRear | 1120 × 800 | 32 |

Die Draufsicht liegt unter den Deckenträgern. Nur für die Monitor-Nahaufnahme
wird der davorstehende Avatarbegleiter im Render ausgeblendet. Sichtbares
Cycles-Korn bleibt bei den begrenzten Samples erhalten. Die Monitortexte sind
als 3D-Vorschau gekennzeichnet. Diese Bilder sind keine Android-Screenshots.

## Im Android-Quellcode integriert

* Bunkerdatei als APK-Asset mit Größenlimit, festem Pfad und SHA-256-Prüfung.
  Fehlende oder beschädigte Erweiterungen lassen die bisherige Szene bestehen.
* Laden und Freigeben im vorhandenen Filament-Renderer; kein zusätzlicher
  Renderer oder Frame-Takt.
* Raum und Schreibtisch besitzen unabhängige, gleichmäßige Transformationen.
  Die Monitorrückseite folgt der vorhandenen Monitormatrix.
* Native Monitorfläche mit echten Dashboard-Zeilen. Sie erscheint nur ohne
  gebundene Quelle und nach einer erfolgreich eingereichten ruhenden Kamera.
* Gebundene Live-Quellen haben auch im Wartezustand Vorrang. Während der
  Kamerafahrt wird die neue Arbeitsbereichsfläche ausgeblendet.
* Reale Projekte und offene Arbeit in den breiten Seitenkarten; CPU/RAM aus
  dem App-Zustand, unbekannte Werte als —. Karten öffnen die Aufgabenansicht.
* Die vorhandene adaptive Anordnung verwendet Fensterbreite und Schriftgröße.
  Acht Navigationsziele, Live-Tabs, Dokumente und Capture-Pfade bleiben erhalten.

Originalavatar-GLB, Orb-GLB und Live-Monitor-Material sind bytegleich mit der
Android-Basis. Avatar-Rig/Morphs und Bewegungs-, Blick-, Sprach- und
Lippensynchronisationscode wurden nicht ersetzt.

## Verifikation und Grenzen

25 JVM-Tests bestanden: Asset-Pins, beschädigte/fehlende Assets, getrennte
Transformationen, Projektion sowie bestehende Monitor-/Kamerabewegung.
Die Transformationsprüfung umfasst sieben Breiten (240, 360, 412, 600, 884,
1280, 1536 dp), drei Schriftgrößen (1, 1,5, 2) und jeweils 101 Bewegungsstände.

Bestanden: native Monitoransicht mit 35 originalen Kotlin-Dateien und zwei generierten Java-Dateien gegen den echten Android-Classpath.

5 native Robolectric-Ansichtstests bestanden.
Die Ansichtstests verwenden Android-Systemressourcen; sie rendern nicht den
vollständigen Homebildschirm oder Filament. Der zusätzliche Dashboard-Test
ContinuumWorkspaceSummaryTest ist angelegt, hier jedoch nicht ausgeführt.

Die GLB-Prüfung kontrolliert Struktur, Puffer-/Accessor-Grenzen, endliche Werte,
Dreiecksindizes und Originalanimationen. Sie ersetzt keinen vollständigen
Khronos-Konformitätstest. Alle acht 3D-/Bilddateien wurden nach dem Drive-Upload
erneut heruntergeladen; Größe und SHA-256 stimmen mit den lokalen Dateien überein.

Der vollständige Gradle-Appbuild ist nicht bestätigt: mehrere Läufe endeten
mit „Gradle build daemon disappeared unexpectedly“. Auch die direkte Prüfung
aller 652 Kotlin-Dateien endete mit SIGKILL (Exit -9), ohne Kotlin-Diagnose.
Die genaue Ursache ist nicht belegt. Es wurde kein fertiges APK erstellt.

ADB zeigte kein angeschlossenes Gerät. Fold7 außen/innen, Rotation, Tablet,
Split-Screen, tatsächliche GPU-Ausgabe, FPS, Temperatur, Gerätespeicher und
sämtliche App-Funktionen sind nicht am Gerät durchgetestet. Hosttests ersetzen
keinen solchen Gerätenachweis.

## Übergabe und Fortsetzung

Das private Quellcodepaket enthält Git-Bundle, binären Diff zum Basiscommit,
Blender-Bauskript, Integritätsprüfung und Prüfbelege. Das Bundle setzt den
oben genannten Basiscommit voraus. Reproduktionsbefehle für Blender stehen
in tools/continuum-bunker/README.md.

Es wurde kein öffentlicher Branch oder PR veröffentlicht. Die automatische
Freigabeprüfung hat den Push abgelehnt, weil die bisher private Drive-GLB und
zugehörige Quelldaten dadurch öffentlich würden. Dafür ist eine konkrete
Zustimmung zur öffentlichen Veröffentlichung erforderlich.

Nächste technische Abnahme: vollständiger Gradle-Build in einer tragfähigen
Buildumgebung und echte Gerätetests unter den bestehenden Signierungsregeln.
