# G1: kontrastiver Evaluationsvertrag, vor der Aufgabenkonstruktion

Status bei Registrierung: **RESEARCH_SHADOW / Aufgaben noch nicht erstellt /
Modelllauf NOT_RUN**. Einziger Meilenstein dieses Auftrags.
Zugehoerige bestehende Registerbereiche: EXP-07, EXP-13, EXP-14; keine
Aenderung ihres Produktivstatus oder des gemeinsamen Experimentregisters.

## 1. Abgrenzung und Erfolgskriterien

G1 liefert Messmaterial, keine Architektur und keinen Verbesserungsalgorithmus.
Wir nutzen unveraendert `tools/fold7_quality_lab/intelligence.py` aus
PR #573, SHA `5a78ee19cddb1f5bd0d5b652114d3132ddc4a73f`.
Kein zweiter Scorer, Geraetecollector, Agent, Speicher oder Trainingsprozess.
Ein kleiner Paket-Vertragstest darf dieses bestehende Werkzeug aufrufen.

**Vorab festgelegtes Artefakt-Gate:**

1. Genau 24 neue oeffentliche DEV-Aufgaben: vier pro Zielkonstrukt,
   organisiert in zwei kontrastiven Paaren je Konstrukt; eindeutige IDs.
   Jedes Paar aendert eine entscheidungsrelevante Bedingung, so dass die
   Sollantwort wechselt. Gleiche Paarfamilie zaehlt nicht als zwei unabhaengige
   Beobachtungen.
2. Jede Aufgabe hat ein eindeutiges strukturiertes Soll, Begruendung,
   Familien-ID, Zielkonstrukt und explizite Messgrenze. Kein privater Inhalt.
3. Bestehender Scorer akzeptiert Format und Sollantworten; absichtlich
   falsche Antworten scheitern, fehlende Antworten bleiben NOT_RUN,
   doppelte/unbekannte IDs werden abgelehnt. Prompt-Export enthaelt keine
   Loesung, Begruendung oder Bewertungsmetadaten.
4. Keine uebernommene H1-H4-/PR573-Aufgabe; Aufgabentexte werden hier neu
   konstruiert. **Quelleninformiert, nicht blind**. Struktur-/Kausal-/Budget-
   Sollwerte soweit moeglich arithmetisch bzw. durch kleine exhaustive
   Aufzaehlung pruefen, nicht durch Modell-Selbstauskunft.
5. Nur neue Dateien unter `docs/research/genesis-lab/` plus eigener
   Omega-Handoff; kein `app/`, Build, Workflow, Gewicht, Modell oder
   Memory-Store geaendert. Commit, Push und Remote-Readback.

PASS bedeutet ausschliesslich **Paket-/Messvertrag erfuellt**.
24/24 eingesetzte Sollantworten sind `synthetic_selftest`, kein Modellscore.
Kein aktueller Intelligence-Gewinn und kein unabhängiger blinder Holdout
wird aus dem Paket abgeleitet. Unklare Labels werden vor Paket-Freeze
repariert und dokumentiert; nach Freeze erfordern Aenderungen eine neue Version.

## 2. Bewertungsrahmen fuer sechs Zielkonstrukte

Nicht ein Gesamtscore, sondern ein Profil mit Fehlern, Abdeckung, Ressourcen
und Unsicherheit. Die sechs DOMAINS des alten Scorers sind Transportkategorien,
nicht deckungsgleich mit den folgenden wissenschaftlichen Konstrukten.
`dimension` und `pair_id` werden nur evaluatorsseitig ausgewertet.

