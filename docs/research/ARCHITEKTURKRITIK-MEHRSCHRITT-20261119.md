<!-- Unabhängige Architekturkritik (Opus als Gegenprüfer), 19.11.2026. Nur gelesen, nichts gebaut oder ausgeführt.
Geprüfter Stand: ccr-7597adbd-8ko252 @ 385863353 und Integrationsseite e35fe06bb. Zeilenangaben gelten für diese Stände.
Die Befunde sind Aussagen des Prüfers aus Codelektüre; „nicht geprüft“ ist dort ausdrücklich markiert. -->

# Architektur-Review: Mehrfach-Aufträge in Continuum

Geprüfte Stände:
- `ccr-7597adbd-8ko252` @ 385863353 („unser Stand“).
- Integrationsseite e35fe06bb. Ich habe sie gegen den Merge-Base 59e3abefc verglichen, damit nur ihre eigenen Änderungen sichtbar werden.

Ich habe nur gelesen und nichts gebaut oder ausgeführt. Pfade sind relativ zu `app/src/main/java/at/heinz/deviceai/`.

---

## A. Wie ein Besitzersatz heute durchläuft

**Reihenfolge in `AgentActivity.sendCurrentInput` (Z. 8068 ff.)**

1. Abfangen und Steuern, in dieser Reihenfolge:
   - `heatMeasurement`, `OwnerInputRepair.strayDots`
   - `consumeOwnerWorkInterruption`
   - `interceptOwnerMission` (Z. 8078 → `OwnerMission.kt:197`)
   - `interceptOwnerDocumentDesk`, `consumeDeviceToolCommand`, `interceptIncomingMessages` (Z. 13284)
   - Computer-Stopp, Research-Antwort, `interceptCodingAgents`, `applyOwnerUnderstanding`, Fähigkeitsfrage, Rollenmanipulation, `OwnerCorrectionIntent` (Z. 8108)
   - die Busy-Sperre (Z. 8120)
   - Routinen, Bot-Routing
   - e35 schiebt `tryReadNotification` vor die Routinen (e35-Diff, Z. ~8097).
2. Deterministische Lenkungen für den ganzen Satz:
   - `ownerTalk.handle` (8171)
   - **`ownerMission.handle` (8173)**
   - `ownerDocumentDesk.handle` (8178)
   - `ownerSend.handle` (8182)
   - `continuumBriefing.handle` (8186)
   - `tryStartComputerRequest` (8187)
   - `ownerWorkTools.handle` (8191)
   - `tryStartOwnerTaskChain` (8192). Darin: Rezepte, dann `ownerMission.proposeFromModel` (9514), dann der Aufgabenketten-Modellplan (nur mit Schalter und bereitem Modell).
3. Danach: Uhr, Akku, Rückblick, Dokument-Referenzen, Bootstrap.
4. **Nur mit Cloud-Gehirn:** `ownerMission.proposeOrderFromModel` (8770). Das ist ein Modell-Mapping auf *einen* Lenkungsauftrag.
5. Danach der freie Modellzug (`presentBootstrapDecision` → `runLocal`): `BoundedAgentOrchestrator` mit Registry-Werkzeugen, höchstens 4 Werkzeugaufrufe (`BoundedAgent.kt:105`), mit Claude ein Werkzeug pro Runde (`ClaudeModelRuntime.kt:552`).

**Was die Missions-Schicht wirklich kann**

- Der Planer (`OwnerMissionPlan.kt:136–171`) schneidet an Kommas und Verbindungswörtern. Er nimmt nur, wenn **jedes** Teilstück von einer Lenkung erkannt wird.
- Bekannte Schritte: OpenPdf, PageSave, News, Export, Mail, FindFile, OpenFound, OpenSaved (`OwnerMissionPlan.kt:10–73`).
- **Send- und Computer-Aufträge sind als Schritt ausdrücklich ausgeschlossen** (`OwnerMissionPlan.kt:204–205`, `167–168`). Ein WhatsApp-Schritt ist in einer Mission also unmöglich.
- Ein Wort aus `blocked` (nicht, ohne, kein…, oder, wenn, …; Z. 110) im **ganzen** Satz verwirft den Plan. Das gilt auch, wenn das Wort nur im Nachrichtentext steht.

**Die fünf Testsätze**

