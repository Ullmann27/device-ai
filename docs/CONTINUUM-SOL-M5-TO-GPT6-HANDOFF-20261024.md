# Continuum MASTER – Sol M5 an GPT-6 und Claude, 24.10.2026

**Ergebnis:** sichere zusammenhängende Kette Gespräch → Rückfrage → kanonischer M4-Plan → erneute Besitzerkontrolle
im vorhandenen Builder implementiert und mit echtem SDK getestet. Kontext-/Freigabe-/Recreation-Grenzen verbessert.
**CTX3 besteht den unabhängigen Sprachvergleich nicht und wird nicht als sichtbarer Antwortkern aktiviert.**
Keine Besitzer-APK, Signierung, Installation, Gewichtsänderung oder Main-Merge.

## 1. Git-Identität, Sicherungen und Einstieg

- Start: **331e2d25c655b07e578ce4fa49da64fbc4f97c61**, erneut per Remote geprüft, Claude-Zweig `opus/continuum-next-20261023`.
- `git merge-base --is-ancestor e4fd67c… <Start>` erfolgreich; genau zehn zusätzliche Claude-Commits,
  keine fehlenden Sol-M4-Commits. Kein erneutes blindes Mergen von PR99/106/107/108.
- Eigener Zweig: **sol/continuum-m5-context-coding-experiments-20261024**, bestehender isolierter Worktree weitergenutzt.
- **Endcommit der Implementierung: fc1d996135d98165a7102d0a4e67b9ff34a0b1f0**.
- **Gesicherter Entwicklungs-/Messabschluss: 2c5bf901083e88c4e566d7ee9c78e8bd4a1c7a2c**.
  Danach folgen nur Übergabe-/Dokumentationskorrekturen. Eigene Identität dieser Übergabe:
  `git log -1 --format=%H -- docs/CONTINUUM-SOL-M5-TO-GPT6-HANDOFF-20261024.md`.
