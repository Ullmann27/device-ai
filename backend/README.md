# Device AI Backend

Dieser Dienst hält den OpenAI-API-Schlüssel vollständig außerhalb der Android-APK und vermittelt zwischen Device AI und der OpenAI Responses API.

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
