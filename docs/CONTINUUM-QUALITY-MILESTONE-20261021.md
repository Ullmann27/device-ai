# Continuum Qualitätsmeilenstein – Übernahme von Sol 6.1 (21.10.2026)

**Status: SOURCE_INTEGRATED + FULL_RENDER_BUILD_VERIFIED; ANDROID_DEVICE_RUNTIME_NOT_VERIFIED.**

## Präziser Branch-/Artefaktbezug

- Repository `Ullmann27/device-ai`; Sol-Qualitätsbranch `sol/continuum-quality-20261021`, Draft-PR #43, abgeleitet von Astras PR #42 / `1c255d25c1597b925ddeb14e5acad717134eaa33`.
- Geprüfter Code-Commit (vor diesem reinen Dokumentationsnachtrag): `c901d3bd31060a5dea6ee5bacb7e94e06213cbf7`. Exakt identischer Git-Quellbaum auf dem existierenden isolierten Render-Auslieferungsbranch im Commit `7474799e7cb87a3da46b35c7cd3c5440b3381797`. Keine Änderung an `main` oder an Astras PR #42.
- Render-Auslieferung `dep-dbc239jncjis73837e7g`, Status `live`, abgeschlossen 21.10.2026 02:26:12 UTC.
- Paketkennung `at.heinz.deviceai.mentorpaired`, Mentor-Preview-Version `0.3.5`, versionCode `8`, minSdk 26.
- Öffentlich ausgelieferte Datei `https://device-ai-mentor-apk-preview.onrender.com/DeviceAI-Continuum-Training-Monitor-v0.3.5.apk`.
- SHA-256 laut ausgelieferter `SHA256SUMS.txt`: `8e30969a2bb5643dba9e429e9bcc67435b8f13ce30a7219308da29ac0c461829`. Ausgelieferte `BUILD-INFO-Training-Monitor-v0.3.5.txt` nennt denselben Render-Quellcommit und den persistenten privaten Preview-Signer.
- Der Buildvertrag fordert `apksigner verify`, den bekannten Zertifikat-Fingerprint `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc` und die exakte Paketkennung. Der erfolgreiche vollständige Shell-Build spricht für Bestehen dieser fest verdrahteten Gates; ein zusätzlicher physischer Samsung-Installationsbeleg liegt nicht vor.

## Was Sol wirklich repariert hat

**EXP-01 / EXP-02 / EXP-11 / EXP-12 – Worker- und Teamleiterrecovery**

Siehe `docs/CONTINUUM-WORKER-AUDIT-20261021.md`. Atomarer INVESTIGATE-Prüfentscheid verhindert dauerhaft hängendes `VERIFYING` zwischen Submit und Abschluss, abgelaufene Claims werden entlang der vorhandenen Versuchslimits geprüft und erneut auswählbar, und direkte/symlink-basierte Schreibziele im Checkout werden für Investigation-Berichte/Backlog verweigert. Fokussierter Node-22-/Node-24-Beleg: 97/97, neun neue reproduzierende Regressionen. Unbeaufsichtigter Langzeitbetrieb auf Android unter Doze ist damit nicht nachgewiesen.

**EXP-04 / EXP-06 / EXP-12 – Forschungs-/CRE1-Schutz**

Siehe `docs/CONTINUUM-RESEARCH-BASELINE-AUDIT-20261021.md`. Verbesserte Test-Fixtures mit echten thematischen Negativkandidaten; Dokument-Hash-Split schützt Train/Audit; Rechteschema verweigert unfreigegebene Quellen; akzeptierte EC-P-256-KeyObjects funktionieren, falsche Schlüssel bleiben gesperrt. Verpflichtende Regression beweist bytegleichen Rollback bei tatsächlich verworfenem Kandidaten. Fokussierter Node-22-Beleg: 25/25. Keine produktive CRE1-Promotion und keine SWB1-Gewichtsmanipulation.

## Übernahme und vollständige Prüfung durch ChatGPT

Die ursprünglich im Sol-Cloud-Lauf angelegte Datei `docs/CONTINUUM-WEITERARBEIT-CHATGPT-PLUS-20261021.md` war beim Anschluss auf GitHub nicht vorhanden (404). Sie wurde ausdrücklich als **rekonstruierte**, keine wortgetreue, Übergabe aus den nachgewiesenen Originalreports angelegt.

Auf dem Sol-Qualitätsbranch:
1. `scripts/render-mentor-preview.sh`: vollständiger Node-Kernvertrag `node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs`, `npm --prefix backend ci --ignore-scripts --no-audit --no-fund`, vollständige Backend-Suite `node --test --test-concurrency=2 backend/test/*.test.js`. Danach folgen die vorhandenen Groq-/Worker-/Forschungschecks und die Android-Gradle-Gates. Kein Test wurde auskommentiert, übersprungen oder von den vorhandenen Checkpoint-/Reparaturschranken entkoppelt.
2. `.github/continuum/node-contract-synthesizer.test.mjs`: Beibehaltung der **exakten** Bedingungen `pass 2` und `fail 0`, aber Akzeptanz der beiden offiziellen Node-Testreporter `#` und `ℹ`, ohne inhaltliche Lockerung.
3. `backend/test/groq-mentor-browser.test.js`: veraltete direkte `auth`-Erwartung auf echte `mentorAuth`-Route und Rückfall zur etablierten Tokenauthentisierung korrigiert. Sicherheitsgrenze unverändert.
4. `backend/test/android-web-tools.test.js`: nicht mehr vorhandene Beschreibung `Android-Internetfreigabe` durch explizite Kontrolle auf `Nur Android entscheidet` und die Freigabemodi `OFF, ASK oder ALWAYS_ON` ersetzt. Keine Backend-Webautonomie eingeführt.
5. Backend-Lockdatei vor tatsächlichen Express-Integrationstests installiert, um falsche `ERR_MODULE_NOT_FOUND`-Fehler durch fehlende Dependencies zu verhindern.

