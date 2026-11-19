# Werkzeug-Audit und Gerätewerkzeuge (19.11.2026)

Besitzerauftrag (Heinz, 19.11.2026): „Kontrolliere das gesamte Werkzeug, erstelle neues Werkzeug, das eben auf mein
Android-Gerät funktioniert. Er soll suchen können auf mein Android-Gerät, Apps, Dateien, egal was, sich holen können,
dann eben PDF erstellen können.“

Feldbefunde vom Fold7 (Claude als Gehirn):

1. „ich suche eine PDF die heißt … Experimente“ → die Dateisuche lief als `find` über die Shizuku-Shell und scheiterte
   nach ihrem Start. Ursache: **alle** Dateiwerkzeuge funktionierten nur mit laufender Shizuku-Brücke.
2. „Erstelle bitte eine PDF und zeig sie mir dann“ → es gab kein Werkzeug, das ein Dokument anlegt oder anzeigt.

Dieses Dokument prüft jedes registrierte Werkzeug und beschreibt, was sich mit diesem Paket ändert. Es behauptet
nichts über das Verhalten auf dem Gerät: alles, was nur ein echtes Fold7 zeigen kann, steht unter **NOT_RUN**.

## Was eine gewöhnliche App auf Android 16/17 darf – und was nicht

| Bereich | Stand |
|---|---|
| Dateien im gemeinsamen Speicher | Lesen/Schreiben per Pfad nur mit „Zugriff auf alle Dateien“ (`MANAGE_EXTERNAL_STORAGE`, im Manifest vorhanden; der Besitzer schaltet es in Androids Einstellungen selbst ein und aus). Auch damit gesperrt: `Android/data` und `Android/obb` anderer Apps und jeder App-interne Speicher. Ohne die Erlaubnis sieht eine App per Pfad nur Dateien, die sie selbst angelegt hat. |
| Neue Datei im Download-Ordner | `MediaStore.Downloads` (ab Android 10), ohne jede Erlaubnis; die Datei gehört dem Besitzer und bleibt nach einer Deinstallation. |
| Installierte Apps | Mit `QUERY_ALL_PACKAGES` (vorhanden) sind alle Pakete sichtbar: Name, Version, Start-Intent. |
| Fremde Apps beenden, neu starten | Kein App-API (`FORCE_STOP_PACKAGES` ist System-Signatur). Nur über die Shell-Identität der Shizuku-Brücke. |
| Prozesse und Logs anderer Apps | Gesperrt (`getRunningAppProcesses` liefert nur die eigene App, `READ_LOGS` ist nicht erteilbar). Nur über die Brücke. |
| Bildschirm anderer Apps | Still nicht möglich. `screencap`/`screenrecord` nur über die Brücke; der App-Weg (MediaProjection) verlangt je Sitzung Androids eigenen Dialog. |
| Installieren/Deinstallieren | Still nur über die Brücke. Mit Androids eigenem Dialog könnte eine App installieren (`REQUEST_INSTALL_PACKAGES` ist vorhanden); fürs Deinstallieren fehlt `REQUEST_DELETE_PACKAGES` – eine neue Erlaubnis kommt nicht in Frage. |
| Andere App starten, Datei anzeigen, Teilen-Dialog | Nur solange Continuum sichtbar im Vordergrund ist (Hintergrund-Startsperre seit Android 10, in 14–16 verschärft). Werkzeuge laufen während eines Besitzerzugs – dann ist die App vorn. |
| Zwischenablage | Lesen nur für die App mit Eingabefokus (seit Android 10); Android blendet beim Lesen einen Hinweis ein (seit 12). |
| Text eines PDFs | Ab Android 15 (`PdfRenderer`-Textebene). Ein Scan ohne Textebene bleibt ohne Texterkennung unlesbar. |

## Die Tabelle: jedes registrierte Werkzeug

Spalten: **Ohne Shizuku?** = läuft auf einem unveränderten Fold7 ohne laufende Brücke. **Freigabe**: `READ_ONLY` ohne
Rückfrage · `CONFIRM` ein Ja im Dialog · `DESTRUCTIVE` zwei Ja. **Beleg**: automatischer Hosttest, sonst NOT_RUN.
Alle Geräteaussagen (echtes Fold7, echte Brücke, echte Dateien) sind zusätzlich NOT_RUN.

