# Continuum-Evaluation v1 (Code63, 17.11.2026)

Reproduzierbare Bewertungsgrundlage für das KI-Gehirn. Alle Fälle sind synthetisch; es sind keine
privaten Nutzerdaten enthalten und es dürfen ohne ausdrückliche Zustimmung keine aufgenommen werden.

## Dateien

| Datei | Fälle | Ausführung | Zweck |
|---|---|---|---|
| `regression.jsonl` | 40 | **Host, automatisch** (`ContinuumEvalDatasetTest`) | deterministische Pfade: Werkzeugwahl, Negation/Kontrast, Objektgedächtnis mit Korrekturen, Geräteuhr, bestätigte Erinnerung, Sprachsteuerbefehle |
| `regression-voice.jsonl` | 32 | **Host, automatisch** (`ContinuumEvalDatasetTest`) | Sprachrunde: Gesprächsregister, Satzende-Erkennung, Fahrmodus-Befehle, Benachrichtigungs-Ansagen (Codes, Links, Freigabestufen) |
| `regression-references.jsonl` | 32 | **Host, automatisch** (`ContinuumEvalDatasetTest`) | Code64 Bezugsverwaltung: Dokumente einer Sitzung (Reihenfolge, Name, „das andere“), Seiten- und Absatzfokus, Vergleiche mit zwei Operanden, Rückfrage bei Mehrdeutigkeit, fehlende Seiten/Absätze; Dokumente aus `regression-references.documents.json` (synthetisch) |
| `regression-news.jsonl` | 64 | **Host, automatisch** (`ContinuumEvalDatasetTest`) | Arbeitswerkzeuge (18.11.2026): Nachrichtenauftrag ja/nein (Abgrenzung zu Mitteilungen, Messenger, Benachrichtigungs-Ansage), Themen, Antworten auf die Rückfrage, Feed-Lesen (RSS 2.0, RDF, Atom), Tagesauswahl und Zusammenführen derselben Meldung; Folgeaufträge auf das Ergebnis: Speichern als PDF/Word, Dateinamen (Datum, sichere Zeichen, nie überschreiben), E-Mail-Übergabe (Adresse/Name aus dem Satz); Feeds aus `regression-news.feeds.json` (synthetisch) |
| `regression-understanding.jsonl` | 74 | **Host, automatisch** (`OwnerUnderstandingRegressionTest`) | Verstehen trotz Fehlhörung: falsch erkannte App-/Produkt-/Modellnamen in Aufträgen, österreichische Formulierungen, Beinahe-Paare mit Rückfrage, gültige Wörter und diktierter Text, die unverändert bleiben müssen; App-Bestand in `regression-understanding.apps.json` |
| `regression-chains.jsonl` | 47 | **Host, automatisch** (`OwnerTaskChainRegressionTest`) | Aufgabenketten über den echten Orchestrator, ToolGate und Guard mit nachgestelltem Geräte-Backend: Reihenfolge, Argumentübergabe, Erfolgsprüfung stoppt die Kette, Freigabe-Pause und „weiter“, Auswahl, STOP, abgelehnte Modellpläne |
| `regression-send.jsonl` | 62 | **Host, automatisch** (`ContinuumEvalDatasetTest`) | Selbst senden (18.11.2026): WhatsApp-Auftrag ja/nein (Abgrenzung zu Vorlesen, Fragen, Nachrichtenüberblick, App öffnen), Wortlaut (diktiert, indirekte Rede, Dialekt, Wunsch nach eigenem Text), Empfängerauflösung im Adressbuch (eindeutig, mehrdeutig, unbekannt, ohne Ländervorwahl), ganze Besitzerzüge durch Lenkung und Sendeablauf auf einem nachgebauten Gerät: Dauerfreigabe je Kanal, Regel a (nur Diktiertes/Bestätigtes), Regel b (Rückfrage statt Raten), Bildschirmprüfung, Tagesgrenze, Bildschirmtext als Daten, Gmail mit/ohne gespeicherte Datei, Beziehungen statt Namen („meinem Vater“: nie über Namensbestandteile, gemerkt erst nach Ja, erster Versand bestätigt), kein Empfängerwort im Text, Bestätigung nach „nicht gefunden“ (19.11.2026); Adressbuch aus `regression-send.contacts.json` (frei erfunden). WhatsApp/Gmail selbst sind **nicht** Teil dieses Laufs (Gerät: NOT_RUN) |
| `regression-library.jsonl` | 160 | **Host, automatisch** (`OwnerLibraryRegressionTest`) | Dokumentenablage (18.11.2026), **Entwicklungssatz**: „Öffne das PDF Argon4“ ja/nein (ohne Namen → Android-Auswahl, Verneinung, Zitat, Web-Formulierung, Zweitauftrag), Name wie gesagt, Spracherkennungs-Varianten (Zahlwort, klein, „öffnet“, „als Wort“, „Fasst/Fast“), Namensvergleich und Rang (genau · Anfang · enthält · Beinahe-Treffer), Antworten auf die Rückfrage, Seitenauftrag mit Speichern, Korrekturen (Seite · Format · andere Datei · Nummer · Name), Stopp, Abgrenzung zu den anderen Lenkungen |
| `regression-library-check.jsonl` | 74 | **Host, automatisch** (`OwnerLibraryRegressionTest`) | Dokumentenablage, **zurückgehaltener Prüfsatz**: dieselben Läufer mit anderen Sätzen; vor der Umsetzung geschrieben und eingecheckt (`b058417cd`), wird nicht zum Abstimmen benutzt |
| `regression-missions.jsonl` | 58 | **Host, automatisch** (`OwnerMissionRegressionTest`) | Auftragsfolgen (18.11.2026), **Entwicklungssatz**: ein Satz mit mehreren Aufträgen für die Besitzer-Lenkungen → geordneter Plan (Lenkung und Argumente je Schritt) oder `NONE`. Besitzerabläufe (PDF öffnen → Seite zusammenfassen → Word → Mail; Nachrichten → PDF → Mail; neuestes PDF suchen → öffnen), Verbinder (Komma, „und“, „und dann“, „danach“, „anschließend“, „dann“, „zuerst“), Bezüge („es“, „sie“, „die Datei“), unabhängige Aufträge hintereinander; dagegen Einzelaufträge, Fragen, Zitate, Verneinungen, Bedingungen, „und“ in Dateinamen und Nachrichtentexten, Bezüge ohne Erzeuger, fremde Teilaufträge, zu viele Schritte |
| `regression-missions-check.jsonl` | 49 | **Host, automatisch** (`OwnerMissionRegressionTest`) | Auftragsfolgen, **zurückgehaltener Prüfsatz**: derselbe Läufer mit anderen Sätzen; vor der Umsetzung geschrieben und eingecheckt (Commit „test(eval): zurückgehaltener Prüfsatz für Auftragsfolgen vor der Umsetzung“), wird nicht zum Abstimmen benutzt |
| `regression-coding-agents.jsonl` | 56 | **Host, automatisch** (`CodingAgentOrderRegressionTest`) | Coding-Agenten (19.11.2026), **Entwicklungssatz**: welche Sätze ein Auftrag für die Cloud-Coding-Agenten sind, für welches Repository der Freigabeliste und welcher Art (Coding-Auftrag / Sicherheitsprüfung) – Auftragsformen des Besitzers („Coding-Agent: …“, „Lass den Agenten in <Repo> …“, „Der Coding-Agent soll …“, „Gib dem Coding-Agenten den Auftrag …“), Repository-Auflösung (genannt, als Link, Besitzer/Name, keines genannt bei einem oder mehreren freigegebenen, fremdes Konto, nicht freigegeben), Bereich öffnen, Stopp, Status; dagegen Fragen, Aussagen, Zitate, Verneinungen, Bedingungen, eingebettete Aufträge, bloßes „Agent“ ohne Repository (lokales Team) |
| `regression-coding-agents-check.jsonl` | 51 | **Host, automatisch** (`CodingAgentOrderRegressionTest`) | Coding-Agenten, **zurückgehaltener Prüfsatz**: derselbe Läufer mit anderen Sätzen; vor dem Parser eingecheckt (Commit „test(eval): zurückgehaltener Prüfsatz für Coding-Agenten-Aufträge vor der Umsetzung“), wird nicht zum Abstimmen benutzt |
| `regression-devicetools.jsonl` | 125 | **Host, automatisch** (`DeviceToolRegressionTest`) | Gerätewerkzeuge (19.11.2026), **Entwicklungssatz**: Gerätesuche über Apps, Dateien und Ordner (Rang genau · Anfang · enthält, Zahlwörter, Umlaute, Typwörter, Ordnernamen, Beinahe-Treffer), Schaltbefehle für die Cloud-Freigaben (Namen · Inhalte) und „Dokumente ohne Rückfrage“, Entscheidung vor dem Start eines Werkzeugs (im Prozess · Shizuku-Brücke · abgelehnt mit Grund: Brücke läuft nicht, Dateizugriff fehlt, Cloud-Freigabe fehlt, nur Systemzentrum, Anzeigen nicht verlangt, Dokumentgrenze), Vorab-Freigabe eines Dokuments, Modellpläne mit den neuen Werkzeugen (gültig/abgelehnt), Kettenläufe über den echten Orchestrator und das ToolGate mit nachgestelltem Backend in den echten Ausgabeformaten, Dokument-Auszeichnung (Überschrift · Liste · Absatz), Seitenbereiche; Geräte-Bestand in `regression-devicetools.fixture.json` (frei erfunden) |
| `regression-devicetools-check.jsonl` | 94 | **Host, automatisch** (`DeviceToolRegressionTest`) | Gerätewerkzeuge, **zurückgehaltener Prüfsatz**: dieselben Läufer mit anderen Fällen; vor der Umsetzung geschrieben und eingecheckt (Commit „test(eval): Prüfsätze für die Gerätewerkzeuge vor der Umsetzung“), wird nicht zum Abstimmen benutzt |
| `regression-incoming.jsonl` | 130 | **Host, automatisch** (`IncomingMessageRegressionTest`) | Eingehende Nachrichten melden (19.11.2026), **Entwicklungssatz**: Absender, Betreff, Text und App einer frei erfundenen Nachricht → melden · nicht melden · unklar, jeweils mit Grund. Gerichte und Behörden (Bezirksgericht, Landesgericht, Staatsanwaltschaft, Finanzamt/FinanzOnline, Magistrat, BH, ÖGK, PVA, AMS, Gemeinde, Polizei, gv.at; auch deutsche), Institute (Bank, Versicherung, Schule, Ordination/Klinik, Kanzlei/Notar, Steuerberatung, Hausverwaltung, Inkasso), amtliche Sachen im Betreff (Bescheid, Ladung, Mahnung, Zahlungserinnerung, Kündigung, RSa/RSb, Aktenzeichen, Frist), Werbung (Rabatt, Gutschein, Newsletter, Sale, „jetzt sichern“, Marketing-Adressen, Institute mit Werbung, „Gerichte der Woche“), Sammelmeldungen sozialer Netzwerke, Personen (Kontakt, Personenname mit und ohne Titel), Messenger (Kontakt, unbekannte Nummer, Geschäftskonto, Gruppe aus/an), Besitzerlisten „immer melden“/„nie melden“, unklare Fälle (Paket, Rechnung, Sicherheitswarnung) |
| `regression-incoming-check.jsonl` | 78 | **Host, automatisch** (`IncomingMessageRegressionTest`) | Eingehende Nachrichten melden, **zurückgehaltener Prüfsatz**: derselbe Läufer mit anderen Absendern und Betreffen; vor dem Klassifikator geschrieben und eingecheckt (Commit „test(eval): zurückgehaltener Prüfsatz für eingehende Nachrichten vor der Umsetzung“), wird nicht zum Abstimmen benutzt |
| `regression-briefing.jsonl` | 94 | **Host, automatisch** (`ContinuumBriefingRegressionTest`) | Systemstart-Bericht und Tagesbriefing (19.11.2026), **Entwicklungssatz**: welcher Satz ist ein Auftrag für Systemcheck, Briefing (mit/ohne Nachrichtenteil), Termine heute/morgen, Wetter (gespeicherter oder genannter Ort), Ortseinstellung, Anruf über die Wähltastatur, das tägliche Briefing-Angebot und den Nachrichtenteil – und welcher Satz behält seine heutige Lenkung (`NONE`: Dokument- und Filmfragen, Nachrichtenaufträge, Geräteuhr, Gruß, Fähigkeitenliste, Systemdiagnose, Sendeaufträge, Dokumentenablage, Routinen, Arbeitsjournal, Verneinung, Zitat, wiedergegebene Rede, Kalender schreiben); dazu die Antwort auf die Folgeangebote („Entscheide du die Aktion“) |
| `regression-briefing-check.jsonl` | 86 | **Host, automatisch** (`ContinuumBriefingRegressionTest`) | Systemstart-Bericht und Tagesbriefing, **zurückgehaltener Prüfsatz**: dieselben Läufer mit anderen Sätzen; vor der Umsetzung geschrieben und eingecheckt (Commit „test(eval): Sätze für Briefing und Systemstart vor der Umsetzung“), wird nicht zum Abstimmen benutzt |
| `regression-talk.jsonl` | 194 | **Host, automatisch** (`OwnerTalkRegressionTest`) | Gesprächssatz (19.11.2026), **Entwicklungssatz**: Gesprächston-Befehl („Sprich persönlich mit mir“ / „Sprich wieder sachlich“), „Nenn mich …“ und Widerruf, reiner Gruß ja/nein, Schalter der Begrüßung, Pause aus zwei Zeitstempeln (Stufen und Rundung), Reparatur verirrter Tastaturpunkte, „Erstelle eine PDF“ ohne Zeigewort, Satz als Auftragsfolge, was das Cloud-Gehirn auf einen Auftrag abbilden darf (und was verworfen wird), direkte Frage nach dem laufenden Modell, Suche der Ablage in der Wunschform, zerlegter Name als ähnlicher Treffer. Enthält die zwei Besitzer-Feldfälle vom Fold7 (`category: field`) |
| `regression-talk-check.jsonl` | 114 | **Host, automatisch** (`OwnerTalkRegressionTest`) | Gesprächssatz, **zurückgehaltener Prüfsatz**: dieselben Läufer mit anderen Sätzen; wird nicht zum Abstimmen benutzt |
| `regression-deepclean.jsonl` | 152 | **Host, automatisch** (`DeepCleanRegressionTest`) | Speicher & Bereinigung (19.11.2026), **Entwicklungssatz**: welcher Satz welcher Auftrag mit welchen Rechten ist – nur prüfen (`ANALYSE`), sicher bereinigen (`SAFE_CLEAN`, nur GRÜN), Tiefenanalyse/-bereinigung (`DEEP_ANALYSE`/`DEEP_CLEAN`, GELB nur zur Auswahl), Optimierung (`OPTIMISE`, nie löschen), gezieltes Löschen (`TARGETED:<Gruppe>` / `TARGETED:NAME:<Datei>`), Bericht, Wiederherstellen, Bildschirm; Einschränkungen und Verneinungen (`NO_DELETE`, `KEEP:<Gruppen>`, `UNSURE`), Rückfragen (`CLARIFY`, `DECLINE`); dagegen fremde Sätze (`NONE`: Mail, Kalender, Bildschirm, Systemdiagnose, eigener Cache der Systemzentrale, Zitate, Anleitungsfragen) und die Antworten auf die nummerierte Auswahl (`deepclean_reply`) |
| `regression-deepclean-check.jsonl` | 75 | **Host, automatisch** (`DeepCleanRegressionTest`) | Speicher & Bereinigung, **zurückgehaltener Prüfsatz**: dieselben Läufer mit anderen Sätzen; zusammen mit dem Entwicklungssatz geschrieben und nach dem ersten Lauf **nicht** zum Abstimmen benutzt – Fehltreffer werden als `KNOWN_CHECK_MISSES` festgehalten |
| `regression-mail.jsonl` | 161 | **Host, automatisch** (`OwnerMailRegressionTest`) | Gmail-Lenkung (19.11.2026), **Entwicklungssatz**: Satz + offener Mailkontext (`context`: NONE · LIST · MAIL · DRAFT · CONFIRM) → Route der Lenkung (`CONNECT`, `NEW`, `SEARCH_FROM:…`, `READ:n`, `PICK:n`, `SUMMARIZE`, `DEADLINES`, `DRAFT:<an>|<Text>`, `REPLY:…`, `SEND`, `CONFIRM:YES/NO`, `STOP` …) oder `NONE`; Fälle mit `keeps` prüfen zusätzlich, dass die genannte Lenkung (SEND, SHARE, DESK, EXPORT, BRIEFING, NEWS) den Satz wirklich nimmt – „Schick das per Mail an Anna“ bleibt die bestehende Weitergabe |
| `regression-mail-check.jsonl` | 80 | **Host, automatisch** (`OwnerMailRegressionTest`) | Gmail-Lenkung, **zurückgehaltener Prüfsatz**: vor dem Parser eingecheckt (Commit „test(eval): Mail-Sätze und zurückgehaltener Prüfsatz vor der Umsetzung“), wird nicht zum Abstimmen benutzt; Fehltreffer sind im Test festgehalten |
| `model-dev.jsonl` | 34 | **Gerät, lokales Modell** – NOT_RUN | Entwicklungs-/Abstimmungssatz für Prompt-, Kontext- und ggf. Adapteranpassung |
| `model-holdout.jsonl` | 34 | **Gerät, lokales Modell** – NOT_RUN | fester Abschlusstest; darf nie für Training, Prompt-Abstimmung oder Fehlersuche verwendet werden |
| `model-holdout.sha256` | – | Host | Prüfsumme des Holdouts; eine Änderung lässt den Test fehlschlagen |

