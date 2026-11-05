# Continuum – zentrale Abschlussübersicht APK42/43 (05.11.2026)

Einzige maßgebliche Übersicht. Andere Zweige sind Zuarbeiten, Prüfnachweise oder historisch.

## 1. Veröffentlichter Stand (verifiziert, unverändert)

| Feld | Wert (unabhängig nachgeprüft 05.11.2026 09:21 UTC) |
| --- | --- |
| Download | https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-a1d56eb87b9224df383cdf9368c0406302c28e36d43ce9b2c4e16a5283be0efe.apk |
| Version | 0.3.41 / versionCode 42 · Paket `at.heinz.deviceai.continuumlocal` · nur `arm64-v8a` |
| Quelle | `8a903fbebe605716b6163da35e1c3b2129af40a4` (Implementierungsbaum `7563a881` = `0628c38b`) |
| APK-SHA-256 | `a1d56eb87b9224df383cdf9368c0406302c28e36d43ce9b2c4e16a5283be0efe` · 101 712 663 Byte (Katalog = Download) |
| Signatur | v2 gültig, genau 1 Signierer `CN=Continuum Local Owner`, SHA-256 `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` (Original) |
| Inhalte | Kopf-GLB `c8b88385…08b9a` (= Besitzer-Drive-Datei), YuNet `8f2383e4…`, SFace `0ba9fbfa…` (= Pins) |

