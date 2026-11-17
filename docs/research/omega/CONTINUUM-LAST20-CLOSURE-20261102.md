# Continuum — geprüfter Teilabschluss und Weiterverarbeitung

Stand: 02.11.2026. Draft-PR [361](https://github.com/Ullmann27/device-ai/pull/361),
Branch `research/continuum-sol61-jarvis-gates-20261102`, Basis PR360
`bdf0f89c1f25673f8fffbb3c6ec27c9e4c89be36`.

**Finaler geprüfter Quellstand:** `48c0fb1f2b4db06619e170bf36f8d070e002eca4`.
Nach diesem Stand werden ausschließlich dieser Bericht und Rohbelege committet.
Der spätere Dokumentations-HEAD ist kein neuer getesteter Produktcode.
**SOURCE_INTEGRATED / HOST_VERIFIED. DEVICE_VERIFIED: NOT_RUN.**
Auf Besitzeranweisung ist die weitere Funktionsentwicklung beendet.

## Ergebnisse und tatsächliche Grenzen

| Prüfung auf 48c0fb1f | Ausgeführt / PASS | FAIL | Übersprungen |
|---|---:|---:|---:|
| Gesamte Android/JVM-JUnit-Suite | 1.687 / 1.687 | 0 | 3 |
| Node-Gesamtsuite, Node22.16.0 | 1.969 / 1.969 | 0 | 13 |
| Kanonisches Node-critical-Profil, zusätzlich | 135 / 135 | 0 | 0 |
| Android Lint | PASS, 0 Fehler | 0 | — |
| Alle 11 verpflichtenden JUnit-Klassen | ausgeführt | 0 | 0 |
| Originalpaket-APK und AAB | Build PASS | 0 | — |

JUnit enthält insgesamt 1.690, Node insgesamt 1.982 Testfälle. Skips zählen nicht
als PASS. Die drei JVM-Skips benötigen explizite CRE1/SWB-Node-Paritätsfixtures;
alle Skipnamen sind in [skipped-tests.json](../../evidence/sol61-jarvis-20261102/final-48c0fb1f/skipped-tests.json)
aufgeführt. Keine neue Parität, Gewichtsqualität oder Decoderleistung ableiten.
Die critical-Tests sind Teilmengen, keine zusätzlich verschiedenen Aufgaben.

Die vollständige frische SDK-Kompilierung lief auf dem bytegleichen Android-Code
`62b3aa90f0b418db4522249d967bfa1bca5b4f3a`: 35 Gradle-Aufgaben ausgeführt,
JUnit und Lint grün. Danach änderten sich nur die Node-Testfixture und Berichte.
Auf48c0fb1f wurden alle JUnit-Fälle erneut ausgeführt; Compile/Lint verwendeten
korrekt unveränderte Gradle-Eingaben aus dem Cache. Der APK-Neuaufruf auf48c0fb1f
bestätigte ebenfalls unveränderte Release-Eingaben. Nicht als erneute kalte
Kompilierung ausgeben. Der originale vollständige Release-Build hatte 54 ausgeführte
Aufgaben; der anschließende unveränderte Aufruf 54 UP-TO-DATE-Aufgaben.

Toolchain: Linux/x64, echte Android-SDK36-/BuildTools35.0.0-Abhängigkeiten,
Temurin17.0.20.1+1, Gradle8.11.1, Kotlin2.4, LiteRT-LM0.18.0-API,
Node22.16.0. Die Gradle-Läufe verwenden **keine LiteRT-Kompilierstubs**.
Separate native Callback-Gegenproben verwenden ausdrücklich deklarierte API-
Stand-ins und zertifizieren weder JNI-Ausführung noch Fold7-Verhalten.

## Was am vorhandenen Gehirn geändert wurde

1. **Erreichbarer Dialogpfad:** `AgentActivity.runLocal` → vorhandener
   `AiContextCompiler`/`LocalPromptFrame` → `LocalModelRuntime` → ursprüngliches
   LiteRT. Ausgewählte Werkzeugbelege werden vollständig übernommen oder mit
   Klärung abgewiesen. JSON-Escaping und UTF-8 werden vor der Weitergabe geprüft.
   Auch Wiederholungen enthalten bezeichnete unvertrauenswürdige JSON-Daten;
   eingeschleuste Rollenüberschriften bleiben Feldwerte. Keine neue Berechtigung
   wird aus Texten oder einer Modellantwort abgeleitet.
2. **Modellgrenze:** `LocalModelHost.Host` akzeptiert das bestehende Runtime-
   Interface; sein produktiver Konstruktor erzeugt weiterhin LiteRT. Zwei
   simulierte Hostadapter durchlaufen denselben Core/ToolGate-Vertrag. Normale
   Chatstarts werden nicht durch fehlende optionale Tokenmetadaten deaktiviert.
   Fortsetzungen prüfen vorhandene native Tokenzahlen, Reserve und vollständige
   Daten konservativ. Der UTF-8-Aufschlag ist **kein exakter Tokenizerbeweis**.
3. **Aufgabe und Wirkung:** vorhandener `BoundedAgentOrchestrator` → `ToolGate`
   → bestehender Dateiquittungsspeicher. Transaktionen sind instanz-/prozessübergreifend
   geschützt. START_PENDING, DISPATCH_COMMITTED und UNKNOWN_AFTER_DISPATCH bleiben
   unterscheidbar. Ungewisse Ausgänge, Quittungskonflikte und Replays sperren den
   Ablauf vor einem weiteren Modell-/Werkzeugaufruf, auch bei neuer Aufruf-ID.
   Es entstehen keine Erfahrung und kein Lernkandidat aus diesen Abbrüchen.
4. **Callback-Lebenszyklus:** bestehender Start-Latch, geordnete Lieferung außerhalb
   der JVM-Monitore und Generationseigentum bis zum Terminalereignis verhindern
   die reproduzierten Sperrzyklen und verspäteten Textlieferungen nach Unload.
   Bereits fest zugesagte, nicht kooperative Backends können weiterwirken. Ein
   Abbruch bedeutet keinen sicheren Rollback physischer Nebenwirkungen.
5. **Prüfpfad:** offizieller SHA-gepinnter Gradle-Wrapper ergänzt. Beide vorhandenen
   Abnahmeeinstiege prüfen sämtliche XML-Dateien strikt, tatsächliche Fälle/Zahlen,
   Pflichtausführung, Frische und unveränderte Quellen; umgeleitete Belegpfade werden
   abgewiesen. Alte RED-Läufe bleiben erhalten.

Identität, Gedächtnis, ToolGate und Erfahrung gehören weiterhin dem vorhandenen
Continuum-System. Keine zweite Gedächtnisbank, Modellverwaltung oder Worker-
Architektur. GGUF/Termux aus PR356 bleibt opt-in Forschung. PR337-Scroll-/Deadline-
Änderungen waren schon vollständig vorhanden und wurden nicht dupliziert. Die
16 Quantumflow-Quell-/Lizenzblobs stimmen; das ist kein neuer Runtime-Avatar.

## Reale Aufgaben- und Coding-Belege

Der vorhandene Zweischritt-Test `ReadOnlyMultiStepTest` führte auf dem Host drei
frische Aufgaben aus: App-Datenträger prüfen und ein isoliertes synthetisches
Downloadverzeichnis lesen. Unabhängige FileStore-/bekannte-Datei-Orakel, sechs
frische Quittungen, Task-IDs und Ereignisse wurden geprüft. `ExperienceCompiler`
erzeugt daraus einen **besitzerpflichtigen Kandidaten**, keine automatische
Fähigkeits-/Gewichtsfreigabe. Originale Rohbelege: `task-evidence/actual-two-step.txt`
im Belegarchiv.

Die bestehenden Node-Integrationstests führen echte Git-/Test-/Patch-/unabhängige
Prüfschritte in Wegwerf-Repositories durch, unter anderem:

- `.github/continuum/teamleader-end-to-end.test.mjs`: tatsächliche Offline-Reparatur
  eines fehlerhaften Git-Fixtures und geplanter Besitzerauftrag mit unabhängiger Prüfung.
- `.github/continuum/coding-agent-loop.test.mjs`: fehlerhafter erster Patch,
  echte rote Tests, Korrektur und VERIFIED; Grenzen für Geheimnisse, Scope,
  Wiederholungen und unzulässige Hostausführung.
- `scripts/continuum-selfhosted-coding.test.mjs`: lokale HTTP-/Werkzeugprotokolle,
  Baseline mit einem Fehler und nachträglich geprüfter Kandidat mit drei bestandenen
  Tests; Modellgewichte und Proxyfreiheit ausdrücklich nicht bestätigt.

Diese Tests bestanden im critical-/Gesamtlauf. Die Kandidaten/Modellantworten sind
kontrollierte Skripte bzw. Protokollfixtures. Das ist **HOST_VERIFIED** für Steuerung,
Isolation und Reparaturablauf, **kein** neuer autonomer Coding-Erfolg eines real
laufenden Gemma-/GGUF-Modells und kein on-device Android-Buildbeweis. Der vorhandene
CCC1-/Repair-Mechanismus wurde wiederverwendet, nicht ersetzt.

## Vorher / nachher

| Vorher belegter Fehler | Jetzt belegtes Verhalten |
|---|---|
| Leere Pflichtklasse oder fehlerhaftes zusätzliches XML konnte PASS ergeben | Strikte Struktur-, Fall-, Zähl-, Klassen- und Frischeprüfung; Negativkontrollen grün |
| Späte Werkzeugkorrekturen abgeschnitten, Quotes konnten Konstruktorfehler auslösen | Vollständige serialisierte Werte oder kontrollierte Ablehnung |
| Optional fehlende Kontextmetadaten blockierten auch normale Starts | Frischer Start bleibt bytebegrenzt; unprüfbare Fortsetzungen werden abgewiesen |
| 13 von32 parallelen Quittungen erhalten | Alle32 im Regressionstest; gleicher Call-ID-Start höchstens einmal |
| UNKNOWN ging nach Neustart oder Journalfehler verloren | Dauerhaft/typisiert; kein automatisches Wiederholen im selben Aufgabenlauf |
| Wiedergegebener Erfolg konnte neue IDs und falsche positive Antwort auslösen | Typisiertes Replay stoppt den Ablauf ohne neue Erfahrung |
| Native Monitor-Inversion/verspäteter Text nach Unload | Bestätigte Host-Gegenbeispiele beseitigt; kein Gerätebeweis behauptet |

Unverändert: keine nachgewiesene allgemeine Intelligenz, kein neues Grundmodell,
keine neue neuronale Qualitätsmessung. Der historische CDX1-Fold7-Befund0/20
bleibt bestehen. Der eingefrorene Holdout72 Zeilen/54 verschiedene Fälle bleibt
unverändert und wurde nicht als neuer unbekannter Benchmark ausgegeben.

## APK und Besitzergrenzen

- Paket: `at.heinz.deviceai.continuumlocal`; Version0.3.37 / Code38.
- Größe: **54.669.275 Byte**.
- SHA256: `abf0664e2bb79190e932c4f8e46adf8545fc50c006c9e37bdc34f5d4a9669845`.
- ZIP-Integrität und binäre Paketmetadaten geprüft; keine V1-Signaturdateien und
  kein APK-Signaturblock. `apksigner`: DOES NOT VERIFY, fehlendes Signaturmanifest.
- **UNSIGNED_BUILD_VERIFIED / ORIGINAL_SIGNING_NOT_RUN / DEVICE_NOT_RUN**.
  Keine Veröffentlichung, Installation, Deinstallation oder Nutzerdatenänderung.
- Privates lokales Prüfartefakt:
  `/workspace/jarvis-qa/48c0fb1f/continuum-local-0.3.37-code38-UNSIGNED.apk`.
  APK/SDK-Caches sind keine Git-Quelldateien; Hash und Buildbelege sind gepusht.

Originalsignierung erfordert weiterhin die Original-P12 ausschließlich auf dem
gepinnten Publisher und die explizite Release-Freeze-/SHA-Freigabe. Ein Catalog-
Eintrag bleibt zunächst `approved:false`. Noch nicht verfügbare Freigaben wurden
nicht durch eigene Umgebungswerte erfunden. Siehe [Besitzerprotokoll](CONTINUUM-FOLD7-OWNER-ACCEPTANCE-20261102.md).

## Reviews, Einschränkungen und nächste Schritte

Unabhängiger Reviewer: gezieltes **APPROVE** für die letzten drei Gegenbeispiele
auf bytegleichem Runtime-Stand62b3. Frühere CHANGES_REQUESTED und Original-REDs bleiben
historisch sichtbar. Externe CodeRabbit-/Gitar-Kommentare werden nicht allein wegen
eigener grüner Tests pauschal geschlossen; abschließende externe Gesamtbestätigung
ist hier nicht behauptet.

NOT_RUN: tatsächliche Modellinferenz, alternativer Fold7-Modellwechsel, JNI-
Scheduling, Fold7-Sprache/Live-Klick/Installation, on-device Coding-Zyklus und
persönliche Datenkontinuität nach einem Update. Windows-Quittungsdurabilität ist
nicht zertifiziert. Ein stiller Fsync-No-op wurde bewusst nicht eingeführt; ein
nicht unterstützter Speicherpfad sperrt die Ausführung.

Die nächste KI beginnt mit [Weiterverarbeitung](SOL61-JARVIS-NEXT-AI-20261102.md),
prüft frische Remote-SHAs/Reviews und wiederholt diese Implementierung nicht.
Ein sinnvoller nächster Nachweis ist ein freigegebener Fold7-Dialog mit echter
lokaler Inferenz, vollständiger Werkzeugantwort und Abbruch/Wiederanlauf. Vor
Gewichts-/Skillpromotion sind unabhängige Qualitätsbelege nötig.

## Reproduzierbarkeit und Commits

Alle tatsächlichen Befehle, JVM-/Node-XML, Original-Exitcodes, Lint, Buildbelege,
Source-Manifeste und Skipnamen: [finale Belege](../../evidence/sol61-jarvis-20261102/final-48c0fb1f/verification.json),
[vollständiges Archiv](../../evidence/sol61-jarvis-20261102/final-48c0fb1f/raw-logs-and-receipts.zip).
Die Skripte im Belegordner enthalten die tatsächlich benutzten Sessionpfade;
bei Wiederholung reale JDK-/SDK-/Ausgabepfade einsetzen. Ausgaben außerhalb des
Checkouts halten. `run-gradle.sh` erzwingt die erneute Testausführung über das
beigefügte Init-Skript; die komplette kalte Kompilierung steht im62b3-Rohlog.

| Commit | Zweck |
|---|---|
| `a5169bdfc9580de0aeedbe1229b8f69ea8df0b7a` | Sichtbarer Startcheckpoint/Draft |
| `f5eb4f1d47efc9a506a8a3d82e53f09b863c1794` | Strikte QA und gepinnter Wrapper |
| `e2152df4988b05016276263ec72794efa070d5ee` | Vollständiger lokaler Werkzeugkontext |
| `5060ec4470f49e3f9aa217189cd69adbb0f42f1a` | Journal, Unsicherheit, parallele Speicherung |
| `25f2d31c8de962fe5f77a65705e0f70613ce630e` | Review-Reparaturen/ungewisse Aufgaben stoppen |
| `62b3aa90f0b418db4522249d967bfa1bca5b4f3a` | Letzte Replay-/Unload-Rennen, untrusted JSON, Android-Codeabschluss |
| `48c0fb1f2b4db06619e170bf36f8d070e002eca4` | Fixture-Frist korrigiert; finaler grüner Prüfabschnitt |

Keine Writes/Merges nach main. Rückfall: Draft ungemergt lassen oder einzelne
Quellcommits kontrolliert revertieren; niemals Besitzerquittungen/Gedächtnis
löschen, um einen gesperrten Ablauf wieder ausführbar zu machen.
