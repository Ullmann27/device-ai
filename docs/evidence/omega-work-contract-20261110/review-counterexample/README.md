# Offene Gegenbeispiele aus PR #721

Status: **REJECT_PROMOTION — Forschungszwischenstand, nicht aktivieren.**

Die abschließende Review meldete nach der einen erlaubten Sicherheitskorrektur eine überbreite Referenzerkennung. Die unveränderten finalen Kontextquellen reproduzieren zwei Fehler: Eine sachfremde Uhrzeitfrage wird dem Animationstask zugeordnet; eine Gedächtnisanweisung verliert im Opt-in-Pfad den erwarteten `MEMORY_LITERAL_REQUIRED`-Hinweis. Der standardmäßig deaktivierte Pfad bewahrt diesen Hinweis. Das beweist weder eine ausgeführte unerlaubte Werkzeugaktion noch einen tatsächlichen Gedächtnisschreibzugriff.

Drei Prüfaussagen, zwei Fehler, erwarteter Prozess-Exit 1. Keine weitere Reparaturschleife; der Prototyp bleibt deaktiviert. Rohdaten und Probe bleiben als negative Evidenz erhalten.

Reproduktion vom Repository-Stamm mit Kotlin 2.4/JDK 21:

```bash
kotlinc app/src/main/java/at/heinz/deviceai/cognitive/{ConversationSituationModel,LocalConversationSituationTracker,LocalConversationContext,WorkingTaskContext}.kt docs/evidence/omega-work-contract-20261110/review-counterexample/ReviewCounterexample.kt -include-runtime -d /tmp/omega-review-probe.jar
java -jar /tmp/omega-review-probe.jar
```

Sicherungsregel: Nach jedem sinnvoll abgeschlossenen Arbeitsschritt committen und auf dem jeweiligen Entwicklungszweig sichern; unfertige Teile kenntlich machen, Zweige/PRs offen halten, keine fremden Änderungen überschreiben, keine Zweige löschen, kein Merge nach main ohne Freigabe. Der wissenschaftliche Abbruch beendet dieses Experiment, nicht den Zugriff anderer Agents auf die Zwischenstände.
