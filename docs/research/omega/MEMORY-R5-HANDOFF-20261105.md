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
Zunächst fünf, später eine sechste **Android-Projekt-JUnit-Testmethode** wurden vor jeder Produktänderung auf `9e2fbbde4e35b4e4f6d0c87dbe8442bb13d863bf` publiziert. Android-Testklasse: `ContextCredentialFollowupR5Test.kt`. Diese Android-Projekt-Tests sind hier ausdrücklich **NOT_RUN**; aus ihnen wird kein behaupteter Gradle-PASS.

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
**Sicherheitsabgrenzung (aktualisiert):** Der Zwischenstand nach dem ersten Regex-Fix konnte einen nicht standardkonform in die nächste Zeile geschriebenen alleinstehenden Bearer-Token übersehen. Das war eine echte, gezielt in zwei neu eingefrorenen Testfällen reproduzierte Schutzlücke. Die finale R5-Fassung erkennt deshalb den mehrdeutigen Fall (alleinstehende Zeile mit `Bearer`, danach neue Zeile mit tokenähnlichem Wort ab acht Zeichen) und verweigert die Kompilierung fail-closed mit `UNTERMINATED_CREDENTIAL_VALUE`. So wird weder ein möglicherweise vertrauliches Token veröffentlicht noch eine Verneinung still verschluckt. Der konservative Schutz kann legitime gewöhnliche Ein-Wort-Überschriften zurückweisen; für C1 ist die tatsächliche Caller-Rückmeldung und das Aufrufprofil zu prüfen. Die expliziten `Authorization: Bearer`-Zeilen sind weiter geschützt. Andere syntaktisch ungültige oder nicht erkannte Zugangsdaten bleiben außerhalb eines universellen Geheimnisdetektors.

**C1-Abnahme:** zuerst C1-PRIVACY-024/#529 übernehmen bzw. genaue gemeinsame Quellen lesen, R5-Diff unabhängig reviewen; dann sechs neue JUnit-Methoden, gesamte R4-Fokus-Suite und vollständige obligatorische Node-/Backend-/Android-Läufe samt NewApi-Lint auf **demselben finalen Quellenstand** ausführen. Wenn ein Test fehlschlägt oder ein ungeklärter Datenschutz-Nachteil bestätigt wird, R5 nicht übernehmen und negative Belege erhalten. Bereits bestehende R4-100-PASS dürfen nicht auf R5 umetikettiert werden.

Keine Originalsignatur-/Paket-/Versions-/Ownerdaten-/Modell-/Offline-Schalter-Änderung, kein Main-Merge, kein Workflow-/kostenpflichtiger Service-Aufruf, keine APK, kein Gerätetest, keine Installation. JSON-Fortsetzung: `MEMORY-R5-STATE-20261105.json`.


## Checkpoint 2 — mehrdeutige Bearer-Zeilenbrüche fail-closed (5.11.2026)

Die independent nachvollzogenen Original- und ersten Kandidatenfälle offenbarten eine Datenschutz-Abwägung: lediglich `\\s+` durch Gleichzeilen-Trennung zu ersetzen bewahrt zwar normale Verneinungen, kann aber ein physisch umgebrochenes Bare-Bearer-Token sichtbar lassen. Daher wurden **zuerst** (Commit `07354a3de00c359e4aa6f5db76f5f3bcd1d2d276`) die neue Android-JUnit-Methode `malformedStandaloneBearerWrappedTokenIsRejectedBeforeRecall` sowie aktualisierte Nicht-Credential-Textfälle eingefroren. Der dazugehörige methodenbasierte Host-Test wurde auf `41ce611bfd36bea2bf6cc5c13701be9c683ee9fe` gesichert.

Der erste R5-Produktkandidat vor dieser dritten Korrektur (Blob `e2c1e39d4282b91e3af6a5237b0c88e054dc61be`) lieferte tatsächlich **10 PASS / 2 FAIL**, weil beide umgebrochenen synthetischen Token fälschlich nicht abgelehnt wurden: [RED](../../evidence/memory-privacy-r5-20261105/host-wrap-red.txt). Beide Original-Negationsfälle wurden als vollständige Textsätze mit nicht alleinstehender Bearer-Erwähnung eingefroren; sie bleiben abgenommen und beweisen weiterhin, dass ein freier Satz durch die Bearer-Heuristik nicht verändert wird.

Erst danach fügte `1957f2ee057da0d96fd40d2e197c215ab3cecdc7` ein enges fail-closed-Gate hinzu. Finaler Compiler-Blob: `8c6b967ba2e5800e4de7674d7caa645c9dfc979b`. Der **veröffentlichte finale Produktionsmethodenkörper** stimmt mit dem tatsächlich kompilierten Hostkörper überein: Länge 2737 UTF-16-Codeeinheiten; FNV32 `48518f45` auf beiden Seiten. Finaler netzloser Kotlin-/JRE-Lauf: **12/12 PASS, 0 FAIL, Exit0**, einschließlich beider `UNTERMINATED_CREDENTIAL_VALUE`-Negativfälle und aller zehn positiven Kontrollen: [GREEN](../../evidence/memory-privacy-r5-20261105/host-wrap-green.txt).

**Aktuelle Grenzen:** Nun sechs Projekt-JUnit-Methoden **NOT_RUN**, kein Android-/Robolectric-/NewApi-/Gerätebeleg auf R5. Weder die R4-100/100 noch die früheren 10/10 sind ein vollständiger Projekt-PASS für den finalen R5-Baum. Der Schutz ist konservativ und kann mehrdeutige Natural-Language-Eingaben ablehnen. C1 muss zusätzliche Regressionsprüfungen für Fehlklassifikation/Caller-Recovery, Speicherereignisse und Performance an derselben finalen Kotlin-/Androidquelle durchführen, bevor der Fachpatch integriert werden darf. Keine neuen Berechtigungen, Hauptbranch-, Besitzer-, Geräte-, Modell- oder Release-Änderungen.
