# Verstehen trotz Fehlhörung · Aufgabenkette (18.11.2026)

Stand: Host (JVM + Robolectric), Basis `a8fdede86`. **Kein Gerät verfügbar – jedes Telefonverhalten ist NOT_RUN.**
Besitzerauftrag: „verstehen, was der Benutzer will, Werkzeuge aussuchen, nach der Reihe ausführen, sauber ausführen,
… selbst wenn Wörter nicht verstanden werden“ und „er kann rein, er kann schreiben, er kann abspeichern, er kann den
Auftrag als erledigt melden, er kann auch melden, wenn ein Fehler passiert ist“.

## 1. Was es schon gab

| Baustein | Was er kann | Wo er aufhört |
|---|---|---|
| `OwnerComputerIntentParser` | 10 feste Apps mit Aliasen, Öffnen/Suchen/WhatsApp-Entwurf | Alias muss buchstabengleich sein („Krom“ ist keine App) |
| `OwnerAppLookupIntent` + `NativeAppLookup` | „öffne X“ für jedes installierte Label | exaktes Label oder Teilstring; „Thermux“ findet Termux nicht |
| `OwnerInternalNavigationIntent` | Cockpit-Ansichten, eigene Tippfehlertoleranz (Levenshtein) | nur Ansichten, keine Apps/Produkte |
| `OwnerCorrectionIntent`, `OwnerContrastResolver` | „nein, ich meinte …“, „nicht X, sondern Y“ | setzt richtig erkannte Namen voraus |
| `SpeechTurnRecovery`/`SpeechRecoveryPolicy`, `OwnerTurnCompletion` | Erkenner-Neustart, Satzende | betreffen den Erkenner, nicht den Wortlaut |
| `BoundedAgentOrchestrator` + `ToolGate` + Freigabedialog | Modell schlägt je Durchgang **ein** Werkzeug vor, Gate prüft Risiko/Freigabe, Quittungen, Budget (4 Aufrufe) | keine Erfolgsprüfung je Schritt, keine Übergabe als Daten, Abschlusstext schreibt das Modell |
| `ReadOnlyToolPlan` | fester lesender Plan, genau ein Fall mit zwei Schritten („Speicher und Downloads“) | keine Argumentübergabe, kein ändernder Schritt, kein Fortsetzen |
| `AgentCheckpointStore` | Zustand des Orchestrators (Aufgabe, Schrittzähler, Prüfpfad) | trägt keine Schrittergebnisse; Fortsetzen nach einer Werkzeuggrenze ist dort absichtlich gesperrt |
| `Command03ActivityProjection` | zeigt reale Schritte über den Cockpit-Bus | bekam für deterministische Geräteprüfungen keine Schritte |

**Ein mehrstufiger Auftrag vorher** – „such die neueste PDF in Downloads und sag mir, wie groß sie ist“: Das Wort
„Downloads“ löst im Startkern (`BootstrapBrain`) genau `list_files /sdcard/Download` aus; die Antwort ist die rohe
`ls -lah`-Liste. Welche PDF die neueste ist und wie groß sie ist, wird nicht beantwortet, ein zweiter Schritt findet
nicht statt. Ohne Schlüsselwort („Finde die neueste APK und zeig mir die Infos“) geht der Auftrag an das Modell: es
darf nacheinander bis zu vier Werkzeuge vorschlagen, aber nichts prüft, ob ein Schritt gelungen ist, bevor der nächste
kommt; der Abschluss ist frei formulierter Modelltext; ohne geladenes Modell gibt es keine Ausführung; ein
abgelehnter oder abgelaufener Freigabedialog beendet den Zug ohne Fortsetzung.

## 2. Was neu ist

### 2.1 Verstehen (`cognitive/OwnerUnderstanding.kt`, `OwnerUnderstandingLexicon.kt`, `GermanPhonetics.kt`)

Reine Kotlin-Schicht, die eine erkannte Äußerung **vor** dem Routing deutet.

* **Lexikon** (nur was Continuum gerade wirklich kennt, begrenzt): feste Apps der Computer-Spur, Produkt- und
  Modellnamen der App (Termux, Shizuku, Gemma, Claude, Jarvis, Continuum, WhatsApp, Gmail, Chrome …), eigene
  Funktionen (Akku, Speicher, Downloads, Screenshot, WLAN …), Launcher-Labels der installierten Apps
  (`InstalledAppLabels`, dieselbe Abfrage wie die App-Suche, höchstens 400, im Hintergrund gelesen), Dokumentnamen
  der Sitzung (höchstens 16), Substantive der letzten sechs Besitzerzüge (höchstens 32). Keine Kontakte, keine Inhalte.