| Werkzeug | Was es tut | Ausführung jetzt | Ohne Shizuku? | Android-Grenzen | Freigabe | Beleg | Urteil |
|---|---|---|---|---|---|---|---|
| `web_search` | öffentliche Quellen suchen | Web, eigene einmalige Internetfreigabe | ja | Netz | CONFIRM | `ApprovedWebToolGateTest` (Bestand) | behalten; nicht Teil dieses Pakets |
| `web_read` | eine HTTPS-Seite lesen | Web, eigene Freigabe | ja | Netz | CONFIRM | Bestand | behalten |
| `get_device_info` | Modell, Android, Build | im Prozess (`Build`) | ja | – | READ_ONLY | NOT_RUN (reiner API-Zugriff) | behalten |
| `get_battery_info` | Akkustand, Laden, Temperatur | im Prozess (Sticky-Broadcast) | ja | – | READ_ONLY | `DeviceBatteryProbeTest` | behalten. **Befund:** der Startkern verlangte dafür bisher fälschlich Shizuku – behoben |
| `get_storage_info` | Gesamt/frei des App-Datenträgers | im Prozess (`File.totalSpace`) | ja | misst den Datenträger der App; auf dem Fold7 derselbe wie der gemeinsame Speicher | READ_ONLY | Ketten-Regression | behalten |
| `get_network_summary` | Netzwerkübersicht | im Prozess (`ConnectivityManager`) | ja | – | READ_ONLY | `DeviceNetworkProbeTest` | behalten; Shizuku-Zwang im Startkern behoben |
| `diagnose_device` | Momentaufnahme im Systemzentrum | im Prozess | ja | – | READ_ONLY | `SystemIntelligenceUiTest` | behalten, **aber nicht modellaufrufbar** (siehe Befund 2) |
| `save_system_snapshot` | Momentaufnahme sichern | im Prozess | ja | – | CONFIRM | Bestand | wie oben |
| `get_system_capabilities` | Shizuku-Identität prüfen | Brücken-Probe | – (prüft die Brücke) | – | READ_ONLY | Bestand | wie oben |
| `preview_own_cache` | eigene Diagnoseberichte auflisten | im Prozess | ja | nur eigener Cache | READ_ONLY | Bestand | wie oben |
| `cleanup_own_cache` | eigene Berichte löschen (mit Rücknahme) | im Prozess | ja | nur eigener Cache | DESTRUCTIVE | Bestand | wie oben |
| `restore_own_cache` | Bereinigung zurücknehmen | im Prozess | ja | – | CONFIRM | Bestand | wie oben |
| `analyze_granted_storage` | ausgewählte Dateien prüfen | im Prozess (Android-Dateiauswahl) | ja | nur ausgewählte Dateien | READ_ONLY | Bestand | wie oben |
| `list_installed_apps` | Paketliste | im Prozess (`PackageManager`) | ja | `QUERY_ALL_PACKAGES` | READ_ONLY | NOT_RUN | behalten; liefert nur Paketnamen – für Namen `search_device`/`find_app` |
| `find_app` | App nach Name suchen | im Prozess (Launcher-Aktivitäten) | ja | – | READ_ONLY | `AppLookupToolPolicyTest` | behalten |
| `get_app_info` | Version einer App | im Prozess | ja | Speicher/Energie je App bräuchten `PACKAGE_USAGE_STATS` | READ_ONLY | Ketten-Regression | behalten |
| `list_processes` | laufende Prozesse (`top`) | **nur Brücke** | nein | kein App-API für fremde Prozesse | READ_ONLY | `DeviceToolGateTest` (Ablehnung vor dem Start) | auf der Brücke lassen |
| `read_logcat` | Logs (`logcat`) | **nur Brücke** | nein | `READ_LOGS` nicht erteilbar | READ_ONLY | wie oben | auf der Brücke lassen; gilt für die Cloud als **Inhalt** |
| `launch_app` | App öffnen und Fenster prüfen | im Prozess (Start-Intent + Bildschirmbeobachtung) | ja | braucht die vom Besitzer eingeschaltete Bildschirmbeobachtung; nur aus dem Vordergrund | CONFIRM | `VerifiedAppDispatchTest` | behalten |
| `read_app_screen` | sichtbaren Text einer App lesen | im Prozess (Bedienungshilfe) | ja | Bildschirmbeobachtung | CONFIRM | Bestand | behalten; bleibt für Claude gesperrt (lokal-only) |
| `stop_app` | App beenden (`am force-stop`) | **nur Brücke** | nein | kein App-API | CONFIRM | `DeviceToolGateTest` | auf der Brücke lassen |
| `restart_app` | beenden und neu starten | **nur Brücke** | nein | kein App-API | CONFIRM | wie oben | auf der Brücke lassen |
| `install_apk` | APK still installieren (`pm install`) | **nur Brücke** | nein | still nur Shell; mit Androids Dialog ginge es im Prozess | CONFIRM | wie oben | auf der Brücke lassen; Folgeauftrag: Weg über Androids Installationsdialog (Updater hat ihn schon) |
| `uninstall_app` | App entfernen (`pm uninstall`) | **nur Brücke** | nein | `REQUEST_DELETE_PACKAGES` fehlt | DESTRUCTIVE | wie oben | auf der Brücke lassen |
| `list_files` | Ordner auflisten | **im Prozess** (java.io), Brücke als Ersatz | **ja**, mit „Zugriff auf alle Dateien“ | `Android/` gesperrt | READ_ONLY | `DeviceFileToolsTest`, `NativeDeviceToolsTest`, Ketten-Regression | **nativ gemacht**; gleiche Ausgabeform (`ls -lah`) |
| `find_files` | Dateien nach Name suchen | **im Prozess**, Brücke als Ersatz | **ja**, mit Dateizugriff | wie oben | READ_ONLY | wie oben | **nativ gemacht**; findet jetzt auch „argon vier“ → `Argon4.pdf` |
| `find_latest_apk` | neueste APK im Download-Ordner | **im Prozess**, Brücke als Ersatz | **ja**, mit Dateizugriff | – | READ_ONLY | wie oben | **nativ gemacht** |
| `get_large_files` | größte Dateien/Ordner | **im Prozess**, Brücke als Ersatz | **ja**, mit Dateizugriff | – | READ_ONLY | wie oben | **nativ gemacht**; gleiche Form (`du -ak`) |
| `move_file` | Datei verschieben | **im Prozess**, Brücke als Ersatz | **ja**, mit Dateizugriff | nur Download, Documents, DCIM, Pictures, Movies | CONFIRM | `DeviceFileToolsTest`, `NativeDeviceToolsTest` | **nativ gemacht**; überschreibt nie (die Shell tat es) |
| `delete_file` | eine Datei löschen | **im Prozess**, Brücke als Ersatz | **ja**, mit Dateizugriff | dieselben Ordner; nie ein Ordner | DESTRUCTIVE | wie oben | **nativ gemacht** |
| `take_screenshot` | Screenshot (`screencap`) | **nur Brücke** | nein | still nicht möglich | CONFIRM | `DeviceToolGateTest` | auf der Brücke lassen |
| `record_screen` | Bildschirmaufnahme | **nur Brücke** | nein | wie oben | CONFIRM | wie oben | auf der Brücke lassen |
| `open_android_settings` | Einstellungsseite öffnen | im Prozess (Intent) | ja | nur aus dem Vordergrund | CONFIRM | NOT_RUN | behalten; neue Seite „dateizugriff“ |
| `search_device` **neu** | Apps, Dateien, Ordner nach Namen | im Prozess | ja (Apps immer, Dateien mit Dateizugriff) | – | READ_ONLY | `DeviceToolRegressionTest`, `NativeDeviceToolsTest`, `DeviceToolsAndroidUiTest` | neu |
| `read_file` **neu** | Text einer Datei (Text, PDF-Textebene, DOCX) | im Prozess | ja, mit Dateizugriff (eigene Dateien immer) | PDF-Text ab Android 15 | READ_ONLY | `DeviceFileTextAndDocumentTest`; PDF-Textebene: Attrappe, echtes `PdfRenderer` NOT_RUN | neu |
| `create_document` **neu** | PDF/Word im Download-Ordner anlegen | im Prozess (`PdfDocument`, `MediaStore`) | ja, ohne jede Erlaubnis | – | CONFIRM | `DeviceFileTextAndDocumentTest`, `DeviceToolsAndroidUiTest` (echtes PDF) | neu |
| `open_file` **neu** | Datei in Androids Anzeige-App zeigen | im Prozess (`ACTION_VIEW`) | ja | nur aus dem Vordergrund; fremde Datei braucht Dateizugriff | READ_ONLY, an den Besitzersatz gebunden | `NativeDeviceToolsTest`, `DeviceToolsAndroidUiTest` | neu |
| `list_recent_files` **neu** | zuletzt geänderte Dateien | im Prozess | ja, mit Dateizugriff | – | READ_ONLY | `DeviceFileToolsTest` | neu (nur Cloud-Gehirn) |
| `get_folder_overview` **neu** | Ordner zählen und wiegen | im Prozess | ja, mit Dateizugriff | – | READ_ONLY | wie oben | neu (nur Cloud-Gehirn) |
| `search_file_text` **neu** | Text in Textdateien suchen | im Prozess | ja, mit Dateizugriff | keine PDFs/Word | READ_ONLY | wie oben | neu (nur Cloud-Gehirn) |
| `read_clipboard` **neu** | Zwischenablage lesen | im Prozess | ja | nur mit Eingabefokus | CONFIRM | `NativeDeviceToolsTest`, `DeviceToolsAndroidUiTest` | neu (nur Cloud-Gehirn) |
| `write_clipboard` **neu** | Text in die Zwischenablage | im Prozess | ja | – | CONFIRM | wie oben | neu (nur Cloud-Gehirn) |
| `share_file` **neu** | Androids Teilen-Dialog für eine Datei | im Prozess (`ACTION_SEND`) | ja | nur aus dem Vordergrund | CONFIRM, als Weitergabe „schwerwiegend“ | wie oben | neu (nur Cloud-Gehirn) |

