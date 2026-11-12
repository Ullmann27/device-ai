# Code55 – Bauanleitung des virtuellen Computers

## Ziel und tatsächlicher Aufbau

Jarvis sitzt im vorhandenen Continuum-Cockpit vor einem aufrechten Monitor. Dessen Inhalt stammt aus der freigegebenen, tatsächlich sichtbaren Android-App. Das Cockpit erfindet keine WhatsApp-, Facebook-, Word- oder Dateien-Oberfläche. Öffnen, Suchen, Scrollen und Texteingaben wirken im Originalfenster; dessen neue Bilder erscheinen auf Jarvis' Monitor.

Der erste Geräteprüffall ist **„Öffne WhatsApp und suche Anna“** auf dem Fold7 im geteilten Bildschirm. Die Implementierung ist im Integrationszweig prüfbar. Eine bestandene Hostprüfung ist noch kein Nachweis für eine bestimmte WhatsApp-Version, Samsung-Fensterkonfiguration oder die GPU des Fold7.

## Zuständigkeiten im Projekt

Die folgenden Klassen liegen unter `app/src/main/java/at/heinz/deviceai/`.

| Bauteil | Zuständige Quelle | Aufgabe |
|---|---|---|
| Auftragseinstieg | `AgentActivity.kt` | Text-/Sprachauftrag, konkrete App-Auswahl und vorhandener ToolGate |
| Auftragsverständnis | `cognitive/OwnerComputerIntentParser.kt` | Unterstützte vollständige Aufträge erkennen; unklare Schritte zurückfragen |
| Sitzungsführung | `OwnerComputerSessionController.kt` | Eine aktive Sitzung, Freigabe, Sichtbarkeit, Start/Stop und erstes echtes Bild |
| Freigabe | `cognitive/OwnerComputerTaskGrant.kt` | Exakter Auftrag, App, Beobachtungsepoche, Query/Entwurf, begrenzte Schritte |
| Android-Freigabeantwort | `cognitive/ComputerCaptureConsent.kt` | Nicht wiederverwendete Request-Codes; verspätete Antworten verwerfen |
| Ablauf | `cognitive/OwnerComputerWorkflow.kt` | Beobachten, eindeutig handeln, neue Wirkung prüfen |
| Native Bedienung | `AndroidLiveNodePort.kt`, `AndroidLiveComputer.kt` | Frische Fensterknoten, tatsächlicher Dispatch und Nachprüfung |
| Bildquelle | `ContinuumLiveProjectionService.kt` | Android MediaProjection, aktuelle Fenstergeometrie und begrenzte Bildannahme |
| GPU-Ausschnitt | `AndroidGpuFrameCopier.kt` | Eigenes unveränderliches HardwareBuffer-Bild des erlaubten Ausschnitts |
| Bildvertrag | `cognitive/LiveComputerFrames.kt` | Sitzung, Fenster, Zeit, Revision, Frische und eindeutiger Ressourcenbesitz |
| Monitor | `ui/VirtualComputerMonitorView.kt` | Bildreferenz, Seitenverhältnis, perspektivische Darstellung und echte Klickposition |
| 3D-Arbeitsplatz | `avatar3d/FilamentCockpitStage.kt`, `avatar3d/CockpitStageFrame.kt` | Raum, Monitorrahmen und gemeinsame Kamera-/Projektionsgeometrie |
| Layout | `ui/CinematicWorkstationLayout.kt`, `ui/ContinuumHomeView.kt` | Fold-/Phone-Anordnung, Szene, Eingabe und Navigation |
| Gemeinsames Ereignis | `cognitive/CockpitWorkEvent.kt` | Erweiterung des vorhandenen CockpitAnimationBus |
| Körper und Ton | `avatar/ComputerWorkAvatarBridge.kt`, `avatar/ComputerWorkCuePlayer.kt` | Reale Eingaben treiben kurze Bewegungs- und Klickimpulse |