**1. „Schau bitte meine E-Mails durch, fasse … zusammen, mach daraus ein PDF und schick meinem Vater auf WhatsApp, dass ich mich später melde.“**
- Missionsplaner: Das erste Stück „Schau bitte meine E-Mails durch“ erkennt keine Lenkung (es gibt keinen Mail-Lese-Weg) → kein Plan.
- Desk: `prohibited` enthält schick/whatsapp → nein.
- Send-Parser: Das Verb muss am Satzanfang stehen (`OwnerSendIntentParser.kt` e35 Z. 120), „Schau“ ist keines → null.
- Computer, Work-Tools (Export: unbekannte Wörter; Share: kein „an“) und Ketten → nein.
- `proposeFromModel.wants()`: Die Mail-Regex trifft „E-Mails“ wegen der Wortgrenze nicht (`OwnerMissionModelPlan.kt:31`). Nur „pdf“ trifft, also eine Fähigkeit → false.
- Mit Cloud-Gehirn greift `wantsOrder()` wegen „pdf“ (Z. 65–72). Claude soll den Satz auf die erlaubten Zeilen abbilden; eine Zeile ist erlaubt (Z. 79–98).
  - Gibt Claude „1. Speichere das als PDF“ zurück, besteht das die Prüfung („ein PDF“ erfüllt `pdfTarget`).
  - `runsWithoutQuestion` ist dann true (Z. 109). Das wird sofort an `workOnHeld` übergeben, und die **letzte Assistentenantwort** wird als PDF gespeichert, mit „Erledigt.“.
  - Mails, Zusammenfassung und WhatsApp fallen **stillschweigend** weg.
  - Ob Claude so oder mit KEIN PLAN antwortet, ist modellabhängig (nicht geprüft). Bei KEIN PLAN wird es ein freier Claude-Zug ohne Mail-Werkzeug. Der e35-Text `OWNER_APP_ACTIONS` verlangt dann ausdrücklich: „give the direct chat command“. Das ergibt eine Antwort im Stil „bitte einzeln“.
- Lokal (Gemma): freier Zug mit Registry-Werkzeugen. Es gibt weder ein Mail- noch ein WhatsApp-Werkzeug.

**2. „Öffne bitte WhatsApp und schreib meinem Vater guten Morgen.“**
- Kein `laneWord` → keine Mission. Send-Parser: Satz beginnt mit „Öffne“ → null.
- `OwnerComputerIntentParser.parseCommand`: Die Öffnen-Regex trifft, Rest „WhatsApp und schreib …“. Der Suffix ist weder Downloads noch „suche“, also kommt `Unsupported("Der zusätzliche App-Schritt wird noch nicht sicher verstanden.")` (Z. 119–120).
- `tryStartComputerRequest` beendet damit den Zug (`AgentActivity.kt:7811–7815`).
- Ergebnis: **Sackgasse, nichts ausgeführt.** Work-Tools, Modell und Send-Lane sehen den Satz nie.

**3. „Suche die letzte Mail von meinem Anwalt, fasse sie zusammen, erstelle daraus ein PDF und öffne es.“**
- Planer: Das Stück „Suche die letzte Mail …“ erkennt niemand. Das NEWEST_FILE-Rezept braucht einen Ordner (`OwnerTaskChainRecipes.kt:96–98`), also null → kein Plan.
- Ganzer Satz: Desk (`prohibited` „mail“), Export (`foreign` „mail“) und Share (kein „an“) lehnen ab.
- `wants()`: pdf + „Mail“ → 2 Fähigkeiten → mit Cloud schlägt das Modell einen Plan vor.
  - Typisch ist „Speichere das als PDF / Öffne es“. Das besteht `check()`, wird angesagt und läuft nach „Ja“.
  - Gespeichert wird **die letzte Chat-Antwort**, nicht die Anwaltsmail. Suche und Zusammenfassung sind weggefallen, ohne dass es gesagt wird.
- Lokal: freier Zug, ohne Mail-Quelle.

**4. „Schau, welche Termine ich diese Woche habe, und sag mir, ob ich etwas vergessen könnte.“**
- Keine Mission. Die Briefing-Termine kennen nur „heute/morgen“ (`briefing/ContinuumBriefingCommand.kt:96–103`), und `ContinuumAgenda` liefert nur Tagesfenster (Z. 56–60).
- Es gibt **kein Kalenderwerkzeug** in `ToolRegistry`. Ergebnis: freier Modellzug ohne Daten.
- Claude verweist bestenfalls auf „Welche Termine habe ich heute“, im schlechtesten Fall rät es. Der „vergessen?“-Teil ist unmöglich.

**5. „Such die PDF-Datei Argon4, öffne sie, analysiere Seite zwei und fasse sie zusammen.“**
- Planer:
  - „Such die PDF-Datei Argon4“ → OpenPdf(open=false).
  - „öffne sie“ → OpenIt → zusammengeführt zu open=true.
  - „analysiere Seite zwei“ und „fasse sie zusammen“: `OwnerPageJobCommand` verlangt immer einen Speicherauftrag (`OwnerLibraryCommand.kt:250–263`) → null → kein Plan.
- Ganzer Satz: Desk lehnt ab (Name > 60 Zeichen/6 Wörter), Ketten auch (`fileSearch` braucht „datei“ direkt hinter dem Artikel).
- Mit Cloud: `wants()` ist true (pdf/seite + datei). Die erlaubten Formen zwingen „Fass Seite <Zahl> zusammen und speichere es …“.
  - Ohne Format besteht das `invented()`, weil `formatSaid(null)` true liefert (Z. 159–160).
  - Plan: PDF öffnen, Seite 2 zusammenfassen **und speichern**. Danach fragt der Desk nach dem Format. Eine Datei, die nie bestellt wurde.
