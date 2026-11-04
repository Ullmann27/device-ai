# C1-MISSING-022 — unabhängige Gegenprüfung

**Ergebnis: Im begrenzten Zwei-Dateien-Slice kein materieller Quellblocker. Der integrierte Fokus `missing022-integrated-02` ist anhand des vollständigen Original-TAP und Receipt mit exakt 26 PASS, 0 FAIL/CANCELLED/SKIP/TODO und Exit 0 belegt.** Dies ist eine unabhängige Quellen- und Belegprüfung, keine eigene Testausführung.

## Gebundene Quelle und Scope

- Gelesener lokaler Commit: `9a442485f2569b39be2bcd8d6687da6f6c15c45d`.
- Gelesener Tree: `8fe54137e976c718c91a4b3bbcfc02d50ca52bdd`.
- Parentbasis: `445807c4ee97c59122a6c98a08866fa932ca24d7`.
- Von C1 gemeldeter Remotezwilling: `0d8a3d46c409e5918c49b6f3407bb63f3519b846`; für diesen Review wurde lokal gepinnt gelesen.

Genau zwei Code-/Testdateien ändern sich. Zusätzlich enthält der Commit das Intake-/RED-/GREEN-Archiv, dessen Zusammenfassung und zwei Handoff-/Board-Dokumente. Kein weiterer Produktionspfad wird geändert.

| Pfad | Basisblob → Zielblob | Ziel-SHA256 |
|---|---|---|
| `.github/continuum/local-teamleader-loop.mjs` | `3dc5d63077fce0244e2bdeeb20b310aa762d2cfa` → `f83e6a1dcd40e7841734f893895766c6c3b1ed29` | `a6f9cf6b556386ca7f04eeedaa97fdf277817ed160f40f7a6945d4b28641e71e` |
| `.github/continuum/local-teamleader-loop-source-preflight.test.mjs` | neu → `13c5661432115c883fc2b64161757aad9d1128f4` | `8c7cf6ff3391da4c5742edb3650459333985c60b41faa9ee98d1a102c4f1c219` |

Beide Dateien haben Modus `100644`. Beide integrierten Zielbytes sind identisch zu `intake/green/` im gepinnten Archiv. Die neue Testdatei ist zwischen archiviertem RED, GREEN und integriertem Commit bytegleich. Die alte RED-Produktionsdatei stimmt mit der Parentbasis überein. Die 22 bisherigen Schedulerfälle sind unverändert: `.github/continuum/local-teamleader-loop.test.mjs`, Blob `d325c114c5a35a5d1ce21969f5aa58d53cf4ad56`, SHA256 `96883a253ea904660543278b472f9b7cddacd73d0da0ad76c1bc74ebe4a02f45`.

## Tatsächlicher Callbackvertrag

`sourceIdentity` kanonisiert den Root und wartet anschließend auf die vertrauenswürdige `sourcePreflight`-Funktion, bevor `git rev-parse` oder `git status` ausgeführt werden. `openCheckpoint` wartet darauf, bevor Checkpointverzeichnis oder Schedulerlock angelegt werden. `checkSource` ist ebenfalls asynchron; der Loop wartet vor jedem Arbeitszyklus und nach `work` auf die vollständige Prüfung. Die Nachprüfung liegt vor der Übernahme von `result.outcome`.

Eine anfängliche Ablehnung wirft, bevor `work`, Checkpoint oder Lock entstehen. Eine Ablehnung zwischen Zyklen beendet den vorhandenen Heartbeat mit ERROR und ohne nächsten Versuch. Eine Ablehnung nach `work` verwirft dessen behauptetes `VERIFIED_PATCH_PENDING_REVIEW`, erhöht Fehler-/Zykluszähler und speichert terminales ERROR. Der bestehende `finally`-Pfad gibt den Schedulerlock frei. Checkpointschema, Integritätsprüfung, Budget-/Backofflogik, Ergebnismenge und Lockimplementierung bleiben unverändert.

**Geltungsgrenze:** `sourcePreflight=null` bleibt der abwärtskompatible Standard. Nur `throw` bzw. ein abgelehntes Promise ist ein Veto; ein erfüllter Rückgabewert `false` wird nicht ausgewertet. Ohne dauerhaften Checkpoint wird diese Quellprüfung nicht aufgerufen. Ein nichtnull Optionswert, der keine Funktion ist, wird vor Quellzugriff/Arbeit abgelehnt. Die Implementierung liefert eine optionale, vollständig erwartete Prüfschnittstelle, keine selbständige vollständige Sicherheitsrichtlinie.

## Echte Git-Canarys und originale Läufe

Die vier neuen Fälle verwenden echte temporäre Git-Repositories und echte `git`-Prozesse (`spawnSync`, `shell:false`), keine simulierten Statusantworten. Zwei lokale ausführbare Fixtures schreiben einen Marker: `core.fsmonitor` sowie ein über `.gitattributes` eingebundener Clean-Filter. Die Prüffunktion hat mit `setImmediate` bewusst eine asynchrone Grenze. Im dritten Fall bleibt der Dateiinhalt gleich, während geänderte Dateimetadaten die reale Filterauswertung auslösen können.

