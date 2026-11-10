# Continuum Ultimate — kanonische Integration

Stand: 10.11.2026. Verantwortlich: root / [PR #740](https://github.com/Ullmann27/device-ai/pull/740).
Branch: `integration/continuum-ultimate-system-20261110`.
Status: **SOURCE_INTEGRATED; gemeinsame Android-Gesamtabnahme noch ausstehend**.
Keine APK-, Fold7- oder Release-Freigabe aus Teilprüfungen ableiten.

## Quellen und Entscheidungen

Cinema-Basis: #715 `c972f56ccc4caa2f1807bcaf1d4665347c66c629`.
Kontrolliert übernommen: #725 `b0a1635`, #727 `ff27033`, #723 `6d515bc`,
#735 bis `1223dc8`, #739 bis `0e8f6c7`. Die eingeschlossenen Vorläufer und getrennten
Experimente stehen mit vollständigen SHA-Werten in
`docs/integration/ULTIMATE_SYSTEM_INTAKE_20261110.json`.
Donor-PRs bleiben offen. Nur dieser Integrator verändert diesen gemeinsamen Branch.

#717/#719 bleiben Forschung; #718/#721/#728 erhalten keine Produktivförderung.
CTX3 bleibt am bestehenden, ausdrücklich eingeschalteten Shadow-Pfad.
Keine Modellgewichte, Originalavatar-Assets, Paketkonfiguration oder Nutzerdateien
wurden durch diese Integration ersetzt. Kein neues Gedächtnis, kein zusätzlicher
Animationsbus, kein zweiter Werkzeugexecutor.

Die alten vollständigen Metadatenkataloge und bisherigen Einzelaudits bleiben
verfügbar. Ein alter, nicht einzeln belegter Eintrag bleibt `AUDIT_REQUIRED`.
Dieser Stand behauptet keine erneute vollständige Quellprüfung aller historischen
PRs und keine pauschale Verbindung jeder früheren Versuchsklasse.

## Tatsächliche Aufrufketten

| Bereich | Verbundener bestehender Laufzeitpfad | Grenze |
| --- | --- | --- |
| Gespräch | `sendCurrentInput` → `LocalConversationContext`/Follow-up-Aufbereitung → `runLocal` → vorhandener Modell-/Agentenpfad | Reales Gemma und freies Verständnis am Fold7 noch nicht geprüft |
| Erinnern | `ContinuumLearningCore`, zeitbewusster Spezialist, bestehender Abruf und Widerruf | Beobachtungen und Modellantworten sind keine Besitzerfakten |
| Lernen | `BoundedAgent` → bewertete Episoden → `experienceStore` → `checkpointContinuumAsync`/Archiv → `AgentExperienceRecall` | Historische Hinweise bleiben unbestätigt; keine automatische Skill-/Gewichtsförderung |
| Diagnose | `sendCurrentInput` → `isDiagnosisRequest` → `dispatchSystemTool` → `createToolGate` → `DeviceToolExecutor` → Android-APIs | Kein Modell und kein Internet für diese Messung nötig |
| Ergebnis | Zentraler Werkzeugbeleg → aktueller Turn → begrenzter Gesprächskontext → `addAssistantMessage`/vorhandene Stimme → Systemansicht | Zustellung ist kein Nachweis einer Gerätebeschleunigung |
| Grafik | Bestehender `TurnLifecycle` → `CockpitAnimationTriggers`/Bus → Cinema/Arbeitsfenster; `AvatarDirector` koordiniert Lesen und Sprechen | Host-Canvas ist kein Filament-/GPU-Gerätebeleg |
| Dokumente/Web | Vorhandene PDF-Auswahl/Dokumententwurf und `runResearch` mit auftragsgebundener Internetfreigabe | Live-Web/SAF/Gemma auf Zielgerät offen |
| Abbruch | STOP/Pause → bestehende Abbruchtoken, Generationen und Gates → veraltete Rückmeldungen verworfen | Bereits gestartete externe Shizuku-Wirkung kann ungewiss bleiben |

## Reparierte Verbindungen

- Der ausdrücklich formulierte Auftrag „Continuum, schau dir bitte an, warum mein
  Handy so langsam geworden ist …“ erreicht die lokale Diagnose. Er erteilt keine
  Bereinigungsfreigabe. Werkzeugstart und Abschluss tragen dieselbe Turn-ID;
  tatsächlicher Start aktualisiert auch den bestehenden Lebenszyklus.
- Der Bericht gelangt in denselben Gesprächsverlauf und den bestehenden Antwort- /
  Sprachpfad. Folgerunden sehen eine begrenzte, datierte Werkzeugbeobachtung mit
  ausdrücklichem Hinweis, dass dies weder eine neue Messung noch eine Freigabe ist.
- Abgelehnte oder ungültig gewordene Recherchefreigaben erklären die Nichtausführung
  genau einmal im aktuellen Dialog. Ersetzte Anfragen und Sitzungen übernehmen keine alte Rückmeldung.
- Ursprüngliche Rechercheaufträge erscheinen vor der Rückfrage. Ein erledigtes
  Freigabeticket verwirft kein noch laufendes Diktat; ersetzte Tickets bleiben gesperrt.
- Ergebniszustellung und verifizierter Handlungserfolg sind getrennt. Alte
  Erfahrungen werden vor der Kandidatenbegrenzung nach ihrem Zeitfenster gefiltert.
- Fensterfarbe fällt bei fehlender Projektionsfarbe auf die tatsächliche
  Arbeitsfensterfarbe zurück. Echte Dokumentarbeit hat Vorrang vor flüchtigen
  Fensterblicken; der vorhandene Zeitablauf beendet auch verlorene Leselizenzen.
- STOP beendet Wirkung und Sprache sofort. Der bereits entworfene Fensterrückflug
  bleibt erhalten. UI-Tests verlangen Beginn des Schließens, begrenztes Ende und
  Schutz vor verspätetem Wiederöffnen.
- Gerätewerkzeuge bleiben lokal; der Cloudpfad erlaubt nur die vorhandenen
  freigabegebundenen Webwerkzeuge. Keine automatische Geräte-Metadatenbeigabe.
- Eigene Diagnoseberichte: Bereinigung nur nach genauer Vorschau und Freigabe,
  Rücknahme mit Identitäts-/Hashprüfung. Indexfehler verlieren den Rücknahmestand
  nicht; abgelaufene Rücknahme sperrt neue Vorgänge nicht unbegrenzt.
- Speicherprüfung reserviert das EOF-Prüfbyte. Freier Speicher gilt nicht als
  Leistungsnachweis. Erfassung berücksichtigt tatsächliches Laden, zufällige
  Android-App-Pfade, verlorene Frame-Grenzen und bereits bestehende Ausgabepfade.
- Native Capture prüft auch unversionierte Eingabedateien. Historische PNG-Belege
  ohne eigenen Quellbeleg sind entsprechend gekennzeichnet.

## Diagnoseumfang

Gemessen werden verfügbare normale Android-Werte, darunter Speicher, RAM-Druck,
eigener Prozess-PSS/CPU, Akku und unterstützte Thermik. Fehlende Werte bleiben
unbekannt. Eigene CPU-Last ist keine Gesamtgeräte- oder Fremd-App-Zuordnung.
Dateianalyse beschränkt sich auf ausdrücklich ausgewählte lokale Dokumente.
Bereinigung entfernt ausschließlich eigene regenerierbare Diagnoseberichte;
keine Nutzerdateien, Modelle, APKs oder fremden App-Daten. Root bleibt deaktiviert.
Ein komplettes autonomes Android-Tuning, freie App-Eingaben und Nachrichtenversand
werden durch diesen Stand nicht als angeschlossen oder freigegeben behauptet.

## Prüfstände vor der gemeinsamen Android-Abnahme

- Kontext-Hostsuite: 112 bestanden; kein echter Modelllauf.
- Backend: Syntaxprüfung und 64 Tests bestanden.
- Python-Erfassung: 63 Tests bestanden.
- Leistungsauswertung: 25 JVM-Tests bestanden.
- Diagnose-/Cloud-Dispatch-Vertrag: 7 JVM-Tests bestanden.
- Speicher/Rücknahme: zuletzt 42 JVM-Tests bestanden. Neue Ablaufkontrolle zuerst
  rot, nach Reparatur grün; Dateien bleiben erhalten.
- Avatar-Leseblick: 7 JVM-Tests, davon zwei neue Fehler zunächst reproduziert,
  danach alle grün. Kein nativer 3D-Renderbeleg.
- Vollständiger Node-Lauf auf Checkpoint `4aaea17`/lokal `3f5b37d`:
  2.455 bestanden, 37 übersprungen, null Fehler. Nachfolgende Quelländerungen
  benötigen die erneute Gesamtabnahme. Der vorherige Lauf hatte genau einen
  veralteten Beleuchtungs-Quellvertrag; dieser wurde gezielt repariert.

Kompakte rote/grüne Belege: `docs/evidence/ultimate-system-20261110/`.
Der fremde Cinema-Gesamtlauf ist keine Abnahme dieses gemeinsamen Quellstands.
Die vorhandene gemeinsame Gradle-Sperre wird respektiert; keine fremden Builds
oder Arbeitsstände werden abgebrochen oder verändert.

## Letzter kombinierter Prüfstand

Checkpoint `3aa0b461` (lokal `6cd3522d`, gleicher Git-Baum): vollständige Kotlin-
und Testkompilierung bestanden; Node 2.455 bestanden, 37 übersprungen, null Fehler.
Gezielter Android-/UI-Lauf: 156 Tests, ein Fehler im neuen Diagnose-UI-Test. Dieser
las den Turn vor Ende der asynchronen Namenszuordnung. Die korrigierte Prüfung
wartet auf den regulären Lookup und Werkzeugabschluss; sie umgeht keinen Router.
Checkpoint `ab9d159c`: Node erneut vollständig grün (2.456 bestanden, 37
übersprungen); natürlicher Diagnoseauftrag grün. Der gezielte Android-Lauf hatte
78 bestandene Tests und drei Fehler in den übernommenen STOP-Tests: ein Zugriff
auf die Lazy-Property statt deren Delegate und zwei Prüfungen vor dem terminalen
Kameraframe. Der Delegate-Zugriff ist korrigiert; maximal acht zusätzliche 16-ms-
Frames sind erlaubt, die Ruheposition bleibt exakt null. Kein Produktionscode
oder Animationsablauf wurde dafür verändert. Gegenprüfung/Gesamtabnahme folgen.

## Verbindlicher Abschluss

Auf sauberem, gepushtem Quellstand: komplette Kotlin-/Testkompilierung,
JVM/Robolectric einschließlich UI, Node, Backend, Python und Android Lint prüfen.
Danach ausschließlich den bestehenden unsignierten ARM64-Bauweg verwenden:
`deviceAiMentorPreview=true`, `continuumLocalRenderUnsigned=true`,
`continuumStandaloneVersionCode=53`, `continuumOwnerArm64=true`, `:app:assembleRelease`.
Erwartung: `at.heinz.deviceai.continuumlocal`, VersionCode 53 / VersionName 0.3.52.

Physischer Fold7, echtes Gemma, Mikrofon/TTS, Filament-GPU, Geräte-Thermik:
**NOT_RUN**. Main-Merge, Originalsignierung, Installation und Veröffentlichung
bleiben ohne ausdrückliche Besitzerfreigabe gesperrt. Rücknahme von Quelländerungen
nur über nachvollziehbare Revert-Commits, keine Force-Pushes oder Branch-Löschungen.