Erfolgsmeldung des Vorchats (#544 `8a903fbe`): Android 3152/3133 PASS/0 FAIL/19 SKIP, Full-Node 2373/0/31,
Backend 64/0/0, Lint 0 auf Baum `7563a881` – exakt passend zum veröffentlichten Stand.

## 2. Gerätebefunde des Besitzers zu Code 42 (Fold7, 05.11. 10:21) → Reparatur erfordert Code 43

| Befund | Ursache | Reparatur (Abschlusszweig) |
| --- | --- | --- |
| Kopf und Kugel verschwinden nach Chat → Start | `syncConversationAvatar` hängte die Live-Ansicht auf Ebene 0 **unter** den deckenden Shell-Hintergrund | `ContinuumHomeView.adoptHologram()` setzt sie über die Shell; UI-Tests prüfen `hologramAboveShell` |
| Sprache wird nur geschrieben, nicht gesendet | Diktat war bewusst reine Entwurfsfunktion | Mikrofon antippen = Gesprächsmodus (Satzende nach ~3 s Pause, senden, antworten, weiterhören); langes Drücken = stilles Diktat / Modus beenden |
| Kopf steif, kleiner Bewegungsradius | Kopf ±8°/±5°, Kiefer 12° | Kopf 24°/12°, Augen 12°/8° (gesamt 36°/20°), Kiefer 15°, Leerlauf ×2,5 |

## 3. Weitere behobene Punkte (A–J)

- A Dialogtest (ScrollView-NPE): in #544 `0628c38b` behoben (Fenster sichtbar/fokussiert) – im Erfolgsbaum grün.
- B `fold7-portrait-reference`: auf aktueller Quelle grün (Node 2367/0/37 Ausgangsquelle).
- C Backend-Pflichtgate vor Signierung: #561 übernommen (`npm ci` + check + test, fail-closed).
- D Exaktes Lockfile: express-rate-limit 8.7.1 installiert, Backend 64/64.
- E AAPT2-Vorprüfung (#554) übernommen; `AvatarEmotion.WARM` kompiliert nicht mehr fehlerhaft (echte Kompilierung).
- F Gesprächsmodus: `ContinuousConversationMode` (16 JVM-Tests) + Verdrahtung + Vertrag.
- G Gemessene PCM-Stille: #562 übernommen.
- H Gesichtserkennung: vorhanden und durch reale Tests abgedeckt; Kamera/JNI auf Fold7 = Geräteprüfung.
- I Referenzbild: Besitzer-PNG 1536×1024, SHA-256 `77e4eda80efd47ea9a899e7bc2d4711f028615a47cb25eecf96aaa608118d68c` (nur Hash gepinnt).
- J Gedächtnis R6–R13 (#538/#548/#553/#555) gezielt vereinigt, beide R13-Testgruppen erhalten.

## 4. CCC1 / TTT1

- **Ursache der 7 CCC1-Fehler:** Node 22 JUnit schreibt kein `<testcase file=…>`; Testidentitäten fielen auf `test.*`
  zurück → REJECTED mit irreführenden Gründen. Node 24.22.1 schreibt `file` → 37/37 PASS.
  Reparatur: `.node-version` = 24.22.1, Publisher-QA bricht bei anderem Major klar ab, Verifier meldet
  `JUNIT_FILE_IDENTITY_UNSUPPORTED_RUNTIME` (weiterhin REJECTED, nie VERIFIED) + Regressionstest. Keine Prüfung abgeschwächt.
- **CCC1 als Agent:** deterministischer Termux-Reparatur-Orchestrator (Journal, Worker, Freigaben, RED→GREEN-
  Neuausführung, Abbruch durch Besitzer/Deadline/Budget/Stale-Base). **Kein Sprachmodell angeschlossen**
  (`lexical`/`probe`/`disambiguate`; `ccc1-lm1-core.mjs` existiert nicht). Keine Modellparameter geändert.
- **TTT1:** vorregistriertes Forschungsexperiment (16×16-Fast-Weight-Arbeitsgedächtnis) nur im Test-Quellset;
  Entscheidung 23.10.: kein Produktpfad (73 falsche Rückbezüge vs. 0). Keine Anschlüsse, nichts geändert.

## 5. Prüfnachweise Abschlussstand

Eigene Läufe, Quelle `0830ad68fc0625bd7cadf84214b77f329685c57c` (Belege: `docs/evidence/apk43-closeout-20261105/`):

| Gate | Ergebnis |
| --- | --- |
| Android compile + Unit/UI + Lint (QA-Flags, versionCode 43, ARM64) | BUILD SUCCESSFUL · 3248 Tests / 3229 PASS / 0 FAIL / 19 SKIP · Lint: No issues found |
| Pflicht-Suiten des Publishers | GREEN (Shizuku 14+2, Controls 10, IntentRouter 5, HybridMemory 6) |
| Node full (24.22.1) | PASS · 2413 / 2376 PASS / 0 FAIL / 37 SKIP · Quelle unverändert |
| Backend (npm ci, Lock 8.7.1) | 64/64 PASS auf `0830ad68` |
| CCC1-Verifier | Node 24: 37/37; Node 22: explizit abgelehnt (neuer Test) |
| Unsigniertes assembleRelease, Signatur, Veröffentlichung, Fold7 | **nicht ausgeführt** (Render-Publisher führt QA + Signatur aus) |

## 6. Veröffentlichung Code 43 – offen

Die Code-43-Quelle ist geprüft und gepusht, aber **noch nicht signiert/veröffentlicht**: Diese Sitzung hat keinen
Render-Zugang (`api.render.com` → 401 Unauthorized). Kleinster Eingriff am Publisher `srv-dbf1tr2vcj2c73ehpfog`
(Zweig `research/continuum-m8-render-signed-release-20261031` per Fast-Forward oder Zweig auf den Abschlusszweig):
`CONTINUUM_RELEASE_APPROVED_SHA` = `CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER` = finale SHA,
`CONTINUUM_RELEASE_VERSION_CODE=43`, `ORG_GRADLE_PROJECT_continuumOwnerArm64=true`, dann Deploy.

## 7. Nicht ausgeführt

Echte Fold7-Abnahme (Installation, Mikrofon, hörbare Stimme, Kamera/JNI, Filament/GPU, Fold auf/zu),
16M-Parameter-Training, App-übergreifende Gerätesteuerungsanimationen: nicht Teil dieses Nachweises.