* **Kandidaten** nach Klang und Schreibung: Kölner Phonetik nach r-Vokalisierung („Javis“/„Jarvis“, „Kalenda“/
  „Kalender“) plus gewichtete Editierdistanz auf einer Normalform ohne die typischen Erkennerverwechslungen
  (b/p, d/t, g/k, v/f/w, s/z, stummes h, ie/i, Doppelbuchstaben, ph/f, th/t, ck/k, x/ks). Dazu je Name eine kleine
  Liste real gehörter Schreibungen englischer Namen („Krom“, „Watsapp“, „Dschimeil“, „Jutjub“, „Schisuku“).
  Getrennte Namen („Ter Mux“, „G Mail“) und angeklebtes „App“ („Thermuxapp“) werden zusammengeführt bzw. getrennt.
  Ähnlichkeit = 0,55 × Normalform + 0,20 × Rohschreibung + 0,25 × gleicher Klangschlüssel; ohne gleichen
  Klangschlüssel bleibt sie ≤ 0,75 und damit unter jeder Schwelle.
* **Entscheidung, fail-closed**:
  * bester Kandidat ≥ 0,86 an einem **Namensplatz** (nach öffne/starte/mach … auf/installiere/such in …, „App X“,
    „Version von X“, „ob X installiert ist“, Modellplatz, Anrede) – oder ≥ 0,90 für einen unverwechselbaren
    Produktnamen an beliebiger Stelle – **und** ≥ 0,08 Vorsprung vor dem zweiten → Äußerung wird repariert;
    die nächste Antwort beginnt einmal mit „Ich habe „Thermux“ als Termux verstanden.“
  * zwei Kandidaten ≥ 0,80 ohne diesen Vorsprung an einem Namensplatz → eine Rückfrage „Meinst du Calendar oder
    Kalender?“; die Antwort darf der Name, „die erste/zweite“ sein, alles andere verwirft die Frage.
  * sonst unverändert weiter.
* **Nie angefasst**: Lexikonnamen selbst, rund 740 häufige deutsche und österreichische Wörter samt Beugung, Zahlen,
  Verneinungen, Zitate, alles nach einem Doppelpunkt, der Text von Nachrichten- und Merkbefehlen, jede Äußerung, die
  eine deterministische Spur schon vollständig auflöst (Computer-Spur bereit, lokale Antwort, interne Navigation,
  Gerätewerkzeug), und jede Reparatur, die eine Nicht-Befehlsäußerung in eine handelnde Spur verschieben würde.
  Gültige Wörter werden nur in einem eng beschriebenen Kontext zum Namen: „Gamma 4“/„Modell Gamma“ → Gemma,
  „Öffne Wort“ → Word.
* **Haken**: `AgentActivity.sendCurrentInput` (einziger Einlass für getippte und gesprochene Züge) ruft
  `applyOwnerUnderstanding()`; `addAssistantMessage` stellt den Hinweis einmal voran. Abschaltbar über
  `OwnerUnderstandingIntake.enabled`.

### 2.2 Aufgabenkette (`cognitive/OwnerTaskChain.kt`, `OwnerTaskChainPlan.kt`, `OwnerTaskChainRecipes.kt`)

Kein zweiter Orchestrator: `OwnerTaskChain` ist **Modell und Prüfer** des vorhandenen `BoundedAgentOrchestrator`.
Gate, Freigabedialog, Quittungen, Budget, STOP und `ExternalContentActionGuard` bleiben unverändert zuständig.

* **Plan als Daten** (`ChainPlan`): Schritte mit Werkzeugname aus `ToolRegistry`, Argumenten (fest oder „aus
  Schritt N“, auch Ordner/Dateiname eines Pfads), Erfolgsbedingung und dem, was der Schritt weitergibt.
  Höchstens 4 Schritte (Werkzeugbudget eines Besitzerzugs).
* **Rezepte** (aus der Äußerung erkannt, eng gefasst; Verneinung, Zitat, Doppelpunkt → kein Rezept):

  | Rezept | Beispiel | Schritte |
  |---|---|---|
  | `newest_file_details` | „Such die neueste PDF in Downloads und sag mir, wie groß sie ist“ | `list_files` → neueste passende Datei aus der echten Liste |
  | `latest_apk_info` | „Finde die neueste APK (von continuum)“ / „Downloade die neueste APK“ | `find_latest_apk` → `list_files`(Ordner der APK) |
  | `file_search_details` | „Such die Datei Mietvertrag“ | `find_files` → (Besitzer wählt bei mehreren) → `list_files`(Ordner) |
  | `app_version` | „Welche Version von Termux habe ich“ | `find_app` → (Auswahl) → `get_app_info`(Paket) |
  | `storage_analysis` | „Was braucht am meisten Platz“ | `get_storage_info` → `get_large_files` |
  | `device_check` | „Mach einen Gerätecheck“ | Akku → Speicher → Netzwerk (→ Gerät, wenn genannt) |
  | `screenshot_saved` | „Mach einen Screenshot“ | `take_screenshot` (Freigabe) → `list_files` prüft die gespeicherte Datei |
  | `app_restart` | „Starte Termux neu“ | `find_app` → `restart_app` (Freigabe) |

