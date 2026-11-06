# JARVIS – technische Karte: Avatar, Szene, Gedächtnis, lokale KI (06.11.2026)

Grundsatz des Besitzers: Animation ist Teil der Architektur. Jede Funktion bekommt Bedienbarkeit, Gedächtnisanschluss
und holographische Darstellung zugleich; nie eine abgeschlossene Aktion zeigen, die noch läuft oder fehlschlug.
Status: alles HOST; Fold7/One UI 9 = REAL_DEVICE_NOT_RUN.

## Bestehender Code → Schwäche → Änderung → Test → Artefakt

| Bereich (tatsächlicher Code) | Schwäche | Änderung | Test | Stand |
|---|---|---|---|---|
| Avatar-Absichten: `AvatarDirector` → `AvatarPoseReducer` → `AvatarMorphMapper` (Augen führen, Hals 65 %, Sprechakzente, Frage-Brauen/Satzende-Nicken) | „Erinnern“ hatte keinen eigenen Ausdruck | Verstandene Korrektur → `confirmationAccepted` (CONFIRM_NOD); Fensterblick über `windowOpened` | `ContinuumDocumentSceneUiTest.rememberingAndCorrecting…` | umgesetzt (4059f076) |
| Cockpit-Ereignisbus `CockpitAnimationBus` (`RecallHits`, `MemoryStored`, Werkzeugszenen) | Arbeitsjournal speiste ihn nicht | Rückbezug sendet `RecallHits` mit echter Trefferzahl | derselbe Test (Anzahl 1) | umgesetzt |
| Sprache: `UtteranceProgressListener` → `speechStarted/wordRange/speechEnded`; zurückgezogene Äußerungen weisen späte Wörter ab | – (Generation über Äußerungs-ID vorhanden) | STOP in Kombination mit Kamera/Fenster geprüft | `speakingInsideAnOpenWindowKeepsTheCameraAndStopEndsBothAtOnce` | vorhanden + geprüft (de9d249c) |
| Frame-Takt: `JarvisShellView.observeMotionFrames` (Deck, Fenster, Lichtbahn) und Avatar-Uhr in `ContinuumHologramView` (Kamerafahrt, Kugel) | zwei Abonnement-Quellen (beide Choreographer, keine freien Timer); kein zentraler SceneController | offen: gemeinsamer Szenenzustand (IDLE … RETURNING/STOPPED) über beide Uhren | – | **offen** |
| Fenster: `JarvisWorkWindowHost` (Plan/Neuplanung), `JarvisWorkWindow`, `JarvisDeckClearance` | – | Neuplanung beim ersten Layout in anderer Größe; Deck räumt/kehrt zurück | Lifecycle 4/4, Status 10/10 | umgesetzt (5162de02) |
| App-Szene: `ToolAppIdentity` (PackageManager, gleiche Regel wie der Start) | Symbol ist ein flaches Emblem auf der Lichtbahn, kein Kristall | offen: Kristall/Prisma aus dem echten Symbol | – | **offen** |
| Dokument: `PdfDocumentIntake` → Dokumentfenster → Entwurf (`DocumentDraftPrompt`, `runLocal`) | Entwurf ersetzt das Dokumentfenster (keine zwei Ebenen); Passagen werden nicht verarbeitungsgebunden hervorgehoben | offen | – | **offen** |
| Gedächtnis: `CockpitWorkJournal`, `WorkRecall`, `OwnerCorrectionIntent`; Fakten bleiben in `ContinuumTemporalMemory` | Projekte/Entscheidungen/überholte Angaben nicht angebunden | offen: Anbindung an das vorhandene Zeitgedächtnis | – | Journal umgesetzt, Rest **offen** |
| Wärme/Last: `JarvisRenderQuality` (Wärmestatus → `reducedBloom`), `LocalModelThermalGuard` | keine Headroom-Vorhersage, kein JankStats | offen: Headroom (API 30+) mit am Gerät kalibrierten Schwellen | – | **offen, Gerät nötig** |
| Faltgerät: Activity verarbeitet `screenSize/orientation/screenLayout` selbst; Fenster planen bei Größenwechsel neu | kein `WindowInfoTracker`/Faltposition | offen | Falt-/Dreh-Test (Host) | teilweise |
| Lokale KI: `LocalModelRuntime` (LiteRT) über `runLocal` | ein einziger Pfad, keine Adapterschicht | offen: `LocalReasoner`-Adapter (vorhandenes Modell / Gemini Nano), ToolGate bleibt Autorität | – | **offen** |

## Geprüfte Aussagen des eingefügten Architekturdokuments

| Aussage | Prüfung | Quelle |
|---|---|---|
| Summarization-API ohne Deutsch (EN/JA/KO) | bestätigt (Seite älteren Datums; Beta) | [Summarization API](https://developers.google.com/ml-kit/genai/summarization/android), [Release Notes](https://developers.google.com/ml-kit/release-notes) |
| Rewriting-API mit Deutsch | bestätigt | [Rewriting API](https://developers.google.com/ml-kit/genai/rewriting/android) |
| Fold7 für Gemini Nano Direct Access (Prompt API) | bestätigt für Text/Bild; Nano-Version auf dem Fold7 nicht belegt | [ML Kit GenAI Overview](https://developers.google.com/ml-kit/genai), [Get started](https://developers.google.com/ml-kit/genai/prompt/android/get-started) |
| Nur im Vordergrund (`BACKGROUND_USE_BLOCKED`), Status zuerst prüfen, nicht bei offenem Bootloader | bestätigt | [ML Kit GenAI Overview](https://developers.google.com/ml-kit/genai), [Get started](https://developers.google.com/ml-kit/genai/prompt/android/get-started) |
| Samsung AI SDK nur mit Partnerantrag + App-Registrierung | **nicht bestätigt** – keine Samsung-Primärquelle gefunden | – |

Folgerung: Für deutsche Entwürfe ist nicht die Summarization-API, sondern Direct Access (Prompt API) der Testpfad –
nur im Vordergrund, nach Statusprüfung, mit Qualitätsmessung auf dem Fold7. Hintergrund-Autonomie darf nicht darauf bauen.
