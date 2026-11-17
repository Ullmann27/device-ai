# Continuum MASTER – Besitzergeführtes Kontext- und Sprachtraining (23.10.2026)

**STATUS:** Ausführbare Nutzeranleitung zu bestehenden UI-Funktionen, KEIN neuer Sprachmodell-Trainingsalgorithmus und keine Aussage über einen erfolgreichen Fold7-Test. Eigentümerauswahl / explizite Freigabe zwingend.

## Warum ein neuer Trainingsplan nötig ist
- Der Menüpunkt **LERN-GESPRÄCH** in `NeuralLearningCoach.kt` kennt exakt zwei Klassen: **Gruß** oder **Frage**. Max. 12 in deutschen Anführungszeichen gekennzeichnete Kurzbeispiele können als ein Paket (Eingabe max. 1600 Zeichen, einzelner Satz 3–320 Zeichen) eingereicht werden. Die KI fragt nach jedem Beispiel nach einem Label und separat nach der Gewichtetrainingsfreigabe. Das Modell kann höchstens 64 Besitzerbeispiele halten. **Kein freies Dialogverständnis und kein Mehr-Hop-Training!**
- **GROSSES ÜBUNGSARCHIV** trainiert den SWB1-Klassifikator und semantische Vergleiche auf insgesamt 12 eingefrorenen Stufen: 1152 zusätzliche Gruß-/Frage-Sätze + 480 Semantikpaare sowie Holdouts. Nach vollständiger Evaluierung ergibt nochmaliges Drücken keinen neuen Stoff; eine zusätzliche Version müsste implementiert und evaluiert werden.
- **CDX1 · GROSSES PAARARCHIV** ist ein separates Forschungsmodell: 576 synthetische Beleg–Frage–Antwort-Fälle über 12 Stufen, kein produktiver Chat. Keine neuen Sätze ohne einen neuen geprüften Forschungsdatensatz.
- **CRE1 · EIGENES TRAINING** ist heute ein Forschungsdialog mit drei künstlichen Trainings- und zwei Auditfällen, nicht Eigentümer-Freitexttraining. Der Name ist missverständlich.
- **AUTO-ÜBEN** wiederholt nur ausdrücklich bestätigte Sprachbeispiele nach Qualitätsgate; nicht „allgemeines Kontextlernen“. `WISSENSARCHIV` und bestätigtes lokales Gedächtnis sind eine getrennte Daten- und Belegschicht.
- **Merke dir:** im regulären Chat speichert genau einen ausdrücklich bestätigten Satz (3–320 Zeichen). `Prüfe Wissen:` nutzt eine exakte, deterministische 3-Felder-Syntax mit Fakten und Regeln. **Nur bis 32 bestätigte Fakten in einem logischen Prüflauf**. Die Übungen sind kein Beweis freier Sprachgenerierung.

## Eigentümertraining – Training vs Messung unterscheiden

### A · Sprachklassifikation (nur „LERN-GESPRÄCH“)
1. In der Rubrik LERNEN → „LERN-GESPRÄCH · BEISPIEL BESTÄTIGEN“ starten.
2. Als Gruß-Block senden (einmal als Textblock, die Anführungszeichen gehören dazu):

„Servus, ich bin wieder da.“
„Guten Morgen, Continuum.“
„Grüß Gott und einen schönen Tag.“
„Hallo zusammen, wir legen los.“
„Schön dich wiederzusehen.“
„Liebe Grüße aus meiner Werkstatt.“

Nach jedem vorgelesenen Satz **Gruß** antworten; separate vorgeschlagene Modellaktualisierung bestätigen, falls der Satz korrekt erkannt ist und das Gerät die Freigabe anzeigt.

3. Als Frage-Block, wenn die vorige Serie vollständig abgeschlossen ist:

„Welche Tests hat der Coding-Agent bereits bestanden?“
„Warum ist eine Sandbox für die Entwicklung wichtig?“
„Kann Continuum die APK selbst bauen?“
„Was passiert, wenn eine Quelle widersprüchlich ist?“
„Wie unterscheiden sich Gedächtnis und Modellgewichte?“
„Wann soll der Agent einen Patch nur zur Kontrolle anbieten?“

Nach jedem Satz **Frage**; wieder separat bestätigen. Bei 64 bereits belegten Owner-Beispielen weitere Trainingseingaben stoppen statt alte Lektionen zu überschreiben.

### B · Echte Projektfakten als Gedächtnis (normaler Chat, NICHT Lern-Gespräch)
Jeden Satz **einzeln** im normalen Chat senden und dessen Speicherung bestätigen. Keine Zugangsdaten, Adressen, persönlichen Daten oder erfundene Tatsachen:
- `Merke dir: Mein Continuum-MASTER-Projekt soll zuerst als persönliche Android-KI vollständig funktionieren.`
- `Merke dir: Continuum soll eigene Android-Apps über isolierte Tests und eine sichere Buildkette entwickeln.`
- `Merke dir: Geprüfte Reparaturpatches dürfen nicht ungefragt nach main übernommen werden.`
- `Merke dir: Ein lokaler PC kann Continuum später als optionaler Worker ergänzen; Android bleibt die Hauptplattform.`
- `Merke dir: Der erste Test einer erzeugten App soll Projektdateien, Tests und ein überprüfbares APK-Ergebnis liefern.`