- Ohne Plan, Claude frei: search_device → open_file → read_file(pages=2) → Antwort. Das sind 3 Aufrufe, 4 Runden, und braucht die Inhaltsfreigabe für die Cloud (`devicetools/DeviceToolPolicy.kt:111–124`).
- Lokal: Gemma-Agent, der Ausgang ist offen (nicht geprüft).

**Fazit A:** Keiner der fünf Sätze wird heute vollständig deterministisch ausgeführt. 2 endet als Sackgasse. 1, 3 und 5 können über den Modellplan **teilweise und falsch** laufen, und das wird als „Erledigt“ berichtet. 4 hat keine Datenquelle.

---

## B. Die zehn schwersten Fehler und Schwachstellen

**1. Modellplan ohne Abdeckungsprüfung — Schritte fallen stillschweigend weg (hoch)**
- Beleg: `OwnerMissionModelPlan.check` (Z. 130–152) prüft nur, dass nichts *erfunden* wurde, nicht dass alles *abgedeckt* ist. `orderPrompt` erlaubt eine einzige Zeile (Z. 81). `runsWithoutQuestion` (Z. 109–110) führt Export sofort aus.
- Eingabe: Sätze 1 und 3 → „Speichere das als PDF“ läuft sofort mit falschem Inhalt.
- Kleinste Korrektur: In `propose(single=true)` ablehnen, sobald `OwnerMissionPlanner.connector` den Satz in mehr als ein Stück mit Verb schneidet. Allgemein: Jede Planzeile nennt den Satzteil, den sie abdeckt. Bleibt ein Teil mit Verb unabgedeckt, läuft nichts automatisch, und die App sagt: „Das Lesen deiner Mails kann ich noch nicht; den Rest so?“.

**2. „das“ greift auf irgendeine letzte Antwort zurück (hoch)**
- Beleg: `OwnerWorkTools.subjectOf` (Z. 397–413) → `OwnerDeliverable.ofReply` (`cognitive/OwnerDeliverable.kt:72–77`, jede Antwort ab 40 Zeichen). `lastAssistantReply` = `command03VisibleMessages` (`AgentActivity.kt:12855`).
- Dort landen auch Lane-Absagen, Missionsberichte und **vorgelesene Benachrichtigungen**: `IncomingMessageAdapter.announce` → `addBubble(…, persist=false)` → Z. 18116.
- Eingabe: Nach „Der zusätzliche App-Schritt wird noch nicht sicher verstanden.“ sagt der Besitzer „Erstelle eine PDF“. Ergebnis: ein PDF mit der Absage.
- Nach einer vorgelesenen Nachricht und „Speichere das als PDF, schick es per Mail an Anna“: Mit Gmail-Freigabe geht der fremde Nachrichtentext als Anhang **ohne Rückfrage** hinaus (Anhang = DICTATED, `sending/OwnerSendDirector.kt:353–356`).
- Korrektur: Nachrichten im Chat typisieren (Modellantwort / Lane-Ergebnis / Systemsatz / Fremdinhalt). `ofReply` nur für Modellantworten.

**3. Verwandtschaftswort wird als Namensteil aufgelöst (hoch, nicht umkehrbar)**
- Beleg: `OwnerSendRecipientResolver.asked` streicht „meinem“ (Z. 36–44); `matches` prüft eine Teilmenge der Tokens (Z. 46–47).
- Folge: „meinem Vater“ trifft jeden Kontakt mit dem Wort „Vater“, z. B. „Vater Lukas (Kita)“. Ist er eindeutig, wird ohne Rückfrage aufgelöst. Mit Dauerfreigabe und eigenem Wortlaut wird dann **ohne Bestätigung gesendet** (`OwnerSendDirector.kt:302, 321–326`).
- e35 verbreitert das mit `familyRecipient` (e35 `OwnerSendIntentParser.kt:79–80`). Die Bildschirmprüfung bestätigt nur den falsch aufgelösten Kontakt.
- Korrektur: Verwandtschaftswörter nie per Token abgleichen. Nur über eine **bestätigte Beziehung** auflösen. Fehlt sie, fragen: „Wer ist dein Vater in den Kontakten?“, dann einmal bestätigen und speichern. Die erste Sendung über eine Beziehung immer mit Rückfrage.

**4. Empfängerwörter landen im gesendeten Text (mittel-hoch, auf unserem Stand)**
- Beleg: Auf unserem Stand gilt `if (toWhom == null) message = tail`. Das ist im e35-Diff als „-“-Zeile zu sehen, die Datei ist auf unserer Seite unverändert.
- Eingabe: „Schick auf WhatsApp meinem Chef, ich komme später“ → Rückfrage „An wen?“ → „Chef Huber“ → gesendet wird **„Meinem Chef, ich komme später“** als DICTATED, also ohne Rückfrage.
- e35 behebt das nur für Familienwörter.
- Korrektur: Ist der Empfänger offen und der Text nicht durch „:“, „,“ oder „dass“ vom Empfänger getrennt, den Text als `unsplit` behandeln oder den Wortlaut einmal bestätigen lassen.