## Schema

Regression (`regression.jsonl`), ein JSON-Objekt je Zeile:

- `id`, `category`, `note`
- `runner`: `route` · `object_state` · `clock` · `owner_recall` · `voice_command`;
  in `regression-voice.jsonl` zusätzlich `register` · `turn_completion` · `drive_command` · `notification`;
  in `regression-references.jsonl` `reference`;
  in `regression-news.jsonl` `news_intent` · `news_follow_up` · `news_feed` · `export_intent` · `file_name` · `mail_intent`
- Eingabe je Läufer: `input` bzw. `turns` + `question`, `facts`, `now` + `zone`;
  für `reference`: `documents` (Schlüssel der Fixture-Datei, in Öffnungsreihenfolge), `turns` (frühere Nutzerzüge;
  `open:<Schlüssel>` öffnet mitten im Gespräch ein weiteres Dokument) und `input`
- `expect` (formales Kriterium): `computer` (`READY:<APP>:<ZIEL>` oder `NOT_READY`), `tool` (Name oder `null`),
  `forbidden_apps`, `contains`, `not_contains`, `abstain`, `command`;
  für `reference`: `kind` (`RESOLVED` · `AMBIGUOUS` · `MISSING` · `NONE`), `document`, `page`, `paragraph`, `intent`
  (`FOCUS` · `QUOTE` · `QUESTION` · `COMPARISON`), `page_assumed`, `compared_document`, `compared_page`;
  `contains`/`not_contains` prüfen den aufgelösten Stellentext bzw. die Rückfrage oder Begründung
