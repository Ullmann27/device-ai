# Unabhängige Methodenprüfung der registrierten Sprachmodellstudie

4. November 2026. Der Autonomie-Forschungsagent las die eingefrorenen Programme und das Protokoll, ohne TEST-Inhalte oder Kandidatenergebnisse zu öffnen. Er führte kein Training aus und änderte keine registrierte Datei. Root prüfte anschließend die tatsächlich entstandenen Rohbelege mit einem eigenen, rein lesenden Programm.

## Befund und zusätzliche Kontrolle

Im Studiendesign wurde keine schwerwiegende Ungültigkeit festgestellt. Im eingefrorenen Analyzer fehlen jedoch ausdrückliche Kontrollen der vollständigen `arm`-/`seed`-Zuordnung und der eindeutigen Bindung jedes Evaluationsreceipts an Modell, Split und Startheader. Insbesondere wäre `all(...)` bei einer leeren Liste von `mixed`-Entscheidungen wahr. Der normale Runner erzeugt diese Metadaten korrekt; das ist eine Lücke der eigenständigen Ergebnisprüfung, kein festgestellter Fehler der ausgeführten Modellrechnung.

Der registrierte Analyzer wurde deshalb nicht nachträglich geändert. `verify_language_results.py` prüfte separat:

- genau fünf erwartete Modellnamen mit den richtigen Arm-/Seed-Paaren;
- gleiche Modellbeschreibungen in Freeze und Abschluss sowie die Bytes aller Checkpoints;
- Kapazitätsbeleg, Ergebniszeile, Exitcode, Loghashes und Checkpointhash für jedes Modell;
- zehn eindeutige Evaluationsreceipts, deren tatsächliche Logs und Startheader einschließlich Datensatz- und Checkpointhash;
- alle 720 Fallbeobachtungen gegen die erwarteten IDs, Prompts, Familien, Antworten und Metadaten;
- freie Korrektheit und beide NLL-basierten Auswahlwerte direkt aus den Rohbeobachtungen;
- genau acht Testentscheidungen, vier pro Score und davon jeweils genau zwei `mixed`-Seeds;
- Erfolgsschwellen und beide aggregierten Entscheidungen erneut, ohne weitere Modellinferenz.

**Ergebnis: alle Kontrollen bestanden. Beide vorregistrierten Haupterfolgskriterien bleiben falsch.** Der vollständige Beleg steht in `language-independent-audit.json`.

## Aussagegrenzen

1. Die Aufgabe prüft gültige Besitzübergaben in einer festen Grammatik und auf zurückgehaltenen Objektpaaren. Sie prüft weder freie allgemeine Sprache noch den Umgang mit ungültigen oder widersprüchlichen Übergaben.
2. Eine passende Regel über den letzten Empfänger des erfragten Objekts könnte die Aufgabe lösen. Selbst ein Erfolg würde keinen bestimmten internen Mechanismus oder eine vollständige Weltzustandssimulation beweisen.
3. Die kritische Teilmenge fasst zwei Familien zusammen. Die Schwelle von 50 Prozent in dieser gemeinsamen Teilmenge würde allein nicht beweisen, dass beide Familien beherrscht werden. Die Ergebnisse je Familie bleiben daher sichtbar.
4. Die beiden Seeds ändern die Trainingsreihenfolge, nicht die initialen Modellgewichte. Testmenge und Startcheckpoint sind gemeinsam; das ist kein Vergleich unabhängiger Modellinitialisierungen.
5. Freie Antwort und Auswahl im bekannten Alphabet A–D sind unterschiedliche Messungen. Die NLL-Ausrichtung wurde am originalen `LmModel.nll` geprüft und ist konsistent; EOS wird separat ausgewertet.
6. Die Schwellen sind vorab festgelegte deskriptive Kriterien eines kleinen Piloten. Sie sind keine statistische Garantie für neue Aufgabenpopulationen.

Die Methodenprüfung begründet keine zusätzliche Kandidatenfreigabe, kein Nachtraining und keine Änderung der registrierten Schwellen.
