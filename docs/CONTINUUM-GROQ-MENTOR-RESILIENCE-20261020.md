# Continuum Groq mentor – stabile, widerrufbare Gerätekopplung

Stand: 20. Oktober 2026. **Experimenteller Android-Patch in Draft PR #42, nicht in `main`, noch kein Geräte-Abnahmenachweis dieser Änderung.**

## Vertrauensgrenzen

- Render-Service `device-ai-continuum-mentor` übernimmt nur vom Besitzer freigegebene SHA-256-Fingerprints aus `DEVICE_AI_MENTOR_APPROVED_DEVICES` (keine App-Schreibberechtigung). Android generiert eine EC-P-256-Signatur mit AndroidKeystore-Schlüssel und sendet keinen privaten Schlüssel.
- Das Backend erstellt ein `MENTOR_ONLY`-Bearer-Token aus Backend-Root-Token und Fingerprint. Der verschlüsselte Token liegt in privaten Android-App-Einstellungen im AndroidKeystore und überlebt gewöhnliche App-Neustarts und Updates mit derselben App-Identität. Das ist **keine** Garantie über Deinstallation, Keystore-Verlust, Widerruf oder Wechsel der Installationssignatur hinweg.
- Wird der bereits gespeicherte Mentor-Token **definitiv mit HTTP 401** abgelehnt, darf die App einmalig die bekannte AndroidKeystore-Identität neu signieren und sich nur für einen bereits betreiberseitig freigegebenen Fingerprint ein neues Token ausstellen lassen. Die komplette Benutzeranfrage wird nur nach nachgewiesenem auth rejection einmal erneut gesendet; dies kann nicht nach Timeout, HTTP 429/5xx oder einer unbekannten Netzunterbrechung geschehen. Die Freigabeliste bleibt auch beim Retry maßgeblich; `PAIRING_PENDING` sperrt den Mentor.
- Während eines Render-Kaltstarts darf ausschließlich der **GET-Challenge-Abruf** nach I/O-Fehler oder HTTP 502 bis 504 **einmal** neu versucht werden. Keine Endlos-Reconnect-Schleife, kein Foreground-Service, kein permanent geöffnetes Netzwerk und kein regelmäßiges verstecktes Daten-Senden.
- Ein Wechsel der Backend-URL macht den alten privaten Mentor-Token ungültig; er wird lokal gelöscht, damit er nicht an ein anderes Backend übertragen wird.
- Jede externe Entwicklungsfrage wird über das vorhandene `showGroqMentorDialog`-Fenster vorbereitet und erst durch **„EINMALIG SENDEN“** versendet. Kein automatischer Upload von GitHub-Quelltexten, persönlichen Dateien, Buildlogs, Erinnerungen oder Geräteinformationen. Groq hat keine Shell-/ToolGate-/Git-Schreibrechte. Groq-Antworten bleiben `UNVERIFIED_EXTERNAL`.

## Integration

Das bisherige Android-`Entwicklungszentrum` bietet nun `GROQ · CODING-ZWEITMEINUNG (OPTIONAL)`. Das öffnet den schon bestehenden expliziten Text-Consent-Dialog mit einer nicht privaten generischen Coding-Frage. Es verbindet **noch keine automatische autonome Repair-Pipeline**, erlaubt aber nutzerkontrollierte technische Rückfragen im vorgesehenen UI.

## Betrieb und Grenzen

Der aktuell konfigurierte Render-Dienst wird als **Free-Webservice** betrieben (Beobachtung aus Render-Service-Metadaten, 20.10.2026). Solche Instanzen können im Leerlauf schlafen; bei Mobilfunkunterbrechung, Betreiberdeploy, ausgefallenem Upstream oder API-Kontingent ist daher **keine unterbrechungsfreie 24/7-Verbindung garantiert**. Das Konzept ist ein sicherer Wiederaufbau bei Bedarf, keine permanente Socket-Verbindung oder Server-Warmhaltung. Netzabhängige Mentor-Aufgaben fallen kontrolliert aus; die lokale Device-AI-Basis darf weiter funktionieren.

## Regression und Abnahme

1. `node --test .github/continuum/groq-mentor-resilience-wiring.test.mjs .github/continuum/groq-mentor-ui-wiring.test.mjs`: Sicherheits- und UI-Verträge prüfen.
2. `cd backend && node --test test/mentor-pairing.test.js`: Backend-Schlüsselrotation, Revocation, Signaturprüfung sowie Replay-Schutz prüfen.
3. Android JVM- und Integrationstests ausführen, dann auf ARM64 mit gepinnter JDK-17-/Gradle-/aapt2-Toolchain kompilieren und Lint prüfen.
4. Nur nach expliziter Besitzerinstallation auf dem Samsung die Praxis testen: Pairing einmal genehmigen, App beenden/neustarten, `mentor/status` abrufen, einen simulierten 401 nach Root-Token-Rotation mit noch bestehender Fingerprint-Freigabe validieren, bei Widerruf `PAIRING_PENDING` sicherstellen, bei Netzwerkverlust keine doppelte Groq-Frage.
5. `main` nicht verändern; vorhandene Preview nicht automatisch ersetzen, keine unbestätigten Reparaturen pushen.

**Evidenzstatus:** Änderungen und negative Testfälle im isolierten Branch gespeichert. Keine Behauptung, dass diese neuen Tests ausgeführt, die neue APK installiert oder die Kopplung am Gerät nach der Änderung bestätigt wäre.