**5. Der Mischsatz „Öffne <App> und …“ wird abgewürgt oder verfälscht (mittel)**
- Beleg Sackgasse: `OwnerComputerIntentParser.kt:119–121` liefert `Unsupported` statt `NotComputerCommand`, und dieses `Unsupported` beendet den Zug (`AgentActivity.kt:7811`).
- Zweiter Fall: `otherAction` (Z. 57) kennt weder schreib, fass, erstell, speicher noch zeig.
  - Eingabe: „Suche in WhatsApp nach Anna und schreib ihr Hallo“ → `Ready(SEARCH, query="Anna und schreib ihr Hallo")` (Z. 123–127, 140). Der Text wird in die WhatsApp-Suche getippt, der Schreibauftrag fällt weg.
- Der Katalog bewirbt „Öffne Chrome und suche <Begriff>“ (`cognitive/OwnerLaneCatalogue.kt:48`), aber CHROME ∈ `OPEN_ONLY_APPS` (Z. 55–56, e35 ergänzt TIKTOK und SMS) → `Unsupported`.
- Korrektur: Ein gemeinsames Verb-Lexikon; jedes „und|dann|danach + Imperativ“ im Suffix bzw. in der Suchanfrage → `NotComputerCommand`, damit der Missionsplaner zusammensetzen kann. Den Katalog korrigieren.

**6. Lane-Frage wird von OwnerTalk gestohlen (mittel)**
- Beleg: `ownerTalk.handle` (8171) läuft vor `ownerSend.handle` (8182), ohne Wache `!ownerSend.awaitingAnswer`. Im persönlichen Ton ist „Guten Morgen“ ein Gruß (`OwnerTalk.kt:131`, `cognitive/OwnerGreeting.kt:90, 94–101`).
- Eingabe: Send-Lane fragt „Was soll ich Papa schreiben?“, Besitzer sagt „Guten Morgen“. Ergebnis: Rückgruß, die Frage verfällt.
- Gleiches Muster: `ownerMission.handle` und der Desk laufen ebenfalls vor der Send-Lane.
- Korrektur: Offene Fragen aller Lanes zuerst bedienen, d. h. eine zentrale `pendingQuestionOwner`-Wache vor allen Ganzsatz-Lenkungen.

**7. Modellplan kann einen Schritt-*Typ* hinzufügen (mittel)**
- Beleg: `invented()` (Z. 166–202) prüft nur Entitäten. PageSave enthält immer „speichern“ (Prompt Z. 85, 118). `formatSaid(null)` ist true.
- Eingabe: Satz 5 → ein Speicherschritt, der nicht bestellt wurde.
- Korrektur: Pro Schritttyp das Verb im Originalsatz verlangen (Speicherverb für PageSave/Export, Öffnen-Verb für open=true, Mailwort für Mail). Dazu einen Schritt „Seite zusammenfassen ohne Speichern“ einführen.

**8. Mission und Agent überstehen keinen Neustart; nach einem Absturz keine Wahrheit (mittel)**
- Beleg: `OwnerMission.kt:159` (`private var mission`, nur im Speicher).
- `AgentCheckpoint` (`BoundedAgent.kt:113–129`) speichert keine Werkzeugergebnisse, und Fortsetzen ist nur vor einem Werkzeugvorschlag erlaubt (253–276).
- Die Send-Lane hält ihren Ablauf nur im Speicher (`OwnerSendDirector.kt:81`).
- Folge: Wird die App beendet, nachdem Senden gedrückt wurde, gibt es weder Bericht noch Beleg. Eine neue Mission würde den Schritt neu anstoßen. Doppelsendung ist möglich, falls der Besitzer neu bestellt, weil die App nichts davon weiß.
- Korrektur: siehe C (persistentes Journal mit Idempotenzschlüssel).

**9. Merge-Gefahr in den fünf gemeinsamen Dateien (hoch für die Korrektheit nach dem Merge)**
- `OwnerExportCommand`: Es gibt zwei unvereinbare Mechanismen.
  - Unser Stand: `unpointed`/`parseMake` + Missionsschritt OpenSaved.
  - e35: `showAfter`/`openAfterSaving`/`needsCurrentDeliverable` (e35 Z. 70–83).
  - Mit e35-`parse` wird „Erstelle eine PDF und zeig sie mir“ ein Einzelauftrag (`singleOrder`, `OwnerMissionPlan.kt:178–181`). Unser Missionsweg ist dann tot.
  - e35 nimmt nur `deliverable?.takeIf{subject}` und ignoriert unseren Zusammenfassungsentwurf (`OwnerWorkTools.kt:404–411`). Direkt nach einer PDF-Zusammenfassung fragt die App dann „Was soll darin stehen?“. Das macht unseren Feldbefund-Fix vom 19.11. rückgängig.
  - e35 meldet „Ich habe die Datei zum Anzeigen geöffnet“, das Ergebnis ist aber nur `Done(FileSaved)`. Bericht und Mission können „geöffnet“ nicht belegen.
