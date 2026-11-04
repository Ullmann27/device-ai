# C1-MEMORY-017 — begrenztes unabhängiges Intake

**Status: STATIC_INTAKE_READY_C1_EXECUTION_REQUIRED.** Kein statischer Einbaublocker im geprüften Umfang gefunden. Zwei getrennte Produkt-/Testpatches sind gegen C1 `4da039d55d44f7087a9e4cd7bd72dc5a0089029e` vorbereitet; zusammen genau fünf Pfade. Der vorgeschlagene erste serielle Slice ist der Compiler mit seinen zwei Frozen-Testklassen. Der Mapper ist anschließend unabhängig übernehmbar. Die vorhandenen Quellen erlauben beide Reparaturen ohne Activity-, Factory-, Layout- oder Buildänderung.

## Tatsächlich selbst gelesene Quellidentitäten

| Zweck | Veröffentlichter Commit | Lokaler unveränderlicher Commit / Tree |
| --- | --- | --- |
| C1-Basis | lokaler Auftragspin | `4da039d55d44f7087a9e4cd7bd72dc5a0089029e` / `974e949a022a7774c6c5c702631217fffb8f8018` |
| Vorab eingefrorene Tests | `33706d166afafe8f93d3b9aa1db34190f950eb68` | `10ecc70a926c6ddb22673cabc4e53d880cf48b52` / `b07ec801de64912f12610d86d11f93c1fa5b89b9` |
| Tatsächlich extern ausgeführte Quelle | `3704b62585c6ce6881ab0dfe0c9947cddd1531c2` | `ec2543bef908d7d346a9767f9b095e84254090d1` / `ed4984ba9ac873e081a66d7a7be871f029ef5be5` |
| Aktuell gelesener PR522-Head | `eadc72bac95f8b1a554a59e5baff624977b09f73` | `61b961bfcd63cf572a06a7d5b31bf2fe622e7ae3` / `187aab3fe44a94b9b01451a867589c8e967cc641` |

Die öffentlichen Commitobjekte wurden direkt abgefragt und ihre Trees mit den lokalen unveränderlichen Objekten verglichen. Der finale Delta ist ausschließlich Dokumentation. Getesteter und finaler App-Unterbaum: `befaef97b94b38ebeba65c0160e332d747dafd7a`.

Aktueller PR: https://github.com/Ullmann27/device-ai/pull/522

Kanonischer selbst gelesener Handoff: https://github.com/Ullmann27/device-ai/blob/eadc72bac95f8b1a554a59e5baff624977b09f73/docs/research/omega/MEMORY-BRAIN-R3-HANDOFF-20261104.md

Die Remote-Lesebelege sind in `provenance/github-reads.json` gesichert. Der fremde Checkout `/workspace/scratch/624aeb26d481/continuum-brain-r3` wurde ausschließlich über Git-Leseoperationen benutzt.

## Minimale Patchgrenzen

- `C1-MEMORY-017-compiler.patch`: 3 Pfade, SHA256 `1f20a41f828ac8bd5fdcca55e31ada9ebf4831a6f4f2eb70c895d7af8600286a`.
- `C1-MEMORY-017-mapper.patch`: 2 Pfade, SHA256 `9dd1d877b0f24ce710118a3907728318482f910305dda60c4248da797a238d3b`.

| Vollständiger Repo-Pfad | C1-Basisblob | Zielblob |
| --- | --- | --- |
| `app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt` | `f42f526cefaa96295205f161241fbc8035f27c01` | `beb0f37d8de8965984bf38846b6479e0137d2340` |
| `app/src/main/java/at/heinz/deviceai/cockpit/MemoryEventSource.kt` | `98e19b678a48b448738009db1d1322efd1e49440` | `42f4714c72ee057566be54171f01703f39d48aaf` |
| `app/src/test/java/at/heinz/deviceai/cognitive/ContradictionBoundaryRegressionTest.kt` | neu | `0c798951d4f19d0ac873330caf2c5b6ce6a29d99` |
| `app/src/test/java/at/heinz/deviceai/cognitive/ContradictionBoundaryHoldoutTest.kt` | neu | `8e0d89fee6ed05af7b32e4ca589c83fb008c5f8d` |
| `app/src/test/java/at/heinz/deviceai/cockpit/MemoryEventMapperLifecycleRegressionTest.kt` | neu | `b8803c074ad56c6b51d1b587818ae4bc5e1003dd` |

Alle Zielmodi sind `100644`. Die zwei geänderten Produktionsdateien stimmen in C1 exakt mit der ursprünglichen Reparaturbasis überein. Die drei neuen Testpfade fehlen dort noch. Beide Patches wurden ausschließlich im Speicher anhand exakter Basisbytes rekonstruiert; die Ergebnisse entsprechen sämtlichen Zielblobs und SHA256. Kein Patch wurde in einem Repository angewendet. `base/`, `target/` und das Manifest sichern die vollständigen reviewbaren Bytes.

