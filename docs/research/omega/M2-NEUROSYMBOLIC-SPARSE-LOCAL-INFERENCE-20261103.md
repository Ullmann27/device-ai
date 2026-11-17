# M2 – Neuro-symbolisches Denken, sparsames Rechnen, lokale Inferenz (Forschungsnotiz)

Stand 03.11.2026 (Europe/Vienna), Grok 2. Nur Befunde mit Beleg; alles andere ist als `RESEARCH_HYPOTHESIS` markiert.
Grundlage: Argon-4-Dossier (5.1, 6.1–6.4, 12.1–12.2), Code auf `7316b1f5` + Branch `continuum/m2-cognitive-memory`.

## 1. Neuro-symbolisch: was jetzt gilt

**Umgesetzt (`HOST_VERIFIED`):** Symbolische Schicht zuerst, neuronale Schicht danach.
- Symbolisch: `OwnerStatementModality` (Aussageart) → `ClaimEvent` (Subjekt, Prädikat, Wert, Gültigkeit, Wissenszeit,
  Änderung/Korrektur) → deterministische Auflösung, letzte Punktabfrage über `TemporalRoleEvidenceLedger`.
- Neuronal (unverändert): SWB1 rankt, EWM2 entscheidet Abruf/Rückfrage, Gemma formuliert nur mit Ankerprüfung.
- Wirkung: Zeitfragen 273/273 statt 67/273 (in-distribution); Holdout 0 falsche Antworten, 2/20 Enthaltungen.

**Grenze (gemessen):** Die Grammatik deckt Wohnort, Arbeit, Alter, „Mein X heißt Y“. Außerhalb → `null`, der
bestehende Pfad antwortet. Vor dem Holdout-Fix gab es 4/20 **falsche** Antworten – deshalb gilt: neue Formulierungen
nur mit eigenem Holdout und Präzisionsziel „0 falsch“.

**Nächster Schritt (RESEARCH_HYPOTHESIS → prüfbar):** Gemma als *Extraktor*, nicht als Wahrheitsquelle.
Für einen Satz außerhalb der Grammatik erzeugt Gemma (LiteRT-LM, eingeschränktes JSON) einen ClaimEvent-Vorschlag.
Aufnahme nur, wenn (a) der Wert wörtlich im Quellsatz steht, (b) `OwnerStatementModality` = ASSERTED/NEGATED,
(c) das Prädikat aus der festen Liste stammt, (d) Modus SHADOW zuerst. Falsifikation: deutscher Holdout mit
≥ 200 Sätzen (Umgangssprache, Dialekt, Inversion, Koordination, berichtete Rede); Abbruch, wenn auch nur eine
Hypothese als Fakt durchrutscht oder die Falschquote > 0,5 % liegt. Baseline: aktuelle Grammatik.

## 2. Sparsames Rechnen

| Befund | Messung | Nächster Schritt |
|---|---|---|
| M2-Antwort liest das Ledger zweimal (Widerrufsprüfung) und dekodiert jedes Mal inkl. SHA-Prüfung | p50 16,5–18,0 ms / p95 22,6–26,8 ms (JVM-Host, 220 Datensätze, 2 Läufe) vs. bestehender Pfad 5,3–5,4 / 8,6–9,6 ms | Sicht cachen, Schlüssel = Ledger-SHA + Arbeitsgedächtnis-SHA (wie `ContinuumBrainBridge`-Sitzungsschlüssel). Erwartung < 1 ms für Wiederholfragen; Negativkontrolle: jede Ledger-Änderung invalidiert. |
| Zeitfragen im Grammatikbereich brauchen **0 Modell-Token** | deterministischer Pfad, kein LLM-Aufruf | Anteil der Besitzer-Gedächtnisfragen messen, die ohne Gemma beantwortet werden (Geräte-Shadow-Log, nur Zähler). |
| Claim-Extraktion ist eine reine Funktion des Datensatz-SHA | – | Über `ComputeFabric`/Reuse-Broker inhaltsadressiert cachen; Negativkontrolle aus #287 (einzigartige Eingaben = keine Ersparnis) übernehmen. |

Keine Aussage über Energie/Thermik ohne Fold7-Messung (`NOT_RUN`).

## 3. Bessere lokale Inferenz (Gemma / LiteRT-LM)

- **Kleinerer Prompt statt größeres Modell (RESEARCH_HYPOTHESIS):** Statt einer Erinnerungsliste nur die aufgelösten
  ClaimEvents der Frage („Wohnort jetzt: Graz seit 01.06.2022; Beleg lx-…“) in den lokalen Prompt geben. Messgröße:
  Prompt-Bytes und Prefill-Zeit pro Turn auf dem Fold7, Antwortkorrektheit auf demselben Fixture. Zeitanker gehört
  ans Prompt-Ende (Grok-1-M1-Befund) – betrifft Routing/Prompt-Bau, daher Grok-1-Übergabe.
- **Konversations-Wiederverwendung (#368-Fortsetzung)** bleibt bei Grok 1 / LiteRT-LM-Arbeit; M2 liefert dafür nur
  stabile, deterministische Gedächtniszeilen (gut cachebar).

## 4. „Jahrelang abrufbar“ – harte Grenze

`FileLearningExperienceStore.MAX_RECORDS = 256`; Besitzerdatensätze werden nie verdrängt, der 257. wirft
`IllegalStateException` (Test `ledgerCapacityIsAMeasuredHardLimit`). Bei einigen Aussagen pro Woche ist das in
1–2 Jahren erreicht. Vorschlag (nicht umgesetzt, Formatentscheidung mit Besitzer): LEX3 mit getrenntem,
nur anhängendem Besitzersegment (z. B. 16.384 Datensätze, segmentiert), lesbar neben LEX2; älterer Build darf eine
LEX3-Datei nicht still verwerfen (Downgrade-Regel). RED-Test existiert bereits als Messung.

## 5. Nächste Inkremente (Reihenfolge)
1. Grok-1-Integration H1/H2 (siehe `GROK2-HANDOFF.md`), danach Fold7-Abnahme im SHADOW-Modus.
2. Ledger-Kapazität LEX3 (RED: 257. Besitzerfakt), Downgrade-sicher.
3. Sicht-Cache nach Ledger-SHA (RED: Latenzbudget-Test p95 < 5 ms Host).
4. Signierte Korrekturen über `ProofCarryingMemory`/`MemoryMutationLedger` (jede Änderung/Korrektur mit Kette).
5. Deutsche LongMemEval-artige Suite (Abruf, Mehrsitzung, Zeit, Wissensupdate, Enthaltung) mit blindem Holdout.
6. Gemma-Extraktor im SHADOW (Abschnitt 1) mit Präzisionsabbruch.

## Quellen
LongMemEval (ICLR 2025) https://github.com/xiaowu0162/LongMemEval · LOCOMO-CONV (EMNLP 2026)
https://aclanthology.org/2026.findings-emnlp.1289/ · LiteRT-LM Kotlin
https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md (laut Dossier 17.1; nicht
erneut abgerufen).
