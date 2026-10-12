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