### Compiler: Quellenidentität und unvertrauenswürdige Widersprüche

Die tatsächliche C1-Activity übergibt `memories = memories` und erzeugt `unresolvedContradictions` durch Filter auf CONTRADICTED sowie unverändertes `.content`. Der neue Compiler löst Identitätskonflikte vor beiden Projektionen auf. Wenn zu einem identischen Text auch nur ein ausgeschlossener oder mehrdeutiger Datensatz gehört, wird dieser Text zurückgehalten. Ein generischer Hinweis erhält den ungelösten Zustand ohne Text, ID, Anzahl oder Privatheitsklasse preiszugeben. Erlaubte Texte und IDs durchlaufen die vorhandene vollständige Untrusted-Sanitizerfunktion. Vollständige eigenständige Korrekturen bleiben verpflichtend und atomar im Bytebudget.

**Vertragsgrenze:** Das String-API ordnet nur exakte Inhaltsprojektionen mit mitgelieferten Originalrecords zu. Freie beziehungsweise umgeschriebene Notizen bleiben ausdrücklich unzugeordnete Caller-Anmerkungen. Es wird keine Herkunft für beliebige umformulierte Texte erfunden. Die gelesene C1-Activity erfüllt den bestehenden Vertrag; keine Activity-Anpassung erforderlich.

`appendSection`, `redactSecrets` samt nachfolgenden Helfern, Observer-Konfiguration, privater Bot-Policy und Observer-Dispatch sind bytegleich. Die für #511 reservierten Tuningabschnitte werden nicht übernommen oder verändert.

### Mapper: nachfolgende Zustellungen und atomarer Schreibzähler

Die Änderung bleibt vollständig innerhalb `MemoryEventMapper`. Der gesamte Factory-/Interface-Präfix von `MemoryEventSource.kt` ist bytegleich. Letztes Storage-Ereignis und Count liegen nun in einem gemeinsamen unveränderlichen AtomicReference-Wert; CAS und `getAndSet(null)` trennen Erzeugung und Verbrauch atomar. Vor jeder nachfolgenden Ereignisart wird Detach erneut geprüft. Über Bus-/Renderer-Callbacks wird kein Mapper-Lock gehalten; der bestehende C1-Caller plant weiterhin über `Handler(mainLooper)`.

Das ist eine Korrektur von Präsentationsmetadaten. Weder ein Verlust gespeicherter Ledgerdaten noch eine globale Abbruchbarriere für einen bereits begonnenen Bus-Emit wird behauptet. Der Count ist Int und sättigt bei Int.MAX_VALUE; die feste 80.000er-Prüfung ist keine Garantie für unbegrenzte Zählbereiche.

## Abhängigkeiten und Testintegrität

Die drei **vollständigen Testdateien** sind bytegleich zum veröffentlichten Stand vor der Reparatur: vier ursprüngliche Compilerfälle, sechs unabhängig eingefrorene Policy-/Alias-Holdouts und vier Mapperfälle, zusammen 14. Methoden und sämtliche Assertions sind damit unverändert; keine Ignore-/Assume-Ergänzung. Die 13 bestehenden Compiler-/Integritätstests bleiben ebenfalls erhalten.

Die aufgezeichneten fokussierten Quellgraphen haben 45 eindeutige Pfade: 41 bereits vorhandene C1-Abhängigkeiten sind bytegleich, vier gehören zu den vorgeschlagenen Änderungen. Die neue Mapper-Testdatei ist zusätzlich separat über ihren Frozen-Blob gebunden. Kein weiterer Import oder Buildwechsel erforderlich. `HybridMemoryRetriever.unambiguousRecords`, `MemoryStored.count`, `CockpitAnimationBus` und die tatsächlichen C1-Caller wurden gelesen.

Die drei externen Hostrunner sind lediglich unter `evidence/upstream/tools/` als Reproduktionsbeleg gesichert. Sie gehören zu keinem Produktpatch und müssen für die bestehenden Android-Testquellen nicht übernommen werden. Sie wurden nicht ausgeführt.

## Originale negative und positive Laufbelege

**Autorenläufe, von mir durch Quellen-/Rohbeleglesen geprüft; keine eigene Testausführung:**

| Lauf | Tatsächliches Originalergebnis | Grenze |
| --- | --- | --- |
| Compiler zuerst | 4 Fälle: 2 FAIL / 2 PASS | Überlappt mit folgendem RED |
| Compiler plus Frozen-Holdouts | 10 Fälle: 6 FAIL / 4 PASS | Datenschutz, Rollentoken, Policy-/Alias-/Budgetgrenzen |
| Reparierter Compiler plus bestehende Verträge | 23/23 PASS |10 neue plus 13 bestehende Fälle |
| Mapper RED | 4 Fälle: 3 FAIL / 1 PASS | Zwei Detach-Fälle; beobachtet 79.387/80.000 Writes |
| Mapper GREEN | 4/4 PASS | Einschließlich exakter 80.000er-Abrechnung und Callback/Producer-Fall |
| Voller Android-Unitlauf | 2.638 gesamt / 2.618 PASS / 1 FAIL / 19 SKIP |467 Suites; kein Gesamt-PASS |
| Separate Activity-/Handlerprüfung | 2/2 PASS | Reale angehängte Activity, kein vollständiges onCreate/onDestroy |
| Separater Lint | 0 Issues, NewApi-only | `checkOnly += setOf("NewApi")` in tatsächlicher Buildquelle geprüft |