- `ClaudeRequestPolicy.frame`: e35 hängt `OWNER_APP_ACTIONS` an den stabilen Block, unser Stand nutzt den Lambda `ownerFrame` mit `ClaudeAbilityCatalogue`. Beide zusammen ergeben einen doppelten, teils widersprüchlichen Fähigkeitstext.
- Korrektur: Einen Mechanismus wählen. Empfehlung: unseren Planer-Weg, und aus e35 nur `needsCurrentDeliverable` als Rückfrage über `subjectOf`. Regressionstests für beide Feldsätze ergänzen.

**10. Eine Werkzeugrunde pro Durchlauf, und der Prompt erzieht zu „bitte einzeln“ (mittel; Latenz und Kosten)**
- Beleg: `ClaudeModelRuntime.kt:481–509, 552, 711`; `ClaudePromptFrame.RUNTIME_PREFACE` „Request at most one tool call per turn“ (Z. 192); `maxToolCalls` = 4 (`BoundedAgent.kt:105`).
- Send- und Missions-Lanes haben **keinen** Registry-Eintrag. Ein Claude-Werkzeugzug kann WhatsApp also nie erreichen. Das ist Absicht, und gut so.
- Satz 5 kostet 4 Runden. Ein fünfter Aufruf wird abgelehnt, und die Schleife fragt trotzdem das Modell erneut (Z. 414–423).
- Korrektur: siehe D.

**Weitere Befunde**

- **(niedrig) Falsche Zuordnung von Werkzeugergebnissen:** `toolResultMessage` (`ClaudeModelRuntime.kt:607`) nimmt notfalls `unused.firstOrNull()`. Ein Ergebnis eines anderen Werkzeugs wird dann dem vorgeschlagenen Aufruf zugeordnet. Besser wäre, ohne Treffer NO_TOOL_RESULT zu senden.
- **(niedrig) „Geöffnet“ heißt nur, dass `startActivity` keinen Fehler warf** (`OwnerWorkTools.kt:514–518`). Es wird nicht geprüft, ob der Betrachter wirklich im Vordergrund ist.
- **(nicht geprüft) Hitzeregel:** Die Mission sendet `ProcessTimeline`-Ereignisse (`OwnerMission.kt:872–883`), auch während der Desk lokal zusammenfasst. `JarvisRenderQuality` drosselt bei laufender Inferenz nur, es stoppt nicht. Ob die Timeline dabei neu animiert, habe ich nicht verifiziert.
- **(nicht geprüft)** Ob vorgelesener Benachrichtigungstext aus `command03VisibleMessages` in den Claude-Gesprächsverlauf gerät. Die Zustimmung (`ClaudeConsentNotice`) sagt „Inhalte von Benachrichtigungen … nicht gesendet“.

---

## C. Zielentwurf für Mehrfach-Aufträge

Kein zweiter Scheduler: `OwnerMissionDirector` bleibt der einzige Ausführer. Jeder Schritt läuft über den Eingang seiner eigenen Lane mit deren Freigaben (ToolGate, Dauerfreigaben, Bestätigungen). BoundedAgent bleibt für offene Erkundung *innerhalb* eines Schritts (z. B. „recherchiere“), nicht als Planer.

**1. Erkennen**
- `OwnerMissionPlanner` wird vom Ganzsatz-Filter zum **Satzteil-Parser**. `blocked` gilt nur für den Befehlsteil, nicht für einen „dass“-Satz oder Text nach „:“ (beides ist Nutzlast eines Send-Schritts).
- Send- und Computer-Teile werden als Schritt zugelassen. Die Ganzsatz-Vorfahrt der Send-Lane bleibt nur für Sätze ohne weitere Verbindung.
- Auf jeden nicht erkannten Satzteil folgt eine Teilerklärung („Mails lesen kann ich noch nicht“) und ein Angebot für den Rest. Kein Teil darf stillschweigend wegfallen.

**2. Neue Schritttypen** (`OwnerMissionStep`)