- `regression-news.jsonl`: `news_intent` erwartet `news` (`GENERAL` · `TOPICS:<Thema>|<Thema>` · `NONE`);
  `news_follow_up` (`immediate`, `briefed` = Wörter der genannten Schlagzeilen) erwartet `follow_up`
  (`TOPICS:…` · `STORY:<n>` · `ASK` · `DECLINE` · `NONE`); `news_feed` (`feeds` = Schlüssel der Feed-Datei, `now`,
  `zone`) erwartet `count`, `first`, `contains`, `not_contains` über die Zeilen
  `Haus: Schlagzeile | weiteres Haus` der ausgewählten Meldungen; `export_intent` erwartet `export`
  (`PDF` · `DOCX` · `PDF:VIA_WORD` · `NONE`) und optional `name`; `file_name` (`requested`, `title`, `date`, `format`,
  `taken`) erwartet `file_name`; `mail_intent` erwartet `mail` (`ADDRESS:<Adresse>` · `NAME:<Empfänger wie gesagt>` · `NONE`)
- `regression-send.jsonl`: `send_intent` erwartet `send` (`ORDER` · `REFUSED:NEGATED|SEVERAL|GROUP|TEXT` · `NONE`) und
  optional `recipient`, `text` (`null` = Wortlaut wird erfragt), `origin` (`DICTATED` · `MINIMAL_TRANSFORM` · `DERIVED`),
  `unsplit`, `draft`, `model_wish`; `send_recipient` (`phrase`) erwartet `resolution` (`RESOLVED:<Ziffern>` ·
  `AMBIGUOUS:<Anzahl>` · `NOT_FOUND` · `UNUSABLE`); `send_flow` (`granted` = Kanäle mit Dauerfreigabe, `turns` =
  Besitzerzüge, dabei `mail-file:<Empfänger>` / `mail-text:<Empfänger>` = Mailauftrag der Arbeitswerkzeuge mit/ohne
  gespeicherte Datei, `screen` = `OK` · `WRONG_CHAT` · `NO_SEND_CONTROL` · `NOT_CONFIRMED` · `INJECTION`, optional
  `already_sent`) erwartet `pressed` (Zahl der gedrückten Senden-Knöpfe) und optional `opened`, `model_calls`,
  `grants_after`, `handled`, `contains`/`not_contains` über die letzte Antwort