Das vollständige Original-RED zeigt vier fachliche Assertion-Fehler: In allen vier Fällen wurde der Marker tatsächlich erzeugt. Es handelt sich nicht um Import-, Syntax- oder Setupfehler. Das anschließende Original-GREEN enthält dieselben vier Fallnamen mit vier Erfolgen. Unter anderem bleiben die Marker aus; die spezifizierten Fehlerzustände, Aufruf-/Zykluszahlen, fehlenden Checkpoints und freigegebenen Locks werden weiterhin verlangt.

| Lauf | UTC-Beginn → Ende | Belegtes Ergebnis |
|---|---|---|
| `missing022-red-01` | 21:59:53.624634 → 21:59:54.125938 | 4 Fälle, 0 PASS / 4 FAIL; Exit 1 |
| `missing022-green-01` | 22:00:07.937737 → 22:00:08.439117 | 4 Fälle, 4 PASS / 0 FAIL; Exit 0 |
| `missing022-integrated-01` | kein `startedAt`, kein Log | Kein belegter Teststart. Letzter Heartbeat 22:04:31.393965; C1 meldet verlorene Ausführungssession. Kein PASS. |
| `missing022-integrated-02` | 22:12:09.194713 → 22:12:10.196593 | 26 Fälle, 26 PASS / 0 FAIL; Exit 0 |

Alle Datumsangaben betreffen den 2026-11-04. Beide ausgeführten isolierten Läufe und der integrierte Lauf haben 0 CANCELLED/SKIP/TODO. Der integrierte TAP enthält exakt die vier eingefrorenen neuen Fallnamen und anschließend exakt die 22 Namen der unveränderten bisherigen Tests, jeweils einmal. Namen, Reihenfolge, Summen, Logkopf, Prozess-Exit und Receipt wurden gegeneinander geprüft.

Die drei abgeschlossenen Receipts benennen beide gemeinsamen Sperren. Die archivierte, hashgebundene `run_c1_gate_v3.py` beginnt den Kindprozess erst nach Erwerb beider Sperren. `sourceUnchanged=true` der isolierten Läufe bezeichnet den sauberen C1-Hostcheckout; die tatsächlich importierten externen Fixturemodule sind separat durch Archiv-/Manifestbytes gebunden. Der integrierte Lauf importiert unmittelbar die beiden Testdateien des gepinnten C1-Repos.

```sh
node --test --test-concurrency=1 --test-reporter=tap .github/continuum/local-teamleader-loop-source-preflight.test.mjs .github/continuum/local-teamleader-loop.test.mjs
```

Dies ist der gelesene ausgeführte Befehl, kein neuer Startauftrag dieses Reviews.

## Unveränderte Grenzen

Die tatsächlichen vier produktiven Aufrufer sind am geprüften Commit sämtlich **ohne `sourcePreflight`-Bindung**:

| Pfad | Gelesener Einstieg | Dauerhafter Modus |
|---|---|---|
| `.github/continuum/teamleader-cli.mjs` | `runTeamleaderLoop`, Zeile 113 | nur mit `--durable` |
| `.github/continuum/ccc1-cli.mjs` | Zeile 117 | ja |
| `.github/continuum/continuum-workshop.mjs` | Zeile 212 | nein |
| `.github/continuum/coding-service.mjs` | Zeile 320 | ja |

Die vollständigen Dateien wurden gepinnt gelesen und ihre Blobs gegen das Intake-Manifest verglichen. Kein Aufrufer wird durch diesen Slice geändert. Es wird kein Branchbot aktiviert und keine neue Merge-/Push- oder Ausführungsbefugnis erteilt. Die Git-Canarys belegen die vier kontrollierten Ablehnungspfade; sie sind kein OS-Sandbox-/Hostisolations-PASS. Der historische 183er-Lauf wird weder erneut als C1-Ergebnis bezeichnet noch zu den 26 Fällen addiert. Android, APK, echtes Modell, Gerät und Gesamtgate bleiben außerhalb dieses Reviews.

## Gesicherte Belege

Originalarchiv am geprüften Commit:
`docs/evidence/c1-integration-20261104/missing022/C1-MISSING-022-intake-and-own-redgreen.tar.xz`

SHA256: `7b30398f74e4fee1e5ea73186c2c91582fa6a0b014c4694d989769d6208fa6b9`. Alle 18 regulären Archivmitglieder wurden gegen die veröffentlichten Einzelhashes geprüft und unter `original-evidence/` dieses Reviewordners gesichert.

- Integrierter Original-TAP `missing022-integrated-02.log`: SHA256 `cb10419268c0bdd0e459cc68bd2e429fa23a34c82f7f13e896252ff4579e675f`.
- Integrierter Original-Receipt `missing022-integrated-02.result.json`: SHA256 `d473da029436b29b10e5fe7c7c91f9bbb9fa8c4341fab78af347ba8988a870e4`.
- Unvollständiger erster Receipt `missing022-integrated-01.result.json`: SHA256 `61dda74986b55930fd2611871a82c1cd5b5e2962744459a09762e738500acd8a`.
- `C1-MISSING-022-review.json` enthält alle Quell-/Beleghashes, die 26 exakten Fallnamen, beide integrierten Receipts und die Grenzen.

Frühere `*-snapshot*`-Dateien und `source-and-evidence-verification.json` dokumentieren den zwischenzeitlichen Wartestand. Maßgeblich für den abgeschlossenen Review sind dieser Bericht, `C1-MISSING-022-review.json` und die mit `-02` benannten Originalkopien. Kein Test, Build, Kommentar oder Repositoryedit wurde durch diesen Reviewer ausgeführt.
