# Kann das Cloud-Gehirn jede Fähigkeit der App bedienen? – Bestandsaufnahme 19.11.2026

Besitzerauftrag: „Schau nochmal nach und schau, dass Opus oder Claude hier wirklich jedes Werkzeug bedienen kann.“

Geprüft wurde am Quelltext (Stand dieses Pakets) und mit Host-Tests gegen einen lokalen Schein-Endpunkt der
Messages-API. **Kein echter API-Aufruf, kein Gerät:** Verhalten des echten Modells und des Fold7 ist überall
`NOT_RUN`.

## Die zwei Wege

1. **Werkzeuge der Registry** (`ToolRegistry`): das Modell schlägt einen Aufruf vor, `ToolProposal.validate`,
   `ToolGate` und die Freigabe des Besitzers entscheiden. Mit dem Cloud-Gehirn sind sie in jeder Arbeitsrunde
   deklariert (gleiche Liste, gleiche Reihenfolge, `tool_choice: auto`, höchstens ein Aufruf je Runde).
2. **Feste Abläufe** („Lenkungen“): der Besitzer löst sie mit einem Satz aus, die App führt sie selbst aus.
   Sie sind absichtlich **keine** Werkzeuge. Das Cloud-Gehirn kennt sie aus dem Fähigkeitskatalog im festen
   Systemrahmen und kann einen frei formulierten Satz auf **einen** Auftrag abbilden (oder auf einen Plan aus
   mehreren), den der Ablauf dann mit seinen eigenen Freigaben ausführt.

## Werkzeuge der Registry

„Cloud“ = kann das Cloud-Gehirn den Aufruf auslösen. Risiko wie in der Registry: lesend · bestätigen ·
zweimal bestätigen (löschend).

| Werkzeug | Ausführung | Risiko / Freigabe | Cloud | Beleg |
|---|---|---|---|---|
| `web_search`, `web_read` | App, über die Internetfreigabe | bestätigen; Internetmodus Aus/Nachfragen/Frei | ja | T1 |
| `get_device_info`, `get_battery_info`, `get_storage_info`, `get_network_summary` | Android selbst | lesend | ja | T1 |
| `list_installed_apps`, `find_app`, `get_app_info` | Android selbst | lesend | ja | T1, T3 |
| `open_android_settings` | Android selbst | bestätigen | ja | T1 |
| `launch_app` | Bedienungshilfe, mit Bildschirmbeobachtung | bestätigen | ja | T1 |
| `read_app_screen` | Bedienungshilfe | bestätigen | **nein, Absicht**: Bildschirminhalte sind „nur auf dem Gerät“; nicht deklariert, ein Aufruf wird abgelehnt | T1 (`theScreenReaderIsNeitherDeclaredNorAccepted`) |
| `diagnose_device`, `save_system_snapshot`, `get_system_capabilities`, `preview_own_cache`, `cleanup_own_cache`, `restore_own_cache`, `analyze_granted_storage` | Systemzentrum | lesend bis löschend | **nein, Absicht – für beide Gehirne**: die App führt sie nur auf den eigenen Satz des Besitzers aus (`SYSTEM_TOOLS_REQUIRE_EXPLICIT_LOCAL_ACTION`). Im Katalog als „[Systemzentrum]“ markiert, mit dem Satz „Analysiere mein Handy“ | T2 |
| `list_processes`, `read_logcat`, `list_files`, `find_files`, `find_latest_apk`, `get_large_files` | Shizuku-Brücke | lesend | ja, **braucht Shizuku** | T1, T4 |
| `stop_app`, `restart_app`, `install_apk`, `move_file`, `take_screenshot`, `record_screen` | Shizuku-Brücke | bestätigen | ja, braucht Shizuku | T1, T3, T4 |
| `uninstall_app`, `delete_file` | Shizuku-Brücke | zweimal bestätigen | ja, braucht Shizuku | T1 |

Welche Werkzeuge die Brücke brauchen, steht in der Werkzeugbeschreibung und im Katalog („[Shizuku]“). Ein
neu registriertes Werkzeug erscheint ohne Änderung in Liste und Katalog; als Brücken-Werkzeug gilt es nur, wenn
es in `ClaudeToolCatalog.bridgeTools` steht (`aNewlyRegisteredToolAppearsWithoutAnyChangeToTheCatalogue`).

## Feste Abläufe