| Schritt | Lane | Eingabe | Ausgabe (typisiert) | „Verifiziert“ heißt |
|---|---|---|---|---|
| `SendChat(channel=WHATSAPP, recipientRef, text|fromSource)` | OwnerSendDirector (neuer Eingang `missionSend`) | PersonRef, Text oder `source`-Ergebnis | `Delivered(SENT/DRAFT)` | `SendOutcome.SENT`, am Bildschirm bestätigt (OwnerSendFlow); DRAFT nur als „vorbereitet“ |
| `SendMail` (bestehendes Mail + Gmail-Grant) | Work-Tools → Send | wie heute | `Delivered` | wie oben |
| `OpenApp(app)` | Computer-Lane, nur Öffnen | ComputerApp | `AppOpened(pkg)` | frisch beobachtetes Zielfenster (`launch_app`-Vertrag) |
| `MailSearch(query, newest)` / `MailRead(id)` | künftige Gmail-API-Lane (nur lesen, OAuth-Scope `gmail.readonly`) | Absender/Stichwort | `MailRef(id, from, subject, date)` / `MailText` | API-Antwort mit Message-ID; Treffer 0 oder >1 → Rückfrage |
| `Summarize(source)` | Work-Tools (Generierung) | `MailText`/`PageText`/`Briefing` | `Deliverable(kind=SUMMARY)` | Generierung vollständig + Ausgabeprüfung wie `NewsBriefing.accept`; mit Quellen-ID |
| `PageSummary(page)` (ohne Speichern) | Desk | geöffnetes PDF | `Deliverable(PAGE_SUMMARY)` | Seitentext gelesen (coveredChars>0), Zusammenfassung akzeptiert |
| `CalendarRange(from,to)` + `AgendaCheck` | Briefing-Lane (`ContinuumAgenda`, READ_CALENDAR) | Zeitraum | `Agenda(events)` / `Findings` (Überschneidungen, Lücken, Termine ohne Ort/Vorbereitung, deterministisch) | Kalenderabfrage erfolgreich; „vergessen?“ nur aus Regeln + Ereignissen, nie geraten |
| `WebResearch(query)` | Research-Lane | Text | `Sources[]` | Internetfreigabe + mindestens eine gelesene Quelle |
| `ResolvePerson("mein Vater")` | neues `OwnerRelations` (bestätigte Fakten) | Beziehung | `PersonRef(contactId)` | bestätigte Beziehung + genau ein Kontakt; sonst Rückfrage und danach Speichern nach Bestätigung |

**3. Typisierter Datenfluss**
- `source: Int?` wird zu `inputs: Map<Slot, StepRef>`, Ergebnisse sind `OwnerLaneResult`-Untertypen.
- `assemble()` prüft Typen statt Wortlisten:
  - `SendChat.fromSource` verlangt `Deliverable` oder `FileSaved`.
  - `Export` verlangt ein `Deliverable` aus *dieser* Mission oder ein ausdrücklich gehaltenes Chatergebnis.
- Die Lane erhält das Ergebnisobjekt direkt, nicht über `subjectOf`/`lastAssistantReply`. Das beseitigt Fehler B2 für Missionen.
- Bei Satz 1 geht an WhatsApp nur der Owner-Text („Ich melde mich später“). Das PDF ist ein Ergebnis, kein Anhang, solange der Besitzer keinen Anhang verlangt hat.

**4. Persistenter Checkpoint** (neu `OwnerMissionJournal`, App-intern, verschlüsselt wie die Werkzeugquittungen)
- Inhalt: `missionId`, Originalsatz (nur lokal), Schrittliste (Typ + Argumente), Status je Schritt, Ergebnis-Referenzen. Zu Ergebnissen nur Hash und ID speichern; Mail- und Nachrichteninhalte nicht.
- **Idempotenzschlüssel** = `sha256(missionId|stepIndex|kanal|empfängerId|textHash|anhangHash)`. Vor dem Drücken wird `INTENT_RESERVED` geschrieben, danach `SENT` oder `UNCONFIRMED`. Gleiches Muster wie `reserveSend` und die ToolGate-Quittungen.
- Wiederholungssicher (automatisch fortsetzbar): MailSearch/Read, CalendarRange, PageSummary, Summarize (neu erzeugt), Export (neue Datei; Name mit missionId → `downloadNameTaken` zeigt das Duplikat), Open*.
- **Nicht wiederholungssicher:** Send*, alles mit CONFIRM/DESTRUCTIVE.
  - Steht beim Start `INTENT_RESERVED` ohne Abschluss: niemals erneut drücken. Melden: „Ich weiß nicht, ob die Nachricht an X rausging – bitte in WhatsApp prüfen“. Weiter erst auf ein ausdrückliches „ja, nochmal“.
  - Steht `SENT`: überspringen, im Bericht nennen.
- Nach einem Neustart ein Satz: „Die Auftragsfolge von 14:02 steht bei Schritt 3 von 4. Weiter?“. Kein automatischer Start.

**5. Abbruch und Korrektur**
- `OwnerMissionCorrection` und `affected()` gibt es schon. Ergänzungen:
  - Korrekturen auch für Person („nicht Papa, Mama“) und Kanal.
  - Bei einem schon gesendeten Schritt immer `confirmFirst`, **auch für den Zielschritt selbst**. Heute nur für abhängige Schritte (`OwnerMission.kt:741`).
- Stopp:
  - Laufender lesender Schritt → abbrechen.
  - Laufender Sendeschritt → nicht unterbrechen, nachdem gedrückt wurde (OwnerSendFlow ist atomar). Danach den Rest verwerfen.
  - Der Bericht nennt nur, was belegt ist (heutiges `report()` beibehalten).

---

## D. Engpass 2: mehrere Werkzeugaufrufe pro Claude-Antwort?

- **(i) Einer pro Runde (heute):**
  - Sicher und Cache-stabil. Jede Runde liest Prefix und Verlauf aus dem Cache.
  - Kosten pro zusätzlicher Runde: etwa die Cache-Lesekosten des Verlaufs ($0,20/MTok bei Opus 5.5) plus neue Ausgabe-Tokens. Latenz: eine volle Modellrunde (mehrere Sekunden).
  - Für Satz 5 sind das 4 Runden.
