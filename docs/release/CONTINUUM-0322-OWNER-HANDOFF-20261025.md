# Continuum 0.3.22 / versionCode 23 – Besitzer-Haupt-App: Build- und Übergabebericht

Stand: 25. Oktober 2026. **Status: READY FOR OWNER-BOUND SIGNING PROCEDURE, NOT SIGNED, NOT DELIVERED, NOT FOLD7-TESTED.** Keine Freigabe durch diesen Bericht.

## Ziel und Produktidentität

- **Eine In-Place-Aktualisierung** der bestehenden Haupt-App, **keine zusätzliche Preview-/Desktop-APK**.
- Erwartetes Android-Paket: `at.heinz.deviceai.mentorpaired`.
- Bestehend veröffentlicht: 0.3.21 / `versionCode=22`; Artefakt SHA-256 `f02130ab6ab733f458bef7e7c351702574040c7f7d065eea2965a2189d6319b0`.
- Neues Ziel: 0.3.22 / `versionCode=23`.
- Zu erhaltende Signierzertifikat-SHA-256: `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`.
- Vorhandene v22-Referenz: https://device-ai-mentor-apk-preview.onrender.com/BUILD-INFO.txt; vollständig bytegeprüfte Rückfall-APK wird vom v23-Rezept zwingend geladen/archiviert.

## Genau geprüfter Anwendungsquellstand

- PR #133 Astra: https://github.com/Ullmann27/device-ai/pull/133
  `5824126e25d3672156a8bf1487c20164497d5481`: begrenzter Dialogkontext, Gedächtnisprovenienz, frische native Sessions pro Nutzerturn, Werkzeug-Fortsetzung innerhalb desselben Tasks; unabhängiger VH-SR-Vergleich in synthetischen Replays.
- PR #135: `e7ac9fdd94f9c568e1a3a55f42846c88dddb87d9`: selektiv eingebundene vollständige Android Typed-PREPARE-Anzeige, Kotlin-Unit-Tests und `AGENTS.md`.
- PR #139: https://github.com/Ullmann27/device-ai/pull/139
  kombinierter und getesteter Git-Quellstand **`d368220596912a2b3d0766b0bb3f324db60ec658`**.
- Der Research-P-256-Tester in PR #137 ist **NICHT** produktiv an die CCC1-Grant-Erzeugung angeschlossen. Seine Signatur allein verleiht keine Besitzerberechtigung. Die internationalen Forschungsmuster in PR #138 sind dokumentierte Forschungsplanung, keine ungeprüft produktiv übernommenen Fremdmodelle.

## Reale, frisch überprüfte Tests des Integrations-HEAD

Render QA: https://dashboard.render.com/static/srv-dbf1b4rrjlhs7397dag0

- Getesteter Git-Commit: `d368220596912a2b3d0766b0bb3f324db60ec658`.
- `env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs`: **1.195 PASS, 13 SKIP, 0 FAIL**, 1.208 Testfälle; darunter ein historischer v22-Inventartest, der im isolierten nicht signierenden Shallow-Checkout ausdrücklich SKIP ist.
- Backend-Node-Regressionssuite: **60 PASS, 0 FAIL**.
- Android-Gradle: `:app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin` auf dem integrierten Stand: **BUILD SUCCESSFUL**, 40 Tasks, 4m 40s; vollständige JVM-Einzeltestanzahl hier nicht gesondert aus XML erhoben. Android-Tests sind keine Fold7-Instrumentation.
- Auf diesem QA-Dienst **kein signer**, **keine APK**. `STATUS.txt`: `OWNER_V23_PREFLIGHT_GREEN_NO_APK`.
- Quellen: https://continuum-owner-v23-jarvis-qa.onrender.com/{NODE-TESTS.log,BACKEND-TESTS.log,ANDROID-QA.log,STATUS.txt}. Logs sind öffentlich abrufbare Testnachweise und dürfen keine privaten Daten enthalten.

## Neuer separater Besitzer-Releasekandidat

PR #140: https://github.com/Ullmann27/device-ai/pull/140

- Aus genau dem oben geprüften Quellstand wird im RELEASE-BRANCH ein **Quellvorschlag** mit **961** Git-Blob-Pins festgehalten:
  - Source SHA `d368220596912a2b3d0766b0bb3f324db60ec658`.
  - `docs/release/continuum-0322-source-proposal-20261025.json`.
  - Vorschlags-Blob SHA `5628bef104921492d74cf6aaa7ee647018db050a`.
  - State `SOURCE_PROPOSAL_NOT_OWNER_APPROVAL` – nicht als Freigabe interpretieren.
