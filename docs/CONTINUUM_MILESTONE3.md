# Continuum Meilenstein 3 (Grok) – Review-Fixes, attestierte CCC1-Quittung, Offline-Evaluation

Branch `continuum/m3-grok-selbstrekonstruktion-20261103`, gestapelt auf M2 (#370, `ea785432`). Draft-PR #373.
**Kein Merge, kein Release, kein versionCode 40**, keine Signatur-, Paket- oder Publisher-Änderung, keine GitHub Actions.
Evidenzstufen: `HOST_VERIFIED` (auf diesem Rechner ausgeführt), `SOURCE_ONLY` (Code vorhanden, Laufzeitpfad nicht belegt),
`DEVICE_REQUIRED` (nur auf dem Fold7 prüfbar). Keine Fold7-, Render- oder Signaturabnahme in diesem Meilenstein.

## 1. Review #370 / #369 (Top-Priorität)
| Punkt | RED | GREEN | Evidenz |
|---|---|---|---|
| 1 Abgelehnte Anweisung wird nicht als Aufgabendaten erneut an Gemma gesendet | `e4d453dd` | `93493b54` | `HOST_VERIFIED` |
| 2 CCC1-Quittung gegen echten Git-Stand geprüft (Node-Verifier) | `62552c7a` | `98c2c909` | `HOST_VERIFIED` |
| 2 Kotlin: GREEN nur mit gebundenem Attest | `ced25aca` | `98c2c909` | `HOST_VERIFIED` |
| 3 Budget-Überlauftest trifft die echte Bedingung | `e4d453dd` (Mutationsnachweis) | `e4d453dd` | `HOST_VERIFIED` |
| Gitar: „heute/guten Morgen“ ist kein Zukunftsmarker | `e4d453dd` | `93493b54` | `HOST_VERIFIED` |

**Punkt 1.** `LocalContextRetentionPolicy.apply()` ist jetzt die einzige Regel, die `AgentActivity.addNonModelReply` benutzt.
Bei `SAFETY_REFUSAL` bleibt der frühere harmlose Kontext erhalten, die abgelehnte Anweisung wird aber **gar nicht** verbucht.
Sie erscheint weder in `sessionEvidence` noch in `render()`. Zusätzlich filtert `LocalFollowUpTaskFrame` frühere
Owner-Turns mit Geheimnissen (PIN, Passwort, IBAN, Token …) oder verändernden Geräte-/Kontoaufträgen (löschen,
deinstallieren, senden, kaufen, überweisen …) aus dem Rahmen. Harmlose Turns bleiben drin.
Test: `LocalContextRetentionTest.refusedRequestIsNeverReplayedButHarmlessContextSurvives`,
`LocalFollowUpTaskFrameTest.sensitiveOrMutatingPriorInstructionsAreDroppedHarmlessOnesKept`.

**Punkt 2.** Neu: `.github/continuum/ccc1-redgreen-verifier.mjs` (CLI und Modul). Er übernimmt aus der Quittung keinen Befehl, keinen Pfad und keinen Runner.
- *Commit-Modus:* base, RED und GREEN sind echte Commit-Objekte, und es gilt base ⊑ RED ⊑ GREEN (`merge-base --is-ancestor`).
  `patchSha256` = sha256 von `git diff --binary --full-index RED GREEN`. Der RED-Commit ändert Testdateien, der Fix (RED→GREEN) **keine**.
- *Patch-Modus (CCC1-Reparatur):* Die Patchdatei hasht auf den behaupteten Wert, `git apply` gelingt auf dem Basis-Commit, und der Patch berührt keine Testdatei.
- In beiden Modi führt der Verifier das feste Node-Testprofil **selbst** in frischen detached Worktrees aus. Das erzeugte JUnit-XML ist
  maßgeblich. Behauptete Fehlschläge, `executed`- und `failed`-Zahlen müssen mit dem eigenen Lauf übereinstimmen.
- Das Ergebnis ist ein Attest `continuum-ccc1-redgreen-attestation`. Es enthält sha256(Quittungsbytes), Verdict, Gründe und die sha256 beider JUnit-Artefakte.
- Negativtests mit echtem Temp-Git-Repo: Ein GREEN-Commit ohne Fix, der Platzhalter-Hash `"a"*64`, nicht existierende SHAs,
  abweichende behauptete Fehlschläge und Zahlen, ein Fix, der den Zieltest umschreibt, und eine manipulierte Patchdatei werden alle `REJECTED`.
- Kotlin: `Ccc1PatchReceipt.evaluate()` liefert höchstens `SELF_CONSISTENT_UNATTESTED`, also nie mehr „verified“.
  `GREEN_ATTESTED_PENDING_REVIEW` gibt es nur über `evaluateAttested()`. Dafür muss `receiptSha256 == sha256(toJson())` gelten, das Attest
  muss `VERIFIED` ohne Gründe sein, der Patch-Digest muss gleich sein, und die RED/GREEN-Zahlen des Verifier-Laufs müssen passen.
- Die M1-Fixture nutzt jetzt den echten Patch-Digest `f9c032aa…` (`git diff 988ff152 cf4620e0`) statt `"a"*64`.
- **Grenzen (`SOURCE_ONLY`):** Der Verifier kennt nur das Node-Profil, es gibt keinen Gradle/JVM-Rerun. `ccc1-worker`/`ccc1-cli status`
  rufen ihn noch nicht automatisch auf, er wird per CLI gestartet. Die Kanonik der Quittungsbytes ist die von
  `Ccc1PatchReceipt.toJson()`, und die CLI hasht die Datei byte-genau.

**Punkt 3.** `LocalConversationFollowUpPolicy` akzeptiert nur `currentRequest` ≤ 1.000 Zeichen. Mit vier Turns à ≤ 1.000 Zeichen
liegt der reine Zeichenrahmen deshalb unter 7.900. Real überläuft das Budget nur auf zwei Wegen: (A) über eine lange `resolvedQuestion`
oder (B) über die UTF-8-Bytegrenze bei umlautreichen Turns. Der neue Test deckt beide Wege ab und prüft, dass der Auftrag **exakt unverändert** bleibt.
Die Kontrolle mit weniger Turns wird gerahmt. Mutationsnachweis: Wird der Budget-Rückfall in `compose()` entfernt, schlägt
`realTaskBudgetOverflowReturnsTheExactUnchangedRequest` fehl (`ComparisonFailure … but was:<[Anschlussfrage im selben…`).

## 2. Offline-Evaluation (deterministisch, `HOST_VERIFIED`)
Die Datei `app/src/test/resources/continuum-eval/m3-de.tsv` enthält 81 deutsche Fälle. Die Ground Truth ist die Absicht des Besitzers, nicht das Ist-Verhalten.
Der Scorer `ContinuumOfflineEvalTest` meldet nur Ergebnisse und benutzt ausschließlich APIs, die es schon bei `ea785432` gab. Deshalb misst dieselbe Datei auch die Baseline.
`harmful` zählt ein Werkzeug oder eine Aktion, die nicht gewünscht war. `NONE` (Übergabe ans Modell) zählt nicht als harmful.

| Kategorie | Baseline M2-Head `ea785432` | nach Review-Fixes `98c2c909` | nach M3 `b1c0ae45` |
|---|---|---|---|
| ROUTE (18) | 10 | 10 | 14 |
| NEG (12) | 10 (1 harmful) | 10 (1 harmful) | 12 (0) |
| TIME (10) | 8 | 8 | 10 |
| MULTI (8) | 6 | 7 | 8 |
| FRAME (6) | 3 | 5 | 6 |
| QUALITY (8) | 4 | 4 | 8 |
| MEMORY (12) | 8 | 8 | 12 |
| PLAN (7) | 6 | 6 | 7 |
| **Gesamt** | **55/81, harmful 1** | 58/81, harmful 1 | **77/81, harmful 0** |

Berichte liegen unter `docs/evidence/m3-eval/`. Ausführen:
`./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.cognitive.ContinuumOfflineEvalTest` → `app/build/continuum-eval/m3-report.tsv`.
Ehrliche Einschränkungen: Die Fälle stammen von mir, ein Teil der M3-Fixes zielt auf eval-gefundene Lücken. Das ist kein unabhängiger Held-out-Test.
E12 („Haustiere“↔„Katzen“) besteht nur über das gemeinsame Wort „habe“, nicht semantisch.
Offen und bewusst nicht geändert: R07/R10/R17. Natürliche „Merk dir, dass …“-/„Vergiss …“-Sätze ohne Doppelpunkt schreiben weiterhin
**kein** Gedächtnis, denn Gedächtnisschreiben bleibt aus Sicherheitsgründen an das explizite Format gebunden. R06 („Welches Gerät ist das?“) ist mehrdeutig.

## 3. Aufgabenschleife plan → act (ToolGate) → verify → self-correct
Diese Schleife gab es schon: `BoundedAgentOrchestrator` mit `AgentLimits` (Schritte, Toolaufrufe, ≤ 2 Retries), ToolGate und
`AgentToolApprovalProvider` für jede Nicht-READ_ONLY-Aktion sowie `AgentReflection` bei einer negativen Bewertung. Ich habe **keine zweite Schleife** gebaut.
M3 verbindet die Verifikation mit dem Feldfehler: `LocalDialogueQualityGate` lehnt Antworten ab, die behaupten, es gebe keinen Zugriff, keine Erinnerung
oder keine Angaben, obwohl die früheren Owner-Turns im Auftrag stehen (`priorOwnerContextSupplied`, von `AgentActivity` gesetzt).
Daraufhin versucht der Orchestrator genau einmal eine korrigierte Antwort. Ist der Retry erschöpft, kommt ein strukturierter Blocker statt der falschen Antwort.
Die PLAN-Fälle der Eval prüfen Folgendes: READ_ONLY läuft ohne Rückfrage. CONFIRM und DESTRUCTIVE fragen nach, eine Ablehnung führt zu 0 Ausführungen, eine Freigabe zu genau 1.
Eine Endlosschleife von Werkzeugvorschlägen bleibt begrenzt. Selbstkorrektur greift bei Kontextleugnung und bei Echo.

## 4. Gedächtnis-Retrieval (MemoryEcology / HybridMemoryRetriever)
Das Retrieval faltet Umlaute (ä→a, ß→ss), kürzt konservativ deutsche Suffixe (≥ 4 Zeichen Stamm) und gleicht Komposita an Kopf oder Ende ab
(„termin“ ⊂ „zahnarzttermin“). Das ist ein öffentlich dokumentierter IR-Ansatz für das Deutsche (leichtes Stemming und Dekompositum-Abgleich).
Es wirkt nur auf das Scoring, nicht auf gespeicherte Inhalte. Temporale Begriffe und Stoppwörter werden weiter auf den Rohformen geprüft.

## 5. Gesamtprüfung (`HOST_VERIFIED`, JDK 17, SDK 36)
| Prüfung | SHA | Ergebnis |
|---|---|---|
| Android-JVM-JUnit vollständig | `b1c0ae45` | 1.786 Tests, 0 FAIL, 5 skip |
| `:app:lintDebug` | `b1c0ae45` | No issues found |
| Node, **alle** `.github/continuum/*.test.mjs` | `b1c0ae45` | 1.306 Tests, 1.293 pass, 0 fail, 13 skip |

(In M2 standen dort nur 58 Android-bezogene Node-Dateien. Diesmal lief die gesamte Suite.)
Messdefinition (Review #370/4): Alle Zahlen hier sind deterministische Host-Ergebnisse. Weder Prompt-Gesamtgröße noch Gerätelatenz wurden gemessen.
