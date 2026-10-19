# Device AI Continuum – optionaler GroqCloud-Sprachmentor

Diese Integration ist eine **eigene, ausschließlich per Einmalfreigabe aufgerufene**
Textfunktion. Sie ersetzt weder die lokale LiteRT-LM-Inferenz, noch die
kostenfreie Wikipedia-/OpenAlex-/Crossref-Forschung, noch die bestätigten
persönlichen Gedächtnisinhalte. Der Backend-Worker recherchiert oder trainiert
**niemals automatisch mit Groq**.

## Technische Anbindung

**Backend:** `backend/src/groq-mentor.js`, Route
`POST /v1/mentor/answer` in `backend/src/server.js`.
Das Server-Bearer-Token `DEVICE_AI_TOKEN` wird wie bei den vorhandenen
Routen geprüft. Nur folgende JSON-Eingabe ist erlaubt:

```json
{"provider":"GROQ","consent":true,"question":"Wie funktionieren Solarzellen?"}
```

Groq erhält nur zwei Chat-Nachrichten: eine statische Systemregel und die
freigegebene Frage. **Keinen** weiteren Chatverlauf, keine Geräte-ID,
keine Shizuku-Information, keine persönlichen Erinnerungen,
keine Tools, keine Forschungsquellen oder Trainingsgewichte.

Der Adapter ruft nur den fest eingestellten Groq-Host
`https://api.groq.com/openai/v1/chat/completions` auf. Kein dynamisches
Upstream-URL-Feld. `tool_choice: "none"`, `stream: false`,
`max_completion_tokens: 450`, 16 Sekunden Timeout,
höchstens 64 KiB HTTP-Antwort, 1800 Zeichen Frage und Antwort,
nur eine parallele Anfrage, standardmäßig 20 Versuche pro
UTC-Kalendertag und Serverprozess. Die In-Memory-Zählung wird beim
Serverneustart zurückgesetzt; sie ist **keine finanzielle Abrechnungssperre**.
Kostenfreiheit muss über den eigenen **Groq-Free-Plan** und dessen
Billing-/Usage-Einstellungen abgesichert werden. Bei HTTP 429 wird
abgebrochen, nicht automatisch gegen Geld fortgesetzt.

**Android:** `AgentBackendClient.groqMentor()` und
`AgentActivity.showGroqMentorDialog()`. Abruf nur über das zuvor
eingerichtete, eigene HTTPS-Backend mit Device-Token. Ein explizites
Bestätigungsfenster zeigt vor **jeder** Frage, welcher Text extern
gesendet wird. Einstieg: **Lernen → KI-MENTOR · GROQ**,
**System → KI-MENTOR · GROQ** oder Chatkommando
`Mentor: Wie funktionieren Solarzellen?`.

Die Antwort wird mit `UNVERIFIED_EXTERNAL` gekennzeichnet,
darf keinen Tool-Aufruf enthalten und wird weder direkt
als Faktenwissen noch als Trainingsfreigabe übernommen. Der
normale lokale Chat bleibt vollständig unabhängig. Ist der
Mentor deaktiviert oder das Netzwerk nicht erreichbar, wird
**nicht** stillschweigend ein neuer externer Anbieter angerufen.

## Einrichtung auf einem eigenen, laufenden Node-Backend

1. Kostenlosen Groq-Schlüssel im eigenen
   <https://console.groq.com/keys> erstellen; im Groq-Konto
   sicherstellen, dass keine kostenpflichtige Nutzung freigegeben ist.
2. **Nur** am Backend, niemals im APK oder in GitHub-Commits,
   die Umgebungsvariablen konfigurieren:

```sh
GROQ_MENTOR_ENABLED=true
GROQ_API_KEY=<secret value in hosting's secret manager>
GROQ_MENTOR_MODEL=openai/gpt-oss-20b
GROQ_MENTOR_DAILY_LIMIT=20
DEVICE_AI_TOKEN=<separately generated private device token>
```

3. Backend über HTTPS bereitstellen, den bestehenden Token in der
   Android-App unter **System → Cloud-Backend** konfigurieren.
   `GET /v1/mentor/status` ist ebenfalls authentifiziert und
   meldet, ob der Dienst eingerichtet ist; er gibt nie den API-Key aus.
4. App öffnen, Frage selbst verfassen, Einmalfreigabe erteilen.

Der Dienst ist bei fehlender Key-/Enabled-Konfiguration **standardmäßig
ausgeschaltet**. Kein Betreiber sollte Schlüssel oder Tokens über den
Chat verschicken. Keine Betriebsfreigabe ist notwendig, um die App weiterhin
rein lokal zu nutzen.

## Abnahme und Grenzen

```sh
npm --prefix backend run check
npm --prefix backend test
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug -PdeviceAiPreview=true
```

`backend/test/groq-mentor.test.js` testet Deaktivierung,
Fragenbegrenzung, Einmalantwort ohne Tools/Privatkontext,
Upstream-Ratenlimit, Fehlerbehandlung, Tageskontingent, Antwortgröße.
Die Kotlin-Integration benötigt zusätzlich echten Gradle-/Gerätetest.

Kein echter kostenloser Groq-Call ohne **eigenen** API-Schlüssel.
Kein zusätzlicher Build in GitHub Actions behauptet, solange die
bekannte Runner-/Abrechnungsblockade nicht behoben ist.
Ein Mentor-Modell generiert Textvorschläge; es trainiert **nicht**
automatisch den eigenen CRE1-/SWB1-/LiteRT-LM-Kern.