* **Modellplan** (zweite Quelle, standardmäßig **aus**, Besitzerschalter „Aufgabenketten mit Modellplan an/aus“):
  ein strenges JSON `continuum-owner-chain-v1` aus einer werkzeugfreien Generierung über `LocalModelRuntime.
  streamTextOnly` (`RuntimeChainPlanSource` – dasselbe für lokales Modell und Claude; kein Cloud-Aufruf wurde
  gemacht). Das Modell nennt nur Werkzeuge und Argumente; Erfolgsbedingung und Übergabe kommen aus
  `OwnerChainStepCatalog`. `OwnerChainPlanValidator` lehnt mit Grund ab, repariert nie: unbekanntes Werkzeug ·
  Internetwerkzeug · löschend/installierend · kein Prüfvertrag · fehlendes/unbekanntes Argument · Typfehler ·
  Verweis auf späteren Schritt oder nicht übergebenen Wert · mehr als ein ändernder Schritt · Wiederholung ·
  vom Auftrag ausgeschlossen · mehr als 4 Schritte. Ein abgelehnter Plan → normaler Modellzug wie bisher.
* **Ausführung**: je Schritt Vorschlag an den Orchestrator → Gate/Freigabe → reale Beobachtung → Erfolgsbedingung →
  Werte entnehmen → erst dann der nächste Schritt. Zeitgrenze 180 s zwischen Schritten, STOP über den vorhandenen
  Abbruchweg. Vor jedem Schritt nach einem Ergebnis prüft die Kette selbst die Herkunftsregel des Guards – auch im
  fortgesetzten Lauf, in dem der Orchestrator noch keine Beobachtung kennt.
* **Ergebnis** (deutsch, nur aus Werkzeugergebnissen): „Erledigt. …“ mit den entnommenen Werten · „Nicht
  abgeschlossen: Schritt 2 von 3 („…“) ist fehlgeschlagen – Grund. Erledigt und geprüft: …“ · „Angehalten …“.
  „Erledigt“ gibt es nur, wenn für jeden Schritt eine frische, erfolgreiche Gate-Quittung vorliegt (`confirm`).
* **Fortsetzen**: Freigabe nicht erteilt, Auswahl nötig, STOP/Zeitgrenze → die Kette bleibt (im Speicher, 10 min,
  je Sitzung) stehen; „weiter“, „die zweite“, „weiter mit 2“, „abbrechen“. Ein ändernder Schritt, der beim STOP
  schon gestartet war, wird nie wiederholt. `AgentCheckpointStore` passt dafür nicht (Schema v1 trägt keine
  Schrittergebnisse und sperrt das Fortsetzen nach einer Werkzeuggrenze); die Pause übersteht deshalb keinen
  Prozessneustart.
* **Sichtbare Arbeit**: `OwnerTaskChainStage` sendet `ProcessTimeline`/`ActionStarted`/`TurnEnded` auf den
  vorhandenen Cockpit-Bus. Ein Schritt erscheint erst, wenn er auf Freigabe wartet oder wirklich gestartet wurde.

**Weitere Werkzeuge einhängen (drei Zeilen, Ausführer unverändert)** – z. B. Export als PDF/DOCX, E-Mail-Entwurf
mit Anhang, Nachrichtenüberblick:

1. `ToolRegistry`: `ToolSpec("export_last_deliverable", "Als PDF exportieren", "…", Risk.CONFIRM, required = setOf("format"))`
2. `DeviceToolExecutor.execute`: `if (call.name == "export_last_deliverable") { callback(runCatching { OwnerWorkTools.export(…) }); return }`
3. `OwnerChainStepCatalog.register(Contract("export_last_deliverable", "Als PDF exportieren", ChainSuccess.Contains("Gespeichert:"), listOf(ChainExtract.Pattern("path", Regex("Gespeichert:\\s*(\\S.*)"), "kein Speicherort gemeldet")), setOf("path")))`

Danach ist das Werkzeug Rezept- und Modellplan-Schritt (`{{1.path}}` als Anhang des nächsten).

## 3. Zahlen (Host, deterministisch)

