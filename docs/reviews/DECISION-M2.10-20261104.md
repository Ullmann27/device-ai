# Entscheidungsvorlage M2.10: Verhalten bei beschädigtem Gedächtnis-Ledger (LEX4)

Stand 04.11.2026, Jarvis Sicherheit (Prüfer). Für diese Vorlage wurde kein Produktivcode geändert. Sie stützt sich auf das Lesen des Codes; Tests oder Builds liefen dafür nicht.
Geprüfter Stand: Branch `continuum/m2-cognitive-memory` @ `de333693ec0a49a3eb68ac616cea6e23ce1190dd` (PR #371, Draft, offen, Basis `continuum/m1-grok-multiturn-routing-20261103`).
Dateireferenzen beziehen sich auf diesen Stand, außer wo eine andere SHA angegeben ist.

Kennzeichnung: **[F]** = belegte Tatsache, **[B]** = Bewertung durch den Prüfer.

---

## 1. Was ist M2.10

- [F] M2.10 ist der Arbeitsschritt "LEX4-Recovery" des Bots Jarvis Gedächtnis auf PR #371. Commits:
  - `bae57986` "WIP: M2.10 RED - LEX4 recovery/rolling-backup tests (compile-red: API missing)"
  - `dc94e325` "WIP: M2.10 recovery + rolling backups implemented; LedgerRecoveryTest 5/5 green, 4 legacy fail-closed tests still red"
  - `117b99e1` "M2.10: LEX4 recovery (verified prefix, timestamped quarantine, LedgerRecoveryReport, restrictive revocation salvage), rolling bak1/bak2, head restore from backup; full suite 1832/0"
  - Folgeschritt `5a84142e` (M2.12): Report wird gespeichert, IO-Fehler bei Backup, Quarantäne oder Report blockieren das Öffnen nicht.
- [F] Eigene Beschreibung des Bots: `TEST_RESULTS.md` Abschnitt "Update 2026-11-04 (M2.10 LEX4-Recovery)" und `docs/research/omega/GROK2-HANDOFF.md` §13 (auf #371).
- [F] Bei M2.10 wurden 4 ältere Tests, die fail-closed verlangten, auf das Recovery-Verhalten umgeschrieben (Diff `117b99e1`):
  - `LearningExperienceStoreTest`: vorher `assertThrows(Exception)`.
  - `LedgerAppendOnlyTest.corruptedCompleteRecordFailsClosed` heißt jetzt `corruptedFirstRecordWithoutBackupIsQuarantinedNeverAdopted`.
  - `ContinuumOwnerFactTest.corruptPrimaryCannotResurrectARevokedFactFromAnOlderBackup`: vorher `assertThrows` für `activeOwnerFacts()` und `replyFromMemory()`.
  - `HistoricalLearningLedgerTest`: angepasst.
- [F] Der Auftrag dazu steht in `/workspace/PLAN-SUPERINTELLIGENZ.md:9`. Darin enthalten ist die Chef-Empfehlung: "fail-closed bleibt Standard, Recovery nur als vom Eigentümer freigegebener Weg; widerrufene Fakten dürfen nie zurückkommen. NICHTS ändern bis Heinz entscheidet."
- [F] Die Abschlussübergabe führt M2.10 als Befund 1 (`device-ai-closeout/docs/CLOSEOUT-JARVIS-20261104.md` §3). Sie hält fest: Der Wechsel von fail-closed zu Recovery ist eine Richtlinienänderung ohne dokumentierte Freigabe durch Heinz.
- [F] Der M2.10-Code liegt nur auf `origin/continuum/m2-cognitive-memory`. Er ist weder in `origin/main` noch in `continuum/m5-integration-repair` (#372) oder `continuum/m5-safe-flows-fg-20261104` (#485) enthalten (`git merge-base --is-ancestor 117b99e1`: NO).

## 2. Ist-Zustand im Code (nur Fakten)

Datei: `app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningExperience.kt`.

**Vorher, @`bae57986`, fail-closed:**
- [F] `loadState()` (Z. 254–263 dort) ruft `decodeLog(bytes, allowTornTail=true)` auf.
  - Ein nur abgeschnittenes Ende ("torn tail") wird gekürzt.
  - Jede andere Beschädigung (Prüfsumme oder ungültiger Datensatz) wirft `IllegalArgumentException`. Das Gedächtnis ist dann nicht nutzbar.

**Jetzt, @`de333693`, automatische Recovery beim Öffnen:**
- [F] `loadState()` (Z. 273–304) arbeitet ohne jede Rückfrage. Bei Beschädigung gilt:
  1. Der verifizierte Ketten-Präfix wird geladen (`scanLog`, Z. 599–627).
  2. Der Rest wird unverändert in die Datei `experiences-v2.quarantine-<Zeitstempel>-<nanoTime>.bin` verschoben (Z. 363–370).
  3. Danach wird die Hauptdatei auf den Präfix gekürzt (Z. 291).
- [F] Ist schon der erste Datensatz beschädigt, holt `restoreFromBackup()` (Z. 306–332) die neueste gültige Sicherung (`bak1`, dann `bak2`) und schreibt sie als Hauptdatei (Z. 316).
  - Gibt es keine gültige Sicherung, startet das Ledger leer. Die Daten liegen dann nur noch in der Quarantäne (Z. 322–329).
  - Ist die Datei gar nicht lesbar und keine Sicherung vorhanden, wird weiter geworfen (Z. 331).
- [F] `salvageRestrictive()` (Z. 334–355) übernimmt aus den **unverifizierten** Frames nur die Status REVOKED oder REJECTED.
  - Das gilt nur für Datensätze, die bereits vorhanden sind und denselben `contentHash` haben.
  - Grundlage ist `salvagePuts()` (Z. 630–640). Es bricht beim ersten ungültigen Längenfeld ab und überspringt nicht lesbare Frames.
- [F] Der Status ist **nicht** Teil des `contentHash`: Z. 43 rechnet `hash(type, provenance, trust, content, annotation, sourceRef)`.
- [F] Ein Widerruf ist ein Status-Update per PUT, kein Löschen (`updateStatus`, Z. 199–211; Aufrufe in `ContinuumLearningCore.kt:581, 750, 758, 779`).
- [F] Die Rotation der Sicherungen (Z. 413–420) sichert die letzten zwei verifizierten Stände. Sie läuft nach jedem vollständig verifizierten Öffnen bei einem Cache-Miss (Z. 282) und alle 256 Anhänge (Z. 468, `ROTATE_EVERY_APPENDS`, Z. 520).
- [F] Der Report wird in `experiences-v2.recovery.properties` geschrieben (Z. 372–392). Er enthält nur Metadaten. Abrufbar ist er über `lastRecovery()` (Z. 395).
  - In `app/src/main` gibt es keinen weiteren Aufrufer von `lastRecovery`/`LedgerRecoveryReport` außerhalb dieser Datei.
  - [F] Die Recovery wird also nur geloggt und in einer Datei abgelegt. Eine Anzeige oder Freigabe in der App ist im Code nicht vorhanden.
- [F] Konnte die Quarantäne nicht geschrieben werden, gilt `unrepaired=true`. Schreibzugriffe werden dann mit `IOException` abgelehnt (Z. 454–455).
- [F] Fehlt die Hauptdatei, aber eine Sicherung existiert, bleibt es fail-closed: `check(...)` in Z. 131 und Z. 262. Das prüft der Test `ContinuumOwnerFactTest.missingPrimaryWithAnOlderBackupCannotStartAnEmptyLedger`.
- [F] Die Hash-Kette beginnt mit `seed() = SHA-256(header())` (Z. 565). Es gibt keinen geheimen Schlüssel. Die Kette erkennt also zufällige Beschädigung, weist aber keine Urheberschaft nach.
- [F] Es gibt ein signiertes Korrektur-Journal (`SignedOwnerCorrectionJournal.kt`, M2.3). In der Produktion ist es nicht aktiv:
  - Der Konstruktor `ContinuumLearningCore(baseDirectory, nowMillis)` setzt `correctionSigner = null` (`ContinuumLearningCore.kt:128`).
  - Alle gefundenen Produktionsaufrufe (`AgentActivity.kt`, `ContinuumDashboardCollector.kt:65`, `ContinuumTrainingMonitorActivity.kt:196`) nutzen diesen Konstruktor.
- [F] Im Recovery-Pfad gibt es keinen Bezug zu ToolGate oder Owner-Freigabe (Funktionen Z. 273–355).
- [F] Zu den Tests (Stand laut `TEST_RESULTS.md`: volle Suite 1845/0/5 bei `8a22399e`; nicht von mir neu ausgeführt):
  - `LedgerRecoveryTest` hat 5 Fälle: Mitte, letzter Datensatz, Kopf mit Backup, Rotation, Lotto-Hypothese.
  - `ContinuumOwnerFactTest.corruptPrimaryCannotResurrect…` beschädigt Byte 40 der Datei, also den ersten Datensatz.
  - In den gelesenen Tests finde ich keinen Fall, in dem genau der Frame mit dem Widerruf beschädigt ist.

## 3. Optionen

### A: fail-closed bleibt strikt (Verhalten wie `bae57986`)
- Inhalt:
  - Bei Prüfsummenfehler oder ungültigem Datensatz bleibt das Gedächtnis gesperrt (Exception).
  - Ein abgeschnittenes Ende darf weiter gekürzt werden.
  - Backups und Quarantäne gibt es nur zur Diagnose, sie werden nie automatisch übernommen.
- Risiken [B]:
  - Widerrufene Fakten: Sie können nicht zurückkommen, weil nichts aus älteren Ständen übernommen wird.
  - Datenverlust: keiner automatisch. Bis zur Reparatur ist das Gedächtnis aber nicht verfügbar. Für die Reparatur gibt es im Code keinen Weg (Hierzu liegen mir keine gesicherten Daten vor, ob ein Export- oder Reparaturpfad außerhalb dieser Datei existiert).
  - Manipulation: Wer Schreibzugriff auf die Datei hat, kann nur sperren (DoS), aber keinen alten Stand einspielen.
  - Prompt-Injection und Owner-Ja: nicht betroffen.
- Aufwand [B]: klein.
  - `loadState()` und `restoreFromBackup()` auf werfendes Verhalten zurückbauen.
  - Die 4 umgeschriebenen Tests wieder auf `assertThrows` stellen.
  - `LedgerRecoveryTest` anpassen.
- Betroffene Dateien: `ContinuumLearningExperience.kt`, `ContinuumOwnerFactTest.kt`, `LedgerAppendOnlyTest.kt`, `LearningExperienceStoreTest.kt`, `LedgerRecoveryTest.kt`, `HistoricalLearningLedgerTest.kt` (alle auf #371).

### B: fail-closed als Standard + vom Eigentümer freigegebener Recovery-Weg (Chef-Empfehlung)
- Inhalt:
  - Beim Öffnen gilt fail-closed wie in A, zusätzlich wird ein Report erstellt.
  - Die vorhandene Recovery-Logik (Präfix, Quarantäne, Backup, restriktive Salvage) läuft erst nach ausdrücklicher Bestätigung durch Heinz.
  - Die Bestätigung kommt aus einer direkten UI-Aktion außerhalb des Chat- und Werkzeugpfads, sodass kein Modell und kein externer Inhalt sie auslösen kann.
  - Vor der Freigabe zeigt die App den Report: verworfene Frames, Herkunft (Präfix oder Backup) und Anzahl übernommener Widerrufe.
- Risiken [B]:
  - Widerrufene Fakten: Das Risiko bleibt nach der Freigabe dasselbe wie bei C (siehe dort). Die Freigabe macht es nur sichtbar und bewusst. Ohne Zusatz erfüllt B die Forderung "nie zurückkommen" nicht.
  - Datenverlust: wie bei C, aber erst nach Bestätigung.
  - Manipulation: Ein bösartig beschädigter Stand wird nicht still übernommen.
  - Owner-Ja: Kann ein Modell-Werkzeug die Freigabe auslösen (über ToolGate mit Owner-Ja im Chat), wäre das ein Angriffspfad für Prompt-Injection ("bestätige die Wiederherstellung"). Deshalb gehört die Freigabe ausschließlich in die UI.
- Aufwand [B]: mittel.
  - Neuer gesperrter Zustand mit eigener API (z. B. eine Freigabe-Methode in `FileLearningExperienceStore`; heute nicht vorhanden).
  - UI-Bestätigung (z. B. in der Lernzentrum- oder Diagnose-Ansicht; konkrete Stelle offen).
  - Tests für Sperre, Freigabe und Ablehnung.
  - Die Recovery-Logik aus `117b99e1`/`5a84142e` bleibt erhalten.
- Betroffene Dateien: wie A, zusätzlich eine UI-Datei (welche, ist nicht festgelegt) und gegebenenfalls `ContinuumLearningCore.kt`.

### C: Status quo M2.10 (automatische Recovery ohne Rückfrage, wie auf #371)
- Risiken [B]:
  - Widerrufene Fakten **können** zurückkommen. Das folgt aus dem Code (Z. 334–344, 630–640), ein Test dafür existiert nicht.
    - Ein Widerruf wird nur gerettet, wenn sein Frame lesbar ist und ein früherer Frame kein ungültiges Längenfeld hat.
    - Ist genau der Widerrufs-Frame beschädigt, zum Beispiel im Status-Feld, das nicht im `contentHash` steckt, dann kann der Frame entweder mit anderem Status gelesen oder gar nicht gelesen werden. Im zweiten Fall wird der Widerruf übersprungen.
    - Der Fakt bleibt dann im Präfix oder Backup als CONFIRMED stehen und wird wieder aktiv.
  - Datenverlust: Bestätigungen nach der beschädigten Stelle gehen verloren (laut Handoff §13 bewusst so). Ohne Backup startet das Gedächtnis leer, die Daten liegen nur noch in der Quarantäne. Das alles passiert still, nur mit Log und Report-Datei.
  - Manipulation: Es gibt keinen geheimen Schlüssel (Z. 565). Wer Schreibzugriff auf das App-Verzeichnis hat, kann die Hauptdatei gezielt beschädigen und damit einen älteren `bak1`/`bak2`-Stand erzwingen, also einen Stand vor einem Widerruf. Die Quarantäne-Bytes, aus denen Widerrufe gerettet würden, kontrolliert der Angreifer dabei selbst. Eingeschränkt wird das dadurch, dass ein solcher Angreifer auch eine gültige Kette neu schreiben könnte, weil die Kette nicht signiert ist.
  - Prompt-Injection: kein direkter Pfad gefunden. Die Recovery ist nicht über Werkzeuge erreichbar.
- Aufwand: keiner (vorhanden). Die Richtlinienfrage bleibt aber ohne Freigabe offen (Closeout Befund 1).

### D (aus dem Code ersichtlich): B + signiertes Korrektur-Journal als Quelle für Widerrufe
- Inhalt:
  - `SignedOwnerCorrectionJournal` (M2.3) wird in der Produktion aktiviert, `correctionSigner` ist heute `null`.
  - Nach einer Recovery wird jeder Eigentümer-Fakt mit dem letzten signierten Stand abgeglichen (`correctionAudit()`, `ContinuumLearningCore.kt:705–706`).
  - Weicht der Stand ab oder fehlt der Nachweis, bleibt der Fakt inaktiv, bis Heinz ihn bestätigt.
- Risiken [B]:
  - Nur dieser Weg kann "widerrufene Fakten nie zurück" auch dann sicherstellen, wenn der Widerrufs-Frame selbst verloren ist.
  - Der Schlüssel und seine Herkunft (`IdentitySigner`) sind eine neue Abhängigkeit. Ob ein produktiver Signer existiert: Hierzu liegen mir keine gesicherten Daten vor.
  - Ist das Journal selbst beschädigt, bleibt nur fail-closed.
- Aufwand [B]: groß (Signer-Bereitstellung, Abgleichlogik, Tests).
- Betroffene Dateien: `ContinuumLearningCore.kt`, `SignedOwnerCorrectionJournal.kt`, `ContinuumLearningExperience.kt`, dazu der Aufbau in der App (`AgentActivity.kt` und weitere Aufrufer von `ContinuumLearningCore(filesDir)`).

Eine einfachere Absicherung ohne Signer wäre B mit dieser Regel: Nach einer Recovery bleiben alle Eigentümer-Fakten aus Präfix oder Backup inaktiv, bis Heinz sie bestätigt. Das ist eine Variante von B, der Aufwand ist mittel.

## 4. Empfehlung [B]

**B** mit der Variante: Eigentümer-Fakten bleiben nach einer Recovery inaktiv, bis Heinz sie bestätigt. D ist ein späterer Ausbau.

- Die Chef-Empfehlung (fail-closed als Standard, Recovery nur mit Freigabe durch den Eigentümer) wird geteilt.
- Abweichung: Die Freigabe allein erfüllt "widerrufene Fakten dürfen nie zurückkommen" **nicht**. Grund ist der in C beschriebene Codepfad: Geht der Widerrufs-Frame verloren, weiß die Recovery nichts mehr vom Widerruf. Deshalb kommt die Zusatzregel dazu, oder später das signierte Journal (D).
- Die Freigabe erfolgt nur per direkter UI-Bestätigung. Ein Modell-Werkzeug oder ToolGate-Owner-Ja im Chat darf sie nicht auslösen, wegen des Prompt-Injection-Pfads.
- Aus M2.10 bleiben erhalten: Quarantäne, Report, rollierende Backups und die Schreibsperre bei misslungener Quarantäne. Sie entsprechen dem Grundsatz "Beweise nicht überschreiben".
- A ist sicherer, aber nicht praxistauglich, weil ein Reparaturweg fehlt. C ist ohne Heinz' Freigabe nicht vertretbar.

Bis zur Entscheidung gilt: Am Code wird nichts geändert. #371 sollte nicht integriert werden, solange die Frage offen ist (Integration informieren).

## 5. Fragen an Heinz (Ja/Nein)

1. Soll fail-closed der Standard bleiben, also das Gedächtnis bei Beschädigung gesperrt werden, bis du entscheidest, und die automatische Wiederherstellung aus M2.10 (`117b99e1`/`5a84142e`) abgeschaltet werden?
2. Soll es einen Wiederherstellungsweg geben, den nur du per direkter Bestätigung in der App-Oberfläche auslöst (nicht per Chat, nicht durch ein Modell-Werkzeug)?
3. Sollen nach einer Wiederherstellung alle wiederhergestellten Eigentümer-Fakten inaktiv bleiben, bis du sie erneut bestätigst, damit ein verlorener Widerruf einen Fakt nie wieder aktiviert?

## Offene Punkte / nicht gesichert
- Ob es außerhalb von `ContinuumLearningExperience.kt` einen Export- oder Reparaturpfad für Heinz gibt: nicht geprüft.
- Ob ein produktiver `IdentitySigner` existiert: nicht geprüft.
- Das Wiederaufleben eines Fakts bei beschädigtem Widerrufs-Frame ist aus dem Code abgeleitet und nicht durch einen Testlauf belegt.
- Der LEX4/LEX3-Konflikt in 5 Dateien (Closeout Befund 3) kann die betroffenen Dateien bei der Integration verändern.