## Befunde

1. **Dateiwerkzeuge hingen an Shizuku.** Alle sechs liefen als Shell-Befehl. Jetzt lesen sie den Speicher selbst
   (java.io unter „Zugriff auf alle Dateien“); die Brücke ist nur noch Ersatz, wenn die Erlaubnis fehlt.
2. **Sieben Systemzentrum-Werkzeuge wurden jedem Modell angeboten, konnten aber für ein Modell nie laufen**
   (`SYSTEM_TOOLS_REQUIRE_EXPLICIT_LOCAL_ACTION`): ein Modellaufruf scheiterte nach dem Start, ein ändernder sogar erst
   nach der Freigabe des Besitzers. Jetzt: vor dem Start abgelehnt („läuft nur im Systemzentrum“), und dem lokalen
   Modell nicht mehr deklariert.
3. **Jede Werkzeug-Deklaration kostet das lokale Modell Kontext.** Die 33 Deklarationen belegten schon über die Hälfte
   des 4 096-Token-Fensters (`LocalNativePromptByteBudgetTest`: ≈ 7 043 Byte ≈ 2 348 Token). Zehn weitere hätten das
   lokale Modell praktisch stillgelegt. Lösung: `ToolSurface` – das lokale Modell bekommt 30 Deklarationen (ohne die
   sieben aus Befund 2, mit vier neuen: suchen, lesen, Dokument anlegen, anzeigen) und damit **weniger** Byte als vorher
   (Test `theOnDeviceModelIsToldLessThanBeforeAlthoughItGotNewTools`); das Cloud-Gehirn bekommt alle. Die gemessene
   Präfix-Tokenzahl auf dem Gerät: NOT_RUN.