| Fähigkeit | Auslöser heute | Cloud | Freigabe | Beleg |
|---|---|---|---|---|
| PDF nach Namen suchen (auch ungefährer Name) | „Such das PDF Argon4“, „Ich suche eine PDF, die heißt …“ – im Programm, ohne Shizuku | Satz geht direkt an die Ablage; freier Satz wird abgebildet | nur lesend | T5 (Feldfall b), T6 |
| PDF öffnen | „Öffne das PDF Argon4“ | abgebildet, läuft ohne Rückfrage | nur lesend; bei mehreren Treffern fragt die Ablage | T5 |
| Seite zusammenfassen und speichern | „Fass Seite 2 zusammen und speichere es als Word“ | als Plan/Auftrag abgebildet | eigene Datei; Seitentext geht nur mit der Claude-Zustimmung („Textstellen aus Dokumenten“) in die Cloud | T5, vorhandene Ablage-Tests |
| Nachrichten | „Fass mir die Nachrichten von heute zusammen“ | abgebildet | Internetfreigabe | vorhandene Nachrichten-Tests |
| Als PDF/Word speichern | „Speichere das als PDF“, „Erstelle (mir) (bitte) eine PDF/Word-Datei [davon]“ | Satz geht direkt an den Ablauf; freier Satz wird abgebildet | eigene Datei im Download-Ordner | T5 (Feldfall a) |
| Gespeicherte Datei öffnen | „Öffne die gespeicherte Datei“, „… und zeig sie mir“ | als Schritt eines Plans | nur lesend | T5 |
| Per Mail weitergeben | „Schick das per Mail an …“ | abgebildet, **erst nach „Ja“** | Übergabe ans Mailprogramm; selbst senden nur mit der Dauerfreigabe Gmail | T5 |
| WhatsApp/Gmail selbst senden | „Schreib Anna auf WhatsApp, …“ | **nie abgebildet, nie Werkzeug**: nur der Wortlaut des Besitzers | Dauerfreigabe je Kanal, Prüfung am Bildschirm | vorhandene Sende-Tests; T2 |
| Auftragsfolge | ein Satz mit mehreren Aufträgen | Plan vom Cloud-Gehirn, **nur gesprochen**, läuft nach „Ja“ | je Schritt die Freigaben seines Ablaufs | T5 |
| Neueste Datei suchen (Aufgabenkette) | „Such das neueste PDF in Downloads“ | abgebildet | nur lesend, braucht Shizuku | vorhandene Ketten-Tests |
| Live-Computer | „Öffne Chrome und suche …“ | nicht abgebildet | Bildschirmbeobachtung; Bildschirminhalt nie in die Cloud | vorhandene Tests |
| Webrecherche | „Recherchiere …“ | nicht abgebildet (Werkzeuge `web_search`/`web_read` stehen daneben zur Verfügung) | Internetfreigabe | vorhandene Tests |
| Systemzentrum | „Analysiere mein Handy“ | nicht abgebildet | lesend; Bereinigen mit eigener Bestätigung | T2 |

## Was geändert wurde

- **Fähigkeitskatalog** (`ClaudeAbilityCatalogue`): erzeugt aus der Werkzeugliste der Anfrage und aus
  `OwnerLaneCatalogue`; fester, cache-stabiler Block des Systemrahmens. Regel darin: nie eine Fähigkeit der App
  verneinen; ein Ablauf ohne Shizuku ist jedem Shizuku-Werkzeug vorzuziehen.
- **Modellpläne mit dem Cloud-Gehirn** (`OwnerMissionAdapter.modelPlans`): bisher gesperrt. Jetzt gleiche
  Prüfung wie beim Modell auf dem Gerät, gleiches ausdrückliches „Ja“. Ungesetzt ist der Schalter mit dem
  Cloud-Gehirn an, mit dem Modell auf dem Gerät aus; „Modellpläne aus“ gilt für beide.
- **Ein Satz → ein Auftrag** (`OwnerMissionDirector.proposeOrderFromModel`): nur mit dem Cloud-Gehirn, erst
  dort, wo der Satz sonst eine freie Modellrunde würde. Lesende und eigene-Datei-Aufträge laufen ohne weitere
  Frage, alles nach außen erst nach „Ja“. `ClaudeTurnGate` (Satz schließt Cloud aus → keine Plananfrage) und
  das Monatslimit (Laufzeit lehnt ab) gelten.
- **Werkzeugrunde** (`CloudBrainToolRoute`): mit dem Cloud-Gehirn bekommt jede Runde die Werkzeuge, außer
  Gruß, Dank und Abschied. Auf dem Gerät unverändert.
- **Werkzeugschleife** (`ClaudeModelRuntime`): mehrere `tool_use` in einer Runde → nur der erste Aufruf wird
  vorgeschlagen, alle werden in **einer** Nutzernachricht beantwortet (die übrigen als nicht ausgeführt,
  `is_error`). `pause_turn` wird höchstens zweimal fortgesetzt, jede Fortsetzung durch dieselben Sperren.
- **Feldfälle**: siehe `docs/evaluation/continuum-eval-v1/README.md`, Abschnitt Gesprächssatz.

## Datenschutz

Nichts Neues geht in die Cloud außer: der Name des Besitzers im Systemrahmen – und nur mit seinem eigenen
Schalter „Namen für das Cloud-Gehirn freigeben“ (Einstellungen → Gesprächston) oder seiner bestehenden
Freigabe für „nur auf dem Gerät“-Erinnerungen. Die Plananfrage enthält nur seinen eigenen Satz. Der
Zusammenfassungsentwurf, der Gruß der App und die Anrede bleiben lokale Vorgänge. Bildschirminhalte nie.

## Belege

- T1 `ClaudeToolLoopTest` – jedes Werkzeug deklariert und vorschlagbar, Ergebnis geht zurück, mehrere Aufrufe,
  `pause_turn`, Ablehnung, fester Systemrahmen.
- T2 `ClaudeAbilityCatalogueTest` – Katalog, Markierungen, jeder genannte Satz wird von seinem Ablauf genommen.
- T3 `ClaudeBrainTurnUiTest` – echte Activity: Werkzeugvorschlag hält am Freigabedialog, Ablehnung geht als
  Fehler zurück.
- T4 `ClaudeToolLoopTest.aToolCallRunsOnlyAfterTheGate…`, `aFailedToolGoesBackAsAnErrorResult…` – produktiver
  Orchestrator und `ToolGate`.
- T5 `OwnerCloudBrainAbilityFlowTest` – beide Feldfälle mit beiden Gehirnen, Abbildung, Rückfrage, Verwerfen.
- T6 `OwnerTalkRegressionTest` – Sätze der Prüfsätze.

`NOT_RUN`: echtes Claude-Modell (ob es den Katalog befolgt, wie es abbildet), Shizuku-Werkzeuge am Gerät,
Sprachausgabe, Fold7.