- `regression-library.jsonl` / `regression-library-check.jsonl`: `library_open` erwartet `open` (`OPEN:<Name wie gesagt>` ·
  `FIND:<Name>` = nur suchen · `NONE`) und optional `folder` (`Download` · `Documents`); `library_match` (`query`,
  `files` = `Ordner/Name.pdf` oder `Name.pdf`, optional `folder`) erwartet `decision` (`OPEN:<Datei>` · `ASK:<Anzahl>` ·
  `NEAR:<Datei>` · `NONE`) und optional `first`; `library_choice` (`hits` = `Name|Ordner|Änderungstag`) erwartet
  `choice` (`PICK:<n>` · `INVALID` · `DROP` · `NONE`); `page_job` erwartet `page_job` (`PAGE:<n|LAST>:<DOCX|PDF|ASK_FORMAT>` ·
  `UNSUPPORTED` = mehrere Seiten · `NONE`) und optional `name`; `desk_correction` erwartet `correction` (`PAGE:<n|LAST>` ·
  `FORMAT:<PDF|DOCX>` · `OTHER_FILE` · `PICK:<n>` · `NAME:<Namensteil>` · `NAMED_PDF:<Name>` · `NONE`); `desk_stop`
  erwartet `stop`; `library_route` erwartet `route` (`DESK_OPEN` · `DESK_PAGE` · `PICKER` · `EXPORT` · `NEWS` · `CHAIN` ·
  `MAIL` · `COMPUTER` · `OTHER`) in der Reihenfolge der Lenkungen der Activity
- `regression-missions.jsonl` / `regression-missions-check.jsonl`: `mission_plan` erwartet `plan` – entweder `NONE` (der Satz
  behält seine heutige Lenkung) oder die Schritte in Reihenfolge: `OPEN:<Name wie gesagt>` (PDF suchen und öffnen) ·
  `FIND:<Name>` (nur suchen) · `PAGE:<n|LAST>:<DOCX|PDF|ASK>` (Seite zusammenfassen und speichern) · `NEWS` · `EXPORT:<PDF|DOCX>` ·
  `MAIL:<Adresse oder Empfänger wie gesagt>` · `FILE:<NEWEST|SEARCH>` (Rezept der Aufgabenketten) · `OPEN_FOUND` (die gefundene
  Datei öffnen) · `OPEN_SAVED` (die gerade gespeicherte Datei öffnen)
- `regression-coding-agents.jsonl` / `regression-coding-agents-check.jsonl`: `coding_order` erwartet `order` – `NONE` (der
  Satz behält seine heutige Lenkung) · `PANEL` · `STOP` · `STATUS` · oder `<CODE|SECURITY>:<Ziel>` mit Ziel `<Besitzer/Name>`
  (genannt und freigegeben) · `ONLY:<Besitzer/Name>` (keines genannt, genau eines freigegeben) · `ASK` (Rückfrage) ·
  `UNKNOWN:<wie gesagt>` (genannt, nicht freigegeben) · `NO_REPOS` (nichts freigegeben). Optional `task`: der Wortlaut,
  der als Auftrag übergeben wird. `repos` ist die Freigabeliste des Falls; fehlt sie, gilt
  `Ullmann27/device-ai`, `Ullmann27/continuum-web`. Anrede des Assistenten im Läufer: „Jarvis“
- `regression-incoming.jsonl` / `regression-incoming-check.jsonl`: `incoming_importance` bekommt `package` (App), `sender`,
  `subject` (nur E-Mail), `text`, optional `contact` (Absender steht in den Kontakten), `group` + `conversation`
  (Gruppenchat), `groups_on` (Besitzerschalter Gruppen), `always` / `never` (Besitzerlisten) und erwartet `verdict`
  (`ANNOUNCE` · `SILENT` · `UNCLEAR`) und `reason` (`OWNER_ALWAYS` · `OWNER_NEVER` · `GROUP_CHAT` · `CONTACT` · `AUTHORITY` ·
  `INSTITUTION` · `OFFICIAL_MATTER` · `PROMOTION` · `SOCIAL_DIGEST` · `GROUP_MESSAGE` · `DIRECT_MESSAGE` · `PERSONAL_NAME` ·
  `UNCLEAR`); beides muss stimmen. Es gibt keine Eingabesätze des Besitzers in diesen Dateien
- `regression-briefing.jsonl` / `regression-briefing-check.jsonl`: `briefing_route` erwartet `route` – `SYSTEM_CHECK` ·
  `BRIEFING` · `BRIEFING:NEWS` (Nachrichtenteil für dieses eine Mal) · `APPOINTMENTS:<TODAY|TOMORROW>` ·
  `WEATHER:<TODAY|TOMORROW>` bzw. `WEATHER:<Tag>@<Ort wie gesagt>` · `PLACE:<Ort>` (Ortseinstellung für das Wetter) ·
  `CALL:<Name oder Nummer wie gesagt>` (Wähltastatur öffnen) · `OFFER:<Stunde|ON|OFF>` (tägliches Briefing-Angebot) ·
  `NEWS_PART:<ON|OFF>` · `NONE` (der Satz behält seine heutige Lenkung); optional `keeps` = die Lenkung, die den Satz
  heute nachweislich annimmt (`NEWS` · `CLOCK` · `GREETING` · `CAPABILITIES` · `DIAGNOSIS` · `SEND` · `DESK` · `ROUTINE` ·
  `RECALL`) – der Test prüft sie am echten Parser dieser Lenkung. `briefing_choice` (`offers` = die angebotenen
  nächsten Schritte in Reihenfolge: `APPOINTMENTS` · `MESSAGE` · `WORK` · `NEWS` · `DIAGNOSIS` · `WEATHER`) erwartet
  `choice` (`PICK:<n>` · `ASK` = Ja bei mehreren Angeboten · `DECLINE` · `NONE` = keine Antwort auf das Angebot)