- **(ii) N Aufrufe annehmen, nacheinander durch das Gate:**
  - Bringt bei **abhängigen** Ketten (suchen → öffnen → lesen) fast nichts, weil die Argumente vom Vorergebnis abhängen und Claude sie nicht vorab kennt. Gewinn nur bei unabhängigen Lesezugriffen (Akku + Speicher).
  - Risiken: Mehrere Bestätigungsdialoge hintereinander. Die Regel „bei erstem Fehlschlag stoppen“ muss neu gebaut werden. `ExternalContentActionGuard` (`untrustedObservationsSeen`) verliert seine Reihenfolge-Semantik. Alle Ergebnisse müssen in *einer* User-Nachricht zurück.
  - Erreicht die Send-Lane trotzdem nicht.
- **(iii) Claude gibt einen typisierten Plan aus, der Director führt ihn aus:**
  - Ein Aufruf statt N. Die Ausführung ist deterministisch. Jeder Schritt behält die Freigaben seiner Lane. Er erreicht die Lanes, die bewusst kein Werkzeug sind (Send, Desk, Export).
  - Die bestehende Prüfung `OwnerMissionModelPlan.check` wird wiederverwendet.

**Empfehlung: (iii)** für Besitzersätze mit mehreren Aufträgen; (i) bleibt unverändert für freie Erkundung in einem Schritt. (ii) jetzt nicht.

Was sich ändern muss:
1. `proposePlan` (Host in `AgentActivity`, Missionsadapter): eine eigene text-only Anfrage mit `output_config.format` (JSON-Schema: `steps[] {kind, args, covers: "<Satzteil>"}` + `unsupported[]`). Kein Tool-Aufruf, kein `tool_choice`-Zwang (Opus/Sonnet 5.5 lehnen erzwungenes Werkzeug ohnehin mit 400 ab).
2. Die Planinstruktion als **konstanter** System-Block mit eigenem Cache-Breakpoint. Der Besitzersatz kommt als User-Nachricht. `RUNTIME_PREFACE` bleibt byte-gleich, damit der Werkzeug-Cache der Dialoge nicht kippt.
3. `OwnerMissionModelPlan.check`: JSON statt Zeilen-Regex; Abdeckungsprüfung (B1); Prüfung des Schritttyps gegen das Verb (B7); `invented()` bleibt.
4. `OwnerMissionStep`/`assemble` um die Schritte aus C erweitern; `runsWithoutQuestion` nur für einen einzigen lesenden Schritt.
5. `ClaudeModelRuntime`: `disable_parallel_tool_use` und `SKIPPED_TOOL_RESULT` bleiben. Einzige kleine Korrektur: Z. 607 (keine Ersatz-Zuordnung).
6. Mehrere Mehrfach-Werkzeugaufrufe im freien Agenten nicht freischalten. Prüfenswert ist nur `maxToolCalls` 4 → 6 für Lesewerkzeuge (Messung nötig).

---

## E. Modell-Routing

**Entscheidungsregel**

1. **Deterministisch zuerst:** Ist jeder Satzteil von einer Lane erkannt, wird ohne Modell geplant. So soll der Normalfall für Sätze 2, 4 und 5 nach den Korrekturen aus C aussehen.
2. **Planen per Modell nur auf dem Satz selbst**, nie auf Inhalten:
   - Lokal (Gemma), wenn das Cloud-Gehirn nicht gewählt ist oder der Satz die Cloud ausschließt (`ClaudeTurnGate`).
   - Claude, wenn es gewählt ist. Der Satz fällt unter die bestehende Zustimmung.
3. **Inhalte zum Modell nur je Datenklasse:**
   - **Nie in die Cloud:** Bildschirminhalte anderer Apps (`ClaudeLocalOnlyGuard`), Benachrichtigungstexte, Kontaktdaten (Nummern/Adressen), als „nur Gerät“ markierte Erinnerungen ohne Häkchen, Ergebnisse von `ResolvePerson`.
   - **Nur mit eigener, getrennter Zustimmung:** private Mailtexte (neuer Schalter je Kanal, analog `contentConsent` in `DeviceToolPolicy`) und Kalendereinträge. Ohne diese Zustimmung lokal zusammenfassen oder nur Metadaten nennen.
   - PDF-Seiten gehen heute schon mit der Grundzustimmung an die Cloud (`OwnerDocumentDesk.kt:756`, Zustimmungstext „Textstellen aus Dokumenten“). Das ist zulässig, darf aber **nicht** das Muster für Mail sein.

