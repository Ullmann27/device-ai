# OpenRouter für Continuum Lokal (optionaler externer Mentor)

**Status:** Quellcode-Kandidat, kein veröffentlichtes APK- oder Backend-Release. Die lokale KI und ihr Gedächtnis bleiben unverändert.

## Sicherheit und Betrieb

Der OpenRouter-Schlüssel wird ausschließlich auf dem persönlichen Render-Backend als Secret `OPENROUTER_API_KEY` hinterlegt, niemals in GitHub, der APK, BuildConfig, Android-Preferences, URLs, Logausgaben oder Screenshots. Bereits öffentlich/gechattet gezeigte Schlüssel müssen bei OpenRouter widerrufen und mit einem **neuen** Secret ersetzt werden. Keine Schlüssel über den Chat senden.

Serverkonfiguration (Render-Webdienst `device-ai-continuum-mentor`):

- `OPENROUTER_MENTOR_ENABLED=true` (standardmäßig deaktiviert).
- `OPENROUTER_API_KEY` (nur als geschütztes Server-Environment-Secret).
- `OPENROUTER_MENTOR_MODEL=openai/gpt-4o` (modellabhängige Preise/Verfügbarkeit bei OpenRouter prüfen).
- `OPENROUTER_MENTOR_DAILY_LIMIT=10` (konfigurierbar, 1–100).

Der feste Endpunkt ist `https://openrouter.ai/api/v1/chat/completions`. Es gibt keinen beliebig konfigurierbaren Zielhost (Schutz vor Credential-Exfiltration). Das bestehende Geräte-Pairing und die Mentor-Authentifizierung schützen die Serverroute `POST /v1/mentor/answer`. Ein Client muss explizit `provider: "OPENROUTER"` und `consent: true` übermitteln. `GET /v1/mentor/status?provider=OPENROUTER` liefert die begrenzte Verfügbarkeit.

In der Android-App: **Modellverwaltung → OpenRouter · externe Einzelfrage** (alternativ Systemcenter). Nur der einzeln bestätigte Fragetext wird zum Server gesendet; keine gespeicherten Erinnerungen, Chat-Historie, Geräteinformationen, Werkzeuge oder automatischen Forschungen. Die Antwort ist als `UNVERIFIED_EXTERNAL` gekennzeichnet und wird **nicht** automatisch ins Gedächtnis oder Modelltraining übernommen.

## Abnahme vor Veröffentlichung

1. `cd backend && npm run check && npm test` mit Dummy-Schlüsseln und Mock-Transport ausführen. Realen API-Schlüssel weder in Tests noch in CI verwenden.
2. Server-Start mit Feature deaktiviert; verifizierter Opt-in-Aufruf muss `DISABLED` liefern. Aktivierung nur durch Backend-Betreiber.
3. GET Status nur nach Geräte-/Token-Auth; POST mit fehlender Zustimmung oder falschem Anbieter ablehnen. Groq muss unverändert funktionieren.
4. Android-Gradle-Compile, Dialoge, tatsächliches Geräte-Pairing, Render-Deployment und einen explizit genehmigten Live-Test mit eng gesetztem Kostenlimit unabhängig prüfen.
5. Release-Signatur und VersionCode gegen die installierte Continuum-Lokal-App prüfen; ohne Originalsignatur **kein** In-Place-APK-Update behaupten.

Diese Integration ist keine allgemeine Websuchmaschine, kein kontinuierliches KI-Re-Training und kein Ersatz für die lokale Continuum-Gehirnarchitektur.