| Konstrukt | Neue diagnostische Variation / zu trennender Endtest | Metriken und Falsifikator |
|---|---|---|
| Gedaechtnis | Gueltigkeitszeit vs. Eintragungszeit, Widerruf vs. alte Kopie; spaeter 1/8/32 Ablenkungsturns und Prozessneustart | Antwort UND Evidenz-ID, Retention nach Abstand, unerlaubte/alte Fakten. Ein falscher aktueller Fakt ist Fehler, kein Teil-PASS. Im Prompt bereitgestellte Historie ist noch kein dauerhaftes Gedaechtnis. |
| Generalisierung | Lexikalische Praefixkollision vs. echte Rueckkehr; Komposition neuer Operatoren mit vertauschter Reihenfolge | Exaktheit je **neuer Struktur-/Sprachfamilie**, Kontrastkonsistenz, Fehler unter Renaming/Negation. Neue Namen/Seeds allein sind kein OOD-Nachweis. |
| Kausales Denken | Beobachtung vs. Intervention, bekannte Struktur vs. nicht identifizierbare Struktur | Intervention/Counterfactual korrekt; ABSTAIN bei Nichtidentifizierbarkeit; Modellmissspezifikation offenlegen. Tabellenabgleich ist nicht automatisch Kausalinduktion. |
| Metakognition | Beleg vorhanden vs. fehlend, widersprechender Beleg vs. aufgeloester Widerspruch | Coverage, selektives Fehlerrisiko, ungestuetzte Behauptungen; spaeter Brier-Score fuer vor Antwort abgegebene Konfidenz und empirische Kalibrierung. Selbstbericht ist kein Wahrheitsbeleg; immer ABSTAIN darf nicht gewinnen. |
| Langfristige Planung | Budget- und Voraussetzungsaenderung, unterbrochener Plan mit/ohne gueltige Quittung | Zielerreichung, Regelverletzungen, Kosten/Regret, Replanning und Recovery. Offline-Plantext beweist keine Handlung; spaeter mindestens 20 Zustandsuebergaenge im isolierten Simulator und nur bestehende autorisierte Tools. |
| Kontinuierliches Lernen | Aufgabenlokaler Regelwechsel, lokaler vs. globaler Scope, alte Anker nach Update | Neue-Leistung minus Vorher, Backward Transfer und Vergessen alter Aufgaben. Hier nur In-Context-Update; Dauerlernen erst nach echter Expositionsphase und getrenntem spaeterem Abruf. Keine Gewichtsveraenderung. |

Fehlerzerlegung pro realer Antwort: Extraktion, Retrieval, Referenz,
Strukturmodell, Entscheidung, Ausgabe/Format, Evidenz/Autorisierung.
Timeout/Abbruch nach begonnenem Item zaehlt als fehlender Erfolg im geplanten
Nenner; nicht gestartete Laeufe bleiben NOT_RUN. Keine Entfernung schwieriger
Faelle. Formatfehler separat ausweisen, aber beim Exact-Match nicht entschuldigen.

## 3. Leckageschutz und spaetere bestaetigende Auswertung

Das gesamte G1-Paket einschliesslich Schluessel ist **oeffentliches DEV**.
Auch ein Prompt-only-Export macht diese Aufgaben nicht geheim.
Keinen oeffentlichen Seed oder Hash als Beweis fuer Geheimhaltung ausgeben.
Keine Aufgaben aus diesen Dateien in Training/automatische Rechercheaufnahme.

Ein spaeterer bestaetigender Lauf ist **nicht Teil dieses Meilensteins** und
braucht vor dem Start:

- Separaten Testverwalter, der weder Kandidat noch Testantworten optimiert.
  Neue Familien statt bloss neuer Namen/Zufallszahlen. Ausdruecklicher
  Abgleich mit H1-H4, PR573, G1, sichtbaren Trainings-/Recherchekorpora;
  unbekannte Vortrainingskontamination bleibt als solche deklariert.
- Freeze von App-/Modell-/Tokenizer-/Policy-/Scorer-SHAs, Quellenstatus,
  Memory-Epoch, Featuremodi, Decoding, Tokens, Timeouts und Budgets **vor**
  Testauswahl. Beide Arme sehen dieselben Inputs und starten mit getrennten
  Kopien desselben autorisierten synthetischen Zustands im bestehenden Store.
  Keine privaten Besitzerfakten; keine neue Speicherarchitektur.