- **[Draft-PR109](https://github.com/Ullmann27/device-ai/pull/109)** gegen Claudes Reviewbasis; kein automatischer Merge.
- Alle funktionsfähigen Schritte, Freeze und Messarchive wurden gepusht. Kein verschlossener Quellbestand,
  keine verschlüsselten Daten oder sitzungsabhängigen Zugangsmechanismen.

Commitgliederung:03f0b4 Entwurf →44c4d74 CTX2-Fix →f8b8577 Shadow/Projektplan →5bf8a6d Review-/Lifecycle-Fixes
→63ea918 Gesprächsisolation →9a30327 Diagnose/Runner →fc1d996 zurückgestellte/indirekte Anforderungen sperren.
Der endgültige Freeze wurde **vor dem ersten TEST3-Lauf** in363475a gespeichert; der frühere Freeze9a30327
blieb unbenutzt und ist als ersetzt archiviert. Das ist keine Optimierung nach Kenntnis des TEST3-Ergebnisses.

## 2. Status jeder wesentlichen Fähigkeit

| Fähigkeit | Tatsächlicher Status |
|---|---|
| CTX2 vollständig negierte Referenten | implementiert und getestet, weiterhin Forschungsquellset |
| CTX1 default | Verhalten unverändert, bestehende Tests erhalten; kein Retten alter Benchmarks durch Umbau |
| CTX3 Quell-/Referenzgate | implementiert und getestet für begrenzte Protokolle; **Sprachhypothese auf TEST3 verworfen** |
| Shadow im echten LearningCore/Chatpfad | angebunden, OFF voreingestellt, nur explizit SHADOW, sichtbare Antwort unverändert |
| Gesprächs-ID/Namespace/Widerruf/Parallelität | implementiert und JVM-/SDK-getestet; kein fremder Shadow-Fokus in neuer Unterhaltung |
| M5-Anforderungsdialog | implementiert/getestet, zwei bestehende Vorlagen, begrenzte Schlüsselwort-/Slotlogik |
| Vorschau, Revision, Planhash, Builder-Übergabe | implementiert/getestet einschließlich Robolectric; kein automatisches Erzeugen/Bauen |
| Activity-Recreation/OS-SavedState | simuliert mit echten Android-Widgets; Angaben/Entwurf wiederherstellbar, Freigaben niemals |
| Prozessneustart | Shadow-Fokus in zwei getrennten JVMs geprüft; Android-Prozessabnahme fehlt |
| Android-Gerät/Fold7/ARM64-Termux | in dieser Runde **nicht nachgewiesen** |
| Freie App-Generierung/österreichisches allgemeines Sprachverständnis | nicht nachgewiesen; kein neues Modell dafür trainiert |
| P2 Intent-Modell/Synonym-Generalisation | neue unabhängige Vergleichsstudie nicht ausgeführt; nächster abgegrenzter Auftrag |
| 30-Sekunden-Vordergrundüben/Modelllernen | nicht implementiert; vorhandene Scheduler unverändert |

Die neue Laborseite ist als Forschung bezeichnet und vom vorhandenen Builder erreichbar. Auf dem bereits
installierten Besitzerpaket erscheinen diese Änderungen erst nach einem gesondert geprüften/autorisierten Update.
Keine quellenintegrierte Schaltfläche wird als bereits auf Fold7 beobachtet bezeichnet.

## 3. Dateien und modulare Übernahme

Vollständiger maschinenlesbarer Dateikatalog: [changed-files.txt](evidence/sol-m5-20261024/changed-files.txt).
Die wesentlichen Produkt-/Testschnittstellen:

- `learning/context/Ctx3Context.kt`: typisierte Entitäten/Aliase/Quellen-IDs, Kandidatenmengen,
  Mehrdeutigkeit/Negation und Quellenrevision. `Ctx3EntityIndex` ist ein konservativer Oberflächenleser, kein allgemeiner NER.
- `learning/context/Ctx3Shadow.kt`: opt-in Vergleich, begrenzte getrennte Sitzungen und Metadatenberichte.
- `ContinuumLearningCore.kt`, `ContinuumCognitiveRuntime.kt`, `AgentActivity.kt`: bestehender Chatpfad,
  ausdrückliche Gesprächs-ID und Sitzungstrennung; keine Layoutänderung am Premium-Dashboard.
- `ContinuumCodingConversation.kt`: reiner begrenzter Anforderungszustand/Vorschau, einmalige Revision-/Hashkontrolle.
- `ContinuumM5LabActivity.kt`, `ContinuumMobileBuilderActivity.kt`, Manifest: Labor, persistierbare unbestätigte
  Angaben, Shadow-Schalter, verständliche Vorschau und bestehender Builder mit geprüfter Projektwahl.
- `Ctx2NegationTest`, `Ctx3ContextTest`, `Ctx3ShadowTest`, `Ctx3SessionTest`,
  `ContinuumCodingConversationTest`, `ContinuumM5UiTest`: Unit-, Integrations-, Negativ- und Widgetfälle.
- `Ctx1DialogueContext.kt`: nur generalized=true repariert; `Ctx3Experiment`/`Ctx3Performance`: testseitige Runner.
- `android-integration-wiring.test.mjs`: bestehender Quellenvertrag prüft die neue tatsächliche Gesprächs-ID-Signatur.
- `scripts/continuum-test-classpath.init.gradle`, `continuum-m5-research-checks.sh`: öffentliche portable
  SDK-/JVM-Reproduktion, keine privaten Sessionpfade erforderlich.

CTX2-Reparatur lässt sich separat übernehmen. Der reine M5-Planer benötigt nur die vorhandene M4-Enum/Planhashes.
Shadow ist abschaltbar und ohne Antwortpromotion. Die Laborseite zeigt sowohl Plan- als auch Shadow-Bedienelemente;
wer nur den Planer übernimmt, muss deren Shadow-UI-Verweise entfernen oder die OFF-Komponente mit übernehmen.
Kein anderer Repairworker, kein zweites Gedächtnis und keine zweite privilegierte Shellpipeline eingeführt.

## 4. CTX2: reproduzierter Absturz und semantischer Fix

Isolierter neuer Test zuerst4 Fälle/3 Fehler: leeres `wanted.first()`, „nicht ihn“ fälschlich als positiver
Rückbezug und mehrere Ersatznamen fälschlich als sicherer Treffer. Danach CTX1-/CTX2-Tests 18/18 grün.

Die bekannte ursprüngliche CTX1-TEST-Regression wurde zusätzlich wiederholt: B1 weiterhin920 richtig/0 falsch,80 Rückfragen/80 Enthaltungen; B0 weiterhin631 richtig/433 falsch. Originalberichte bleiben unverändert; dies ist kein neuer Blindnachweis.

Nur generalized=true erhält die Schutzlogik: verneinte Namen werden nicht positiv salient. Eine Korrektur
mit keinem/einem mehrdeutigen Ersatz invalidiert den vorherigen Frage-/Entitätsbezug und verlangt Rückfrage.
Ein einzelner bekannter positiver Ersatz darf die alte Frage übernehmen; ein unbekannter darf keine bekannten
Fakten borgen. „Nicht Konrad, sondern Paul“ bleibt von „Nicht Konrad“ und „Nein, nicht ihn“ unterschieden.

Der ursprüngliche CTX2-Gesamtrunner stürzt auf dem geöffneten TEST2 bereits im **unveränderten B1** ab.
Deshalb wurde nicht einfach behauptet, der gesamte alte Runner sei geheilt. B1 wurde nicht verändert.
Repariertes B2 wurde separat auf demselben geöffneten TEST2 gemessen:
**885 richtig,27 falsch,0 verpasst,8 unnötige Rückfragen,80 nötige Rückfragen,80 Enthaltungen**.
Dies ist Regression/Diagnose, **keine frische Blindvalidierung**. Originale Forschungsberichte bleiben bytegleich.

Alle27 falschen Fälle wurden unabhängig exakt rekonstruiert:
13 SWITCH: fehlender Gegenstand im Register lässt alten Personenfokus stehen;
14 RETURN: zu breit zugeordnetes Tiergeschlecht lässt falsches Pronomen passen.
Keine Zeitauflösungsursache; alle falschen Belege waren im aktuellen Faktenpool, aber dem falschen Referenten
zugeordnet. Keine eigenständige Widerrufsverletzung aus diesem statischen Korpus ableiten.
[Taxonomie mit allen Fällen](research/CTX2-OPEN-ERROR-TAXONOMY-20261024.md), Rohbericht und ausführbare Diagnose im Repo.

## 5. Kontextadapter und neue Gesprächsisolation

CTX3 arbeitet auf expliziten `Ctx3Entity`-Werten: stabile ID, Label/Aliase, grammatische Referenzen und aktuelle
Quellen-IDs. Der Gate hält höchstens acht Fokusentitäten. Der Oberflächenleser unterstützt nur einen Teil
freier Faktgrammatik; das ist der wesentliche Reichweitenengpass, nicht durch neue Regex-Sonderfälle am TEST3 kaschiert.

Wichtige Gegenbeispiele wurden im unabhängigen Code-Review reproduziert und RED→GREEN abgesichert:
unknown Gustav durfte Max-Belege erhalten; „Nicht die …“/nachgestelltes „meine ich nicht“ blieb positiv;
Aliasmehrdeutigkeit ließ vorherigen Fokus bestehen. Diese Pfade fragen jetzt nach bzw. enthalten sich.
Eine offene Personenfrage darf nicht durch Registry-Reihenfolge entschieden werden.

Der Nachtrag belegte zusätzlich eine reale Sitzungsvermischung: zwei Core-Instanzen, gleiches Verzeichnis,
zweite Unterhaltung mit „er“ erhielt QUERY statt CLARIFY. Jetzt Schlüssel **kanonischer Namespace + Gesprächs-ID**.
Validierte IDs1…80 ASCII-Zeichen,16 Sitzungen insgesamt,32 Metadatenereignisse je Sitzung,15 Minuten
Inaktivitätsablauf bei nächster Auswertung; expliziter Reset. Profilverzeichnisse sind getrennte Namespaces;
eine echte Mehrbenutzerprofilfunktion wurde nicht erfunden.

`AgentActivity.sessionId` wird durch `ContinuumCognitiveRuntime` explizit übergeben und über Activity-SavedState
behalten. „Neue Sitzung“ verwirft den alten Shadow-Kontext. Direkte neue Core-Instanzen haben eigene Default-IDs.
Jeder Turn bindet aktuelle bestätigte USER_FACT/USER_CONFIRMED-Quellen; Quellen-/Inhaltsänderung oder Widerruf
invalidiert den Fokus. Keine Dialogaussage wird dadurch dauerhafte Besitzererinnerung.

Berichte enthalten Inputhash, Gesprächshash, Modellhash, Quellenrevision, Gründe und höchstens drei Baseline-/Vorschlags-IDs,
keine Rohtexte. Der Vergleich ersetzt nie die sichtbare Antwort und ruft keine Werkzeuge oder Modelltrainer auf.
Unaufgelöstes Retrieval ohne expliziten Scope heißt `UNRESOLVED_RETRIEVAL_SCOPE`, nicht sichere Referenz.
Die historischen Metadaten der eigenen App dürfen zur Diagnose eingesehen werden; sie werden nie als Fokus einer
anderen Sitzung verwendet. IDs sind Korrelation in vertrauenswürdigem Appcode, keine zusätzliche OS-Autorisierungsgrenze.

Wichtig: Die bisherige Baselineantwort und ihre vorhandene Dialogue-History-Architektur bleiben erhalten.
Die neue Isolation beweist die Trennung des **Shadow-Kontexts**, nicht fehlerfreies Kontextverhalten des alten B0.
Die nachgewiesenen B0-Sprachfehler sind durch einen ausgeschalteten Forschungsadapter nicht behoben.

## 6. M5-Auftragsdialog, Wiederherstellung und Freigabe

Projektart Notizen/Zähler und lokale Speicherung sind getrennte Slots. Fehlendes führt zu Rückfrage;
Abhaken/Löschen/Sync/Todo, widersprüchliche Rechte, Verneinungen, hypothetische oder indirekte Dokumenttexte
werden nicht heimlich in einen passenden Plan umgedeutet. „Noch nicht bauen“ invalidiert auch eine bereits fertige
Vorschau. Unsichere Fragen werden nicht als bestätigte Anforderung gespeichert. Keine allgemeine Intent-Erkennung behauptet.

Vorschau trennt explizite Angaben von Vorlagenannahmen, zeigt Paket/Revision/Planhash. Der vorhandene Katalog wird
mit StrictJson/canonical SHA unabhängig gelesen. Eine veraltete oder bereits verbrauchte Freigabe wird abgewiesen.
Ein alter Dialog kann weder eine neue Conversation-Instanz noch eine zerstörte Activity freigeben.

Nach Kontrolle öffnet ein fester Intent nur den existierenden Builder; er prüft Enumkennung/Planhash erneut.
Kein Modell-/Benutzersatz wird Shellargument. Erzeugen und Bauen bleiben getrennte bestätigungspflichtige Aktionen;
Termux-Berechtigung, PendingIntent, Build-Lease und CCC1-Belege bleiben unverändert erforderlich. Wiederholtes
Öffnen eines solchen Intents ist keine Buildberechtigung. Keine automatische Installation.

Versionierter Android-SavedState hält höchstens 32 Eingaben mit je 512 Zeichen und einen 512-Zeichen-Entwurf;
Transcriptanzeige ist auf 12000 Zeichen begrenzt. Anforderungen werden rein rekonstruiert, Freigaben niemals.
Unbekannte/ungültige Stateversionen dürfen keine alte scheinbar gültige Planvorschau anzeigen. Überlauf verlangt
einen neuen Auftrag. Mit OS-SavedState können Angaben nach Recreation wieder erscheinen und verlangen erneute
Kontrolle; ohne OS-SavedState zeigt die UI ausdrücklich die notwendige Neueingabe an.

Robolectric prüft neue Activity, Entwurf/Anforderungen, alten Dialog nach Reset und ungültige SavedState-Version.
Das simuliert Android-Widgets/Lifecycle, aber keinen echten Samsung-Prozesskill. Ein separater Prozessnachweis
startete zwei JVMs mit derselben Shadow-ID/Verzeichnis: **prime QUERY, resume CLARIFY**; die statische Referenz
wurde nicht persistiert, nur der ausdrückliche ON-Schalter.

## 7. Unabhängiger TEST3: Hypothese verfehlt

Vorregistrierung, unveränderte Entscheidungsregel,23 Sourcehashes und Dataset-Identität vor Ausführung gesichert.
Frischer unabhängiger Autor schrieb24 synthetische Dialoge/198 Turns, davon sechs längere Folgen, ohne die
Implementierung/alten Korpustemplates zu lesen. Korpus-SHA256:
`e5681030efe4f83a471cc2e91dae7bf3ed577f889b0fc0aabd48cf39bfd39077`.

Der Implementierer las bis Freeze keine Inhalte/Ergebnisse. Ein anderer Agent führte nach Hashprüfung genau
**eine** finale Invocation auffc1d996 aus, Exit0, keine Wiederholung. Frühere Freeze-/Overlay-Vorbereitungen
hatten **keine** TEST3-Invocation. Korpus war unverschlüsselt öffentlich im Repo: disziplinierte Maskierung,
keine institutionell/doppelt verblindete Studie. Nach Auswertung keine Algorithmusänderungen.

| System | richtig | falsch | verpasst | unnötige Rückfrage | nötige Rückfrage | verpasste Rückfrage | Enthaltung |
|---|---:|---:|---:|---:|---:|---:|---:|
| B0 |111|77|5|0|0|1|4|
| CTX1/B1 |2|1|136|15|8|11|25|
| repariertes CTX2/B2 |84|50|15|32|8|1|8|
| CTX3 |13|3|109|31|6|13|23|

Keine Abstürze. Zusätzliche falsche Quellen:255/4/156/12. Keine zitierte Quelle außerhalb des jeweils
bestätigten Dialogpools; das ist keine Garantie korrekter Faktenzuordnung. CTX3-Median/p95:0,035/0,482ms;
maximal zwei Fokuswerte in diesem Korpus, keine Historydatei. CPU-/Speicherwerte sind Host-/Gesamt-JVM-Werte.

**Entscheidung: FAIL, keine Promotion.** Weniger falsche Antworten genügen nicht:98 richtige Erinnerungsantworten
gehen gegenüber B0 verloren. Ein zusätzlicher, ausdrücklich Posthoc genannter Einzelvergleich ergibt 26 Verbesserungen
gegen 99 Verschlechterungen,16 beidseitig richtige und57 beidseitig unrichtige Fälle. Die Abnahmeregel wurde nicht geändert.
Bessere Zahlen durch pauschales Nichtwissen sind ausgeschlossen. Kandidat nur für Untersuchung behalten, nicht als
Erfolg verkaufen. Kein aktiver Besitzer-Antwortschalter wurde angeboten.

Vollständiger Bericht enthält792 Vergleichszeilen: Eingabe, erwartete Entscheidung, Gold, tatsächlich ausgegebene
Baselineantwort, vorgeschlagene Entscheidung/Query, Quellen-IDs, Scopekennzeichnung und Laufzeit.
[Ergebnis](research/CTX3-INDEPENDENT-RESULT-20261024.md), [Rohbericht](research/ctx3-independent-test3-report-20261024.json).

## 8. Lastmessung, Daten und Grenzen der Forschung

Separater synthetischer DEV-Lasttest am echten LearningCore:0/10/50/128/256 Fakten, OFF/ON, kurze/lange Folgen,
Widerruf und neue Quellrevision, insgesamt 1150 Aufrufe. Keine Fehler oder unberechtigte Vorschlags-IDs.
Bei 256 Fakten/langer Folge:Median7,47ms OFF/9,83ms ON, p9510,47/15,07ms.
Bei 50 Fakten p95 ON25,86ms: realer Ausreißer, kein Weglassen. Heapwerte sind Allokations-/GC-beeinflusstes
Wachstum der JVM, keine feste Android-RAM-Zusage. Parallelität wird separat in Unit-Tests geprüft.
[Volle Tabelle und Messgrenzen](research/CTX3-PERFORMANCE-DEV-20261024.md).

Kein neuer TRAIN-Satz und keine Gewichtsoptimierung. Eigene Unit-/Reviewerfälle und geöffnetes TEST2 sind DEV.
TEST3 ist einmalige unabhängige Gegenprüfung, nun für zukünftige Arbeit **geöffnet**. Nachfolger brauchen einen
neuen Holdout. Vorhandene SWB1-Fixtures wurden verwendet; keine Besitzergewichte geändert. Vier vorhandene Checks
enthalten synthetische temporäre DEV-Trainingsschritte; daraus wurde kein neues M5-Lernergebnis gemacht.

TTT1/LCT1/WSC1/I1/I2 bleiben in ihren bisherigen überprüften Bereichen. Keine produktive Verbindung behauptet,
die hier nicht implementiert wurde. Selektive Enthaltung/Versionsbindung nutzen deren Sicherheitsideen, aber sind
keine neue I1/I2-Zertifizierung. Der bestehende SWB1-Ranker wird tatsächlich verwendet; lokale generative Runtime
wurde nicht neu ausgeführt oder vermessen. Kein bezahlt externer KI-Aufruf für die App eingeführt.

## 9. Echte Prüfungen und reproduzierbare Befehle

Baseline1085/1085 Node,889/889 SDK-JUnit, keine Fehler/Skips. Abschluss:
**1085/1085 Node,60/60 Backend,913/913 SDK-JUnit einschließlich12 Robolectric-Widgettests**;
vier zusätzliche Kotlin-Checks, SWB2/CRE1-Parität und Android-Lint bestanden.
`android-final4.log`: **BUILD SUCCESSFUL in 1m 49s**,40 Tasks. Keine APK- oder Geräteausführung daraus ableiten.

Zwei zwischenzeitlich rote Node-Tests verlangten textuell die alte Runtime-Signatur. Nach Prüfung der realen
Verdrahtung prüfen sie explizit die neue Gesprächs-ID/Reset,20/20 im Modul und1085/1085 insgesamt.
Weitere negative SDK-Läufe sind als solche archiviert; keine rote Entwicklungsprüfung wurde als Endstatus verschwiegen.

[Belegbundle mit vollständigen Befehlen/Rohlogs](evidence/sol-m5-20261024/README.md).
Die Hauptbefehle waren:

```sh
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 backend/test/*.test.js
gradle --offline --no-daemon --max-workers=2 --console=plain -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug
```

Node24.19.0, vollständiges JDK17.0.21+9, Kotlin2.4, Gradle8.11.1, AGP8.10.0, SDK36,
build-tools35/36 aus der vorherigen offiziellen M4-Toolchain. Keine Android-/LiteRT-Kompilierstubs.
Backend-Lockdatei unverändert, Dependencies per `npm ci --ignore-scripts --no-audit --no-fund` reproduzierbar.
Ende nutzt echten Gradle-Testclasspath; öffentliches Exportskript ersetzt die bisher temporäre Sessionhilfe.
Erster Dependency-Download benötigt Netzwerk; die dokumentierten finalen SDK-Aufgaben liefen offline.

Die beiden alten echten M4-APKs wurden erneut mit CCC1/Signer/aapt2 geprüft, unveränderte Hashes/Signatur.
Kein neuer M5-APK und keine Besitzer-APK gebaut. Main-UI-Widgettests sind Hostprüfungen, keine Fold7-Abnahme.

## 10. Sicherheits- und Releasegrenzen

- Persönliche Besitzer-App bleibt0.3.17/code18, `at.heinz.deviceai.mentorpaired`; vorbereiteter Nachfolger0.3.18/code19.
- Bestehender Besitzer-Schlüssel nicht gelesen, ersetzt oder verwendet. Keine Gewichts-/Archiv-/Chatdaten auf dem
  Telefon berührt. Neue Chat-Sitzungs-ID-Verkabelung verändert keine Premium-Layouts/Hologramm/Editor/Kategorien.
- Alte v19-Attestierung passt jetzt13/22 Dateien; neue/modifizierte Dateien brauchen ausdrücklichen Review und
  neue Freigabe vor Besitzer-Build. Gate nicht gelockert, Rollback-/Signierdienst nicht als ausgeführt behauptet.
- Worktree ist keine OS-Sandbox. Fremdtext bleibt Daten; keine Befehle aus Übungen oder Modellvorschlägen.
- Thread-/Namensraumtests beweisen keine OS-Mehrbenutzerautorisation. Große Speicherpools über256 Fakten erzeugen
  sicheren Shadow-Fehlerstatus; Baselineantwort bleibt erhalten. Echter Fold7-RAM/Akku/Thermik/Doze unbekannt.
- Activity-Recreation ist getestet, physischer Prozesskill/Termux-IPC/Systemgrenzen und Fehlerfälle auf dem Telefon fehlen.
- Forschungskandidat ist OFF und allein SHADOW, keine neue aktive Antwortqualität. Pauschale Fehlerfreiheit wird nicht behauptet.

## 11. Rollback, offene Teile und unmittelbarer Anschluss

Rücksetzung ausschließlich auf eigenem Reviewzweig per `git revert` in umgekehrter Abhängigkeitsfolge oder neuer
Vergleichsbranch vom unveränderten331e2d2. Kein fremder Reset, Force-Push oder Löschen älterer Zweige.
Shadow lässt sich sofort OFF schalten; das löscht seine flüchtigen Fokuswerte und ändert keine Besitzerfakten/Gewichte.
M5-Planer/Labor sind getrennte Dateien und müssen nicht als Antwortkern übernommen werden.

Offen: P2 mit echter austauschbarer Intent-Vorschlagsschicht und getrennten österreichisch-deutschen TRAIN/DEV/TEST-
Formulierungsfamilien; keine SWB1-/LLM-Intentüberlegenheit gemessen. Freie Kotlin-Generierung erst als begrenzter
Kandidat über bestehenden CCC1-Kritiker/Tests/Belege, niemals als Rohshell. 30-Sekunden-Modus noch nicht umgesetzt:
ÜBEN/PRÜFEN/LERNEN getrennt halten, Lebenszyklus/Endlichkeit/Snapshots prüfen, vorhandene JobScheduler-Intervalle
nicht als garantierten 30-Sekunden-Takt deklarieren. Fehlender Sprachlerngewinn macht bloße Wiederholungen nicht sinnvoll.

**GPT-6/Claude können unmittelbar fortsetzen:**
1. Fetch/HEAD/Abstammung prüfen; diese Übergabe, Ergebnisbericht und Rohbelege lesen; keine erneute TEST3-Blindbehauptung.
2. Auf resultierendem Integrations-SHA relevante Regression ausführen; keine automatische produktive Übernahme des gescheiterten CTX3.
3. Den sicheren M5-Planer isoliert reviewen und auf Fold7 nach eigenständiger Besitzer-APK-Freigabe prüfen:
   mehrere Turns → Vorschau → kontrollierter Builder → bestehende Erzeugen/Bauen-Bestätigungen → Termux/CCC1-Belege.
4. Activity-Rotation/Falten/IME/OS-Kill mit und ohne SavedState, Sessionwechsel/Profilnamespace, Entwurfserhaltung,
   ausstehende M4-Aufträge und Issue76 separat am Gerät abnehmen. Keine Deinstallation privater Besitzerdaten.
5. Für Kontextforschung zuerst die **Fakten-/Entitätsextraktion und Quellenentailment** gegen einfache Baselines
   untersuchen, statt nach TEST3 Sonderregeln hinzuzufügen. Typisierte Quellenmetadaten/TTT1 nur bei passender Schnittstelle.
6. Für P2 semantische Merkmale STORE_TEXT/INCREMENT_ON_TAP von Projekt-/Berechtigungsautorisierung trennen;
   Synonymnormalisierung/Typofehler/SWB1/lokale Runtime als Vorschlagskandidaten vergleichen und an neuem Holdout messen.
7. Besitzerkandidat0.3.18 erst nach erneuten Quellpins, tatsächlicher0.3.17-Rollbacksicherung und gesonderter Freigabe
   mit dem vorhandenen Schlüssel bauen. In dieser Runde kein ersatzweise signierter Updatekandidat.

Getroffene Routineentscheidung im autonomen Auftrag: vorhandene zwei Vorlagen als Grenze beibehalten;
Kosten dieser Entscheidung sind begrenzte Ausdrucks-/Projektvielfalt. Der reproduzierbare Fortschritt ist die
sichere Auftrags-/Zustandskette, **kein behaupteter Durchbruch im allgemeinen Dialogverständnis**.