## Ablauf eines realen Suchauftrags

1. Der Besitzer sagt oder schreibt „Öffne WhatsApp und suche Anna“.
2. Der Parser erzeugt einen gebundenen Suchauftrag. Ein unerkanntes zusätzliches Kommando wird nicht stillschweigend abgeschnitten.
3. Der vorhandene App-Finder prüft installierte Kandidaten. Ein einzelner Treffer führt direkt zur konkreten Freigabe; mehrere Treffer benötigen eine Auswahl. Ein App-Name allein gilt nicht als Beweis eines erfolgreichen Starts.
4. Der bestehende Startpfad muss das richtige Paket im Vordergrund bestätigen. Continuum und die Ziel-App müssen gleichzeitig sichtbar sein, damit das Cockpit das reale Fenster zeigen und weiter bedienen kann.
5. Im geteilten Bildschirm startet Continuum den Android-Dialog für Bildschirmübertragung. Seine Antwort ist an den ursprünglichen Auftrag gebunden. Abbrechen beendet den Auftrag. Ein verspätetes OK für einen alten Auftrag startet keinen neuen.
6. Die Aufnahme startet erst nach Rückkehr in die sichtbare Activity. Die erste Bedienung wartet auf ein frisches Bild der gestarteten Aufnahmesitzung.
7. Ist bereits ein eindeutiges Suchfeld vorhanden, wird dieses verwendet. Andernfalls wird der eindeutig beobachtete Suchknopf einmal angeklickt. Das folgende Suchfeld muss in einer neuen Beobachtung bestätigt werden.
8. Die exakte Query „Anna“ wird in das bestätigte Suchfeld eingetragen. Die App selbst filtert ihre echten Kontakte. Continuum prüft die neue Beobachtung und den tatsächlich eingetragenen Text.
9. Ein eindeutiger Treffer, mehrere Treffer und eine noch unbestätigte Trefferlage führen zu unterschiedlichen Rückmeldungen. Der Monitor zeigt weiterhin die echte Suchansicht. Es wird keine Nachricht gesendet.
10. „Stopp“ beendet Auftrag und Aufnahme. Alte Bilder, alte Callback-Antworten und späte GPU-Ergebnisse dürfen danach nicht erneut erscheinen.

## Bildweg und Ressourcen

Der schnellere Pfad verwendet die ausdrücklich freigegebene Standardbildschirm-Aufnahme und schneidet ausschließlich das gleichzeitig beobachtete Ziel-App-Fenster aus. Ein Accessibility-Fensterbild dient als langsamerer Alternativpfad auf unterstützten Android-Versionen. Beliebige Android-Apps werden nicht als fremde Activities in eine Textur eingebettet.

Der MediaProjection-Pfad lässt Bilder mit mindestens 33 ms Abstand zu. Das ist eine Aufnahmegrenze, **keine gemessene 30-FPS-Zusage**. Der Screenshot-Pfad fragt etwa alle 350 ms nach. Das tatsächliche Tempo, die Verzögerung und die Wärmeentwicklung müssen auf dem Gerät gemessen werden.

Es gibt keine Warteschlange alter Monitorbilder: `acquireLatestImage`, begrenzte ImageReader-Puffer und höchstens eine aktive GPU-Kopie begrenzen den Rückstau. Diese Begrenzung gilt auch über einen schnellen Aufnahme-Neustart hinweg. Für Monitorbilder werden weder PNG/JPEG/Base64-Kodierung noch Modellaufrufe pro Frame eingesetzt.

Jedes Bild enthält Aufnahmesitzung, Zeit, Paket-/Fensterbindung, Beobachtungsrevision und den tatsächlich ausgeschnittenen Quellbereich. Ein Wechsel von Fenster oder Aufnahmegeometrie erneuert die Bildoberfläche. Der Monitor hält ausschließlich einen freigegebenen Bildbesitz und löst alte Referenzen ab.

