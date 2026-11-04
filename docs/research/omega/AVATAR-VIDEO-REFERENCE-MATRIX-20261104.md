# Avatar: Originalvideos und vier Ergänzungen

Status: VISUAL_REFERENCE_REVIEWED / IMPLEMENTATION_PARTIAL / DEVICE_ACCEPTANCE_OPEN.

Besitzerauftrag: Kopf, Sprechen und Programmablauf müssen den Originalvideos einschließlich vier Ergänzungen entsprechen. Der bereits bestätigte Continuum-Kopf bleibt die Identitätsgrundlage. Diese Matrix beschreibt sichtbare Anforderungen und den tatsächlich überprüften Anschluss im Quellcode. Die Videos sind Referenzen; ihre eingeblendeten Kennzahlen und Anbieterlogos beweisen keine ausgeführten Kontozugriffe oder Rechenleistungen.

## Material und Prüfmethode

Im angegebenen Ordner liegen zehn MP4-Dateien. Fünf davon sind die Avatar-Clips: ursprüngliche Referenz plus genau vier Ergänzungen. Alle Dateien wurden vollständig heruntergeladen, mit SHA-256 erfasst und mit ffprobe geprüft. Private Dateien, Drive-Kennungen und Downloadadressen werden nicht ins Repository kopiert.

Dateigrößen, vollständige Hashes und Medienströme: `avatar-alive-20261104/reference-media-inventory.json`.

Die fünf Avatar-Clips wurden über ihre gesamte Bilddauer im Abstand von 0,5 Sekunden extrahiert, mit Zeitmarken versehen und auf acht Kontaktbögen betrachtet. Das ist eine visuelle Stichprobenprüfung mit 160 Bildern, keine lückenlose Bild-für-Bild- oder Tonabnahme. Alle fünf enthalten eine Audiospur. Deren hörbarer Inhalt und eine genaue Phonemzuordnung sind hier **NOT_RUN**; es wird kein gehörtes Transkript behauptet.

| Datei | Dauer | SHA-256 |
|---|---:|---|
| `gemini_generated_video_33fd6ada.mp4` | 10,027 s | `a9c525346b200c2aa062ff9d2c7495f8492fbaa9016a4cf125364dda77b35893` |
| `gemini_generated_video_f6e57d6b.mp4` | 20,032 s | `c73ce6cf204fe2c29cc572da80514c7a2c8657a411c049255a476647bfe7c878` |
| `gemini_generated_video_3d63b568.mp4` | 20,032 s | `7bd81c97a8c6d1a8e84b776461bbb454dcb4368777c6ecf97a90c1be7d10ea1d` |
| `gemini_generated_video_837e419a.mp4` | 20,032 s | `90467376589d1773d81f5060e106a8b6ac930291950c8090856ed21f921ffbd1` |
| `gemini_generated_video_000ea66a.mp4` | 10,027 s | `be5e561913a794ad3c17384f39eecc69ecb09e086a3b77692d47a87994408e5a` |

Alle fünf: 1280 × 720, 24 Bilder/s, AAC 48 kHz Stereo. Zeitabschnitte unten sind beobachtete ungefähre Bereiche innerhalb der Stichproben, keine aus dem Ton ermittelten Synchronisationsmarken.

## Sichtbare Abläufe

