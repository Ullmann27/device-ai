# Device AI Backend

Dieser Dienst hält den OpenAI-API-Schlüssel vollständig außerhalb der Android-APK und vermittelt zwischen Device AI und der OpenAI Responses API.

## API-Integrationstests ohne externe Modellaufrufe

Die HTTP-End-to-End-Prüfung in `test/api-integration.test.js` startet den echten
Express-Dienst als lokalen Kindprozess. Eine zweite, lokale HTTP-Instanz simuliert
`/conversations` und `/responses`. Es werden weder echte OpenAI-Schlüssel
noch öffentliche Netzwerkzugriffe für Modellantworten benötigt.

```bash
npm install --ignore-scripts --no-audit --no-fund
npm test
```

Die Tests prüfen Bearer-Authentifizierung, Sitzungsfortsetzung,
Android-kompatible JSON-Antworten, Quellen, Funktionsaufrufe, strikte
Funktionsschemas, deaktivierte Web-Tools, Eingabegrößen und Upstream-Fehler.
Eine grüne Mock-Prüfung ersetzt keine reale Android-APK- oder Geräteabnahme.

## Sicherheitsmodell

- `OPENAI_API_KEY` nur als Server-Environment-Secret.
- `DEVICE_AI_TOKEN` ist ein separates langes Token für den persönlichen Device-AI-Client.
- Die Android-App speichert dieses Device-Token verschlüsselt mit Android Keystore.
- Geräteaktionen werden nicht serverseitig per Shell ausgeführt. Das Modell erhält ausschließlich typisierte Function Tools.
- Die Android-App erzwingt die Risk-Policy und fragt vor CONFIRM/DESTRUCTIVE erneut nach.
- Der Server begrenzt Requests und Eingabegrößen.

## Start

1. Node.js 20+ installieren.
2. Variablen aus `.env.example` als echte Environment Secrets konfigurieren.
3. `npm install`
4. `npm start`

Der produktive Dienst muss hinter HTTPS erreichbar sein. Die Android-App akzeptiert für den Cloud-Modus ausschließlich `https://`-Backends.

## Endpunkte

- `GET /health`
- `POST /v1/agent/turn` mit `Authorization: Bearer <DEVICE_AI_TOKEN>`

Das Modell ist über `OPENAI_MODEL` serverseitig austauschbar. Websuche kann mit `WEB_SEARCH_ENABLED=false` abgeschaltet werden.

## Continuum Research Daemon

Der optionale Continuum-Daemon ist ein vom Android-Gerät unabhängiger, persistenter Forschungsprozess.
Er kann deshalb weiterarbeiten, wenn die APK geschlossen oder nicht installiert ist, solange der Server/Worker selbst läuft.

Eigenschaften:

- read-only öffentliche Webrecherche; keine Shell, keine Geräteaktionen, keine Logins und keine Schreibaktionen auf fremden Diensten,
- persistentes, gehashtes Forschungsjournal mit Last-Known-Good-Fallback,
- Quellen-URLs und Quellen-Digest für jede übernommene Beobachtung,
- Wissenslücken-Queue und Planner, der geringe Abdeckung, Alter und Fehlversuche berücksichtigt,
- mehrere Suchstrategien: Grundlagen, aktueller Stand, offene Fragen/Widersprüche und alternative unabhängige Quellen,
- fehlende oder unzitierte Ergebnisse werden als Fehlversuch gespeichert und nicht als Wissen übernommen,
- Webinhalte bleiben `WEB_OBSERVATION / authority=NONE`; sie dürfen keine Geräte- oder Faktenautorität erzeugen,
- Tages- und Zeitbudgets verhindern unkontrollierte API-/Webschleifen.

Lokal starten:

```bash
CONTINUUM_DAEMON_ENABLED=true npm run worker
```

Für echten Dauerbetrieb muss der Prozess auf einem ständig laufenden Server oder Background Worker betrieben werden und sein
`CONTINUUM_STATE_PATH` auf persistentem Speicher liegen. Ein nicht installierter Android-Client kann selbst keinen Code ausführen.

## Kostenfreie, autonome Quellenrecherche (neu)

Der lokale Research-Worker benötigt **keinen** OpenAI-Schlüssel und keine kostenpflichtige KI-API mehr.
Standard ist `CONTINUUM_RESEARCH_PROVIDER=public`. Er nutzt drei fest definierte
öffentliche HTTPS-Metadatenquellen: Wikipedia (Seitensuche), OpenAlex und Crossref.
OpenAlex gestattet einfache Anfragen ohne Schlüssel; Beschränkungen und Anbieterregeln
gelten weiterhin.

```bash
cd backend
npm run worker:once
npm run worker
```

`worker:once` führt einen echten, budgetierten Forschungsversuch aus und beendet
sich danach. `worker` bleibt auf dem **eingeschalteten** lokalen Rechner aktiv,
sucht standardmäßig etwa alle 20 Minuten und begrenzt die Zyklen auf 48 pro 24 Stunden.
Der eigentliche Code läuft auch ohne offene Android-App; eine reine GitHub-Datei führt
jedoch keine Hintergrundaufgaben aus. Für dauerhaftes Weiterarbeiten muss ein lokaler
PC/NAS oder Server eingeschaltet sein und den Worker über einen Prozessmanager betreiben.

Den persistenten Pfad muss der Betreiber auf einen **nicht flüchtigen, gesicherten**
Datenträger konfigurieren, zum Beispiel:

