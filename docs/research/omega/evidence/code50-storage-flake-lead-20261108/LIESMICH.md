# Code 50 – lastabhängiger `storage-agent`-Fehlschlag auf `cc8ee5f` (Beweislage des Hauptagenten, 08.11.2026)

**Status: WIP / UNVERIFIED.** Es wurde kein Produktionscode und keine Testdatei des Repositories geändert. Der Kandidat in `kandidat/` ist **nicht angewendet**. Alle Läufe stammen aus der geteilten 2-Kern-Sandbox (Node 24.22.2, Nicht-root-Benutzer) und sind **keine** offizielle Release-Host-Abnahme und **kein** Gerätenachweis.

## 1. Befund

- Quellstand: `cc8ee5fe2dffd98f41c505c01b5d9ffdae4d8cf9` (Staging `chatgpt/code50-qa-staging-20261108`, PR #630). Gegenüber `6f0137f` und `8bc695f` ändert dieser Commit nur Doku. Die Git-Baumhashes von `app` (`b440b7e9…`), `backend` (`f4bc00c1…`), `scripts` (`01e9af00…`), `.github` (`e85d53ff…`) und `gradle` (`4167f4ea…`) sind in allen drei Commits identisch (per `git rev-parse <commit>:<pfad>` geprüft).
- Host-Lauf `critical` auf `cc8ee5f`: **FAIL**. Gate `storage-agent` 28 PASS / 4 FAIL / 0 SKIP (32 Fälle); `process` 37/0/0, `android-source-contracts` 64/0/0, `host-checker` 2/0/0. Die vier fehlgeschlagenen Fälle (alle `FULL_REPLICATION_REQUIRED_METADATA_NOT_COMMITTED`), Rohdateien in `critical-cc8ee5f-FAIL/`:
  1. `real two-process replication, corruption failover, repair, unavailable node, cache and restart`
  2. `versions survive restart; tombstone commits before unavailable replica deletion and reconciles`
  3. `real disk/quota limits reject new writes without committing object or claiming synthetic capacity`
  4. `root/block/cache symlinks and catalog HMAC tampering fail closed; untrusted bytes are data only`
- Derselbe Code lief auf `6f0137f` grün (`critical` 135/135, `full-node` 2393/0/37, Backend 64/64). Auf `8bc695f` war `critical` Lauf 1 rot, Lauf 2 grün (Handoff §10). Der Fehlschlag hängt daher von der Last ab, nicht vom Code.

## 2. Mechanismus (direkt belegt)

1. `.github/continuum/storage-fabric.test.mjs:42` erzeugt die Loopback-Adapter des Fixtures mit `timeoutMs: 500`. Der Produktionsstandard ist `timeoutMs = 1500` (`.github/continuum/storage-fabric.mjs:100`), zulässig sind 50–5000 ms (`storage-fabric.mjs:104`).
2. `.github/continuum/storage-fabric-catalog.mjs:103`: Schreibzugriffe warten per `Promise.allSettled` auf alle Replikate. Lehnt auch nur eines ab, wird `FULL_REPLICATION_REQUIRED_METADATA_NOT_COMMITTED` ausgelöst, bevor Metadaten committet werden. Das ist korrektes Fail-closed-Verhalten und **wird nicht verändert**.
3. Der Harness startet dieses Gate mit `--test-concurrency=2` (`scripts/continuum-reliability-qa.mjs:19-20,95`): drei Testdateien laufen auf nur zwei Kernen parallel, jedes Fixture startet echte Kindprozesse.
4. Instrumentierung (`mechanismus/instrumentierung-storage-fabric-catalog.patch`, nur in einer Wegwerfkopie; sie schreibt einen absoluten Sandbox-Pfad und ist **nicht** zum Übernehmen gedacht) protokolliert bei jeder Ablehnung Knoten, Timeout und Fehlername. Ergebnis in `mechanismus/rejections.log` (vier Zeilen):
   - Zeile 1 und 2: ein Replikat läuft bei `t=500` in `TimeoutError`, das andere antwortet (`ok`). Das sind die unbeabsichtigten Fehlschläge.
   - Zeile 3: `NODE_HTTP_507` auf beiden Knoten = absichtliche Ablehnung des Quota-Tests.
   - Zeile 4: ein Knoten mit `t=50` = absichtlicher Timeout des dedizierten 50-ms-Tests (`storage-fabric.test.mjs:168-181`).
   - `mechanismus/lauf-2.xml` enthält genau zwei fehlgeschlagene Fälle (Zwei-Prozess-Replikation und Tombstone), `mechanismus/lauf-1.xml` keinen.
5. Schluss: Unter CPU-Last antwortet ein gesunder Loopback-Replikatprozess nicht immer innerhalb von 500 ms. Der Produktionscode meldet das wie vorgesehen fail-closed. Die Instabilität liegt in der knapp bemessenen Fixture-Frist, nicht im Verhalten des Produktionscodes.

## 3. Messdaten (`summary.json`, erzeugt mit `werkzeuge/auswerten.py` direkt aus den Rohdateien)

Variante **A** = unveränderter Stand `cc8ee5f` (500 ms), Variante **B** = Kandidat `c7fe719` (nur Fixture-Timeout 1500 ms). Die Läufe wurden abwechselnd A/B ausgeführt. „Fehlrunde“ = mindestens ein fehlgeschlagener Fall im `storage-agent`-Gate (je 32 Fälle).

| Gruppe | A: Fehlrunden | B: Fehlrunden | einseitiges Fisher-p (A schlechter) |
|---|---|---|---|
| isoliert (keine künstliche Last), `isoliert-ab/` | 0 / 6 | 0 / 6 | 1,000 |
| Stress, `stress-ab/` (3 CPU-Burner laut Aufzeichnung des Hauptagenten) | 2 / 6 (5 Fälle) | 0 / 6 | 0,227 |
| Harness `critical`, `harness-ab/` | 1 / 5 (1 Fall) | 0 / 5 | 0,500 |
| **gepoolt Stress + Harness** | **3 / 11** | **0 / 11** | **0,107** |

Fehlgeschlagene Fälle unter A: Stress-Runde 1 (3 Fälle: Zwei-Prozess-Replikation/Korruptions-Failover; Versionen/Tombstone; Disk/Quota), Stress-Runde 5 (2 Fälle: begrenzter verschlüsselter Cache; gemessener HTTP-Timeout hält Metadaten zurück), Harness-Lauf A-5 (1 Fall: Zwei-Prozess-Replikation). Unter B: kein einziger fehlgeschlagener Fall in 22 Läufen.

**Aussagekraft:** Der Mechanismus (Abschnitt 2) ist direkt belegt, die **Häufigkeit** nicht belastbar. Mit p ≈ 0,107 liegt die gepoolte Stichprobe knapp über dem für die neuen Runden vorregistrierten Schwellwert 0,10; die isolierten Läufe unterscheiden A und B erwartungsgemäß nicht. Das Ergebnis ist ein Hinweis, kein Beweis.

## 4. Kandidat (WIP / UNVERIFIED, nicht angewendet)

`kandidat/c7fe719-fixture-timeout-1500-WIP-UNVERIFIED-NICHT-ANGEWENDET.patch` ändert ausschließlich `.github/continuum/storage-fabric.test.mjs:42` von `timeoutMs: 500` auf `timeoutMs: 1500` (= Produktionsstandard). Der dedizierte 50-ms-Timeout-Test setzt `node.timeoutMs=50` selbst und bleibt unberührt. Kein Produktionscode; keine Datei unter `app/` verweist auf `storage-fabric`, `LocalBlockAdapter` oder `ReplicatedStorageFabric` (per Suche geprüft), der Android-Build ist daher nicht betroffen.

Warum nicht angewendet: Besitzerentscheidung A (Handoff §11) ist offen, und die belastbare Wiederholung steht aus. **Vorregistrierte Entscheidungsregel** (an Agent 1 `a1-test-stability-v2` übergeben, gilt für NEUE Runden):

- mindestens 10 abwechselnde Runden je Variante unter drei zeitbegrenzten CPU-Burnern;
- gepooltes einseitiges Fisher-p ≤ 0,10 **und** B ohne Fehlschlag in den neuen Runden;
- Mutationsbeweis: Neutralisieren von `storage-fabric-catalog.mjs:103` muss die Fail-closed-Tests unter A **und** B rot machen (das Fail-closed-Verhalten bleibt also gepinnt);
- Prüfung weiterer kurzer Timeouts im Test;
- zeigt A in den neuen Runden keinen Fehlschlag, gilt das Ergebnis als „unentschieden“ und es wird nichts gepusht.

## 5. Grenzen

- Geteilte 2-Kern-Sandbox ohne Android-SDK und Gradle (`dl.google.com` ist gesperrt): keine Release-Host-Abnahme, kein Android-Build möglich.
- Das Lastprofil der Harness-Läufe (`harness-ab/`) und die genauen Burner-Befehle sind **nicht** im Rohlog festgehalten. Die Stress-Läufe (`S-*`) liefen laut Aufzeichnung mit drei Burnern.
- Kleine Stichprobe (11 vs. 11 gepoolte Runden); keine Aussage über absolute Fehlerraten auf anderen Hosts. In den beiden bekannten GitHub-Diagnoseläufen (42473004670 auf `d8353c2`, 42478725998 auf `8bc695f`) war der Node-/Backend-Job grün; das ist nur Diagnose und keine Abnahme.
- `report.json` des fehlgeschlagenen `critical`-Laufs (2.384.112 Bytes) ist **nicht** Teil des Commits, nur per SHA-256 referenziert: `dc0109e4423003ecedf25bc2d022e629477bf1353211102ce0568e0f31ee05a6`. Die Fall-Identitäten und Fehlermeldungen stehen vollständig in `report.md` und den vier JUnit-XML-Dateien.
- Die Umgebung injiziert `GIT_CONFIG_*`-Einträge, darunter `core.fsmonitor`. `werkzeuge/code50-run-filtered.py` entfernt **nur diesen einen** Eintrag im Kindprozess (Details: Handoff §4); es werden keine Prüfungen des Produktivcodes abgeschwächt.

## 6. Reproduktion (Sandbox; Node 24 unter `/tmp/node24/node_modules/node/bin`, Benutzer `qa`)

```
# kompletter critical-Lauf (Ausgabeordner darf nicht existieren, der Harness schreibt mit Flag wx)
runuser -u qa -- python3 werkzeuge/code50-run-filtered.py <Repository> node scripts/continuum-reliability-qa.mjs critical --out <NEUER-ORDNER>

# nur das storage-agent-Gate (eine Runde, Exit-Code wird ausgegeben); Last separat erzeugen
bash werkzeuge/code50-storage-ab.sh <Repository> <ausgabe.xml>

# Auswertung der Rohdaten dieses Ordners
python3 werkzeuge/auswerten.py
```

## 7. Inhalt

| Pfad | Inhalt |
|---|---|
| `critical-cc8ee5f-FAIL/` | `report.md` und vier JUnit-XML des fehlgeschlagenen `critical`-Laufs, unverändert kopiert |
| `isoliert-ab/`, `stress-ab/` | je 12 JUnit-XML (A-1…6, B-1…6 bzw. S-A-1…6, S-B-1…6) |
| `harness-ab/` | je Lauf `report.md`, `storage-agent.xml`, `launcher.log` (A-1…5, B-1…5) |
| `mechanismus/` | Instrumentierungs-Patch, `rejections.log`, `lauf-1.xml`, `lauf-2.xml` |
| `kandidat/` | Patch des Kandidaten (nicht angewendet) |
| `werkzeuge/` | `code50-run-filtered.py`, `code50-storage-ab.sh`, `auswerten.py` |
| `summary.json` | maschinenlesbare Auswertung (Schema `continuum.code50-storage-flake-summary.v1`) |
| `SHA256SUMS` | Prüfsummen aller Dateien dieses Ordners (`sha256sum -c SHA256SUMS`) |
