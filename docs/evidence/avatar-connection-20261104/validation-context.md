# Evidenzindex: Toolchain, Callback-Zeitmodell und bestehender GLB-Vertrag

Dieser Index beschreibt ausschließlich die unten genannten beobachteten Stände.
Er nimmt weder einen späteren positiven Lauf noch eine Geräteabnahme vorweg.
Es wurden für diesen Index keine Builds ausgeführt und keine Projektquellen geändert.

## 1. Wiederhergestellte lokale Bauumgebung

Quelle: `../toolchain/setup-result.json`, Rohprotokoll
`../toolchain/logs/sdk-install.txt`. Die verwendeten Archivpins stammen aus
`e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.

- JDK17-, Android-CLI- und Gradle8.11.1-Archiv stimmen mit den vorhandenen
  Repository-SHA-256-Pins überein.
- Die offizielle SDK-Installation endete mit **Exit 0**, ohne Timeout.
  Android36, BuildTools35.0.0 und Platform Tools sind installiert;
  `android.jar`, `aapt2`, `aidl` und `adb` wurden als vorhanden geprüft.
- Das SDK und seine beschreibbaren Verzeichnisse sind für diesen Auftrag
  isoliert. Proxy und CA-Verifikation wurden beibehalten.

`READY` bezeichnet hier die Toolchain-Vorbereitung, keine bestandene App-Prüfung.

## 2. Erster Callback-Lauf: Fehler vollständig erhalten

Quelle: `initial-callback-run/summary.json`, die 23 JUnit-XML-Dateien und
`initial-callback-run/gradle.txt`.

| Quellstand | Ausgeführt | Bestanden | Fehlgeschlagen | Errors / Skips |
| --- | ---: | ---: | ---: | ---: |
| `557e659a48dcd0f166acf3ca7a2a34199dbe6a24` | 136 | 133 | 3 | 0 / 0 |

Alle drei Fehler lagen in den neuen `AvatarTtsLiveConnectionUiTest`-Fällen:
PCM-Energie an der sichtbaren Oberfläche, aktuelle Lautform nach Äußerungswechsel
und Sprachzustand beim Umhängen der Oberfläche. Die bestehenden 133 Tests
bestanden, einschließlich der 26 übernommenen Helferregressionen.

Der Gesamtprozess blieb **FAIL / Exit 1**. Lint wurde wegen des vorherigen
Testfehlers **nicht ausgeführt**. Der damalige Befund war ausdrücklich noch
in Diagnose; ein Produktionsfehler war damit nicht nachgewiesen.

## 3. Separate Uhrendiagnose der neuen Testfixture

Quelle: `clock-diagnostic-run/summary.json`,
`clock-diagnostic-run/TEST-at.heinz.deviceai.AvatarTtsLiveConnectionUiTest.xml`
und `clock-diagnostic-run/gradle.log`.

- Exakter Quellstand: `f6c3bdbcc1b9b1c5830c0c72763dd1e2bd727283`.
- Exakter Tree: `6ca6989d688065ffa9c4deaad2a18ff6bf1c58c4`; Worktree laut
  Protokoll sauber.
- **Tatsächlich drei Tests, alle drei fehlgeschlagen, null Errors/Skips.**
  Dies ist die wiederholte Diagnose derselben drei Fälle, kein weiterer
  unabhängiger Testbestand und kein positiver Abschlusslauf.

Die XML-Ausgabe zeigt zwei unterschiedlich fortschreitende Uhren. Im ersten
protokollierten Fall steigt die virtuelle Android-Zeit von 185 auf 356 ms
(171 ms), die JVM-Wanduhr dagegen nur um 59 ms. Nach dem Callback enthält der
echte Reducer bereits Energie `0.32`, während die erwartete Lautform noch
`SIL` ist. Die Diagnose-Zusammenfassung bestätigt die Vermischung realer
JVM-Wandzeit und virtueller Android-Zeit in der neuen Robolectric-Fixture.
Die fehlgeschlagenen Erwartungen belegen daher keinen neu festgestellten
Produktionsregressionsfehler. Rohfehler und Diagnosen bleiben unverändert
erhalten; die spätere Fixture-Korrektur benötigt ihre eigene Ausführungsabnahme.

## 4. Unveränderter historischer GLB-Konflikt

Quelle: `../asset-connection-check/summary.json`, `validator-result.json`,
`pivots.json` und `validator-self-tests.txt`.

Das bei `5e2a0c2b26d1e2978b19d70878f30c98c36d94e9` geprüfte verpackte Asset
ist byte-identisch zu Helferstand `6cf6e2128463e6a24dfac4652bb65e325ff6f18a`:
10.165.644 Bytes, SHA-256
`5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`.

Der unveränderte vorhandene Checker
`tools/epsilon-avatar-contract/validate_glb.py` liefert **FAIL / Exit 1** bei
`rig_hierarchy_orientation`. Kiefer-Y beträgt 0,266999998 m statt der alten
Vertragsgrenze 0,20–0,25 m; der Augen–Kiefer-Abstand beträgt 0,021414421 m statt
0,04–0,09 m. Die acht vorhandenen synthetischen Checker-Selbsttests bestehen
mit Exit 0. Der Asset-Lauf bricht nach dem Hierarchiebefund ab; nachfolgende
Morph-/Skin-/Budgetprüfungen dieses Checkers sind dadurch nicht ausgeführt.

Die Kotlin-Laufzeitprüfung erzwingt diese alten Pivotfenster nicht. Der Befund
ist deshalb kein Nachweis einer Android-Ladeverweigerung. Die vorhandene
historische Hinge-V2-Abnahme bindet das andere Asset `6b199c59…`; ihr PASS wird
nicht auf die aktuellen Bytes übertragen. Asset und Verträge wurden nicht
geändert oder abgeschwächt.

## Nachweisgrenzen

Dieser Index enthält keinen späteren GREEN-, Gesamt-Lint- oder APK-Erfolg.
Native Filament-/GPU-Darstellung, tatsächliche Lautsprecherausgabe, physisches
Fold7 und visuelle Referenzabnahme sind durch diese Nachweise nicht geprüft.
