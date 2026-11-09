# Device AI – Master Prompt für KI-Ausbau

Du arbeitest direkt am Repository `Ullmann27/device-ai`. Behandle das bestehende Projekt als funktionsfähige Basis, nicht als Wegwerf-Prototyp.

## Rolle

Handle als Principal Android Engineer, AI Agent Architect, Security Engineer und Product Designer. Ziel ist ein persönlicher Android-Assistent, der auf einem eigenen Samsung Fold läuft, natürlichsprachliche Aufträge versteht, bei Bedarf das Internet recherchiert, sichere lokale Geräteaktionen über Shizuku ausführt und eigenständig ohne Kopplung an andere App-Projekte arbeitet.

Arbeite autonom, aber zerstöre keine bestehende Funktionalität. Nach jeder größeren Änderung: kompilieren, Tests ausführen, Fehler beheben und den echten Android-Build verifizieren.

## Nicht verhandelbare Architektur

1. Android-Client: Kotlin, bestehendes Device-AI-Projekt weiterverwenden.
2. Privilegierte lokale Aktionen: weiterhin über Shizuku und die bestehende Shell-Bridge.
3. KI: OpenAI Responses API.
4. Internetrecherche: OpenAI `web_search` Tool.
5. Geräteaktionen: Function Calling / Tool Calling. Das Modell darf niemals rohe Android-Shell-Befehle frei erfinden und ungeprüft ausführen.
6. API-Schlüssel niemals in APK, Quellcode, Gradle, GitHub-Repository oder SharedPreferences speichern.
7. OpenAI-Aufrufe über einen kleinen eigenen Backend-Proxy. API-Key nur serverseitig als Secret/Environment Variable.
8. Für lokale Offline-Funktionen bleibt der bestehende deterministische Interpreter als Fallback erhalten.
9. Destruktive oder sicherheitsrelevante Aktionen benötigen immer eine explizite Nutzerbestätigung.
10. Bestehende Device-AI-Funktionen und Shizuku-Verbindung dürfen nicht regressieren.

## Zielbild

Der Nutzer soll frei formulieren können:

- „Öffne WhatsApp.“
- „Finde die neueste Continuum-APK, prüfe sie vor einem Update.“
- „Prüfe Continuum auf Fehler und erkläre mir, was wahrscheinlich kaputt ist.“
- „Suche im Internet nach der aktuellen Android-16-Shizuku-Problematik und vergleiche sie mit meinem Gerät.“
- „Welche meiner Downloads brauchen am meisten Speicher?“
- „Recherchiere die aktuelle Rechtslage zu Thema X und gib mir Quellen.“
- „Prüfe die installierte Continuum-Version und das Update-Angebot.“
- „Mach einen Screenshot, prüfe anschließend Logcat und erkläre mir den Fehler.“

Die KI plant mehrstufig, ruft nur freigegebene Tools auf, verarbeitet deren Resultate und formuliert danach eine verständliche Antwort.

## Tool Registry

Implementiere eine stark typisierte Tool-Schicht. Mindestens:

### Gerät lesen
- `get_device_info()`
- `get_battery_info()`
- `get_storage_info()`
- `list_installed_apps(userOnly: Boolean)`
- `get_app_info(packageOrName)`
- `list_processes()`
- `read_logcat(packageOrName?, lines?)`

### Apps steuern
- `launch_app(packageOrName)`
- `stop_app(packageOrName)`
- `restart_app(packageOrName)`
- `install_apk(path)`
- `uninstall_app(packageOrName)`

### Dateien
- `list_files(path)`
- `find_files(query, roots, limit)`
- `find_latest_apk(filter?)`
- `get_large_files(roots, limit)`
- `move_file(source, destination)`
- `delete_file(path)`

### Medien
- `take_screenshot()`
- `record_screen(seconds)`

### System
- `open_android_settings(page)`
- `get_network_summary()`

Jedes Tool erhält:
- eindeutigen Namen,
- klare Beschreibung,
- striktes JSON Schema,
- Risk-Level: READ_ONLY, CONFIRM oder DESTRUCTIVE,
- Timeout,
- validierte Argumente,
- strukturierte Rückgabe.

Rohe Shell-Kommandos bleiben ausschließlich im expliziten Expertenmodus und dürfen von der Cloud-KI nicht autonom erzeugt oder ausgeführt werden.

## Agent Loop

Implementiere einen echten Agent-Loop:

1. Nutzeranfrage + relevanten Gerätekontext an Backend senden.
2. Backend ruft OpenAI Responses API mit System-Prompt, Conversation State, `web_search` und den Function Tools auf.
3. Falls Tool Calls kommen:
   - Android-App zeigt vor CONFIRM/DESTRUCTIVE eine Freigabe.
   - READ_ONLY darf automatisch laufen.
   - Tool lokal über Device AI/Shizuku ausführen.
   - Ergebnis strukturiert an Backend zurückgeben.
4. Backend führt Conversation fort.
5. Wiederholen, bis finale Antwort vorliegt.
6. Finale Antwort in Device AI anzeigen inklusive Quellen bei Webrecherche.

Maximale Tool-Schleifen pro Nutzerauftrag fest begrenzen, z. B. 12.

## Internetrecherche

Aktiviere das integrierte OpenAI-`web_search` Tool.

Regeln:
- Bei aktuellen Fakten, Preisen, Software-Versionen, Nachrichten, Rechtslage oder technischen Änderungen recherchieren.
- Quellen/Citations im finalen Ergebnis anzeigen.
- Bei rein lokalen Geräteaktionen nicht unnötig suchen.
- Bei Unsicherheit klar unterscheiden zwischen lokal gemessenen Daten und recherchierten Informationen.

## Modellstrategie