„Live“ setzt ein frisches Bild der richtigen Sitzung voraus. Ohne neues Bild wird nach der konfigurierten Frist, standardmäßig 1,5 Sekunden, pausiert. Bei verdeckter oder ungültiger Quelle, Sitzungswechsel und Stop werden die App-Pixel verworfen. Ein eingefrorenes altes App-Bild darf nicht weiter als Live-Inhalt stehen bleiben.

Android kann geschützte Inhalte verweigern. Die Screenshot-API meldet bestimmte Schutzfälle ausdrücklich; MediaProjection kann gesperrte Inhalte auch als leere oder schwarze Pixel liefern. Deshalb wird keine universelle Erkennung aller geschützten App-Inhalte behauptet. OEM-Abweichungen bei der Auswahl einer Aufnahmequelle sind Teil der Geräteprüfung.

## Bedienung, Freigabe und Nachprüfung

Eine vorbereitete Aktion muss unmittelbar vor ihrem nativen Dispatch noch zur aktiven Freigabe gehören. Diese bindet Auftrag, Paket, Beobachtungsepoche und die exakten zulässigen Texte. Sie läuft ab, wird beim Abbruch widerrufen und nach Ende des Arbeitsablaufs entzogen.

Knoten werden aus dem gebundenen Fenster gelesen. Sichtbarkeit, Eindeutigkeit, Rolle, Passwort-/Sende-Erkennung und neue Beobachtungsrevisionen entscheiden über den nächsten Schritt. Ein erfolgreicher Android-Methodenaufruf allein bestätigt noch keine Wirkung.

Bei unbekannter Wirkung gibt es keine automatische Wiederholung. Bereits vorhandene Nachrichtenentwürfe werden nicht überschrieben. Der WhatsApp-Entwurfspfad verlangt den bestätigten Empfänger im Chatkopf und ein überprüfbares Eingabefeld. Kontaktlisten werden nur begrenzt gescrollt. Das Senden bleibt im derzeitigen Computerpfad gesperrt, weil kein geprüfter Versandweg angeschlossen ist.

Das Bild kann nach einem abgeschlossenen Suchauftrag sichtbar bleiben; dies erhält keine Eingabeberechtigung. Ein neuer Auftrag benötigt seine eigene Bindung. Die Aufnahme endet bei Stop oder beim vollständigen Verlassen der sichtbaren Arbeitsfläche.

## Bewegung und Klang

Ausführung, Avatar und Monitor benutzen den vorhandenen Cockpit-Ereignisbus. Die Ereignisse tragen Auftrag, Sitzung, Reihenfolge und monotone Zeit. Fremde Sitzungen, alte Reihenfolgen und Ereignisse nach Stop werden verworfen.

Nur ein tatsächlicher Eingabe-Dispatch erzeugt den kurzen Klick-/Körperimpuls. Der Klickring wird mit der Geometrie genau des importierten Quellbildes auf den Monitor projiziert; ein Tastatur-Ausschnitt darf ihn nicht verschieben. Wartezeiten und Rückfragen erzeugen kein vorgetäuschtes Tippen.

Die vorhandenen Avatar-Gelenke bleiben erhalten. Eine kleine Bewegung des AvatarRoot, kombiniert mit dem bestehenden Kopf-/Blicksystem, macht reale Texteingaben und Klicks sichtbar. Eine native `SET_TEXT`-Aktion ist eine einzelne Texteingabe; daraus wird keine erfundene Buchstabe-für-Buchstabe-Arbeit. Der leise Klick benutzt die bestehende Audio-/Sprachrichtlinie.

## Visuelle Vorgaben

Die Originaldatei des Jarvis-Avatars bleibt bytegleich. Die Originalkugel liegt hinter dem etwas transparenten Avatar; ihre Mitte liegt im Stirnbereich. Monitorrahmen und Raum werden hinzugefügt. Echte App-Pixel bleiben deckend und behalten ihre Farben sowie ihr Seitenverhältnis.