Modellfälle (`model-*.jsonl`):

- `id`, `category`, `turns` (`role` = `user`/`assistant`, letzter Zug immer `user`), `rubric`
- `expect.type`: `contains_all` · `contains_any` · `numeric` · `abstain` · `asks_back` · `tool_call` · `no_tool`
- `expect.values`, `expect.tool`, `expect.forbidden_tools`

Kategorien (jede in Dev und Holdout mindestens einmal): `long_conversation`, `dialect`, `ambiguity`, `pronoun`,
`back_reference`, `correction`, `negation`, `temporal`, `multi_step`, `math`, `document`, `tool_choice`,
`interruption`, `incomplete_info`, `uncertainty_abstention`, `contradiction`, `abort_resume`.

## Gemessener Stand (Host, 17.11.2026)

Dieselben 40 Regressionsfälle, einmal gegen die unveränderten Klassen der Integrationsbasis
(`3613a23a7` + Cinema63-Merge `a2bb26fa6`), einmal gegen den Code63-Arbeitsstand:

| Stand | bestanden | fehlgeschlagen |
|---|---|---|
| vorher | 31 / 40 | `REG-CON-001…003`, `REG-TOOL-002`, `REG-COR-001…003`, `REG-AMB-001`, `REG-PRO-001` |
| nachher | 40 / 40 | – |

Das ist ein Vergleich deterministischer Pfade, **keine** Aussage über die Antwortqualität des lokalen Modells.

Für `regression-voice.jsonl` gibt es keinen Vorher-Wert: die geprüften Bausteine existierten vorher nicht.
Stand: 32 / 32.

Für `regression-references.jsonl` (Code64, 18.11.2026) gilt dasselbe: die Bezugsverwaltung existierte vorher nicht.
Stand (Host): 32 / 32.

Für `regression-news.jsonl` (Arbeitswerkzeuge, 18.11.2026) gibt es ebenfalls keinen Vorher-Wert: Nachrichtenauftrag,
Feed-Leser und Auswahl existierten vorher nicht (Feldbefund: das lokale Modell antwortete mit einer Meta-Erklärung,
ohne ein Web-Werkzeug zu benutzen). Stand (Host): 64 / 64. Geprüft sind die deterministischen Pfade; ob die
Zusammenfassung des lokalen Modells auf dem Gerät menschlich klingt, ist NOT_RUN, ebenso das Schreiben der
PDF-Datei (Android-`PdfDocument`), das Öffnen und die Übergabe an das Mailprogramm auf dem Gerät.
Für `regression-understanding.jsonl` und `regression-chains.jsonl` (18.11.2026) stehen Vorher/Nachher und Messweg in [OWNER-UNDERSTANDING-AND-TASK-CHAIN-20261118.md](../../research/OWNER-UNDERSTANDING-AND-TASK-CHAIN-20261118.md):
Verstehen 30 / 74 ohne die Schicht (genau die Schutzfälle) → 74 / 74; Ketten 47 / 47, vorher lief für 1 von 29 Ketten dieselbe Werkzeugfolge (rohe Ordnerliste ohne die gefragte Antwort).
Beide Sätze haben eigene Läufer (`understand`, `chain`) und eigene Testklassen; `ContinuumEvalDatasetTest` bleibt unverändert.

Für `regression-library.jsonl` und `regression-library-check.jsonl` (Dokumentenablage, 18.11.2026) gibt es keinen
Vorher-Wert: die lokale Suche nach Namen und der Seitenauftrag mit Speichern existierten vorher nicht („Öffne das PDF
Argon4“ öffnete nur die Android-Auswahl, „Fass Seite 2 zusammen und speichere es als Word“ wurde ohne Speichern im
Chat beantwortet). Getrennte Daten: Der Prüfsatz wurde zuerst geschrieben und eingecheckt, der Entwicklungssatz
danach; kein Satz steht in beiden (der Test prüft das). Gemessen am Host:

| Lauf | Entwicklungssatz | Prüfsatz |
|---|---|---|
| erster Lauf nach der Umsetzung, vor jeder Anpassung | 146 / 147 | 74 / 74 |
| nach Behebung (nachgestelltes „… meinte ich“ bei Dateinamen) und Erweiterung um Fehlhörungen und Abgrenzungsfälle | 160 / 160 | 74 / 74 |

Im Prüfsatz gab es keinen Fehlschlag, also auch keine nachträgliche Korrektur an ihm oder wegen ihm. Einordnung: Den
Prüfsatz hat dieselbe Hand geschrieben wie die Parser, mit der geplanten Auftragsform vor Augen – er belegt, dass
nicht auf ihn abgestimmt wurde, nicht, dass fremde Formulierungen des Besitzers getroffen werden. Was die
Spracherkennung am Fold7 wirklich schreibt, ist NOT_RUN; ebenso Speichergang, PDF-Textauszug, Modell-Zusammenfassung
und das Schreiben nach Download auf dem Gerät.

Für `regression-devicetools.jsonl` und `regression-devicetools-check.jsonl` (Gerätewerkzeuge, 19.11.2026) gibt es keinen
Vorher-Wert: Gerätesuche, Cloud-Freigaben und die Entscheidung vor dem Start eines Werkzeugs gab es nicht. Beide Sätze
wurden vor der Umsetzung geschrieben und eingecheckt; kein Fall steht in beiden (der Test prüft das). Gemessen am Host
(`DeviceToolRegressionTest`): erster Lauf nach der Umsetzung, vor jeder Anpassung – Entwicklungssatz **125 / 125**,
Prüfsatz **94 / 94**. Läufer: `device_search` (`expect.first`/`total`/`contains`/`absent`/`near`), `consent_command`
(`expect.command`), `gate` (`expect.decision`: `RUN:NATIVE` · `RUN:BRIDGE` · `RUN:OTHER` · `REFUSED:<Grund>`),
`preapproval` (`PREAPPROVED` · `ASK`), `chain_plan` (`VALID` · `REJECTED`), `chain_run` (wie `regression-chains.jsonl`),
`document_blocks` (`H:` · `L:` · `P:`), `page_range`. Geräteverhalten: NOT_RUN.
In den Kettenfällen heißt der Besitzersatz `owner_task` statt `input`: er ist der Auftrag, mit dem ein Modellplan geprüft
wird, kein gelenkter Satz (`OwnerMissionRegressionTest` prüft jedes `input` des Ordners gegen die Auftragsfolgen, und
„Such die Datei … und zeig sie mir“ ist in der App eine Auftragsfolge der Dokumentenablage). Diese Umbenennung des
Schlüssels ist die einzige Änderung an beiden Sätzen nach der Umsetzung; kein Fall und keine Erwartung wurden berührt.
Für `regression-talk.jsonl` und `regression-talk-check.jsonl` (Gesprächssatz, 19.11.2026): Läufer `tone_command`
(`tone`: `PERSONAL` · `FACTUAL` · `NONE`), `name_command` (`name`: `SET:<Name>` · `FORGET` · `NONE`), `greeting`
(`YES` · `NO`), `greeting_switch` (`ON` · `OFF` · `NONE`), `absence` (`last`, `now`, `zone` → `NONE` · `BRIEF` · die
gesprochene Dauer), `input_repair` (`text`), `make_export` (`PDF` · `DOCX` · `NONE`), `owner_order` (Satz nach der
Punktreparatur → Plan wie bei den Auftragsfolgen oder `NONE`), `order_wanted` (würde das Cloud-Gehirn gefragt),
`order_mapping` (`model` = Modelltext → `RUN:<Plan>` ohne Rückfrage · `ASK:<Plan>` erst nach „Ja“ · `REJECTED`),
`identity_question` (`YES` · `NO`), `desk_search` (`FIND:<Name>` · `OPEN:<Name>` · `NONE`), `near_name` (`files` →
`EXACT:` · `NEAR:<Datei>` · `NONE`).

