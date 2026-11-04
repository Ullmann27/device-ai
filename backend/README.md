# Device AI Backend

Dieser Dienst hält die API-Schlüssel außerhalb der Android-APK. Er vermittelt
den bestehenden Cloud-Agenten zur OpenAI Responses API und ausdrücklich
freigegebene einzelne Mentorfragen zu Groq, OpenRouter oder Inworld Router.

## Inworld Router: optionaler Online-Mentor (EXP-10)

> **Zurückgestellter Prototyp:** Nach Präzisierung des Ziels (eigene KI, Avatar, Darstellung und Gedächtnis) wird diese reine Einzelfragen-Anbindung nicht zur Übernahme oder Aktivierung vorgeschlagen. Die folgende Beschreibung dokumentiert den getesteten Entwurf. Ein konkreter Nutzen für Avatar oder Gedächtnis ist damit noch nicht nachgewiesen. Siehe `docs/research/omega/INWORLD-FIT-ASSESSMENT-20261104.md`.

Inworld verwendet den vorhandenen authentifizierten Mentorweg. In der Android-App
öffnet **INWORLD · EINZELFRAGE** die Einmalfreigabe. Nur die dort freigegebene Frage
wird über das Backend übertragen. Die Antwort bleibt als externe, unbestätigte
Textausgabe gekennzeichnet. Sie erhält keine Geräte-, Werkzeug- oder
Gedächtnisschreibrechte. Der lokale Dialog benötigt Inworld nicht.

Der Eintrag ist im Lernzentrum, im Modellzentrum und unter **Cloud-KI einrichten**
erreichbar. Ohne Gerätefreigabe führt er zuerst zur bestehenden Kopplung und
anschließend wieder zur Inworld-Frage. Jede Frage benötigt eine eigene Bestätigung;
zu lange Entwürfe werden vollständig angezeigt und abgelehnt, statt abgeschnitten.
Die Frage startet auch bei bereits aktivierter Dauerforschung keine zusätzliche
Themensuche oder Lernvormerkung. Modell und Denkstufe werden vom Backend gewählt.

### Backend-Konfiguration

Auf dem Backend-Dienst konfigurieren, der von der App als Mentor-Backend verwendet
wird. Bei Render geschieht das unter **Environment** des betreffenden Dienstes.

| Variable | Standard / Zweck |
| --- | --- |
| `INWORLD_API_KEY` | Vollständiger Portal-Schlüssel als Server-Secret; kein Wert in diesem Repository |
| `INWORLD_MENTOR_ENABLED` | `false`; erst zur bewussten Aktivierung auf `true` setzen |
| `INWORLD_MENTOR_MODEL` | `inworld/models/gemma-4-31b-it`; vom Betreiber wählbare Modellkennung |
| `INWORLD_MENTOR_REASONING_EFFORT` | `medium`; erlaubt: `none`, `minimal`, `low`, `medium`, `high`, `xhigh`, `max` |
| `INWORLD_MENTOR_DAILY_LIMIT` | `10`; ganze Zahl von 1 bis 100 |
| `DEVICE_AI_TOKEN` | Bestehende Backend-Authentifizierung; separat vom Inworld-Schlüssel |

