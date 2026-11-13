# Code55: Zweigabgleich und gezielte Übernahme

## Einstieg und Zuständigkeit

- Repository: `Ullmann27/device-ai`.
- Integrator: Root; gemeinsame Linie `integrate/code55-apk-fable-20261112`.
- Geprüfte Cockpitbasis: PR #799, `585d660a1c8ab8ca31822daae1714f67eec9d107`.
- Lokaler Integrations-HEAD vor diesen Änderungen: `2e0668165caf29d9ef000a40d870866c1f466107`.
- Fable #808 und Hands-free #793 werden vom Integrator und den anderen Helfern betreut.

Diese Übernahme verändert bestehende Aufrufpfade. Es entsteht weder ein weiterer
Renderer noch ein zweiter Scroll-, Lern- oder Ereignispfad. Betroffene Register:
EXP-05, EXP-07, EXP-11 und EXP-12. Forschungs-/Besitzergrenzen bleiben erhalten.

## Tatsächliche Abdeckung der aktuellen Kandidaten

Die GitHub-Metadaten und Compare-Ergebnisse wurden am 12.11.2026 gelesen.
Ein offener PR wurde nicht allein wegen seines Status als fehlend behandelt.

| Linie | Befund gegenüber #799 `585d660a` | Entscheidung |
|---|---|---|
| #791 | Basis `1b90611388cc2b1978ddfe32064a25dd3a08cee1` enthalten | Bewahren |
| #800 | HEAD `b9de06b7`, Compare `behind_by=0` | Enthalten |
| #801 | Originalavatar/Fünfringe/Unified-Szene, Merge `5f87bdc6` | Enthalten |
| #803 | Sichtbarer Monitor, Capture-/Viewport-/Eingabebindung, Merge `711f23b3` | Enthalten |
| #804 | Merge ist `585d660a` | Enthalten |
| #805 | HEAD `41248190`, Compare `behind_by=0` | Enthalten |
| #806 | HEAD `adbd4e72`, Compare `behind_by=0` | Enthalten |
| #807 | HEAD `16ce5bdb`, Compare `behind_by=0` | Enthalten |
| #787 | HEAD `61f1a06e`, Compare `behind_by=0` | Thermik-Aufzeichner enthalten |
| #788 | HEAD `a22d62cf`, Compare `behind_by=0` | Diagnoseauswertung enthalten |
| #793 | Readiness-/Foreground-Recovery fehlte im tatsächlichen Produktcode | Separater Android-Port |
| #796 | Missing-Evidence-Kontrollen und ihr Lab-Aufruf fehlten | In dieser Runde portiert |
| #802 | Formal divergiert, zahlreiche Änderungen bereits übernommen/ersetzt | Nur drei fehlende Schutzdetails portiert |
| #798 | MCP-Testreparatur und Übergaben, kein Produktcode | Separater Testkandidat |
| #794 | Passives Qualitäts-Experiment, keine normale Runtime-Änderung | Keine automatische Aktivierung |
| #789 | Historisches Code54-CI-Gate für bekannte SKIPs | Kein APK-Produktpfad |

Dies ist der dokumentierte Abgleich dieser Kandidaten; kein Anspruch einer
vollständig abgeschlossenen Prüfung sämtlicher älterer Repository-Zweige.

## CDX1 aus #796

Quelle: `9dc1910357deace7bf47d8c7847b2ea4398ae15c`.
Die vier betroffenen Dateien stimmten vor dem Port bytegenau mit dessen Basis
`caba9c83d13a9dc25b74da5ef9a4c0317ef5e7c8` überein. Der entsprechende Git-Diff
wurde geprüft und angewandt; lediglich zwei neu hinzugefügte Kommentare wurden
gekürzt. Die Workflow-Dateien dieses PR wurden nicht übernommen oder gestartet.

- `ContinuumCdxLargeCurriculum.validationAudit` ergänzt die bestehenden 48 Fälle
  um fünf getrennte Fragen ohne ausreichenden Beleg; erwartete Antwort `unbekannt`.
- `ContinuumCdxLargeLab.next()` verwendet diesen Audit sowohl für die strukturelle
  Vorprüfung als auch für die zwei bestehenden Evaluationsaufrufe.