Die Architektur darf das Modell serverseitig konfigurierbar machen.

Empfohlener Default:
- kostengünstiger/normaler Assistent: ein aktuelles ausgewogenes OpenAI-Modell,
- komplexe Recherche, Debugging und Planung: stärkeres Reasoning-Modell.

Keinen Modellnamen tief im Android-Client fest verdrahten. Der Backend-Server entscheidet über Modell und Reasoning-Level.

## Generische App-Bedienung ohne Projektkopplung

Device AI Continuum ist ein unabhängiges Produkt. Allgemeine Android-App-Aktionen
dürfen über vom Nutzer freigegebene, systemkonforme Schnittstellen erfolgen.
Dadurch entsteht keinerlei Entwicklungsabhängigkeit oder Datenaustausch mit
anderen App-Projekten.

Ein generischer `AssistantSkill`-Vertrag bleibt bei Bedarf erweiterbar:
- id
- displayName
- description
- supportedPackages
- toolDefinitions
- promptExtension

Kein app-spezifischer Sonderfall, kein automatisch eingeräumtes Tool-Recht,
kein ungeprüfter Codeimport und keine projektexterne Freigabe.

## Conversation / Memory

Implementiere:
- fortlaufenden Chatverlauf,
- getrennte Sessions,
- optional lokale Zusammenfassungen älterer Turns,
- keine unnötige Übertragung kompletter Dateilisten oder Logcats,
- sensitive lokale Daten nur senden, wenn sie für den konkreten Auftrag erforderlich sind.

Keine permanente heimliche Speicherung sensibler Inhalte.

## Android UI/UX

Die App soll wie ein moderner persönlicher Assistent wirken, nicht wie eine Terminal-Hülle.

Erforderlich:
- Chat-Verlauf mit User-/Assistant-Bubbles,
- Streaming-Antworten,
- klarer Online-/Offline-/Shizuku-Status,
- Tool-Aktivitätsanzeige: „Suche im Web“, „Prüfe installierte Apps“, „Analysiere Logcat“,
- Plan-Preview bei mehrstufigen Aktionen,
- Freigabe-Karten für CONFIRM/DESTRUCTIVE,
- Quellenkarten bei Recherche,
- Schnellaktionen,
- Spracheingabe,
- Fold-optimiertes Responsive Layout,
- Dark Mode,
- saubere Fehlerzustände und Retry.

Auf aufgeklapptem Fold:
- linke Spalte: Chat/Sessions/Skills,
- rechte Spalte: Gespräch, Tool-Aktivität, Ergebnisse.

Auf schmalem Display:
- einspaltige Navigation.

## Backend

Erstelle einen minimalen sicheren Backend-Dienst, z. B. unter `backend/`.

Anforderungen:
- OpenAI API Key ausschließlich über Environment Secret.
- Endpunkte für Agent-Session/Responses.
- Authentifizierung des einzelnen Device-AI-Clients mit eigenem App-Token oder kurzlebigem Session-Token.
- Rate Limits.
- keine Secrets in Logs.
- strukturierte Fehler.
- HTTPS.
- OpenAI Responses API.
- `web_search` aktivierbar.
- Function Tool Schemas aus einer gemeinsamen Versionierung.

Bevorzuge eine kleine, klar wartbare Lösung.

## Sicherheit

READ_ONLY:
- Geräteinformationen lesen
- Apps listen
- Dateien suchen/listen
- Speicher/Logs lesen
- Webrecherche
- Screenshots nur auf ausdrücklichen Nutzerauftrag

CONFIRM:
- App stoppen/neustarten
- APK installieren
- Datei verschieben
- Bildschirmaufnahme
- Einstellungen ändern

DESTRUCTIVE:
- Datei löschen
- App deinstallieren
- größere Systemänderungen

Für DESTRUCTIVE niemals stillschweigend Zustimmung aus vorigen Nachrichten ableiten.

Keine Root-Eskalation.
Keine Umgehung von Android-App-Sandboxen.
Keine versteckte Überwachung.
Keine unbegrenzte autonome Shell.

## Fehlertoleranz

Wenn ein Tool fehlschlägt:
- Ergebnis an das Modell zurückgeben,
- Modell darf alternative sichere Schritte planen,
- nach maximal wenigen Wiederholungen stoppen,
- technischen Grund verständlich anzeigen.

Wenn Shizuku aus ist:
- lokale nicht-privilegierte Funktionen weiter anbieten,
- deutlichen Button „Shizuku starten/verbinden“ anzeigen,
- keine Endlosschleifen.

Wenn Internet fehlt:
- auf lokalen Interpreter zurückfallen,
- deutlich „Offline-Modus“ anzeigen.

## Qualitätssicherung

Vor Abschluss:
- Debug APK erfolgreich bauen.
- Unit Tests für Command Parser, Tool Validation und Risk Policy.
- Tests für mehrstufige Tool-Pläne.
- keine API Keys im Repository.
- Shizuku-Verbindung nach App-Rückkehr automatisch refreshen.
- existierende Aktionen Apps, Downloads, Screenshot, Speicher weiterhin testen.
- Build auf Android API 36.
- GitHub Actions grün.

## Lieferziel

Die nächste Version soll mindestens enthalten:

1. modernes Chat-UI,
2. OpenAI-Backend-Schnittstelle,
3. Webrecherche,
4. Function Calling für Device Tools,
5. mehrstufige Agent-Aufträge,
6. lokaler Offline-Fallback,
7. Risk/Approval-System,
8. generischer App-Skill nur bei belegter Integration,
9. GitHub-Actions-Build,
10. installierbare APK.

Arbeite iterativ. Erstelle keine reine Demo-Oberfläche. Jeder sichtbare Button und jedes Tool muss an echte Funktionalität angeschlossen sein.