| Satz | Fälle | vorher | nachher |
|---|---|---|---|
| `regression-understanding.jsonl` | 74 (39 Reparatur, 5 Rückfrage, 30 „unverändert“) | 30 / 74 – genau die 30 Schutzfälle; 0 / 44 der Fälle mit Fehlhörung | 74 / 74 |
| `regression-chains.jsonl` | 47 (29 ausgeführte Ketten, 11 abgelehnte Modellpläne, 7 „keine Kette“) | 1 / 29 Ketten mit derselben Werkzeugfolge (die rohe Ordnerliste, ohne die gefragte Antwort); 0 mit geprüftem Abschluss | 47 / 47 |

„Vorher“ heißt beim Verstehen: derselbe Prüfweg mit abgeschalteter Schicht; gezählt wird, ob die Äußerung, die das
Routing erreicht, die erwartete ist **und** die erwartete Spur nimmt. In 12 der 39 Reparaturfälle erreichte die
unreparierte Äußerung schon vorher die richtige Spur (das Modell) – mit dem falschen Namen. Bei den Ketten ist
„vorher“ die unveränderte deterministische Weiche (`BootstrapBrain`); der Modellweg ist am Host nicht messbar.
Alle Besitzeräußerungen der bisherigen Sätze (`regression*.jsonl`, `model-dev`, `model-holdout`) bleiben von beiden
Bausteinen unberührt (zwei eigene Tests); `ContinuumEvalDatasetTest` läuft unverändert grün.

Host-Experiment „Modell antwortet direkt“ gegen „Modell schlägt Plan vor, Plan wird geprüft und ausgeführt“:
**NOT_RUN**. Kein Modellgewicht mehr auf dem Host, 2,6 GB freier Platz bei parallelem Build eines zweiten Agenten,
und ein neuer Messaufbau gegen die LiteRT-LM-JNI hätte den Zeitrahmen gesprengt.

## 4. Grenzen

* Kein Wörterbuch: Ein seltenes gültiges Wort, das nicht in der Schutzliste steht, an einem Namensplatz steht und
  denselben Klangschlüssel wie ein bekannter Name hat, würde repariert. Der Hinweis in der Antwort macht das sichtbar.
* Fehlhörungen zwischen gewöhnlichen Wörtern („anmaßbare“ statt „annehmbare“, „Applikationen“ statt
  „Komplikationen“) löst die Schicht nicht – dafür fehlt ein Ziel im Lexikon; das bleibt beim Modell.
  „MOOC“ für einen Werkzeugnamen ebenso (unbekannt, welches Werkzeug gemeint war).
* Dialektverben („moch ma … auf“) werden nicht übersetzt; repariert wird nur der Name.
* „Cloud“ wird bewusst nie zu „Claude“.
* „Downloade/installier die neueste APK“ endet mit Befund und dem Hinweis auf den ausdrücklichen Auftrag: Der
  bestehende Guard lässt ein Installationsziel, das nur aus einem Werkzeugergebnis stammt, nicht zu. Auch die
  Auswahl „die zweite“ zählt dort nicht als Herkunft – das ist eine Entscheidung für Besitzer/Integration.
* „Prüfe Akku und Speicher“ behält die Rückfrage des Startkerns (`REG-TOOL-006`); nur das Wort „Gerätecheck“
  (Handy-/System-/Statuscheck) startet die Kette.
* Jeder Werkzeugfehler nach dem Start wertet das Gate als „Ausgang UNKNOWN“; die Kette meldet das wörtlich weiter
  und wiederholt nichts.
* Dateien öffnen kann kein registriertes Werkzeug; die Kette sagt das, statt es zu behaupten.

## 5. Am Gerät zu prüfen (alles NOT_RUN)

1. Echte Erkennerausgaben für Termux, Shizuku, Gemma 4, Jarvis, WhatsApp, Gmail: wird repariert, stimmt der
   Hinweis, wird er auch gesprochen?
2. Fehlreparaturen im Alltag über mehrere Tage (jede ist am Hinweis erkennbar) – Schwellen 0,86 / 0,90 / 0,08.
3. Rückfrage bei „Kalender/Calendar“ im Freisprechmodus: hört das Mikrofon danach wieder zu?
4. Zeit bis `InstalledAppLabels` gefüllt ist (erster Zug nach dem Start arbeitet nur mit den festen Namen).
5. Format von `ls -lah` und `find_latest_apk` auf dem Fold7 (die Rezepte lesen ISO-Datum und Größenspalte).
6. Screenshot-Kette mit Shizuku: Freigabedialog, Datei im Ordner, Bühne zeigt 1/2 und 2/2.
7. STOP mitten in einer Kette, danach „weiter“; „weiter“ ohne offene Kette muss wie bisher die Antwort fortsetzen.
8. Modellplan mit Gemma 4 E4B/E2B und mit Claude: Anteil gültiger Pläne, Zusatzzeit der Plan-Generierung.