4. **Ein Werkzeug, das nicht laufen kann, scheiterte erst nach dem Start** – als „Ausgang unbekannt“. Jetzt entscheidet
   eine Vorprüfung im ToolGate (`ToolPrecheck`) vor Freigabe und Start: fehlt die Brücke, die Android-Erlaubnis oder
   eine Cloud-Freigabe, endet der Auftrag mit genau diesem Grund im Klartext; niemand wird um ein Ja für etwas gebeten,
   das nicht laufen kann.
5. **Der Startkern verlangte Shizuku für jedes Gerätewerkzeug außer `get_storage_info`** – auch für Akku, Netzwerk,
   Geräteinfo und App-Liste, die nie eine Brücke brauchten. Behoben: nur noch die acht echten Brücken-Werkzeuge.
6. **Offen (nicht geändert):** `stop_app`, `restart_app`, `uninstall_app` nehmen einen freien App-Namen an und lösen
   ihn erst bei der Ausführung zu einem Paket auf; der Freigabedialog zeigt dann nur „[Text, n Zeichen]“. `launch_app`
   verlangt dafür schon heute den eindeutigen Paketnamen. Empfehlung: dieselbe Regel für diese drei – Besitzerentscheidung.

## Neue Werkzeuge

| Werkzeug | Argumente | Risiko | Freigabe |
|---|---|---|---|
| `search_device` | `query`, `scope?` (apps · dateien), `limit?` | READ_ONLY | keine; Cloud: Namensfreigabe |
| `read_file` | `path`, `pages?` („2“ · „2-4“), `offset?` | READ_ONLY | keine; Cloud: Inhaltsfreigabe |
| `create_document` | `title`, `content`, `format?` (pdf · docx), `name?` | CONFIRM | ein Ja im Dialog; mit Besitzerschalter ohne Dialog, wenn der Besitzersatz das Dokument verlangt |
| `open_file` | `path` | READ_ONLY | keine – aber nur, wenn der Besitzersatz Anzeigen/Öffnen verlangt; höchstens eine Datei je Auftrag |
| `list_recent_files` | `filter?`, `limit?` | READ_ONLY | keine; Cloud: Namensfreigabe |
| `get_folder_overview` | `path?` | READ_ONLY | keine; Cloud: Namensfreigabe |
| `search_file_text` | `query`, `path?`, `limit?` | READ_ONLY | keine; Cloud: Inhaltsfreigabe |
| `read_clipboard` | – | CONFIRM | ein Ja; Cloud: zusätzlich Inhaltsfreigabe |
| `write_clipboard` | `text` | CONFIRM | ein Ja |
| `share_file` | `path` | CONFIRM | ein Ja; nach fremdem Inhalt nur für eine Datei, die der Besitzer selbst genannt hat |

