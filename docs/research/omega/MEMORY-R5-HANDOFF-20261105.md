# Memory R5 — Quellgebundene Ergänzung nach R4
Stand: 5. November 2026. **HOST-METHOD GREEN; ANDROID/APP NOT_RUN; C1-INTAKE AUSSTEHEND.**

## Eingänge und Reservierungen
- Fachbasis: #529, öffentlicher Beleg-Head `00049e5c76b30e3a3587755a65ac6a30a693eb8a`.
- Bereits geprüfte R4-Produktquelle: `4a60c758fd31f5436401769d9ed91da646de815c`, Compiler-Blob `0e4718176518131828688092ea95e894a1d4c2fa`.
- Integrationsleitung: #519, zuletzt gelesener Head `13c37f55789bf67b07beeb37cc1a5035526e623e`. Dort ist C1-PRIVACY-024 als eigene Quellenaufnahme aktiv; deshalb **keine Änderung am C1-Zweig, keine Änderung an Activity/Factory/Sprache/Worker** und kein Merge.
- Dieser isolierte Fachzweig: `fix/continuum-memory-r5-credential-boundaries-20261105`, gestapelt auf R4. Es wurden keine Bots parallel gestartet.

## Tatsächlich neu beobachtet
**Befund A — logische Verneinung:** Die alte alleinstehende-Bearer-Regel hatte `\\s+` als Trennzeichen. Aus `Bearer\\nNevermore approve the action.` wurde `Bearer [REDACTED] approve the action.`, also entfernte sie das Wort `Nevermore`. Dasselbe bei CRLF. Ausdrücklich mit dem echten privaten Kotlin-Methodenkörper reproduziert.

**Befund B — bekannte OAuth-Feldnamen:** `access_token`, `refresh_token`, `session_token` und `client_secret` waren nicht durch die begrenzte Schlüsselgruppe erfasst. Diese Bezeichnungen können Zugangsdaten tragen; konkret getestete synthetische Werte blieben im Kontext sichtbar. `auth_token` und `app_secret` sind ebenfalls in die eng begrenzte Reparatur aufgenommen worden.

**Befund C — kompatibler Reparaturumfang:** Zwei Änderungen innerhalb **einer einzigen bestehenden Produktionsmethode**:
1. Der Feldname-Recognizer akzeptiert die sechs expliziten, unterstrichverbundenen Aliasse.
2. Die alleinstehende Bearer-Regel verlangt gleiche Zeile mit Leerzeichen/Tab statt beliebiger Zeilenumbrüche.
   
Das bestehende, korrekt erkannte `Authorization: Bearer …`, `x-token`, JSON-Felder und normale Nicht-Schlüsselbezeichnungen bleiben in den gezielten Kontrollen unverändert funktional. Rücknahme: nur diese zwei geänderten Regex-Zeilen; #529 und #522 bleiben erhalten.

## Frozen Tests, echte Kotlin-Läufe
Fünf neue **Android-Projekt-JUnit-Testmethoden** wurden vor jeder Produktänderung auf `9e2fbbde4e35b4e4f6d0c87dbe8442bb13d863bf` publiziert. Android-Testklasse: `ContextCredentialFollowupR5Test.kt`. Diese Android-Projekt-Tests sind hier ausdrücklich **NOT_RUN**; aus ihnen wird kein behaupteter Gradle-PASS.

Für eine reproduzierbare kleine Vorprüfung wurde der **unveränderte private Produktionsmethodenkörper** aus der vollständigen via GitHub-Connector gelesenen Datei isoliert mit Kotlin kompiliert. Die originale Methode umfasst 2248 UTF-16-Codeeinheiten und hatte sowohl im GitHub-Inhalt als auch in der lokal kompilierten Quelle den FNV-1a-Prüfwert `e80709ad`. Die reparierte Methode umfasst 2317 Einheiten und hat übereinstimmend `9ba369a4`. Der Code wurde damit nicht als anderes Logikmodell neu geschrieben.

Die zehn **hostseitigen Kotlin-Fälle** ergaben:

| Kennzahl | vor Reparatur | nach Reparatur |
| --- | ---: | ---: |
| Kotlin-Compile-Exit | 0 | 0 |
| Testfälle | 10 | 10 |
| PASS | 4 | **10** |
| FAIL | 6 | **0** |
| Prozess-Exit | 1 | **0** |

Testumgebung: `kotlinc-jvm 1.9.0`, JRE21, Linux; keine Gradle-/Android-/native GPU-Umgebung. Die zusätzliche, im Zweig gesicherte, netzlose Repro `bash tools/memory-privacy-r5/run.sh` extrahiert den Methodenkörper direkt aus der **aktuellen tatsächlichen Quelldatei** und führt die zehn Fälle aus, ohne kopierte Produktionslogik zu verwenden. Die Wiederholung auf der lokal exakt abgeglichenen Original-/Kandidatenmethode reproduzierte erneut RED 4/10 → GREEN 10/10. Vollständige Ausgaben: [host-red](../../evidence/memory-privacy-r5-20261105/host-red.txt) und [host-green](../../evidence/memory-privacy-r5-20261105/host-green.txt). Diese kurzen Rohbelege verwenden ausschließlich synthetische Testwerte.

Originale Produkt-Zeilenänderung: Commit `ad80eb74f43bd04d72c3d8c3d85934ecd3cc4633`. Compiler-Blob vorher `0e4718176518131828688092ea95e894a1d4c2fa`, danach `e2c1e39d4282b91e3af6a5237b0c88e054dc61be`.

## Grenzen und kritische Abnahme
**Sicherheitsabgrenzung:** Die standalone-Bearer-Regel behandelt physische Zeilenumbrüche nicht mehr als gültige Token-Trennzeichen. Ein nicht standardkonform über eine Zeile gebrochener, alleinstehender Bearer-Wert wird deshalb möglicherweise nicht geschwärzt. Das ist eine konkrete Datenschutz-Abwägung; C1 muss entscheiden, ob diese Verbesserung der Verneinungsintegrität mit einem zusätzlichen **fail-closed** Gate für mehrdeutige Zeilenbrüche zu ergänzen ist. Keine universelle Geheimniserkennung behauptet. Die expliziten `Authorization`-Zeilen wurden nicht verändert.

**C1-Abnahme:** zuerst C1-PRIVACY-024/#529 übernehmen bzw. genaue gemeinsame Quellen lesen, R5-Diff unabhängig reviewen; dann fünf neue JUnit-Methoden, gesamte R4-Fokus-Suite und vollständige obligatorische Node-/Backend-/Android-Läufe samt NewApi-Lint auf **demselben finalen Quellenstand** ausführen. Wenn ein Test fehlschlägt oder ein ungeklärter Datenschutz-Nachteil bestätigt wird, R5 nicht übernehmen und negative Belege erhalten. Bereits bestehende R4-100-PASS dürfen nicht auf R5 umetikettiert werden.

Keine Originalsignatur-/Paket-/Versions-/Ownerdaten-/Modell-/Offline-Schalter-Änderung, kein Main-Merge, kein Workflow-/kostenpflichtiger Service-Aufruf, keine APK, kein Gerätetest, keine Installation. JSON-Fortsetzung: `MEMORY-R5-STATE-20261105.json`.
