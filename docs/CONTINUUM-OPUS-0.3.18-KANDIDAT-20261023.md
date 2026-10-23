# Continuum 0.3.18 (code 19) – Release-Kandidat zur Besitzerfreigabe (24.10.2026, Opus)

**Status: KANDIDAT, NICHT GEBAUT, NICHT SIGNIERT, NICHT AUF DEM GERÄT GEPRÜFT – eine Freigabe-Entscheidung von Heinz offen.**
Nichts wurde nach `main` oder auf den Signierzweig geschrieben, nichts veröffentlicht oder installiert.
Die Freigabeliste im Build-Skript wurde **nicht** verändert.

## Kurzfassung

Der letzte von GPT-6 freigegebene Stand (`b842d471`, PR #107; 22/22 Fingerabdrücke im v19-Skript passen) wäre im
Render-Build **gescheitert**, bevor eine APK entsteht:

| Prüfung im v19-Skript | an `b842d471` | Ursache |
|---|---|---|
| Node-Gesamtgate | **5 rot** von 1041 | veraltete statische Verdrahtungstests (alter „Bauen“-Dialog) |
| Gradle `:app:testDebugUnitTest` (hier ohne Gradle nachgestellt) | **2 rot** von 863 | `ContinuumMobileBuildPlanTest` veraltet (M4 brachte 3 neue Operationen); `BootstrapBrainTest`: **echter Sicherheitsfehler** |

Der Sicherheitsfehler: Der österreichische nackte Imperativ „Installier das und zeig mir den Akku“ wurde **nicht**
als verändernder Geräteauftrag erkannt und als Gerätewerkzeug (`DEVICE_TOOL`) eingestuft; der Regex verlangte
„installiere/installieren/installierst“. Sol hat das später mit einem Zeichen behoben (`c787c098`), der Kandidat
übernimmt genau diese Zeile. `BootstrapBrain.kt` gehört aber zu den 22 freigegebenen Dateien.

## Die eine Entscheidung für Heinz

In `scripts/render-mentor-owner-update-v19.sh` (Liste `APPROVED_RELEASE_SOURCES`) die Zeile

    5ddd43cb32120c43cd2f854897136178464b3645 app/src/main/java/at/heinz/deviceai/BootstrapBrain.kt

durch

    e0981a9a8052f72bc5959ae2de68319fc1692e66 app/src/main/java/at/heinz/deviceai/BootstrapBrain.kt

ersetzen. Inhalt der Änderung: nur der Regex in Zeile 39 (`installier(?:e|en|st)\b` → `installier(?:e|en|st)?\b`,
ebenso für `deinstallier`). Der neue Blob ist bitgleich zu Sols geprüftem Stand `e4fd67c3`.
Ohne diese Änderung bricht das Skript an der Blob-Prüfung ab; ohne den Fix scheitert `BootstrapBrainTest` unter Gradle.
Ich habe die Liste bewusst nicht selbst geändert (Sicherheits-Gate = Besitzerentscheidung).

Alternative: Sols vollständiger Stand (`opus/continuum-next-20261023`) mit acht neu zu prüfenden Fingerabdrücken
(M4-Projektauswahl, `HOST_SDK_VERIFIED_NOT_DEVICE_TESTED`).

## Identität

| | |
|---|---|
| Kandidatenzweig | `opus/continuum-0.3.18-candidate-20261023` (dieses Dokument ist der letzte Commit) |
| Basis | `b842d471183fcf181f392d522a0d0a8f5936a649` (PR #107) |
| Zusätze | `663412b8` #76-Doppelfehler · `18b6a0d6` 5 Node-Verdrahtungstests · `ContinuumMobileBuildPlanTest` (Sols Fassung) · `BootstrapBrain.kt` (Sols Ein-Zeichen-Fix) |
| Signierzweig heute | `astra/render-mentor-apk-builder-20261019` @ `285639ba` (signierte 0.3.17 / code 18) – Vorfahre des Kandidaten, reiner Vorlauf ohne Force-Push |
| Version | bei `-PdeviceAiOwnerSignedUpdate=true`: `versionCode 19`, `versionName 0.3.18`; Paket `at.heinz.deviceai.mentorpaired` und Manifest seit 0.3.17 unverändert |
| Zertifikat | SHA-256 `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc` (bestehender Schlüssel, nicht im Repository) |

## Was gegenüber der installierten 0.3.17 neu ist (85 Commits bis `b842d471` plus Zusätze)

- CCC1 im Entwicklungszentrum und verständliche Gründe, wenn Termux nicht antwortet (`0a268a86`).
- Issue #76: geschriebenes Forschungs-Audit nicht mehr `FAILED_SAVE_CYCLE` (`7c6291f9`) plus Doppelfehler (`663412b8`).
- GPT-6: Navigation/Kategorien, Trainingsstudio, österreichisch-deutsche Dialogführung, M4-Notiz-App über den Builder;
  signierte 0.3.17-Oberfläche erhalten.
- Sicherheitsfix „installier“ (siehe oben). Span-Kern mit I1/I2 (`e9714f70`), ohne Aufrufer.

## Geprüft (hier, Linux-Container; v19-Gates wörtlich aus dem Skript übernommen)

| Prüfung | an `b842d471` | am Kandidaten (Code-Stand `be469760`) |
|---|---|---|
| Statische v19-Prüfungen: 22 Fingerabdrücke, Pflichtdateien, Version 19/0.3.18 | 22/22, ok | **21/22** – nur `BootstrapBrain.kt` (Entscheidung oben), sonst ok |
| Node-Gesamtgate `node --test .github/continuum/*.test.mjs scripts/*.test.mjs` | 1030 bestanden, **5 rot**, 6 übersprungen | **1035 bestanden, 0 rot**, 6 übersprungen (brauchen `kotlinc`, das auch auf Render fehlt) |
| Backend (`npm ci`, `backend/test`) | – | 60/60 |
| Groq-Vertrag / Astra-Werkstatt / AUTOGENESIS-CDX1 | – | 26/26 · 34/34 · 30/30 |
| Kotlin-Unit-Tests ohne Gradle (`scripts/android-kotlin-unit-check.sh`, gleiche Testklassen wie `:app:testDebugUnitTest` ohne Robolectric-Flag) | 861/863, **2 rot** | **863/863** + 4 Prüfprogramme |

Die Gates sind wörtlich aus `render-mentor-owner-update-v19.sh` übernommen (`sed`-Auszug zwischen
„Sol quality handover“ und „Require persistent mentor preview signing“, gleiche Befehle, ohne Signierumgebung).

## Nicht geprüft (hier nicht möglich)

- Echter Gradle-Lauf mit Lint und `assembleDebug` (`dl.google.com` gesperrt). Signatur, `apksigner`/`aapt`, Rollback.
- Gerät: In-place-Update, Datenerhalt, Bedienung, CCC1 über Termux, #76 im echten Forschungsjob.

## Freigabe und Bau (nur Heinz)

1. Pin-Entscheidung oben treffen (Commit auf dem Kandidatenzweig), dann Diff `285639ba..<Kandidaten-SHA>` prüfen.
2. Signierzweig vorspulen (kein Force-Push): `git push origin <Kandidaten-SHA>:refs/heads/astra/render-mentor-apk-builder-20261019`
3. Render: `CONTINUUM_RELEASE_SHA=<Kandidaten-SHA>`, `CONTINUUM_ASTRA_UI_SHA=811803138c86a5c1a7a2a2b9cd196580a574a4a1`,
   Skript `scripts/render-mentor-owner-update-v19.sh`. Der Schlüssel bleibt im Dienst; ohne gesicherte 0.3.17
   (SHA-256, code 18, Zertifikat) bricht das Skript ab.
4. `BUILD-INFO.txt`: `status=SIGNED_BUILD_VERIFIED_NOT_DEVICE_INSTALLED`, `version_code=19`, Kandidaten-SHA; APK-SHA-256 notieren.
5. In-place-Update auf dem Fold7 (nie deinstallieren). Prüfen: Start/Chat/Lernen/Entwickeln/Mehr, „Installier das …“
   löst nichts aus, CCC1-Knopf und `ccc1-check --full`, Forschungszyklus ohne `FAILED_SAVE_CYCLE`. Issue #76 erst danach schließen.
