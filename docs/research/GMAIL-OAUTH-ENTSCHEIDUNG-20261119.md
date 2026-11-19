# Gmail-Verbindung: Entscheidung, Quellen, Datenschutz (19.11.2026)

## Entscheidung

Continuum verbindet Gmail über Googles empfohlenen Android-Weg **`Identity.getAuthorizationClient(activity)
.authorize(AuthorizationRequest …)`** aus `com.google.android.gms:play-services-auth` **22.0.0** (neueste stabile
Version auf Googles Maven, Stand `maven-metadata.xml` vom 25.08.2026). Kein `google-services`-Plugin, keine
`google-services.json`, keine Client-ID und kein Client-Secret in der APK.

### Abweichung vom Wortlaut „Authorization Code Flow mit PKCE“

Der Besitzerauftrag nannte „Authorization Code Flow mit PKCE“. Umgesetzt ist stattdessen der AuthorizationClient:

- Für Android empfiehlt Google genau diesen Weg; die Zustimmung läuft in Googles eigener Oberfläche, nicht in
  einem App-Browser.
- Ein Code-Flow mit PKCE bräuchte für Refresh-Tokens entweder ein Client-Secret (gehört nicht in eine APK) oder
  einen eigenen Server, der Codes tauscht und Refresh-Tokens lagert. Beides würde ein Geheimnis bzw. einen
  Dienst einführen, den der Besitzer betreiben müsste.
- Mit dem AuthorizationClient speichert die App **keinen** Refresh-Token: Google hält die Freigabe, und
  `authorize()` liefert später still ein neues Access-Token, „as long as the granted permissions are not removed
  by the user“. Das erfüllt den Besitzerwunsch „einmal freigeben, bis Widerruf/Ablauf nutzbar“.
- Folge: Die Verbindung funktioniert nur in der original signierten App (Android-Client = Paketname + SHA-1).

## Geprüfte Quellen

- Android Authorization: https://developer.android.com/identity/authorization – `authorize`, `hasResolution`,
  `getPendingIntent`, `getAuthorizationResultFromIntent`, `clearToken`, `revokeAccess`; Access-Token etwa 1 Stunde.
- `AuthorizationClient`-Referenz: https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient
- API der Bibliothek selbst (`javap` auf `play-services-auth-22.0.0.aar`): `AuthorizationRequest.Builder
  .setRequestedScopes/.setAccount`, `AuthorizationResult.getAccessToken/getGrantedScopes/getPendingIntent/hasResolution`
  (kein Ablauf-Getter), `ClearTokenRequest.builder().setToken`, `RevokeAccessRequest.builder().setAccount().setScopes()`.
- Gmail-Scopes und Einstufung: https://developers.google.com/workspace/gmail/api/auth/scopes
- Prüfpflicht nur bei Fremdnutzern: https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification
- Zielgruppen (Persönlich / Extern-Testen / Extern-Produktion): https://support.google.com/cloud/answer/15549945
- Gmail REST: `users.messages.list/get`, `users.threads.get`, `users.messages.attachments.get`, `users.drafts.create`,
  `users.messages.send`, `users.getProfile` (Referenzseiten unter https://developers.google.com/workspace/gmail/api/reference/rest).

## Rechte

| Fähigkeit | Scope | Methoden | Einstufung |
|---|---|---|---|
| Lesen | `gmail.readonly` | messages.list/get (mit `q`, format=full), threads.get, attachments.get, getProfile | eingeschränkt |
| Entwürfe | `gmail.compose` | drafts.create (`gmail.send` reicht nicht) | eingeschränkt |
| Senden | `gmail.send` | messages.send | sensibel |

Jede Fähigkeit wird **einzeln** angefragt („Verbinde Gmail“ fragt nur nach Lesen). Googles compose-Scope erlaubt
technisch auch das Senden; die App sendet trotzdem nur mit dem eigenen Senderecht und nach Bestätigung
(`GmailGrant.maySend`).

## Fehlerbehandlung

401 → `clearToken` + einmal still neu autorisieren + einmal wiederholen · 401 erneut → „Berechtigung erforderlich“ ·
403 `insufficientPermissions` → „Berechtigung erforderlich“ für dieses Recht · 403 `SERVICE_DISABLED`/
`accessNotConfigured` → Gmail-API nicht aktiviert · 403 Ratenlimit, 429, 5xx → höchstens zwei Wiederholungen
(500 ms, 2 s), dann ehrlicher Fehler · `invalid_grant` oder still nötige Zustimmung nach früherer Freigabe →
„Berechtigung widerrufen“ · Netzfehler → „Verbindung unterbrochen“, nie wiederholt · `messages.send` wird bei 5xx
**nie** wiederholt (keine Doppelmail), das Ergebnis heißt dann „unklar“ · `ApiException` 10 (DEVELOPER_ERROR) →
„Google kennt diese App-Fassung nicht“ (Debug/QA-Signatur oder fehlender Android-Client).

## Datenschutzregeln (im Code durchgesetzt)

- Tokens: nur im Arbeitsspeicher (Googles Cache + aktuelles Token je Auftrag), `toString` geschwärzt, nie in
  Log, Prompt, Ausnahme, Bühne oder Datei. Test `noTokenAppearsInAnythingTheLaneSaysLogsOrStores`.
- Gespeichert wird (app-privat) nur: verbunden ja/nein, Kontoadresse, gewährte Scopes, letzte Fehlerart,
  Schalter „Mailinhalte an die Cloud senden“, Zeitpunkt der letzten Verwendung.
- Mailtext, Absender und Betreffe: nur Sitzungsspeicher der Lenkung und „private“ Antworten – angezeigt und
  gesprochen, aber **nicht** in die Gesprächsablage geschrieben und **nicht** in den Modellkontext gelegt.
- Cloud: Mailtext geht an Claude nur mit dem eigenen Schalter (Standard aus). Ohne ihn trägt der Auftrag die
  Marke `CONTINUUM_MAIL_DATA_LOCAL_ONLY_V1`; `ClaudeLocalOnlyGuard` verweigert jede Claude-Anfrage mit dieser Marke
  (`CLAUDE_LOCAL_ONLY_MAIL_DATA`) – unabhängig von der Entscheidung der Lenkung.
- Mailinhalt ist Daten, nie Anweisung: gerahmt, zwischen Markierungen, keinem Parser und keiner anderen Lenkung
  übergeben. Test `aMailThatGivesOrdersIsOnlyText` (0 Entwürfe, 0 Sendungen).
- Internetfreigabe AUS sperrt auch Gmail.

## Nicht gebaut

- Google Drive (nur Hinweiszeile „noch nicht gebaut“).
- PDF- und Binäranhänge lesen (nur Text-Anhänge bis 200 KB; sonst ehrliche Meldung).
- Dauerhaftes Selbstsenden ohne Rückfrage, Löschen, Archivieren, Labels, Weiterleiten.
- Ein eigener Ergebnistyp in `OwnerLaneResult` für Suche/Lesen/Zusammenfassung (Übergabe an die Auftragsfolge, siehe Bericht).
- Hintergrundabruf / Push neuer Mails.
- Gerät und echtes Google: **NOT_RUN** (Checkliste in `docs/handover/GMAIL-VERBINDUNG-EINRICHTUNG-20261119.md`).
