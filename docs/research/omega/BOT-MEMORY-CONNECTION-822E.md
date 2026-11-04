# Bot 2: Kalenderkorrektur aus #505 übernommen

Stand: 2026-11-04. Dies ist ausschließlich die Übernahme bereits fertiggestellter Quellen; es wird kein zusätzlicher Retriever aktiviert.

- Branch: `bot/memory-calendar-connect-20261104`.
- Integrationsbasis: `e5c06e6bf657db556bc4861c7f46a55ebcd6e03a` (App-Quellen entsprechend #506).
- Quelle: lokaler, nur gelesener Checkout `/workspace/scratch/7dcf5d78f2cb/continuum-repair`, HEAD `63dec3b1296b1d4257310296de3781d75b7a266a`; abgeschlossener Reparaturcommit `f6ddd0f62afe9c09af7b06f96abdd257c6fab25b` aus #505.
- SHA-256 der unverändert übernommenen Produktionsdatei: `8d095be29157246c8f7dad09e2ae3960519ae816c428dfe399ebcdfe65463e1d`.
- Übernommene Dateien: `ContinuumMemoryRetriever.kt`, `TemporalCueCalendarTest.kt` und `tools/temporal-cue-host-check/run.sh`. Alle drei sind bytegleich zur Quelle.

Die Korrektur verwendet Kalendergrenzen für die vorherige Woche und den vorherigen Monat und behandelt überlaufende Tageszahlen ohne Ausnahme oder falsche Datumsinterpretation. Die 15 bestehenden Kalendertests einschließlich DST- und Überlauf-Fällen werden unverändert mitgeführt.

## Aufrufpfad und offene Grenzen

Der tatsächliche Hauptchat-Abruf läuft bereits in #506 über `cognitive.HybridMemoryRetriever` aus `BoundedAgent` beziehungsweise `AiContextCompiler`. Dieser Port verändert den Aufrufpfad nicht. Der M2.9-Retriever `learning.ContinuumMemoryRetriever` bleibt im Hauptchat inaktiv. Die bekannte Ranking-Regression von 43/44 auf 42/44 bleibt offen; eine Verbesserung des Live-Abrufs wird nicht behauptet. Speicher, bestätigte Fakten und Provenienz werden nicht migriert oder verändert.

## Prüfung und Übergabe

- Lokal ausgeführt: Quell-Hash geprüft und Bytegleichheit aller drei übernommenen Dateien geprüft; `git diff --check` ist der abschließende lokale Prüfschritt.
- Hier nicht ausgeführt: Host-JUnit, Gradle, Android- oder Gerätetests. Kein RED/GREEN-Ergebnis wird behauptet.
- Gemeinsame Tests und Veröffentlichung im Draft #512 übernimmt der Hauptagent. Keine APK erstellt oder installiert; Geräteabnahme fehlt.
- Nächster Schritt: lokalen Commit in den gemeinsamen Integrationsbranch übernehmen und den kombinierten Testlauf ausführen.
- Rollback: ausschließlich diesen Port-Commit auf dem Integrationsbranch revertieren.
- Erlaubnisstatus: die drei Quelldateien und dieser separate Handoff wurden durch den Hauptagenten ausdrücklich freigegeben; kein Push, Merge oder Release durch Bot 2.