- Neue `scripts/continuum-v23-source-gate.mjs` verlangt **zusätzlich** zur unveränderlichen Quelle eine eigenständige Besitzerfreigabe für Quell-SHA, Manifest-Blob und finalen Builder-Commit; alte v22-Variablen können keine v23-Freigabe erzeugen.
- Neue `scripts/render-mentor-owner-update-v23.sh` prüft dieselbe bisherige P12-Signieridentität und bytevalidierte bestehende 0.3.21-APK; historische v22-Audits dürfen im signierten Build **nicht** per `CONTINUUM_OWNER_V23_QA` übersprungen werden.
- Neuer Dispatcher-Fall `CONTINUUM_OWNER_RELEASE_VERSION=23` bei unverändertem Standard des alten signierten Builds.
- Separater Render-Release-Preflight: https://dashboard.render.com/static/srv-dbf1tr2vcj2c73ehpfog: **5/5 Node-Tests PASS, 0 FAIL, 0 SKIP** auf Release-Commit `948748e9aa3e599b5590c55238e0a3e23d675aa8`. Exakter finaler Builder-SHA muss nach jedem nachfolgenden Commit neu ermittelt, revidiert und explizit freigegeben werden.
- Für signierten Build muss der historische 0.3.21-Quellbaum per Git verfügbar sein (`ec92490bbe7d75a2b9c16a47a94a1991f61837cc`); fehlt er, soll das Release strikt abbrechen.

## NOCH NICHT ERLEDIGT: Besitzerfreigabe / Signierung

1. Der Benutzer muss die v23-Releasefassung **konkret** autorisieren: fest gepinnte Source SHA, Manifest-Blob, finaler Builder SHA, gleicher Paketname und gleiches Zertifikat, keine neuen privilegierten Schnittstellen. Eine pauschale Zustimmung zu Forschung oder altem v22 darf nicht als Zustimmung gelten.
2. Der existierende Render-Signer `device-ai-mentor-apk-preview` besitzt den bisherigen Signierkontext. Seine Git-Branch-/Build-Command-Konfiguration kann über den aktuell verbundenen Render-Connector **nur gelesen, nicht verändert** werden; eine solche Einstellung erfordert bei Bedarf den Benutzer in Render Settings. Den Dienst **nicht** löschen, die P12 **niemals** in Chat, Git oder neue öffentliche Dienste übertragen.
3. Die Freigabewerte `CONTINUUM_OWNER_RELEASE_VERSION=23`, `CONTINUUM_OWNER_V23_APPROVED_SOURCE_SHA`, `CONTINUUM_OWNER_V23_APPROVED_MANIFEST_BLOB`, `CONTINUUM_RELEASE_SHA`, `CONTINUUM_OWNER_V23_APPROVED_SHA` sowie bisher zulässiges `CONTINUUM_ASTRA_UI_SHA` müssen für den exakt gleichen **Signer-Kandidatenbranch** eingestellt sein; nur nach expliziter Zustimmung. Build nur über geschützten bestehenden Signierdienst, nach Git-Remote-/Quelltest. Keine produktive Änderung über das Testlabor.
4. Vor Veröffentlichung: `aapt dump badging` = `at.heinz.deviceai.mentorpaired`, code 23, name 0.3.22; `apksigner verify --print-certs` exakt bisheriges Zertifikat; veröffentlichtes APK `SHA256SUMS` stimmt, alte 0.3.21-APK bleibt im Rollback-Archiv.
5. Erst dann echten Downloadlink für **das Haupt-App-Update** veröffentlichen. Benutzer soll alte App vorher **nicht deinstallieren**, damit App-Daten und Updatepfad erhalten bleiben.

## Geplante Funktionsprüfung auf Fold7

1. Aktualisierung ohne Löschen der App-Daten und Kontrolle der angezeigten Version 0.3.22.
2. Dreistufige Gespräche: Kontext wird beim selben Profil gehalten, nach Sitzungwechsel keine unbeabsichtigte Fortsetzung der nativen Modellunterhaltung.
3. Verifizierte Erinnerung inklusive Quellenkennung, widerrufene Erinnerung wird nicht benutzt.
4. Lesendes Werkzeug inkl. ToolGate und tatsächlicher Beobachtung; keine automatische Besitzerfreigabe.
5. Android Builder: langer vollständiger Coding-Auftrag, scrollbarer PREPARE-Dialog, Abbrechen, separater RUN-Dialog, Fingerprintbindung.
6. Das lokale Modell muss **tatsächlich bereitgestellt/geladen** sein. Vertrags-/Mock-Tests garantieren keine echte Dialogintelligenz. Latenz, RAM, Fehlerrate, Akkuverbrauch und native Modellstabilität dokumentieren.

## GitHub-first / Übernahme

Eigene dauerhafte Forschungs-/Release-Branches, alle Quellen mit SHA, Testbelegen, keine geheimen Arbeitsstände. Keine Mutation von `main`, keine Änderung von Besitzer-P12, kein Zwang zu zusätzlicher App. Nach neuer Release-SHA oder Quellenänderung sind aktuelle Prüfungen zu wiederholen.