**Planer + Kritiker (zweites Modell)**
- Kosten: eine zusätzliche Runde (Latenz etwa wie ein Planaufruf, also verdoppelt) plus Ein- und Ausgabetokens für Plan und Kritik. Eingabe ist nur Satz + Plan (wenige hundert Tokens); mit Haiku 5.5 wäre das finanziell vernachlässigbar, die Latenz bleibt.
- Lokal heißt das zweimal Gemma: Wärme und Zeit. Das wäre ein Verstoß gegen den Geist der Hitzeregel.
- Nutzen gering, denn das Risiko fängt die **deterministische** Prüfung ab: Abdeckung, Typ gegen Verb, keine erfundenen Entitäten, Gates je Lane. Ein Kritiker-Modell fände kaum mehr und hätte keine zusätzlichen Rechte.
- Sinnvoll höchstens für eine Klasse: **ausgehende Schritte mit vom Modell formuliertem Text an Dritte** (z. B. „schreib meinem Anwalt eine höfliche Absage“). Auch dort ersetzt die Besitzerbestätigung (TextOrigin.DERIVED → Confirm) den Kritiker.
- **Empfehlung: keinen Kritiker einbauen.** Ein Modell plant, Code prüft, der Besitzer bestätigt.

---

## F. Arbeitsliste (max. 12) mit Dateibesitz für zwei Entwickler

Entwickler **A** besitzt Erkennen und Planen (`cognitive/*` außer Send), Entwickler **B** besitzt Ausführen und Lanes (`sending/*`, `OwnerWorkTools.kt`, `OwnerMission.kt`, `localmodel/*`). `AgentActivity.kt` gehört nur **B**; A liefert dort keine Änderungen.

| # | Priorität | Aufgabe | Besitzer / Dateien |
|---|---|---|---|
| 1 | P0 | Merge-Entscheid ExportCommand und ClaudeRequestPolicy (B9) und Regressionstests beider Feldsätze | A: `cognitive/OwnerExportCommand.kt`; B: `localmodel/ClaudeRequestPolicy.kt`, `OwnerWorkTools.kt` |
| 2 | P0 | Abdeckungs- und Verb-Typ-Prüfung im Modellplan; kein Sofortlauf bei Mehrsatz (B1, B7) | A: `cognitive/OwnerMissionModelPlan.kt` |
| 3 | P0 | Verwandtschaft nie per Token; Rückfrage und bestätigte Beziehung; Empfängertext-Fix (B3, B4) | B: `sending/OwnerSendRecipientResolver.kt`, `sending/OwnerSendIntentParser.kt`, `sending/OwnerSendDirector.kt` |
| 4 | P0 | „das“ nur auf typisierte Ergebnisse; Fremdinhalt und Systemsätze ausschließen (B2) | B: `OwnerWorkTools.kt`, `cognitive/OwnerDeliverable.kt`, `AgentActivity.kt` (Nachrichtentyp an `addBubble`) |
| 5 | P1 | Offene Lane-Fragen vor Ganzsatz-Lenkungen (B6) | B: `AgentActivity.kt` |
| 6 | P1 | Computer-Parser: Suffix mit weiterem Auftrag → `NotComputerCommand`; gemeinsames Verb-Lexikon; Katalog korrigieren (B5) | A: `cognitive/OwnerComputerIntentParser.kt`, neu `cognitive/OwnerVerbLexicon.kt`, `cognitive/OwnerLaneCatalogue.kt` |
| 7 | P1 | Planer als Satzteil-Parser; `blocked` nur für den Befehlsteil; Send- und OpenApp-Schritte erkennen | A: `cognitive/OwnerMissionPlan.kt` |
| 8 | P1 | Director: neue Schritttypen ausführen, typisierte `inputs`, Eingang `missionSend` der Send-Lane | B: `OwnerMission.kt`, `OwnerLaneOutcome.kt`, `sending/OwnerSendDirector.kt` |
| 9 | P1 | `OwnerMissionJournal` mit Idempotenzschlüsseln, Fortsetzen nach Neustart, Korrektur mit `confirmFirst` auch für den Zielschritt | B: neu `OwnerMissionJournal.kt`, `OwnerMission.kt` |
| 10 | P2 | `OwnerRelations` (bestätigte Beziehungen) + `ResolvePerson`-Erkennung | A: neu `cognitive/OwnerRelations.kt`; B nutzt es in #3 über ein Interface |
| 11 | P2 | Kalender-Wochenfenster + deterministische `AgendaCheck`-Regeln + Satzform „diese Woche“ | A: `briefing/ContinuumAgenda.kt`, `briefing/ContinuumBriefingCommand.kt` |
| 12 | P2 | Strukturierter Planaufruf (D) + konstanter Plan-System-Block; Korrektur der Ergebnis-Zuordnung (Z. 607); Gmail-Lese-Lane mit eigener Cloud-Zustimmung erst danach | B: `localmodel/ClaudeModelRuntime.kt`, `localmodel/ClaudeRequestPolicy.kt`; A: Schema-Parsing in `cognitive/OwnerMissionModelPlan.kt` |

**Nicht geprüft:**
- das tatsächliche Claude-Verhalten bei `orderPrompt` für Sätze 1, 3 und 5 (KEIN PLAN gegen Teilplan),
- die Animation der Mission-Timeline während lokaler Inferenz,
- ob vorgelesener Benachrichtigungstext über den Verlauf in Claude-Anfragen gerät,
- `OwnerUnderstanding.commandShaped` für Satz 1 (angenommen true),
- das Verhalten des lokalen Gemma-Agenten bei den Sätzen.