### Echter End-to-End-Render-Qualitätsdurchlauf gegen identische Quellbytes

- Node-Version des Render-Builds: `26.12.0`; JDK `17.0.20.1`; Gradle `8.11.1`.
- Vollständige Continuum-Node-Testsuite: **728 Tests, 722 bestanden, 0 fehlgeschlagen, 6 übersprungen**. Übersprungene Tests sind kein Grünnachweis.
- Vollständige Backend-Suite: **60/60 bestanden**.
- Zusätzliche feste Buildprüfungen: Groq/Pairing **26/26**, Worker/Academy **34/34**, Forschungs-/Autogenesis-/CDX1-/Monitor-Verkabelung **30/30**.
- Android: `:app:compileDebugKotlin`, `:app:compileDebugJavaWithJavac`, `:app:compileDebugUnitTestKotlin`, `:app:testDebugUnitTest`, `:app:lintAnalyzeDebug`, `:app:assembleDebug` wurden im erfolgreichen Build ausgeführt. `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PdeviceAiMentorPreview=true` ist der Buildvertrag. Kotlin lieferte nicht-fatale Warnungen u.a. zu API-Deprecations und Null-Assertions. Die Gradle-8.11.1-Kompatibilitätswarnung weist auf künftige Kotlin-Plugin-Versionen hin; Toolchain-Upgrades sind ein eigener reproduzierbarer Auftrag, nicht Teil dieses sicheren Korrekturlaufs.
- Render markiert den vollständigen Build `live`. Die veröffentlichten `BUILD-INFO`-/`SHA256SUMS`-Dateien wurden nach Veröffentlichung unabhängig vom GitHub-Commitinhalt abgerufen und enthalten den genauen Delivery-Commit und Artefakt-Hash.

## Grenzen, nicht belegte Ergebnisse und bewusste Nicht-Änderungen

- **Keine Geräteabnahme** für neue Quellstände (Samsung Android 16 / Fold-Layouts, Akku/Doze, Restart-Recovery, Termux-IPC/BUILD-Callback, PackageInstaller-Installation). Das signierte Cloud-APK ist keine Bestätigung eines lokal auf dem Handy gebauten APKs. EXP-00/EXP-08 bleiben für den vollständigen Android-first-Vertical-Slice offen.
- **Keine nachgewiesene Qualitätssteigerung der lokalen Modelle.** Die letzten Eigentümer-Screenshots zeigen SWB1 Intent 4/8 und Semantik 3/6 bei 14.400 Gewichtsupdates; das beweist Training, aber keinen Generalisierungsgewinn. Prospektive, unbenutzte Holdouts bleiben Pflicht.
- Aktiver `DEFERRED_BATTERY_LOW`-Text aus dem früheren Offline-Audit darf nicht als aktuelle 100%-Akku-Sperre gelesen werden; die App muss den laufenden Job getrennt vom historischen Ergebnis zeigen. Ein garantiertes 15-Minuten-Android-Zeitintervall wurde nicht behauptet.
- Keine Eingriffe in aktuelle private Trainingsdaten, Owner-Fakten, produktive `main`, Paketkennung, Signierschlüssel oder lokale Installation. Kein ungeprüfter auto-merge; PR #43 bleibt Entwurf bis Owner-Review.
- Weitere nicht gepushte Sol-Cloud-Dateien (insbesondere dessen damaliger Android-/Scheduler-Audit) sind nicht wiederherstellbar und werden nicht als umgesetzt bezeichnet. Diese Rekonstruktion schließt nur den **gesicherten** PR-Inhalt und die tatsächlichen vollständigen Prüfungen ab.

## Nächste konkrete Geräteabnahme

1. Vor einem Installationswechsel aktuelle Modell-Hashes, Erfahrungen, Holdout und JobScheduler-Laufstatus protokollieren, bestehende APK nicht deinstallieren.
2. Das signierte 0.3.5-Paket mit Hash/Zertifikat und gleicher packageId als reguläres Update (nach Bestätigung) installieren.
3. Echt laufende Quelle/Gradienten/Audit nachweisen, Prozessunterbrechung und historische Statusanzeige prüfen; 15-Minuten-Scheduler nicht als Echtzeitgarantie behandeln.
4. Termux-BUILD-Callback und APK-Download auf demselben Gerät getrennt mit Userfreigabe bestätigen.
5. Für Modellfortschritt vorher eingefrorene, unabhängige positive/negative, neue und zeitlich versetzte Holdouts messen. Erst dann Trainingstechniken adaptieren.

## Rollback

Letzte verifizierte Preview-APK und Signaturnachweise vor neuer Geräteinstallation sichern; Quality-PR #43 offen/review-only halten. Build-Gate- und Testfixture-Commits auf `sol/continuum-quality-20261021` können einzeln mit Git revert zurückgesetzt werden. Kein Rücksetzen, Überschreiben oder Löschen installierter privater SWB1-/CDX1-Daten.