**Warum `create_document` CONFIRM ist:** es verändert den Speicher des Besitzers. Standard ist deshalb das eine Ja im
vorhandenen Dialog (der jetzt Titel, Format, Ziel, Umfang und Anfang zeigt). Damit er nicht gefragt wird, was er eben
selbst verlangt hat, gibt es den Schalter „Dokumente ohne Rückfrage anlegen an“ (Standard: aus): dann entfällt der
Dialog – aber nur, wenn sein eigener Satz ein Dokument verlangt (Erstell-Verb und Dokument-Wort, nicht verneint), nie
für ein Dokument, das sich nur das Modell ausgedacht hat. Immer: eine neue Datei im Download-Ordner, nie über eine
vorhandene, höchstens zwei je Auftrag, höchstens 24 000 Zeichen Inhalt, nach dem Schreiben zurückgelesen und verglichen.

**Warum `open_file` READ_ONLY ist:** Anzeigen verändert, sendet und löscht nichts; die Datei geht mit einem reinen
Leserecht an die Anzeige-App, die der Besitzer in Android gewählt hat. Zwei Rückfragen für „erstelle … und zeig sie
mir“ wären genau das doppelte Fragen, das er nicht will. Damit kein Modell ungefragt Fenster öffnet: nur wenn der
Besitzersatz selbst Öffnen/Anzeigen verlangt (verneint zählt nicht), eine Datei je Auftrag, keine APK (das würde
Androids Installation starten), keine Webseiten/Skripte, keine unbekannten Typen. Der eingebaute PDF-Leser bleibt der
Besitzerbefehl „Öffne das PDF …“ (Dokumentenablage); eine saubere, synchrone Einhängestelle dafür gibt es nicht.

