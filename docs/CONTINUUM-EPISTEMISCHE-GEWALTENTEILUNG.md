# Epistemische Gewaltenteilung – ausführbare Continuum-Architektur

Stand: 18. Oktober 2026. Konkretisierung der Chronik einer souveränen Maschine, getrennt von literarischen Aussagen über bereits erreichte Autonomie.

## 1. Besitzer und Herkunftsbelege

ContinuumLearningCore.kt und ContinuumMemoryDialogue.kt bleiben zuständig für bestätigte Nutzerfakten, Quellenrechte und Widerrufe. CRE1-Punktwerte oder Modelltexte dürfen keine eigenen OWNER_CONFIRMED-Einträge erzeugen.

Neu: CognitiveDecisionBridge.kt verwendet die unveränderten Roh-Belege genau eines ContinuumCognitiveTurn. Unbelegte persönliche Fragen werden deterministisch beantwortet; das Sprachmodell wird dabei nicht aufgerufen. Zahlen, Negationen, Originalanker und zusätzliche, nicht belegte großgeschriebene Entitäten werden in der begrenzten lexikalischen Prüfung kontrolliert. Zwei zusätzliche Kotlin-Negativtests liegen vor. Die Heuristik ersetzt keinen semantischen Wahrheitsbeweis.

## 2. Neuronale Intuition ohne Freigaberecht

SWB1/CRE1 dürfen nur suchen, vorschlagen und innerhalb bereits autorisierter Kandidaten umsortieren. Der Entwicklungsranker cefr-repair-ranker.mjs nutzt separate Gewichte cre1-dev-repair-v1.cre1; er verändert die produktive repairSession()-Reihenfolge NICHT. Ein Research-Promotion-Schritt braucht nicht nur Fortschritt auf dem Trainingsbeispiel, sondern messbar bessere Ergebnisse auf einem getrennten, mindestens vier Fälle umfassenden Holdout.

## 3. Der deterministische Richter – unabhängiger Testbeweis

cefr-independent-verifier.mjs kontrolliert einen sauberen Checkout und den exakten Basis-Commit, eröffnet einen zweiten Git-Worktree, reproduziert einen ursprünglichen Testfehler, führt bis zu drei Kritiker-zugelassene negative Kandidaten auf dem Basisstand tatsächlich aus und sammelt ausschließlich unabhängig reproduzierte FAILED_TESTS-Kandidaten. Nach erneutem Reset wird der erfolgreiche Patch mit git apply --check angewandt. Die geänderten Dateien müssen mit den freigegebenen Pfaden übereinstimmen. runChain() und evidenceSufficient() entscheiden anhand der gerade ausgeführten Tests statt anhand behaupteter Testzahlen.

cefr-research-cycle.mjs verknüpft diese Beweise mit copy-on-write-Training. Sein Standard ist promote=false, also reine Schattenbeobachtung. Ungesicherte Kandidaten, fehlende Testausführung, ungültige Baselines und unverifizierte Negativbeispiele dürfen keine Gewichte verändern. Kein neuronal erzeugter Callback darf als unabhängiger Prüfer gelten.

Grenze: Ein Git-Worktree ist keine Betriebssystem-Sandbox. Lokale Testausführung muss unter einem vertrauenswürdigen, eingeschränkten OS-Konto erfolgen. SHA-256 bestätigt Inhalt, keine Autorschaft oder Signatur.

## 4. Evolution durch geprüftes Copy-on-Write und LKG

cefr-dev-checkpoint.mjs schreibt in einen geschützten Ordner außerhalb des Git-Checkouts. Eine Promotion verlangt einen gültigen CRE1-Snapshot, exakten bisherigen Checkpoint-Hash und eine exklusive Lockdatei. Die vorherige aktive Modellversion wird als .lkg gesichert, bevor ein neuer Stand atomar den aktiven ersetzt. restoreCefrLastKnownGood() erlaubt die explizite Wiederherstellung mit dem SHA-256 des aktiven Zustands, nicht durch das Modell selbst.

CRE1.save() beinhaltet Modellgewichte und Steps, aber nicht die gesamte persistierte Adam-Historie. Wiederhergestellte Instanzen erhalten daher einen neuen Optimiererzustand. Ein vollständiger Beweis fortgesetzter Adam-Momente wird ausdrücklich nicht behauptet.

## 5. Durchsetzung am realen Entwicklungs-Worker (Ledger und Shadow)

Die Trennung aus Vorschlag, Prüfung und Lernen gilt jetzt explizit auch für den periodischen Teamleader:

- `recordSession()` in `repair-ledger.mjs` unterscheidet **drei Zustände**. `independentPass:true` erlaubt eine positive Lernerfahrung; `independentPass:false` verbucht einen unabhängigen Fehlschlag negativ; bei fehlendem oder ungültigem Nachweis lautet das Sitzungsergebnis `PENDING_INDEPENDENT_VERIFICATION`. Solche vorläufigen Sitzungen erhalten **weder positive noch negative Modell-Trainingssignale**. Die ursprünglichen Diagnoseversuche bleiben zur Fehlersuche erhalten.
- `repairSession()` akzeptiert den optionalen `shadowObserver`. Der Beobachter erhält Kopien der Kandidaten, Erfahrungen und Diagnosen, nie die aktiven Objekte. Seine Rangliste darf destruktive Risiko-Stufen nicht vor nichtdestruktive Reparaturen ziehen. Bei Ausnahme oder ungültiger Rangfolge wird die Schattenbeobachtung verworfen; **die tatsächlich ausgeführte Kandidatenreihenfolge ist identisch zur bisherigen Implementierung**.
- `localTeamleaderDriver().repair()` lädt bei vorhandenem externen Entwickler-Checkpoint den CEFR-Observer. Sein Modell darf das produktive Ranking nicht verändern und keinen Patch autorisieren. Ein fehlender Checkpoint deaktiviert nur die Beobachtung.
- Ein intern erfolgreicher Reparaturkandidat wird nun in einem **zweiten frischen Git-Worktree** per `git apply --check`, `git apply` und einer erneuten `runChain('node')`-Ausführung überprüft. `validateVerifiedReport`, `evidenceSufficient` sowie tatsächlich ausgeführte Tests sind erforderlich. Beide Prüfläufe teilen sich dasselbe lokale Zeitbudget.
- Schlägt diese unabhängige Abnahme fehl, schreibt der Teamleader **keinen öffentlichen `VERIFIED`-Ergebnisbericht**, liefert `NO_VERIFIED_REPAIR` zurück und speichert über den Ledger ausschließlich die abgelehnte Lernentscheidung. `main` wird niemals automatisch verändert.

Neue gezielte Regressionen sind `repair-ledger-independent.test.mjs` und `cefr-shadow-integration.test.mjs` sowie zwei Zusatzfälle in `autonomous-teamleader.test.mjs`. In der laufenden Entwicklung wurden **6 Ledger- und 5 Shadow-Prüfungen gegen die Originalquellen im V8-Isolat erfolgreich ausgeführt**; die übrigen Teamleader- und Repository-Tests erfordern noch einen vollständigen lokalen Node-/Git-Checkout.

## Abnahmekette auf dem lokalen Entwicklungsrechner

~~~bash
node --test .github/continuum/cefr-repair-ranker.test.mjs \
  .github/continuum/cefr-independent-verifier.test.mjs \
  .github/continuum/cefr-research-cycle.test.mjs \
  .github/continuum/cefr-dev-checkpoint.test.mjs \
  .github/continuum/cegis-critic-safety.test.mjs
node --test .github/continuum/*.test.mjs
gradle :app:testDebugUnitTest --console=plain -Pkotlin.incremental=false
~~~

Bisherige Nachweise dieser Runde: Syntaxprüfung der acht JS-Dateien direkt vom GitHub-Stand und drei simulierte Verifier-Abläufe, darunter fail-closed bei nicht reproduzierbarem Ausgangsfehler. Die vollständigen echten Node-, Kotlin- und APK-Tests wurden in dieser Sitzung nicht ausgeführt. Die hinzugefügten Git-Fixture-Tests sind noch auf dem lokalen Buildrechner auszuführen.

## Verbleibende Produktionsgrenzen

CEFR ist nicht in die echte Reparaturstrategie-Auswahl geschaltet. CognitiveDecisionBridge ist nicht als neuer Standard im Android-Chat-Router aktiviert. Ein reales lokales LiteRT-LM-Gewicht und ein Android-Geräte-/APK-Nachweis fehlen. Die von Sol gemeldeten elf CEGIS-Dateien sind noch nicht als GitHub-Commit verfügbar.

Ein messbarer nächster Meilenstein ist: unabhängige Reparatur auf bisher ungesehenen Aufgaben, reale Negativ- und Positivtests, bestandene Holdouts, wiederherstellbares Modell und ein über mehrere Fälle statistisch besseres Shadow-Ranking gegenüber der bisherigen deterministischen Reihenfolge. PR #28 bleibt Draft; main wird nicht automatisch geändert.