- Verdeckter lokal verwahrter Schluessel; randomisiert gesalzener
  SHA-256-Commitment des kompletten Katalogs vor Lauf. Salt/Schluessel
  nicht an Kandidaten oder in GitHub; Hash allein authentifiziert nichts.
  Nur prompt-only Daten ans Modell; Netz/Recherche/Training fuer TEST aus.
- Genau ein vorab festgelegter Lauf je Arm; Reihenfolge gepaart
  randomisiert. Abgebrochene/ungueltige Laeufe mit Grund erhalten; kein
  selektives Wiederholen bis PASS. Nach Einsicht ist TEST verbraucht.
- Ausfuehrungsprovenienz: Quell- und Modellhash, geraetelokale Zeit,
  Prompt-/Antwort-Digests, Runtime-/Decodingkonfiguration, Mode, Netzwerkstatus,
  CPU/PSS/Latenz, Fehler und echte Toolquittungen. Ein `device_model_declared`
  Label des PR573-Scorers bleibt **nicht verifiziert**.

**Vorgeschlagener, noch nicht aktivierter Bestaetigungs-Gate:** primaer
Generalisierung, 100 voneinander unabhaengig konstruierte neue
Welt-/Regelfamilien mit je zwei Kontrastitems. Familieneinheit bestanden
nur bei beiden richtigen Antworten. Fester Umfang, kein optionales Stoppen:
mindestens +10 bestandene Familien gegen eingefrorene Baseline und
einseitiger exakter gepaarter Diskordanztest p<0,05; 95%-Intervall der
gepaarten Differenz berichten. Kein Power-Versprechen: 100 Familien
garantieren nicht den Nachweis jedes Effekts.

Fuenf andere Dimensionen zunaechst diagnostisch; fuer sechs separate
Bestaetigungsbehauptungen Holm-Korrektur ueber alle sechs Tests, vorab
definierte Effektgroessen und ausreichender Umfang je Dimension erforderlich.
Keine nachtraegliche Auswahl der besten Dimension. Unabhaengigkeit auf
Familienniveau begruenden; bei gemeinsamem Template konservativ clustern,
nicht Itemzahlen als N ausgeben.

Bei jedem behaupteten Gewinn: keine zusaetzlichen ungestuetzten Antworten
auf den vorab bestimmten Negativkontrollen, keine Rechte-/Revisionsverletzung,
Coverage mindestens Baseline, CPU und p95-Latenz jeweils <=110% der Baseline,
Peak-PSS <=105%. Oberhalb dieser Ressourcen wird ein moeglicher
Qualitaetsgewinn separat mit Mehrkosten berichtet, nicht als
Intelligenz-pro-Ressource-Gewinn. Diese Grenzen sind Forschungskriterien,
keine allgemeingueltigen Definitionen von Intelligenz oder Sicherheit.

## 4. Lokale Durchfuehrbarkeit und Stop

Paket und Scorer benoetigen nur Python-Standardbibliothek, kein Netzwerk,
GPU, PC oder Rechenzentrum zur Auswertung. Eine vorhandene Android/Termux-
Python-Umgebung kann spaeter verwendet werden; hier keine Telefonpruefung.
Geräteautomation aus PR573 benoetigt ADB und ist **keine Voraussetzung** fuer
den lokalen manuellen Test. Keine neue Termux-Installation oder APK.

Abbruch bei unklarer Provenienz, Schluesselleck, nicht identischen
Kontrollbedingungen, unerlaubtem Speicher-/Toolzugriff, thermischem
Budgetbruch oder veraendertem Kandidaten nach TEST-Einsicht. Ergebnis dann
INVALID/BLOCKED, nicht PASS. Kein automatischer Repair-/Trainingslauf.

Nach G1-Paketpruefung wird gestoppt. Kein G2, kein Anschluss an Build 51,
kein Main-Merge und keine APK-Freigabe. Ein fehlender echter Modell-/Geraetelauf
wird nicht durch erzeugte Sollantworten ersetzt.