Die ersten 136 + 80 Fälle wurden vor jeder Umsetzung geschrieben und eingecheckt (`f541508c9`). Die weiteren 58 + 34
Fälle kamen in der Fortsetzung dazu: `greeting_switch`, `identity_question`, `desk_search` und `near_name` vor dem
Code, den sie prüfen; `absence` nach dem gesicherten Zwischenstand, der die Pause schon berechnete. Kein Fall steht in
beiden Sätzen (der Test prüft das). Gemessen am Host, echtes Modell und Gerät `NOT_RUN`:

| Lauf | Entwicklungssatz | Prüfsatz |
|---|---|---|
| erster Lauf auf dem gesicherten Zwischenstand (136 + 80 Fälle) | 135 / 136 | 80 / 80 |
| erste Ausführung der neuen Fälle nach ihrer Umsetzung (58 + 34 Fälle) | 58 / 58 | 34 / 34 |
| Endstand | 194 / 194 | 114 / 114 |

Der eine Fehler des ersten Laufs war `TLK-MAP-010` (Feldfall b): „Such das PDF a und 4 Experimente“ wurde wegen des
„und“ im Namen nicht als Suchauftrag erkannt. Behoben am Code – eine reine Suche darf ein „und“ im Namen tragen, wenn
danach nichts wie ein zweiter Auftrag aussieht –, der Fall selbst blieb unverändert. Kein Prüffall wurde angepasst.

Für `regression-missions.jsonl` und `regression-missions-check.jsonl` (Auftragsfolgen, 18.11.2026) gibt es keinen
Vorher-Wert: einen Planer für mehrere Aufträge in einem Satz gab es nicht (der Satz ging als Ganzes an die erste
Lenkung, die einen Teil erkannte, oder an das Modell). Der Prüfsatz wurde zuerst geschrieben und eingecheckt, der
Entwicklungssatz danach; kein Satz steht in beiden (der Test prüft das). Gemessen am Host:

| Lauf | Entwicklungssatz | Prüfsatz |
|---|---|---|
| erster Lauf nach der Umsetzung, vor jeder Anpassung | 53 / 57 | 47 / 49 |
| nach der Regel für gleich feine Schnitte und der Korrektur eines Entwicklungsfalls | 58 / 58 | 49 / 49 |

Die vier Fehlschläge im Entwicklungssatz: drei Sätze mit Komma nach dem Dateinamen (darunter der Besitzersatz „Öffne das
PDF Argon4, fass Seite 2 zusammen, …“) – die Ablage liest „Argon4, fass Seite 2 zusammen“ auch als Dateinamen, zwei
gleich feine Schnitte galten als mehrdeutig; behoben mit der Regel, dass von gleich feinen Schnitten derjenige gilt,
der jeden Auftrag am ersten Verbinder beendet, an dem er vollständig ist. Der vierte war ein falsch erwarteter Fall
(„Schlagzeilen, dann speichere das als PDF“ liest der Export heute als einen Auftrag; er steht jetzt als `NONE` im
Satz, ein neuer Fall deckt die Form mit „Gib mir die Schlagzeilen und dann …“ ab). Die zwei Fehlschläge im Prüfsatz
(MISC-001 Komma nach dem Dateinamen, MISC-013 Satzende nach dem Dateinamen) hatten dieselbe Ursache und waren im
selben Lauf sichtbar; für sie wurde nichts Eigenes geändert, sie bestehen seit der Regel aus dem Entwicklungssatz.
Kein Satz, der seine Lenkung behalten muss, wurde in einem der beiden Sätze je zu einem Plan. Einordnung wie oben:
dieselbe Hand hat Prüfsatz und Planer geschrieben. Bewusst als `NONE` festgelegt und im Entwicklungssatz belegt: ein
Satz, den eine Lenkung heute als einen Auftrag liest („Fass mir die Nachrichten von heute zusammen und speichere sie
als PDF“ ist ein Export-Auftrag), WhatsApp-Aufträge als Schritt, „schick sie an Anna“ ohne „Mail“ oder Adresse.
Was auf dem Gerät passiert – Spracherkennung, Dateizugriff, Modell, Mailprogramm, Gmail – ist NOT_RUN.

Für `regression-coding-agents.jsonl` und `regression-coding-agents-check.jsonl` (Coding-Agenten, 19.11.2026) gibt es
keinen Vorher-Wert: eine Lenkung für Cloud-Coding-Agenten gab es nicht. Gemessen am Host, erster und einziger Lauf:

| Lauf | Entwicklungssatz | Prüfsatz |
|---|---|---|
| erster Lauf, Parser unverändert | 56 / 56 | 51 / 51 |

Einordnung, damit die Zahl nicht mehr sagt, als sie belegt: Der Prüfsatz wurde vor dem Parser eingecheckt. Der Parser
stammt aus einem abgebrochenen Arbeitsgang derselben Hand und wurde unverändert übernommen; ob er dort schon gegen den
Prüfsatz gelaufen ist, ist nicht belegt. Der Entwicklungssatz wurde erst danach geschrieben, in Kenntnis des
Prüfsatzes (dessen Schema daraus abgelesen wurde); kein Satz steht in beiden (der Test prüft das), unabhängig
voneinander entstanden sind die beiden Sätze aber nicht. Nach den Messungen wurde am Parser nichts geändert. Der Test
prüft zusätzlich, dass kein Satz der übrigen Sätze dieses Ordners zu einem Coding-Agenten-Auftrag wird und dass nie ein
Repository außerhalb der Freigabeliste des Falls vorgeschlagen wird. Was die Spracherkennung am Fold7 aus „Coding-Agent“
macht, ist NOT_RUN; ebenso jeder Lauf bei Anthropic und jede Antwort von GitHub.
Für `regression-incoming.jsonl` und `regression-incoming-check.jsonl` (Eingehende Nachrichten melden, 19.11.2026) gibt
es keinen Vorher-Wert: eine Einordnung nach Wichtigkeit gab es nicht (die bisherigen Ansagen kannten nur „App
freigegeben oder nicht“). Der Prüfsatz wurde zuerst geschrieben und eingecheckt, der Entwicklungssatz danach, beide vor
dem Klassifikator; kein Fall steht in beiden (der Test prüft das). Gemessen am Host:

