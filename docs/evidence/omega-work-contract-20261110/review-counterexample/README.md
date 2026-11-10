# Offene Gegenbeispiele aus PR #721

Status: **REJECT_PROMOTION — Forschungszwischenstand, nicht aktivieren.**

Die abschließende Review meldete nach der einen erlaubten Sicherheitskorrektur eine überbreite Referenzerkennung. Die unveränderten finalen Kontextquellen reproduzieren zwei Fehler: Eine sachfremde Uhrzeitfrage wird dem Animationstask zugeordnet; eine Gedächtnisanweisung verliert im Opt-in-Pfad den erwarteten `MEMORY_LITERAL_REQUIRED`-Hinweis. Der standardmäßig deaktivierte Pfad bewahrt diesen Hinweis. Das beweist weder eine ausgeführte unerlaubte Werkzeugaktion noch einen tatsächlichen Gedächtnisschreibzugriff.

Drei Prüfaussagen, zwei Fehler, erwarteter Prozess-Exit 1. Keine weitere Reparaturschleife; der Prototyp bleibt deaktiviert. Rohdaten und Probe bleiben als negative Evidenz erhalten.

Die zweite Review (`PRRT_kwDOVGSW-c6148EP`) ist mit exakt benannten Referenzen ebenfalls reproduziert: „Aber/Also/Lieber/Okay, Bernstein nicht.“ wählt jeweils Bernstein. Fünf Prüfungen einschließlich positiver Namenskontrolle, vier Fehler, Exit 1. Eine erste Fixture verwendete „Animation Bernstein“ ohne Alias und lieferte viermal NoMatch; deren unveränderte Ausgabe bleibt als `unmatched-label-negation-result.txt` erhalten. Zwei vorherige Compileraufrufe scheiterten wegen fehlender/falscher Abhängigkeitspfade; deren Logs bleiben erhalten und sind keine Produktbefunde. Kein Produktcode wurde für diese Diagnose geändert.

Reproduktion vom Repository-Stamm mit Kotlin 2.4/JDK 21. `CONTINUUM_COMPILED_CLASSES` muss auf unveränderlich gesicherte Android-Debugklassen und `CONTINUUM_JSON_JAR` auf die passende org.json-JAR zeigen. Beide finalen Qualitätsproben erwarten Exit 1.

```bash
: "${KOTLINC:?}" "${CONTINUUM_COMPILED_CLASSES:?}" "${CONTINUUM_JSON_JAR:?}"
for probe in ReviewCounterexample NegationReviewCounterexample; do
  "$KOTLINC" app/src/main/java/at/heinz/deviceai/cognitive/{ConversationSituationModel,LocalConversationSituationTracker,LocalConversationContext,WorkingTaskContext}.kt "docs/evidence/omega-work-contract-20261110/review-counterexample/$probe.kt" -jvm-target 17 -Xfriend-paths="$CONTINUUM_COMPILED_CLASSES" -cp "$CONTINUUM_COMPILED_CLASSES:$CONTINUUM_JSON_JAR" -include-runtime -d "/tmp/$probe.jar"
  java -cp "/tmp/$probe.jar:$CONTINUUM_COMPILED_CLASSES:$CONTINUUM_JSON_JAR" "at.heinz.deviceai.cognitive.${probe}Kt"
done
```

Tatsächlich verwendete Abhängigkeiten: `/workspace/scratch/7366d957876c/omega-evaluation-output/dependencies/app-classes` und daneben `json.jar`; compiler `/tmp/continuum-toolchain/kotlinc/bin/kotlinc`, JAVA_HOME `/tmp/continuum-toolchain/jdk`.

Sicherungsregel: Nach jedem sinnvoll abgeschlossenen Arbeitsschritt committen und auf dem jeweiligen Entwicklungszweig sichern; unfertige Teile kenntlich machen, Zweige/PRs offen halten, keine fremden Änderungen überschreiben, keine Zweige löschen, kein Merge nach main ohne Freigabe. Der wissenschaftliche Abbruch beendet dieses Experiment, nicht den Zugriff anderer Agents auf die Zwischenstände.