| Referenz / Zeit | Beobachtung | Anforderung an die Anwendung |
|---|---|---|
| `33fd6ada`, 0–2,5 s | Frontaler blau-violetter Kopf mit Schultern; Blinzeln, wechselnde Mundform, Lichtkontur und orbitale Ringe | Bestehenden Kopf beleben; Blick, Lider und Mund über echte Zustände und Audiowiedergabe steuern |
| `33fd6ada`, etwa 3–5,5 s | Rückzug ins Cockpit, Seitenleiste, Arbeits- und Systemkarten, sechs Aktionen; Dokumentflug zum Kopf | Eine zusammenhängende Startsequenz mit bedienbaren Steuerelementen und tatsächlichem Arbeitsstatus |
| `33fd6ada`, etwa 6–7 s | Gehirn-/Orb-Nahansicht, Dokumentströme, Wellenform | Bestehenden Orb mit echten Speicher-/Abrufereignissen verbinden; Wellenform an Audio binden |
| `33fd6ada`, etwa 7,5–10 s | Wechsel zur Handyansicht und zurück; Kopf bleibt Bestandteil der Oberfläche | Identität und laufende Sprachverbindung bei Navigation/Formfaktorwechsel erhalten |
| `f6e57d6b`, 10–12,5 s | Lichtübergang; Telefon mit Dateien/Ordnern | Navigation und Dokumenteingang sichtbar verbinden |
| `f6e57d6b`, 13–16 s | Mehrere Geräteflächen, kleine Assistentenfiguren, Dateitunnel zum Orb | Reale Aufgaben-/Workerereignisse darstellen; keine ausgedachten Beschäftigungszustände |
| `f6e57d6b`, 16,5–20 s | Ganzkörperfigur in violetter Kugel; Verbindung zu Aufgabenflächen | Zusätzliche Körper-/Raumdarstellung: eigener offener Umfang; der derzeitige GLB ist eine Büste |
| `3d63b568`, 10–14 s | Mail-/Dokumentkarten und Optionen „Direkt antworten?“, „Ausarbeiten?“, „Juristische Studie?“, „Anrufen?“ | Echten Eingang, passende Auswahl und freigegebene Ausführung verbinden |
| `3d63b568`, 14,5–20 s | Wetter, Kalender, Ressourcenfelder, App-Wechsel, Cockpit | Datenherkunft und Aktualität anzeigen; den Kopf während Navigation erhalten |
| `837e419a`, 10–14,5 s | Gmail-, Kalender-, Ordner- und App-Symbole; Wechsel auf Handy-App-Raster | Bestehende zulässige App-/Werkzeugpfade mit sichtbarer Rückmeldung verbinden |
| `837e419a`, 15–20 s | Kopf-Nahaufnahme mit Mundbewegung, Ringen und Wellenform; Tablet mit Kontextoptionen | Audio, Kopf und Kontextaktionen im selben laufenden Ablauf darstellen |
| `000ea66a`, 0–3,5 s | Kopf zwischen App-Kacheln, Nachrichtenfläche, Handyansicht und Dokumenteingang | Konkrete Navigation und tatsächlichen Eingang statt dekorativer Ergebnisbehauptungen |
| `000ea66a`, 4–6,5 s | Wetter-/Kalenderkontext und aktive Arbeitsflächen | Nur angeschlossene Quellen und wirklich laufende Vorgänge anzeigen |
| `000ea66a`, 6,5–10 s | Dokument-/Ordnertunnel, Gehirn-Nahaufnahme, Abschluss im Cockpit | Durchgängige Ereignisfolge zwischen Kopf, Orb und Arbeitsflächen |

Die drei 20-Sekunden-Clips wiederholen im ersten Abschnitt weitgehend die ursprüngliche Sequenz. Die Ergänzungen werden trotzdem getrennt erfasst; wiederholte Bilder sind kein zusätzlicher Implementierungsnachweis.

## Abgleich mit dem aktuellen Arbeitszweig

