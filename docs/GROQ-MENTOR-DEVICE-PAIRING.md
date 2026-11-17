# Continuum: sichere Groq-Mentor-Gerätekopplung

## Sicherheitsgrenze
Die optionale Groq-Anbindung ist nur ein Textmentor. Sie führt keine Android-Tools aus, speichert keine Modellantwort als bestätigte Tatsache und kann keine Git-Arbeitszweige ändern. Jede Frage benötigt die in der Android-App sichtbare Einmalfreigabe.

Der existierende `GROQ_API_KEY` bleibt ausschließlich auf dem HTTPS-Backend. Der privilegierte `DEVICE_AI_TOKEN` bleibt beim Betreiber; er darf niemals als Gerätedatei veröffentlicht oder in die APK eingebaut werden.

## Pairing-Ablauf
1. Android erzeugt beim ersten Pairing ein EC-P256-Schlüsselpaar unter `AndroidKeyStore` (Private Key verlässt den Keystore nicht). Die Identität ist pro Android-Paket isoliert.
2. Der Android-Client liest `GET /v1/mentor/pairing/challenge`: kurzlebiger, durch Backend-HMAC authentifizierter Challenge mit Zeitstempel und zufälliger Nonce.
3. Die App signiert den exakten Text `device-ai-mentor-pair-v1\n<issuedAt>\n<nonce>\n<publicKeyBase64>` mit `SHA256withECDSA`.
4. `POST /v1/mentor/pairing/redeem` validiert Challenge, Android-Schlüsselsignatur, P256-Kurve und SHA256-Fingerprint.
5. Wenn der Eigentümer den vollständigen Fingerprint noch nicht auf dem Betreiberkonto freigegeben hat, antwortet der Server mit HTTP 403 `PAIRING_PENDING`, **ohne Zugangstoken**. Der Fingerprint ist öffentlich und kann als Screenshot an die zuständige Person zur Freigabe übermittelt werden.
6. Der Betreiber prüft die Identität des Geräts außerhalb dieses Endpunkts und trägt den vollständigen Fingerprint in der **Render-Umgebung** als `DEVICE_AI_MENTOR_APPROVED_DEVICES` ein (Kommaseparierung für höchstens acht Geräte). Keine Geheimnisse im Repository speichern.
7. Nach der Bereitstellung wählt der Besitzer in der App erneut **KOPPLUNG PRÜFEN**. Der Server gibt einen nur für Mentor-Endpunkte gültigen HMAC-Zugang aus. Android verschlüsselt ihn im eigenen Keystore-gestützten Tokenstore.
8. Zum Widerruf den Fingerprint aus `DEVICE_AI_MENTOR_APPROVED_DEVICES` entfernen und den Dienst neu bereitstellen. Ein zuvor ausgegebener Mentor-Token verliert sofort seine Gültigkeit.

## Rechte und Schranken
- Nur `/v1/mentor/status` und `/v1/mentor/answer` erlauben den neuen Mentor-Token. Der Besitzer-Token bleibt separat erforderlich für `/v1/agent/turn`, `/v1/continuum/*` sowie andere privilegierte Endpunkte.
- Unbekannte/abgelaufene Challenges, falsche elliptische Kurven, ungültige Signaturen, erneut eingelöste Nonces oder ungeprüfte Fingerprints werden abgewiesen. Neustarts invalidieren offene Challenges.
- Rate Limit auf Pairing-Endpunkten: zwölf Versuche je 15 Minuten pro Client-IP zusätzlich zum globalen Serverlimit. Der Betreiber-Fingerprint ist kein Passwort; die zugrunde liegende private Schlüsselsignatur ist weiterhin erforderlich.
- Groq beantwortet nur die pro Anfrage bestätigte Frage. Er erhält weder Gerätedaten noch Erinnerungen oder Tooldefinitionen.
- Nach einem Geräteverlust oder einer kompromittierten Installation die Betreiberfreigabe aktiv widerrufen.
- Anfragen und Antworten nicht als abgesichertes Langzeitwissen übernehmen.

## APK
Der getrennte Android-Pairing-Test hat die Paketkennung `at.heinz.deviceai.mentorpaired`. Er überschreibt `at.heinz.deviceai` und `at.heinz.deviceai.mentorpreview` nicht. Der Render-Build muss den festgelegten Preview-Signierer per Zertifikats-SHA256 kontrollieren; kein veröffentlichter Build mit vorübergehender Gradle-Debugsignatur.

## Tests
`node --test backend/test/groq-mentor.test.js backend/test/mentor-pairing.test.js .github/continuum/groq-mentor-ui-wiring.test.mjs`

Im unabhängigen Render-Build folgen zusätzlich Android JVM-Tests, Android-Lint, APK-Build, Signatur- und Paketidentitätsprüfung. Erfolgreicher Build ist kein Ersatz für die abschließende Installation und Funktionsprüfung auf dem echten Samsung.