Die 80.000er-RED-Interleavings sind schedulingabhängig; die beiden Detach-Fälle sind direkt durch Callback-Reihenfolge bestimmt. Die JVM-Compilerbelege extrahieren nur die unveränderte RecallHit-Deklaration; der Mapper-Hostlauf lässt die unbenutzte Factory aus. Beide Grenzen sind in den unveränderten Manifesten erhalten und werden nicht als vollständiger Androidlauf bezeichnet.

Ich habe das komplette Roharchiv SHA256 `0e89de23cb7b68bf4b9e419250775f33a57560e219abdbf6761ed1e262e9d927` geöffnet, **alle 480 enthaltenen Dateien** gegen Größe/SHA256 geprüft und die XML-Zahlen neu berechnet. Die vier Vor-/Nach-Quellenmanifeste sind bytegleich; **alle 1.080 Quellenhashes** stimmen mit den tatsächlich getesteten Git-Blobs überein. Die 14 neuen Fälle stehen namentlich und fehler-/skipfrei in den originalen Android-XML.

Der einzige volle Fehler bleibt `ContinuumLandscapeCockpitLayoutTest.everythingFitsOneScreenWithUsableTargetsAndNoOverlap`, Meldung `832.0 x 700.0 font 1.0 VIEW_AVATAR ≥ 48 dp`. Diagnose und ursprüngliche Assertion bleiben gesichert. Der frühere Infrastrukturabbruch `NOT_RUN_LESS_THAN_1_GIB_FREE`/75 ist separat erhalten und zählt nicht als Test.

**Keine Übertragung auf C1:** Gegen die externe Ausführung unterscheidet sich C1 an 20 im Laufmanifest aufgezeichneten Quellpfaden. Fünf davon decken die vorgeschlagenen Patches ab; 15 stammen aus anderen C1-Slices, darunter Activity, Avatar, Tagesgrenzen, Sequence und UI015. Zusätzliche C1-Dateien werden durch den alten externen Lauf ebenfalls nicht zertifiziert. Fokusgruppen überlappen und dürfen nicht addiert werden.

## AGENTS separat: bestehende Dokumentationskorrektur

`AGENTS-existing-public-wording.patch` enthält ausschließlich die beiden bereits veröffentlichten Absätze. Die **gesamte** Zieldatei besitzt denselben Blob `219e2123b275b99872fa70315a486b61b9922ab1` wie die selbst frisch gelesenen Quellen #494 `93f1f1130a3963062ad4c43c4a1edfe0cf7e2759` und #499 `999225dc3823cd027c06adf2847e7fec9f772cf4`. C1 enthält noch `511396c8e88ef0b9811b791d6c8bccc10f868b26`.

Dies ist der separat reviewbare Nachzug vorhandener Formulierungen; keine neue Erlaubnis, Sichtbarkeitsänderung oder Aufhebung von Geheimnis-/Besitzerschranken. Der Doku-Patch ist ausdrücklich aus beiden Produktpatches ausgeschlossen.

## Serieller Einbau und offene Abnahme

C1 bleibt einziger Einbauer. Vor Anwendung die fünf Basiszustände gegen den dann aktuellen eigenen HEAD prüfen. Zuerst die vier Klassen `ContradictionBoundaryRegressionTest`, `ContradictionBoundaryHoldoutTest`, `AiContextIntegrityTest` und `AiContextCompilerTest` mit 10 neuen und 13 bestehenden Fällen, danach Mapper mit vier Frozen-Fällen und vorhandener Activity-/Handlerklasse tatsächlich auf C1 ausführen; anschließend die verpflichtenden gemeinsamen vollen Gates auf den exakten neuen Stand binden. Kein externer PASS ersetzt diese Schritte. Für die eigene Prüfung wurden keinerlei Tests, Builds oder Locks gestartet.

Rollback nur über die benannten kleinen Hunks beziehungsweise neu hinzugefügten Testdateien, nie über einen alten kompletten Fremdbranch oder eine fremde Activity. Modellgewichte, Owner-/Consent-Dateien, #505-Retriever/Sequence, UI015, Paket/Version/Signatur/Workflows und alle übrigen Produktdateien bleiben außerhalb dieses Intakes. EXP-06/EXP-12 sind berücksichtigt, ohne Experiment-/Modellpromotion.

**Kein Gesamt-PASS, keine APK-/Gerätefreigabe und kein externer C2-ACK.**