| Bereich | Tatsächliche Quelle / Verbindung | Befund |
|---|---|---|
| Bestätigter Kopf | `AvatarGlbContract`, `FilamentAvatarRenderer`, gepinntes GLB `c8b88385…b9a` | Eingebunden; Kopf-/Körpergeometrie und 21 Ziele erhalten. Native Pixelabnahme offen |
| Leben im Ruhezustand | `AvatarIdleMotion`, `AvatarMorphMapper`, `AvatarDirector` | Blinzeln, kleine Blick-/Kopfbewegung, kamerabezogener Blick und dezentes Lächeln hostseitig geprüft |
| Sprechen / STOP | `AgentActivity` → `TtsHologramSignal` → Director → Reducer → Mapper | Vorhanden. PCM-Stille und fehlende PCM-Daten nun getrennt; vier nachgewiesene Stillefehler behoben. Hörbare Geräteabnahme offen |
| Exakte Phonemsynchronität | `GermanVisemeApproximation` und Android-Wortbereichsrückmeldungen | Derzeit wortbasierte Annäherung; keine nachgewiesen genaue Phonemzeitachse. Nicht als exakt videoidentisch ausgeben |
| Kopf bleibt verbunden | `ContinuumHomeView`, `JarvisHoloAvatar`, `JarvisAvatarSlot` | Eine bestehende Oberfläche/Director-Verbindung. Reproduzierter Fehler beim Überlappen alter/neuer Activity-Instanzen wird durch Eigentümerprüfung vor dem Andocken und beim Aufräumen korrigiert; aktuelle Regression siehe Handoff |
| Start und Kopf-Effekte | `CockpitTimeline`, `CockpitAnimationPresenter` → `JarvisHoloAvatar` → bestehende `ContinuumHologramView` | `CockpitEntered` und bestätigtes `DocumentLearned` lösen nun begrenzte Symbole über der Kopfebene aus. Kein zweiter Animationszyklus. Tiefe/Verkleinerung der vollständigen Startsequenz bleiben offen |
| Begrüßung | Bestehende lokale TTS-Initialisierung → `CockpitTimeline.GREETING_TEXT` | Reguläre freigegebene Begrüßung nutzt denselben Text wie das Cockpit. Bestehende Bereitschafts-, Offline-Stimmen- und Vordergrundregeln bleiben erhalten; kein neuer Mikrofonstart |
| Dokumentflug / Gedächtnis | `MemoryEventMapper`, `BrainOrbAnimationRenderer`, `JarvisSequenceView` | Speicher-/Abrufereignisse haben einen Weg zur Darstellung. Allgemeiner PDF-Eingang und dessen Auswahl noch nicht angeschlossen |
| Arbeitsprozesse | `CockpitAnimationTriggers`, `ContinuumTaskEffects`, `JarvisSequenceView` | Reale Start-/Endereignisse vorhanden; neuere Referenzgestaltung separat zu prüfen |
| Antwortoptionen | `ToolGatePillActionGate` in `SafePillActionGate.kt`, `DocumentQuestion` | Gate vorhanden, aber kein allgemeiner Produktionsaufrufer für Dokumenteingang. Der Overlay zeigt bisher nur eine Frage |
| Gmail / Konten | `ApprovedAccountEventSource`, `AccountEventSource` | Vertrag und Gate allein sind kein Kontozugriff. Vollständiger Anbieteranschluss und Kontoberechtigung offen |
| Wetter / Kalender | Bestehende App-/Werkzeugnavigation gesondert prüfen | Referenzkarten beweisen keine Live-Datenquelle; keine Referenzwerte übernehmen |
| Ganzkörperfigur / Geräteraum | `AvatarPresentation.FULL_BODY_ORB` ist eine Schnittstelle | Kein nachgewiesenes vollständiges Körpermodell oder entsprechender Ablauf im geprüften Avatar-Pfad |

## Weitere fünf Videos

Die übrigen Dateien wurden visuell mit 5-Sekunden-Stichproben, der 620-Sekunden-Lehrclip mit 20-Sekunden-Stichproben gesichtet. Sie ergänzen Funktionsideen; Aussagen über Preise, Anbieter, Leistungszahlen oder Modelltraining werden daraus nicht als verifizierte technische Tatsachen übernommen.

- `352eb01fc3727a42309837f9dd2b6be9.mp4`: Sprachassistent, Werkzeuge/MCP, wiederkehrende Aufgaben, Spezialisten und Morgenübersicht. Vorhandenes früheres Transkript: `docs/research/jarvis-video-20261030/JARVIS-CLAUDE-MCP-ORIGINALTRANSKRIPT-20261030.srt`.
- `3e5b5b426c8e35ec62254c2359e45100.mp4`: beworbene Modell-/Anbieteranbindung; keine Darstellung des bestätigten Avatar-Kopfes.
- `c50ff70ba64cada8e5261bd398df8f07.mp4`: sichtbares JARVIS-Dashboard, räumliche Säulen und Arbeitsfiguren. Früheres Transkript B im selben Forschungsordner.
- `eefb15d11b85648f647fb5b5c88b2240.mp4`: Installations-/Einrichtungsanleitung mit Sprachchat, Gedächtnis und PC-Steuerung. Früheres Transkript A im selben Forschungsordner.
- `f6144be7e261f6fe4e704a1f4014ed76.mp4`: Behauptungen zur gezielten Veränderung von Modellgewichten und Leistungsgewinnen. Kein Beleg einer entsprechenden Fähigkeit des aktuellen Android-Programms.

## Abnahmegrenze

Fertigmeldung erst nach quellgebundener Funktionsprüfung und tatsächlicher nativer Darstellung mit Ton. Hosttests und Blender-Posewiedergabe belegen jeweils ihren eigenen Umfang. Aktuell fehlen insbesondere native Gerätepixel, hörbare Synchronität, vollständige Start-Cues, allgemeiner Dokument-/Kontoeingang sowie die Ganzkörpersequenz. Weiterarbeit erfolgt auf dem bestehenden Arbeitszweig; das Originalprogramm wird nicht ungeprüft veröffentlicht oder auf dem Besitzergerät ersetzt.