Den Standard-API-Schlüssel bei Bedarf im [Inworld Portal](https://platform.inworld.ai/)
erstellen und vollständig als `INWORLD_API_KEY` hinterlegen. Er wird als
`Authorization: Basic <api-key>` verwendet, ohne zusätzliche Base64-Kodierung.
Der Schlüssel wird weder in die APK noch in eine Client-Konfiguration übernommen.
Die vorhandene Geräte-Kopplung oder das bestehende Device-Token authentifiziert
weiterhin ausschließlich den Zugriff auf das eigene Backend.

### Verwendeter Vertrag

- Backend: `GET /v1/mentor/status?provider=INWORLD` und
  `POST /v1/mentor/answer`, beide mit vorhandener Mentor-/Device-Authentifizierung.
- POST akzeptiert ausschließlich `provider`, `consent` und `question`;
  `provider` ist `INWORLD`, `consent` muss `true` sein.
- Upstream: festes `POST https://api.inworld.ai/v1/chat/completions` mit
  `stream: false`, `max_completion_tokens: 2048` und
  `extra_body.reasoning.effort` aus der Server-Konfiguration.
- Pro Freigabe genau ein Upstream-Versuch. Kein stiller Anbieterwechsel und kein
  automatischer Wiederholungsversuch bei einer leeren Antwort oder einem Fehler.
- 3–1800 Zeichen pro Frage, insgesamt 45 Sekunden Upstream-Zeitlimit,
  höchstens 64 KiB Antwortkörper und 4200 Zeichen dargestellter Antwort.
- Ausgegeben wird ausschließlich `choices[0].message.content`; Reasoning,
  Werkzeugvorschläge und interne Metadaten werden nicht an die App weitergereicht.
  Eine vorhandene gültige `model`-Kennung des Ergebnisses wird übernommen.
- Der Client-Abbruch beendet den laufenden Backend-Fetch. Das garantiert keine
  Rückerstattung oder Beendigung einer bereits beim Provider gestarteten Generierung.

Die Tagesgrenze zählt Versuche je UTC-Tag **pro Serverprozess** und liegt im
Arbeitsspeicher. Ein Neustart setzt sie zurück; mehrere Instanzen besitzen
getrennte Zähler. Sie ist deshalb keine kontoweite Kostengrenze.
Die angeforderte Denkstufe und Modellverfügbarkeit hängen vom ausgewählten Modell
ab; ein erfolgreicher HTTP-Aufruf bestätigt nicht automatisch, dass die
Denkstufe vom Modell angewendet wurde.

### Prüfung und Fehler

Die normalen Tests verwenden ausschließlich synthetische Schlüssel und lokale
Fixtures:

```bash
cd backend
npm test
npm run check
```

`test/inworld-api-integration.test.js` startet den echten Express-Dienst mit einem
lokalen Inworld-Testdienst. Es prüft Authentifizierung, Freigabe, Basic-Header,
Antwortformat, Fehlercodes, Parallelzugriffe und Tageslimit. Der Testprozess
übernimmt keine Umgebungsschlüssel und blockiert andere ausgehende Fetch-Aufrufe.

Erst nach sicherer Konfiguration des Backend-Secrets und ausdrücklicher Aktivierung
ist ein echter, potenziell kostenpflichtiger Einzeltest vorgesehen:

```bash
npm run smoke:inworld -- --confirm-live
```

Der Test sendet eine feste synthetische Frage und protokolliert nur Anbieter,
Modell und Erfolgsflags. Keine Frage, Antwort, Reasoning-Texte oder Schlüssel
werden ausgegeben. Ohne Flag, Secret oder Aktivierung erfolgt kein Modellaufruf.
Der Test benutzt einen eigenen Prozess und teilt dessen Tageszähler nicht mit
einem bereits laufenden HTTP-Dienst.

Fehler wie `INWORLD_KEY_REJECTED`, `INWORLD_MODEL_UNAVAILABLE`,
`INWORLD_REQUEST_REJECTED`, `UPSTREAM_LIMIT`, `UPSTREAM_TIMEOUT` und `DISABLED`
werden als feste Codes zurückgegeben. Provider-Fehlertexte werden verworfen.
Zum Rücksetzen `INWORLD_MENTOR_ENABLED=false` setzen und den Backend-Prozess
kontrolliert neu starten. Ein lokal grüner Testlauf belegt keinen Live-Modellaufruf,
keine Veröffentlichung und keinen Fold7-Gerätetest.

Quellen: [Quickstart](https://docs.inworld.ai/router/quickstart),
[Chat completions](https://docs.inworld.ai/router/capabilities/text-chat),
[OpenAI-Kompatibilität](https://docs.inworld.ai/router/openai-compatibility),
[Reasoning](https://docs.inworld.ai/router/capabilities/reasoning),
[Request-level routing](https://docs.inworld.ai/router/capabilities/request-level),
[API-Referenz](https://docs.inworld.ai/api-reference/routerAPI/chat-completions).

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