```bash
CONTINUUM_RESEARCH_PROVIDER=public CONTINUUM_STATE_PATH="$HOME/continuum-research-state.json" npm run worker
```

Windows-Nutzer setzen diese Umgebungsvariablen über ihre gewohnte Shell oder den
Dienstmanager. Mehrere Worker dürfen **nicht gleichzeitig dieselbe Zustandsdatei**
beschreiben. Die Android-App greift nicht automatisch auf diese getrennte lokale
Forschungsdatei zu; dafür ist eine authentifizierte, überprüfte Synchronisations-
oder Retrieval-Verbindung zum lokalen Modell noch notwendig.

Pro Durchlauf werden höchstens drei Suchantworten je Anbieter abgerufen und
höchstens acht Quellenreferenzen übernommen. HTTP-Weiterleitungen und freie URLs
werden nicht verfolgt, Metadaten-Antworten bleiben auf 350 KB pro Anbieter begrenzt,
Fehler führen zu protokollierten Fehlversuchen statt erdachten Quellen.

Über `GET /v1/continuum/knowledge?q=Kotlin` (mit dem bestehenden
`Authorization: Bearer <DEVICE_AI_TOKEN>`) kann ein späterer Android-Client
bis zu sechs passende zitierte Quellenbeobachtungen abrufen. Der Endpunkt
liest dieselbe persistente State-Datei auch dann, wenn API und Research-Worker
getrennte Prozesse sind. Er antwortet ausdrücklich mit `WEB_OBSERVATION`
und `authority=NONE`; eine automatisch generierte Antwort oder die
Verbindung zum bereits lokalen Android-Modell wird dadurch noch nicht hergestellt.

**Wichtig:** Das ist überprüfbares *Sammeln und Indexieren von Recherchehinweisen*,
nicht automatisches Retraining eines großen Sprachmodells. Der persistierte
Eintrag behält `WEB_OBSERVATION / authority=NONE`. Die bibliografischen Titel
alleine belegen keine wissenschaftlichen Aussagen. Bestätigtes Gedächtnis und
Modellgewichte werden dadurch nicht ungeprüft verändert.

Der bisherige OpenAI-basierte Forschungsadapter bleibt ausschließlich als
bewusst konfigurierbare Alternative erhalten:
`CONTINUUM_RESEARCH_PROVIDER=openai`. Brain Factory und Hypothesis Tournament
haben weiterhin gesonderte Adapter und können externe Modellschnittstellen benötigen;
sie werden durch den kostenfreien Worker **nicht** automatisch trainiert.

## Brain Factory

Die Brain Factory ist ein eval-gesteuerter Prompt-/Strategie-Evolutionspfad über dem Research Daemon:

1. Aus Wissenslücken, schwacher Themenabdeckung und widersprüchlichen Beobachtungen wird ein Benchmark-Dataset gebaut.
2. Der aktuelle Champion und kurze Fehlersignale erzeugen mehrere Challenger-Strategien.
3. Jeder Challenger beantwortet dieselben Benchmarkfälle.
4. Ein separater Grader bewertet Quality, Grounding, Unsicherheitskalibrierung, Planungsqualität und Safety.
5. Safety- und Grounding-Floors sind harte Gates.
6. Nur ein Challenger, der den Champion um eine Mindestmarge schlägt, wird promoviert.
7. Abgelehnte Kandidaten erzeugen kurze operative Reflexionen für spätere Generationen.
8. Die unveränderliche Governance liegt außerhalb des mutierbaren Prompt-Genoms und wird per Hash gebunden.

Die Factory verändert **Prompts/Strategien, nicht eigenmächtig Programmcode**. Codeänderungen bleiben im GitHub-/CI-/Review-Pfad.

### Always-on Deployment

`render.yaml` beschreibt einen einzelnen bezahlten Render-Webdienst mit persistentem Disk-State unter `/var/data`.
Damit teilen API, Research Daemon und Brain Factory denselben persistenten Zustand. Ein Persistent Disk ist bei Render
nicht für Free-Services verfügbar; die Konfiguration darf daher erst bewusst als kostenpflichtiger Dienst aktiviert werden.

Authentifizierter Diagnose-Endpunkt:

- `GET /v1/continuum/status`

Er liefert nur aggregierte Lern-/Forschungsmetriken und keinen geheimen Prompt- oder Credential-Inhalt.

## Hypothesis Tournament

Nach genügend belegten Forschungszyklen kann optional ein Hypothesen-Turnier laufen:

- Generation Agent erzeugt mehrere ausdrücklich spekulative Hypothesen aus bereits gespeicherter Evidenz.
- Reflection Agent sucht Gültigkeitsrisiken, fehlende Evidenz und Falsifikationstests.
- Ranking Agent führt paarweise Vergleiche durch; Elo-Werte bilden die relative Turnierhistorie ab.
- Evolution Agent kombiniert starke Hypothesen und muss die vorhandene Kritik adressieren.
- Die Sieger erzeugen ausschließlich neue **prüfbare Forschungsfragen**.
- Diese Fragen gehen als `sourceKind=HYPOTHESIS` zurück in die normale Research-Agenda.
- Erst der read-only Research Daemon kann später mit echten Quellen daraus neue `WEB_OBSERVATION`-Einträge erzeugen.
- Hypothesen bleiben immer `authority=NONE`; es gibt keine automatische Beförderung zum Fakt.

Das Turnier ist budgetiert und standardmäßig außerhalb der Render-Blueprint-Konfiguration deaktiviert; der Blueprint aktiviert
es bewusst nur für den vorgesehenen persistenten Always-on-Dienst.