Anschließend getrennt fragen: `Was weißt du über Continuum MASTER?` und `Welche Regeln gelten bei Code-Reparaturen?`. Exakte Projektfakten dürfen, wenn bereits gespeichert, nicht sinnlos mehrfach eingetragen werden. Kein Erfolg behaupten, wenn die Antwort keinen Beleg liefert. Immer `Was weißt du?` als Inventar nutzen, bevor neue Aussagen hinzugefügt werden.

### C · Schlussfolgerung / Gegenprobe (testweise, normaler Chat)
Hinweis: Nur durchführen, wenn noch Kapazität für mindestens drei bestätigte Fakten vorhanden ist (max. 32). Dieser Abschnitt verwendet **synthetische Übungsnamen**, keine reale Behauptung.

1. `Merke dir: Fakt: testprojekt42 | ist | appprojekt`
2. `Merke dir: Regel: appprojekt | braucht | tests`
3. `Prüfe Wissen: testprojekt42 | braucht | tests` → erwartet: **ABGELEITET**.
4. `Prüfe Wissen: testprojekt42 | nutzt | internet` → erwartet: **UNBEKANNT**.
5. `Merke dir: Fakt: testprojekt42 | nicht braucht | tests`
6. `Prüfe Wissen: testprojekt42 | braucht | tests` → erwartet: **WIDERSPRUCH**.
7. `Widerrufe: Fakt: testprojekt42 | nicht braucht | tests` → Bestätigungsdialog und **erneute Prüfung** → **ABGELEITET**.
8. Übungsdaten bereinigen: `Widerrufe: Fakt: testprojekt42 | ist | appprojekt` und `Widerrufe: Regel: appprojekt | braucht | tests` jeweils exakt eingeben und bestätigen.
9. Danach erneut prüfen → **UNBEKANNT**. Auf keinen Fall `Vergiss alles` dafür verwenden: würde reale Besitzerfakten betreffen.

### D · Kontext-/Rückbezugs-/Planungsprüfung (normaler Chat, nur MESSUNG, noch kein neuronales Training)
- `Ich plane eine kleine Notiz-App mit zwei Ansichten und privatem Speicher. Welche drei Prüfungen sind vor einer APK nötig?`
- `Welche davon würde uns vor einer kaputten App schützen?`
- `Und wie kann ich die Ergebnisse später unabhängig kontrollieren?`
- `Wir wechseln das Thema: Was ist der Unterschied zwischen einem Modell-Checkpoint und dem Wissensarchiv?`
- `Was haben wir unmittelbar vor dem Themenwechsel geplant?`
- `Denk dir keine Fakten dazu: Welche dieser Aussagen kannst du aus unseren bestätigten Quellen belegen?`

Hier darf Continuum bei fehlender belegter Erinnerung **ehrlich nicht wissen**. Ein Themenwechsel kann den bisherigen engen Kontext beenden. Bei korrekter Antwort Quelle, Zeitpunkt und Status notieren; **nicht automatisch trainieren**.

### E · Gerätebefehle gezielt (normaler Chat, getrennte ausdrückliche Besitzerfreigabe)
- `Trainiere: Bitte zeig mir den Stromvorrat => Akku`
- `Trainiere: Wie eng wird es auf dem Telefon => Speicher`
Nur akzeptieren, wenn das Gerät eine freigegebene **Nur-Lese-Funktion** bestätigt. Beispiele nicht in neuronales Gruß-/Frage-Training mischen. Dieser Mechanismus ist klassisches Mapping, kein Sprachverständnis.

## Trainingstakt und Abnahme
- 1 kurze Besitzerlektion: 6 sichere Gruß- oder Fragesätze **oder** 3–5 geprüfte Projektfakten **oder** ein isolierter Schlussfolgerungslauf. Nicht 40 verschiedene Gewichtsupdate-Dialoge ohne Auswertung.
- Vorher/nachher die tatsächlich gespeicherten Fakten, Gewichts-Hashes, Lern-/Auditentscheidung und erkannte Fehler notieren. Neue Beispiele nicht zugleich als Holdout für sich selbst zählen.
- Von den 24 möglichen Auswertungen mindestens 8 **noch nicht trainierte** Fälle verwenden. Die echten Aufgaben und Antwortqualität sollen außerhalb der Neuron-Klassifikation gemessen werden.
- Jede Fehlantwort sachlich dokumentieren und korrigieren; keine synthetische Aussage als reale Bestätigung übernehmen. Notwendige neue Kategorien (Kontextbezug, Entitätsketten, Chronologie, Quellenkritik, Planerstellung, Dialog) werden als **eigenständige trainierbare Fähigkeit** auf einem isolierten Experimentzweig entwickelt – nicht als imaginärer Effekt von Gruß-/Frage-Lektionen.
- Aktuelle bekannte Speichergrenzen: NeuralDialogue Besitzerlektionen bis 64, strukturierter Wissensprüfer bis 32 bestätigte Fakten, Gesprächskontext bis 8 protokollierte Turns. Für langfristige KI-Fähigkeit müssen diese Grenzen zusammen mit Sicherheits-/Lösch-/Auditfunktionen eigens architektiert werden.

**Keine automatische Promotion von Trainingsbeispielen zu Privilegien, Gerätebefehlen, Belegen oder Modellwahrheiten.**