- Trainingsdaten, Rehearsal, historischer Audit, Stage-Cursor, Version und
  Annahmeschwellen bleiben erhalten. Kein Training wurde hier gestartet.
- Die zwei portierten Testklassen prüfen Trennung, bestehende Auditfälle,
  gültige Abstention und Ablehnung geratener Antworten.

Status dieser Helferarbeit: **PORTIERT**; Kotlin/Android-Ausführung durch den
Integrator ausstehend; **GERÄTEBESTÄTIGT: NEIN**.

## Restschutz aus #802 auf dem aktuellen Scrollpfad

Quelle des Vergleichs: `c1d41bcdce73b42cbce3b77920ecd86360b379fe`.
Die alten `repair.patch`-Dateien wurden nicht angewandt. Der moderne
`ScrollWatch`-/`LiveScrollDispatch`-Pfad mit Capture-Sitzung, Node-Identität,
Zielfingerprint und Render-/Viewportschutz bleibt erhalten.

1. Content-, Text- und Scrollereignisse reichen ihre Window-ID an
   `contentChanged` weiter. Bei bereits gebundener Fenster-ID beeinflusst eine
   bekannte andere Fenster-ID derselben App den laufenden Read nicht.
   Passende und unbekannte IDs behalten die bisherige Invalidierung.
2. Scrollereignisse aus der Zukunft erzeugen keinen Bewegungsbeleg.
3. `Int.MAX_VALUE` als Delta oder Positionswert erzeugt keine Bewegung;
   gültige Bewegungen anderer Achsen und normale Rückwärtsbewegung bleiben gültig.

Neue Regressionen liegen in `LiveScrollEvidenceTest`, `AndroidLiveScrollTest`
und `ScreenObservationRevisionTest`. Sie prüfen die echten vorhandenen
Produktklassen; die Android-Grenze ist dabei synthetisches Robolectric.

Bereits übernommene #802-Arbeit wurde erhalten: Stop bei Sitzungswechsel,
Foreground-Service-Reihenfolge, gebundener Stop-PendingIntent, Journalüberlauf,
auftragsbezogene Avatar-ID, Workflow-Revisionen, Pitch/Z, indirektes Licht,
Animator-Lifecycle und lesbare Layouts. Die aktuelle Monitorfahrt aus gebundenen
Work-Events wird nicht durch den älteren Feed-Start ersetzt.

Status dieser Helferarbeit: **PORTIERT**; neue Regressionen **NOT_RUN** durch
diesen Helfer; **GERÄTEBESTÄTIGT: NEIN**.

## Prüfung und nächster Schritt

- `git diff --check`: PASS vor Übergabe.
- CDX1-Port: Basisbytes aller vier Dateien vor Anwendung verglichen, PASS.
- Keine eigenen Gradle-Läufe, Commits, Staging- oder Remote-Schreibaktionen.
- Root führt die gemeinsame Android-Prüfung seriell auf dem eingefrorenen Stand aus.

Betroffene Prüfklassen:

- `at.heinz.deviceai.learning.ContinuumCdxLargeCurriculumTest`
- `at.heinz.deviceai.learning.ContinuumCdxLargeCurriculumCycleTest`
- `at.heinz.deviceai.cognitive.LiveScrollEvidenceTest`
- `at.heinz.deviceai.AndroidLiveScrollTest`
- `at.heinz.deviceai.ScreenObservationRevisionTest`

Ergänzend bestehende Nachbarn: `DecoderGenerativeGateTest`,
`LiveComputerActionsTest`, `LiveSupportBoundaryTest` und `AndroidLiveNodePortTest`.
Der Integrator ergänzt hier oder in seiner gemeinsamen Abnahme den tatsächlichen
Quell-SHA, die Resultate und den APK-Bezug. Ein erfolgreicher Hostlauf ersetzt
keine Fold7-, Mikrofon-, Filament- oder Laufzeitmessung.

Rücknahme nur über den gezielten Diff der hier genannten Dateien; kein Reset
des gemeinsamen Arbeitsbaums und kein Verwerfen paralleler Grafik-/Spracharbeit.