## Cloud-Freigaben für Werkzeugergebnisse

Ergebnisse gehen an das aktive Gehirn. Mit Claude würden Dateinamen und Inhalte das Gerät verlassen. Deshalb zwei
getrennte Freigaben, beide **aus**, bis der Besitzer sie einschaltet (getippt oder gesprochen):

| Freigabe | gilt für | Befehl |
|---|---|---|
| Namen | Suchen und Listen: `search_device`, `find_files`, `list_files`, `find_latest_apk`, `get_large_files`, `list_recent_files`, `get_folder_overview`, `list_installed_apps`, `find_app`, `get_app_info`, `list_processes` | „Dateinamen an Claude erlauben“ / „… sperren“ |
| Inhalte | `read_file`, `search_file_text`, `read_clipboard`, `read_logcat` | „Dateiinhalte an Claude erlauben“ / „… sperren“ |

Fehlt die Freigabe und Claude ist gewählt, wird das Werkzeug **nicht ausgeführt**; der Besitzer liest, welche Freigabe
fehlt, wie er sie erteilt und dass das lokale Modell dasselbe kann, ohne etwas zu senden. Mit dem lokalen Modell und in
festen Abläufen der App (Aufgabenkette, Startkern, Systemzentrum) gilt die Sperre nicht – dort geht nichts an die Cloud.
„Werkzeugfreigaben zeigen“ nennt den Stand aller Schalter; „Dateizugriff einrichten“ öffnet Androids Seite für
„Zugriff auf alle Dateien“.

## Nicht gebaut – mit Grund

- **Claude-seitige Websuche und Code-Ausführung:** eigener Folgeauftrag (vom Besitzer freigegeben).
- **Texterkennung für gescannte PDFs und Bilder (OCR):** braucht ein Modell oder eine Bibliothek; ohne das wäre jedes
  Ergebnis erfunden. Ein Scan wird als „keine Textebene“ gemeldet.
- **Excel/PowerPoint lesen, Text in PDFs suchen:** machbar, aber nicht billig genug für dieses Paket.
- **Datei kopieren, umbenennen, Ordner anlegen, Text an eine Datei anhängen:** ändernde Werkzeuge; jedes braucht seine
  eigene Freigabe- und Rücknahme-Regel. `move_file` deckt Umbenennen im selben Ordner bereits ab.
- **Kalender, Kontakte, SMS, Anrufliste, Standort:** jede dieser Quellen braucht eine neue Android-Erlaubnis – in
  diesem Paket ausgeschlossen (`NormalInstallManifestTest` hält den Satz fest).
- **App-Speicher und Akkuverbrauch je App, Prozessliste ohne Brücke:** bräuchten `PACKAGE_USAGE_STATS`.
- **Screenshot ohne Brücke:** ginge über die Bedienungshilfe (`takeScreenshot`) oder MediaProjection; beides ändert die
  Dienstkonfiguration bzw. verlangt je Sitzung Androids Dialog – Folgeauftrag.
- **Eingebauter Leser für `open_file`:** siehe oben.

## NOT_RUN

- Alles auf dem echten Fold7: Lauf der sechs Dateiwerkzeuge über einen Speicher mit zehntausenden Dateien (Dauer,
  Zeitgrenze 10 s), `MediaStore`-Eintrag und Anzeige-App für ein angelegtes Dokument, Medienindex-Verweis für eine
  fremde Datei, Zwischenablage nach dem Freigabedialog, Präfix-Tokenzahl des lokalen Modells mit der neuen Deklaration,
  Verhalten von Claude und des lokalen Modells mit den neuen Werkzeugen.
- Textebene eines echten PDFs (`PdfRenderer`): im Hosttest durch eine Attrappe ersetzt.
- Die Brücken-Werkzeuge selbst (unverändert).