Die aufrechten Monitore haben eine geringe Arbeitsplatzperspektive. Kamera und Monitorhöhe lassen Gesicht, Kinn und Schultern sichtbar. Die Szene verwendet den vorhandenen Filament-Renderer und die vorhandene Kameraabstimmung.

| Ziel | Farbe | Offenes Fold7 | Normales Phone |
|---|---|---|---|
| Start | Cyan | 1 × 8 Buttons | 2 × 4 Buttons |
| Gespräch | Türkis | dieselbe Zielzuordnung | dieselbe Zielzuordnung |
| Gedächtnis | Violett | direkt | direkt |
| Forschung | Indigo | direkt | direkt |
| Aufgaben | Bernstein | direkt | direkt |
| Internet | Blau | direkt | direkt |
| Lernen | Grün | direkt | direkt |
| Geräte | Koralle | direkt | direkt |

Weitere vorhandene Ziele bleiben über Mehr erreichbar. Die Eingabe heißt „Was soll ich für dich erledigen?“. Farbige Glasflächen bleiben echte Android-Bedienelemente mit Fokus-/Accessibility-Zugriff.

## Tatsächlicher Funktionsumfang

| Ablauf | Implementierter Weg | Verbleibende Abnahme |
|---|---|---|
| App öffnen und anzeigen | Installierte App suchen, Freigabe, bestätigter Start und gebundene Bildquelle | Reale App-/Samsung-Version |
| WhatsApp-/App-Suche | Eindeutiger Suchknopf/-bereich, exakte Query, frische Beobachtung | Konkrete zugängliche App-Oberfläche |
| WhatsApp-Entwurf | Kontakt bestätigen, begrenzt scrollen, bestehenden Entwurf schützen, Text prüfen | Gerätetest, kein automatischer Versand |
| Eigene Dateien / Downloads | Eindeutiger Ordnerklick und neue Ordnerüberschrift | Samsung-Dateien-Version |
| Facebook, Word, Excel anzeigen | App-Start und dieselbe reale Bildquelle | Geräteprüfung und App-Zugänglichkeit |
| Beliebige Word-/Excel-Inhalte bearbeiten | Kein allgemeiner Dokument-/Tabellen-Automatisierer angeschlossen | Eigener überprüfter Editorablauf erforderlich |
| Nachrichten senden / Beiträge veröffentlichen | Kein freigegebener nativer Versandweg angeschlossen | Separater Implementierungs- und Freigabeschritt |

## Reproduzierbare Abnahme

Hostprüfungen testen Parser, Freigaben, Ereignisreihenfolge, Fenster-/Bildbindung, Zuschnitt, Nachbedingungen, Navigation und Android-Canvas-Darstellung. Blender-Render benutzen die tatsächlichen GLB-Dateien und exportierte Kamera-/Layoutwerte. Sie prüfen die räumliche Anordnung; sie beweisen weder Android-Filament noch Live-Apps auf dem Fold7.

Auf dem Gerät werden zunächst WhatsApp und Continuum nebeneinander geöffnet. Danach folgen Suche, mehrdeutiger Kontakt, Scrollen, Tastatur ein-/ausblenden, Fenstergröße ändern, Aufnahme ablehnen, Aufnahme widerrufen, App wechseln, Stop und erneuter Start. Zu jedem Fall werden tatsächliche Wirkung, Bildalter, sichtbarer Zustand und ungewollte Eingaben geprüft. Performance wird mit Zeitstempeln, Bildverlusten, Speicher und thermischem Status gemessen.

Die konkreten Quellstände, Testergebnisse und Build-SHA stehen in `docs/handoff/ASTRA55-COCKPIT-INTEGRATION-20261112.md`. Ein unsigniertes Code55-APK ist ein Prüfartefakt. Paket, Signatur und Installation werden erst im dafür vorgesehenen nächsten Schritt behandelt; es gibt keinen Merge nach `main` aus dieser Integrationsarbeit.