| Lauf | Entwicklungssatz | Prüfsatz |
|---|---|---|
| erster Lauf nach der Umsetzung, vor jeder Anpassung | 129 / 130 | 78 / 78 |
| nach der Korrektur für „Ihr Bescheid“ | 130 / 130 | 78 / 78 |

Der eine Fehlschlag im Entwicklungssatz (INC-059 „Ihr Bescheid ist abrufbar“): die Ausnahme für die Alltagswendung „gib
ihr Bescheid“ hatte auch das Anredewort „Ihr“ getroffen; behoben, indem „Bescheid geben/sagen“ als Wendung erkannt
und vor der Suche nach amtlichen Begriffen entfernt wird. Im Prüfsatz gab es keinen Fehlschlag, also auch keine
Korrektur an ihm oder wegen ihm. Einordnung, ausdrücklich: Prüfsatz, Entwicklungssatz und Regeln stammen aus derselben
Hand und wurden hintereinander geschrieben – die Regelidee stand beim Schreiben des Prüfsatzes schon fest. 78 / 78 belegt
deshalb, dass nicht nachträglich auf den Prüfsatz abgestimmt wurde, **nicht**, dass echte Absender und Betreffe des
Besitzers so gut getroffen werden. Die Wortlisten sind endlich (österreichisch/deutsch); ein Institut, das sie nicht
kennen, endet als „unklar“ und wird in der Voreinstellung kurz gemeldet, nicht verschwiegen. Was eine Benachrichtigung
von WhatsApp oder Gmail auf dem Fold7 wirklich enthält (Absendername statt Adresse, Betreff, Gruppen-Kennzeichen) und ob
das Signal ohne Benachrichtigungsdienst überhaupt mit Inhalt ankommt, ist NOT_RUN
(`docs/handover/NACHRICHTENEINGANG-GERAETECHECKLISTE-20261119.md`).
Für `regression-briefing.jsonl` und `regression-briefing-check.jsonl` (Systemstart-Bericht und Tagesbriefing,
19.11.2026) gibt es keinen Vorher-Wert: eine Lenkung für Systemcheck, Briefing, Termine, Wetter und Anrufe gab es
nicht (die Sätze gingen an das Modell, das weder Kalender noch Wetter noch Gerätezustand kennt). Beide Sätze wurden vor
der Umsetzung geschrieben und eingecheckt; kein Fall steht in beiden (der Test prüft das). Gemessen am Host:

| Lauf | Entwicklungssatz | Prüfsatz |
|---|---|---|
| erster Lauf nach der Umsetzung, vor jeder Anpassung | 94 / 94 | 86 / 86 |

Es gab keinen Fehlschlag, also auch keine Anpassung wegen eines Falls und keine Änderung an einem der Sätze. Danach kamen
nur Schutzwörter hinzu, die kein Fall verlangt hatte (eine Zeitspanne ist kein Ort: „in zwei Wochen“; Notruf-Wörter beim
Anruf); beide Sätze stehen danach unverändert bei 94 / 94 und 86 / 86. Einordnung, ehrlich: Beide
Sätze hat dieselbe Hand geschrieben wie die Erkennung, mit den geplanten Auftragsformen vor Augen – das Ergebnis
belegt, dass nicht auf den Prüfsatz abgestimmt wurde, nicht, dass fremde Formulierungen des Besitzers getroffen
werden. Zusätzlich geprüft (`ContinuumBriefingRegressionTest`): kein Satz, der seine Lenkung behalten muss, wird
angenommen; wo ein Fall die Lenkung nennt, die den Satz heute hält (`keeps`), nimmt der echte Parser dieser Lenkung ihn
an; kein Auftrag dieser Lenkung gehört einer Lenkung, die in der Activity vor ihr gefragt wird (Auftragsfolge, Ablage,
Senden, Nachrichten, Routinen). Fünf Äußerungen der anderen Sätze nimmt die neue Lenkung bewusst an und sie sind im
Test namentlich festgehalten: „Wie wird das Wetter morgen?“ (`LIBC-041`, `REF-010`, `REF-023`), „Wie ist das
Wetter?“ (`REG-LONG-001`) und „Ruf Anna an“ (`VOI-END-007`) – dort themenfremde Füllsätze bzw. ein Satzende-Beispiel
ohne eigene Lenkung; ihre eigenen Tests sind unverändert grün. Jede weitere Äußerung, die wandert, lässt den Test
fehlschlagen. Was auf dem Gerät passiert – Spracherkennung, Kalender-Anbieter, Wähltastatur, Open-Meteo, Sprachausgabe –
ist NOT_RUN.

## Kennzahlen für die Gerätemessung (alle NOT_RUN)

Je Lauf werden Modellkennung, Modelldatei-SHA-256, Kontextfenster, Backend und App-Commit protokolliert.

| Kennzahl | Definition |
|---|---|
| Auftragsinterpretation | Anteil bestandener Fälle je Kategorie (Kriterium `expect`) |
| Recall-Genauigkeit | `back_reference`, `long_conversation`: `contains_all` erfüllt |
| Korrektur-/Widerspruchsbehandlung | `correction`, `contradiction`, `ambiguity` |
| Tool-Selection-Genauigkeit | `tool_call`: richtiges Werkzeug **und** kein verbotenes; `no_tool`: keines |
| Halluzinationsrate | `abstain`/`asks_back`-Fälle mit inhaltlicher Behauptung statt Enthaltung/Rückfrage |
| Ausgabevollständigkeit | Anteil Antworten ohne Abbruch durch das Ausgabelimit |
| Latenz | P50/P95 bis zum ersten Token und bis zum Antwortende, Tokens pro Sekunde |
| Ressourcen | maximaler RSS, Thermalstatus-Verlauf, Akkuverbrauch je 10 Fälle |

## Regeln

1. Der Holdout wird nicht bearbeitet. Neue Fälle oder Korrekturen erzeugen `continuum-eval-v2`; Ergebnisse
   verschiedener Versionen werden nicht miteinander verglichen.
2. Kein Fall des Holdouts darf in Trainingsdaten, Few-Shot-Beispielen oder Prompt-Abstimmung vorkommen
   (der Test prüft die Überschneidungsfreiheit der Nutzeräußerungen zwischen Dev und Holdout).
