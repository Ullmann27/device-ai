# Device AI Continuum – Autonomous Knowledge Frontier v1
Stand: 20.10.2026. Experimentbranch / Draft Pull Request #42.

## Forschungsgrundlage
- Lewis et al., "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks"
  https://arxiv.org/abs/2005.11401
  -> Fakten und zeitabhängige Quellen zuerst abrufen, nicht in 4.113
     trainierbare SWB1-Parameter pressen.
- ContinualAI Avalanche (MIT) https://github.com/ContinualAI/avalanche
  -> Rehearsal, getrennte Testaufgaben, Forgetting-Metriken und
     reproduzierbare Trainingsvergleiche.
- Mammoth Continual Learning https://github.com/aimagelab/mammoth
  -> Vergleich von Replay- und Regulierungsverfahren in PyTorch; nicht
     direkt als Kotlin/Android-Code zu übernehmen.
- Wikimedia MediaWiki API (prop=links)
  https://www.mediawiki.org/wiki/API:Links
  -> Von einem gelesenen Artikel begrenzt neue sachliche Quellthemen
     entdecken, statt denselben festen Themenkatalog zu wiederholen.
- Android JobInfo Periodic
  https://developer.android.com/reference/android/app/job/JobInfo
  -> Mindestintervall ca. 15 Minuten, keine zeitliche Garantie,
     Lade-/Batterie-/Netzwerkbeschränkungen.
- Wikimedia API usage and 2026 rate limits:
  https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits
  -> angemessener User Agent, nur sequentielle Requests,
     HTTP 429/503 mit Retry-After; nicht aggressiv crawlen.

## In dieser Entwicklungsstufe integriert
1. `ContinuumTopicFrontier`: autonom gewählte Folgethemen aus
   Wikipedia-Links, 48 kuratierte Startthemen, persistent bis 128
   wartende und 384 zuletzt untersuchte Themen. 3 von 4 geplanten
   Durchgängen priorisieren Neufunde; jeder vierte führt zu einem
   kuratierten Ausgangsthema zurück. Doppelungen und ungültige Titel
   werden abgewiesen.
2. `ContinuumPublicStudyPolicy` begrenzt dynamisch gewählte Titel
   auf sichere Artikelnamen ohne eingebettete Hosts, API-Parameter,
   Pfade oder MediaWiki-Sondernamensräume.
3. `ContinuumPublicStudyFetch.discover`: pro Durchgang höchstens
   eine zusätzliche offizielle Link-API-Abfrage mit maximal 16
   Linkvorschlägen. Keine Redirects, keine beliebigen HTTP-Hosts,
   bytebegrenzte Antwort. Ausfall der Linkentdeckung bricht eine
   gültige Archivierung nicht automatisch ab.
4. `ContinuumPublicStudyScheduler`: dauerhaft gespeicherter
   Themenstand, Zähler für Warteschlange und erkundete Titel,
   Retry-After-Backoff nach Wikimedia 429/503, ein ca.
   15-minütiges periodisches Jobfenster mit Ladung/Internet und
   Geräte-Ressourcenschutz. Android darf beliebig verzögern. Der
   Nutzer muss den Dienst **einmal** ausdrücklich aktivieren und
   kann ihn jederzeit deaktivieren. Wird er bereits auf einer
   älteren APK freigegeben, sollte die aktualisierte App den Job
   nach dem Neustart erneut registrieren.
5. `ContinuumKnowledgeArchive`: bestehende quellengebundene
   Hashdateien und SAF-Backup bleiben; Kapazität von 640 auf
   4.096 Versionen erhöht, bei weniger als 128 MiB freiem
   Speicher werden neue Schreibvorgänge abgelehnt, statt das
   Smartphone zu füllen.
