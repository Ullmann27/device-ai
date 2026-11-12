# Code55: bestehender Live-Anschluss repariert

Quellcommit: `97bb5767c77c36db434ab33ddbb21af351eee9cc`; gemeinsamer Suchrollen-Anschluss `627f2b3b593737b3f6659524baaa1b7ba29329ea` enthalten. Integration: Parent `/root` über die bestehende Code55-Linie; eigenen Branch-HEAD übernehmen, da Produktionsänderungen schon im WIP-Vorgänger `4f4ad20357` enthalten sind. Keine neue Entwicklungslinie oder Experiment-ID.

## Vorhandene Verbindung

`ContinuumScreenObservationService` empfängt Text-/Scrollereignisse im bestehenden `ScreenObservationBus`. `AndroidLiveNodePort` bindet unmittelbar vor dem einmaligen Dispatch den ausgewählten Container an Task, Epoch, Revision und Fenster. Nur dessen frisches Scrollereignis mit gemessener Bewegung erreicht den `VerifiedNodeAction`-Verifier. Die Nachbeobachtung muss weiterhin vollständig sein und einen geänderten Baum besitzen. Textänderungen wecken dieselbe Nachbeobachtung; der erwartete Feldinhalt wird weiterhin exakt per Digest geprüft.

Native Ereignisquellen werden sofort freigegeben. Der eine geklonte Scrollknoten wird bei Operationsende, Dienstwechsel, Interrupt und Disconnect freigegeben. Unbekannte absolute Ausgangswerte bilden kein Bewegungsdelta. Androids unbekanntes Delta `-1` wird nur über ein gültiges absolutes Messpaar ersetzt.

Die vorhandenen Fenster-/Journalfehler sind geschlossen: andere Window-ID bestätigt keine Aktion; unsichere Fingerprints werden bei voller Kapazität nicht vergessen. Nach Überlauf bleibt das bestehende Task-Journal gesperrt. Suchrollen-Anschluss und Berechtigungsprüfungen bleiben erhalten.

## Tatsächlich geprüft

- Direkter Kotlin-Lauf: **36/36 PASS** (6 alte Supportregressionen, 11 Frames, 13 Actions, 6 VerifiedNodeClick).
- Direkter Android-/Robolectric-Lauf: **47/47 PASS** in den drei Klassen aus `manifest.json`; eigene eingefrorene Quellen zuerst im Klassenpfad.
- Negative Fälle: fremder Knoten, fremdes Fenster, altes Ereignis, Stillstand, unbekanntes Delta, unbekannte absolute Ausgangsposition, widerrufene Epoch, ungeeignete Bewegungsbelege, Journalüberlauf. Positive Scroll-/Text-/Suchrollenpfade bleiben grün.
- Quellhashes nach den Läufen gegen den Commit abgeglichen; `git diff --check` ohne Fehler. XML syntaktisch geparst, Text-/Scrolltypen vorhanden.
- **Gradle-Gesamtintegration: NOT_RUN durch diesen Agenten.** Parent bündelt den Lauf unter gemeinsamer Sperre. Der direkte Androidlauf nutzt vorhandene Fremdkompilate nur als Umgebung und enthält keine App-Ressourcen-/Manifest-Abnahme.
- **Fold7: NOT_RUN. Veröffentlichung: NOT_PUSHED durch diesen Agenten.**

## Nächster bestehender Prüfschritt

Kanonischer Integrator führt `:app:testDebugUnitTest -PcontinuumUiWidgetTests=true` für `AndroidLiveNodePortTest`, `ScreenObservationRevisionTest`, `NativeClickCandidateTest`, `cognitive.LiveComputerActionsTest` und die bisherigen Nachbarn unter der gemeinsamen Gradle-Sperre aus. Danach vorhandenen Fold7-Pfad prüfen: Scroll nach Sichtfreigabe, Textentwurf, fehlender Bewegungsbeleg, Stop/Widerruf.

Rückfall: nur die sieben vereinbarten Produktions-/Testdateien der Anschlussreparatur zurücknehmen; den gemeinsamen Suchrollen-Commit erhalten. Keine Änderungen an Capture, GPU, Activity, Controller oder AndroidLiveComputer durch diesen Agenten.