3. Eine Verbesserung gilt nur mit Vorher-Nachher-Lauf auf demselben Gerät, demselben Modell und derselben
   Datensatzversion als belegt.

## Abgrenzung: was „Lernen“ hier heißt

| Stufe | Was sich ändert | Stand Code63 |
|---|---|---|
| A Prompt-/Kontextoptimierung | Instruktionen, Kontextaufbau, Werkzeugverträge | geändert (ältere Nutzeraussagen, Kontrastauflösung) |
| B Langzeitgedächtnis | gespeicherte, abrufbare Nutzerangaben | vorhanden; Sitzungs-Objektgedächtnis korrigiert |
| C Präferenzanpassung | ausdrücklich freigegebene Einstellungen | vorhanden (u. a. Zuhörfenster, Leistungsprofil neu) |
| D Fine-Tuning | **Modellgewichte** | **nicht durchgeführt** |

Für Stufe D (z. B. LoRA/QLoRA auf Gemma-Basis) fehlen derzeit belegt: ein ausreichend großer, freigegebener
Trainingssatz (die 34 Dev-Fälle sind ein Mess-, kein Trainingssatz), GPU-Rechenzeit außerhalb des Telefons,
die Lizenzprüfung der Gemma-Nutzungsbedingungen für abgeleitete Gewichte und ein geprüfter Weg, einen Adapter
in das auf dem Gerät verwendete `.litertlm`-Format zu überführen. Ohne Gerätemessung der Ausgangslage ist
zudem nicht belegt, dass Fine-Tuning der wirksamste nächste Schritt wäre.

## Messlauf am Gerät (Code64)

Die App enthält den Entwicklungs- und den Holdout-Satz unverändert als Assets (`continuum-eval-v1/`; ein Test
prüft die Byte-Gleichheit mit diesem Ordner). Gestartet wird der Lauf im Modellzentrum:

- **4 · EVALUATION · ENTWICKLUNGSSATZ** – beliebig oft, zum Abstimmen.
- **5 · EVALUATION · HOLDOUT** – nur für den Abschlussvergleich; Ergebnisse nicht zum Abstimmen verwenden.

Umfang des Laufs (steht auch in jedem Bericht als `scope`): Rohmodell mit dem Gespräch in einem Prompt, ohne
Continuum-Gedächtnis, ohne deterministische Pfade, ohne Werkzeuge. Werkzeugwahl-Fälle werden deshalb als
„nicht bewertbar“ geführt und sind über `regression*.jsonl` am Host abgedeckt.

Der Bericht (`Download/Continuum/continuum-eval-<satz>-<zeit>.json`) enthält ausschließlich Werte, die während
des Laufs auf dem Gerät gemessen wurden:

| Feld | Bedeutung |
|---|---|
| `meta` | Modellkennung, Modell-SHA-256, Dateigröße, gemeldetes Kontextfenster, Rechenthreads, App-Version, Gerät, Android-SDK, Energiesparmodus |
| `meta.backend`, `meta.backendRequested`, `meta.backendNotice`, `meta.contextTokens` | Backend, auf dem die geladene Engine wirklich rechnet; Stellung des Beschleuniger-Schalters; Hinweis bei einem Rückfall; konfiguriertes natives Kontextfenster (nicht das Maximum der Modelldatei). Unbekanntes ist `null`. `meta.computeThreads` gilt nur für die CPU. |
| `meta.dialogueReuseSwitch`, `meta.casesIndependent`, `meta.dialogueReuseAppliedToCases` | Stellung des Schalters „Gespräch im Modell behalten“ während des Laufs. Jeder Fall ist ein unabhängiger Prompt in einem neuen nativen Gespräch (`casesIndependent` immer `true`), der Schalter wirkt auf diese Zahlen also nie (`dialogueReuseAppliedToCases` immer `false`). Seine Messung ist die Gesprächs-Zeitprobe im Modellzentrum: Bericht `continuum-eval-dialogue-probe-<zeit>.json`, Schema `continuum-dialogue-latency-probe-v1`, je Zug `reused`, `reuseReason`, `sentBytes`, `firstTextMs`, `totalMs`, `thermalBefore`/`thermalAfter`. |
| `formal` | bestanden / nicht bestanden für `contains_all`, `contains_any`, `numeric` |
| `heuristic` | bestanden / nicht bestanden für `abstain`, `asks_back` (am Wortlaut erkannt, stichprobenartig gegenlesen) |
| `notScored` | Werkzeugwahl-Fälle |
| `latencyMs` | Median und 95 %-Wert der Gesamtzeit und der Zeit bis zum ersten Text |
| `tokensPerSecondMean` | nur wenn die Laufzeit Ausgabe-Tokens meldet, sonst `null` (LiteRT-LM 0.18.0 meldet sie nur im Benchmark-Modus, den die App nicht einschaltet; siehe `docs/research/LOCAL-MODEL-SPEED-20261118.md`, Abschnitt 2.7) |
| `charactersPerSecondMean` | Zeichen pro Sekunde nach dem ersten Text; immer gemessen und in der Zusammenfassung gezeigt |
| `inputTokensMax` | größte von der Laufzeit gemeldete Prompt-Tokenzahl |
| `outputLimitReached` | Antworten, die am Ausgabelimit endeten |
| `thermalStatus`, `batteryPercent`, `availableRamBytesMin`, `jvmHeapUsedBytesMax` | Android-Messwerte während des Laufs |
| `results[]` | je Fall: Urteil, Begründung, Antwort (gekürzt), Zeiten, Tokenzahlen, Thermalstatus davor/danach |

Der Lauf endet von selbst bei kritischer Gerätewärme (`THERMAL_CRITICAL`) und kann mit Stopp abgebrochen werden;
ein dabei laufender Fall wird nicht gezählt.

### Protokoll für einen Modellvergleich

1. Gerät: Akku über 50 %, nicht am Ladegerät, Energiesparen aus, fünf Minuten abgekühlt, Continuum im Vordergrund.
2. Modell A laden, Entwicklungssatz laufen lassen, Bericht sichern. Fünf Minuten Pause. Wiederholen (zwei Läufe je
   Modell, um Streuung zu sehen).
3. Dasselbe mit Modell B bzw. einer anderen Quantisierung – gleiche App-Version, gleiche Reihenfolge.
4. Erst wenn die Entscheidung auf dem Entwicklungssatz gefallen ist: je Modell genau ein Holdout-Lauf.
5. Ein Modellwechsel ist nur begründet, wenn das neue Modell im Holdout formal besser ist **und** bei
   Antwortzeit, Thermalstatus und Speicher nicht schlechter abschneidet.

Stand: kein Gerätelauf durchgeführt (NOT_RUN). Es gibt deshalb weder einen Vergleich von Modellen oder
Quantisierungen noch eine Aussage, ob Fine-Tuning sinnvoll wäre.