6. `AgentActivity.runContinuumDialogue`: nur bei nicht schon
   bestätigter Antwort werden lokale Quellen gesucht; nur ein
   starker Treffer im Titel ergibt einen ausdrücklich als
   **unverifizierten Quellenauszug** gekennzeichneten Abruf mit
   Original-URL, Herkunft und SHA-256. Es gibt dadurch keine
   automatischen Geräteaktionen, Owner-Fakten oder freie
   Textgenerierung. Qwen3 bleibt separat.
7. Das bisher unklare Trainingsurteil "Holdout verschlechtert"
   wird um konkrete Testergebnisse, alten/neuen Verlust und
   die tatsächlich fehlschlagenden Prüfsätze erweitert.
8. Die Menüpunktzuordnung des vorhandenen `GEWICHTE / LABOR`-
   Untermenüs wurde korrigiert: RIS an 6, Archivbackup an 7,
   großes Trainingsarchiv an 8.

## Bewusst noch nicht umgesetzt
- Unbegrenztes Crawling des gesamten Internets: Providerlimits,
  Datenschutz, Nutzungsrechte und Akkuschutz verhindern das.
- Modellgewichte ohne unabhängige Kontrollgruppen freigeben.
- Vollständiger SQLite-FTS-/Vektorindex für viele zehntausend
  Quellen. Aktuell sind 4.096 Quellversionen mit linearer Suche
  der nächste überprüfbare Ausbauschritt.
- Freies lokales Sprachgespräch: der separate LiteRT-LM/Qwen3-
  Fehler `Local generation cancelled` wurde nicht dadurch behoben.
- Offizielle automatische RIS-Gesetzes- und Judikatur-Synchronisierung;
  lokale RIS-Auszüge bleiben zunächst manuell dokumentiert.

## Prüf- und Installationsplan
1. Bei aktiviertem alten Lernkern und Internetforschungs-Opt-in:
   derzeitige Lernstände in LERNKERN → DETAILS notieren.
2. Im Experimentbranch die Node-Tests
   `autonomous-topic-frontier.test.mjs`,
   `continuum-growth-and-archive.test.mjs`,
   `research-job-permission.test.mjs` und den bisherigen
   `local-model-smoke.test.mjs` ausführen.
3. Über den vorhandenen Termux-Builder Gradle Unit-Tests,
   Android-Lint und signierte APK erstellen; Installations-/
   Signaturabgleich durchführen. Keine Deinstallation!
4. Im aktualisierten Lernzentrum →
   `AUTONOME INTERNETFORSCHUNG · EIN/AUS` →
   `PLANUNG PRÜFEN`. Erwünscht:
   `Android-Forschungsjob: REGISTRIERT`;
   Sichtbarkeit von wartenden Themen und nächstem Forschungstitel.
5. Gerät laden, WLAN oder mobile Daten verbunden lassen;
   echte Ausführung kann wegen Doze/Quoten später erfolgen.
   Nach dem ersten echten Durchgang: `Versuche`, `neue Quellen`,
   `WISSENSARCHIV · QUELLEN SUCHEN` prüfen. Ein erfolgreicher
   Job ist nicht automatisch ein akzeptiertes Gewichtstraining.
6. Im normalen Chat zu einem bereits archivierten Thema eine
   Sachfrage stellen. Erwartet: lokaler Quellenhinweis mit
   URL/Herkunft, keine ungeprüfte ausformulierte Wahrheit.
7. Nur eine weitere `GROSSES ÜBUNGSARCHIV`-Stufe prüfen,
   um die erweiterte Holdout-Diagnose zu validieren. Keine
   vollständigen zwölf unkontrollierten Runden.
8. `WISSENSARCHIV · BACKUP / WIEDERHERSTELLEN`
   für expliziten lokalen oder Cloud-Dokumentexport verwenden.

## Echte Abnahme offen
Neue Kotlin Unit- und Node-Regressionsfälle liegen im
Experimentbranch. Ohne aktuellen Gradle-Build und realen Samsung-
Durchgang darf **nicht** behauptet werden, dass diese Änderungen
bereits installiert, ausführbar oder in Android erfolgreich
getestet sind. Keinen Commit nach Main/Production mergen.
