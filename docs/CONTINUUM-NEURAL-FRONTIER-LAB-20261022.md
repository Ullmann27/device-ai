# Continuum Neural Frontier Lab – Forschungszyklus NFC1 (Opus, 22.10.2026)

**Status: Vorregistrierung.** Dieses Dokument wurde committet und gepusht, **bevor** ein einziges NF-Modell trainiert
oder bewertet wurde. Ergebnisse werden unten in eigenen Abschnitten ergänzt; die Regeln hier werden danach nicht mehr
geändert. Nachträgliche Analysen werden als solche gekennzeichnet.

- Zweig: `opus/continuum-neural-frontier-lab-20261022` (Basis PR #75, `68b0a335`).
- Code: `app/src/test/java/at/heinz/deviceai/learning/nf/` – **nur Testquellen**, nichts davon gelangt in die APK.
- Einzige Änderung am LM1-Kern: `Tape.record` ist `internal` statt `private`, damit Forschungsoperationen den Tape
  erweitern können. Verhalten und Checkpoint-Format von LM1 sind unverändert (LM1-Tests 7/7).
- Keine Änderung an SWB1-, CDX1-, CSX1- oder LM1-Gewichten. Keine App-Anbindung.

## 1. Daten NFC1 (vor jedem Lauf eingefroren)

| Teil | Dokumente | Segmente | Bytes | SHA-256 |
|---|---:|---:|---:|---|
| DOC TRAIN | 71 | 2.446 | 422.773 | `86ad7f86…` |
| DOC VALIDATION | 9 | 395 | 60.365 | `e6c7c3f6…` |
| **DOC TEST (eingefroren)** | 8 | 251 | 43.124 | `3bbb7b5b6fd5195217507cf99365cd0141cd4f7e2620402dbfb7f6bb568ac7dd` |
| CODE TRAIN | 126 | 6.138 | 1.306.918 | `78324e71…` |
| CODE VALIDATION | 14 | 318 | 66.085 | `68cb72c9…` |
| **CODE TEST (eingefroren)** | 11 | 475 | 100.826 | `ac3881236bac73db6baca830e2e037395a0bf5eca750466bc2407a98522c3b25` |

- **Quelle:** Commit `68b0a335`. DOC = Markdown des Besitzers (ohne `docs/evidence/`, `THIRD_PARTY_NOTICES.md`),
  LM1-Datenschutzfilter. CODE = Kotlin unter `app/src/main/java`, gleiche Ersetzungen (Namen, Links, E-Mail, Hashes,
  lange Zahlen), Segmente ≤ 255 Bytes an Zeilenenden. Datenschutz-Nachkontrolle: 0 Namen, 0 Links, 0 E-Mail-Adressen.
- **Aufteilung:** erstes Byte von SHA-256(`"nfc1\0" + Pfad`) mod 10 → 0 TEST, 1 VALIDATION, sonst TRAIN.
- **LM1-TEST bleibt unberührt:** Jeder Pfad, den LM1 seinem TEST zuordnet, ist aus allen NFC1-Teilen entfernt.
- **Tokenizer:** Byte-BPE v512, nur aus DOC TRAIN + CODE TRAIN gelernt, SHA-256 `296567ef…6224`.
- Protokoll der Berechnung: `docs/evidence/nf-20261022/corpus.txt`.

## 2. Gemeinsames Messprotokoll

- **Fairness:** gleicher Tokenizer, gleiche Datenreihenfolge je Seed, gleicher Plan (AdamW 2e-3, Warmup 100, Kosinus,
  Weight Decay 0,1, Clipping 1, Batch 16), gleiche Epochenzahl. Parameterzahlen werden immer mit berichtet.
- **Seeds:** 1, 2, 3. Ein Unterschied gilt nur als **nachgewiesen**, wenn alle Seeds der einen Variante besser sind als
  alle Seeds der anderen (vollständige Trennung) **und** der Mittelwertabstand die genannte Mindestgröße erreicht.
- **Auswahl nur auf VALIDATION.** Der eingefrorene TEST wird am Ende des Zyklus **genau einmal** für die vorab
  benannten Kandidaten gemessen (Abschnitt 9) – nicht für Zwischenentscheidungen.
- **Gemessen werden:** BPB auf VALIDATION, ms je Token (1 Thread, JVM x86_64), Parameterbytes, Zustandsgröße,
  Trainingszeit, Heap-Spitze des Prozesses, bei NF-1 Lernzuwachs, Vergessen und Unversehrtheit des Basis-Checkpoints.
- **Nicht gemessen und nicht behauptet:** Energieverbrauch (hier nicht zuverlässig erfassbar), ARM64-Leistung
  (kein Gerät in dieser Umgebung), allgemeine Sprachfähigkeit.
- **Abbruch:** Ein Lauf mit nicht endlichen Parametern bricht ab und wird als Fehlschlag berichtet. Jede Variante bekommt
  genau einen Satz Läufe; es gibt keine nachträgliche Hyperparametersuche auf VALIDATION, außer wo hier vorgesehen.
- **Belege:** Jede Ausgabezeile mit Checkpoint-SHA; Protokolle unter `docs/evidence/nf-20261022/`.

## 3. NF-2 – Plastische Fast-Weight-Neuronen (Gated Delta Rule)

**Mechanismus.** Statt eines Vektorzustands (minGRU) trägt jede Schicht pro Kopf eine kleine Gewichtsmatrix S
(16 × 16), die beim Lesen jedes Tokens umgeschrieben wird:
`S_t = α_t (I − β_t k_t k_tᵀ) S_{t−1} + β_t k_t v_tᵀ`, Ausgabe `S_tᵀ q_t`. Das Lösch-/Vergessensgate α und das
Schreibgate β sind gelernt; der Term `− β k kᵀ S` löscht den alten Wert unter demselben Schlüssel, bevor der neue
geschrieben wird (zustandsabhängige Gewichtsanpassung).

1. **Was soll die Verbesserung erzeugen?** Assoziatives Kurzzeitgedächtnis mit Kapazität ~dh² statt dh je Kopf.
2. **Bekannte Methode mit ähnlichem Zweck:** DeltaNet (Schlag et al. 2021; Yang et al. 2024), Gated DeltaNet
   (Yang et al. 2025), Fast-Weight-Programmer (Schmidhuber 1992), lineare Attention mit Gates (GLA, Mamba-2).
3. **Was ist an unserer Variante anders?** Eigene Implementierung im LM1-Autodiff mit exakter Rückwärtsrechnung;
   zusätzlich eine **exakte algebraische Rücknahme** einzelner Schreibvorgänge (`undoDeltaWrite`): Weil
   `(I − β k kᵀ)` für β < 1 invertierbar ist, genügt das Journal (k, v, α, β), um einen fehlerhaften Schreibvorgang
   ohne gespeicherte Zustandskopie rückgängig zu machen. Getestet: 20 Schreibvorgänge vor- und zurückgerechnet,
   Abweichung < 2·10⁻³.
4. **Widerlegende Messung:** (a) Sprachmodell: D-S ist auf VALIDATION nicht besser als G-S (gleiches Budget).
   (b) Gedächtnis: D-S erreicht beim Assoziationstest nicht mindestens 0,10 mehr Trefferquote als G-S bei N = 16.
5. **Kosten:** 5 statt 4 d×d-Matrizen je Schicht (+~1 % Parameter bei S), Zustand H·dh² = 512 statt 32 Floats je
   Schicht; Rechenzeit gemessen.
6. **Abbruch:** NaN/Inf, oder D ist bei gleichem Budget in beiden Tests schlechter als G → Ergebnis negativ berichten.

**Varianten (Größe S: d = 32, 2 Schichten, 2 Köpfe, MLP 128, Kontext 256; 6 Epochen auf DOC TRAIN):**
G (minGRU = LM1-G), T (Transformer = LM1-T), **D** (Gated Delta), L (gated lineare Attention, ohne Löschterm),
Dna (Delta ohne Vergessensgate, α = 1), Dnb (Delta ohne Schreibgate, β = 0,5).

**Entscheidungsregeln:** „D besser als G“ bei vollständiger Seed-Trennung und Mittelwertabstand ≥ 0,02 BPB.
Gleiche Regel für die Ablationen (Beitrag des Löschterms: D gegen L; Gates: D gegen Dna/Dnb).

**Gedächtnistest (MQAR, Arora et al. 2023):** N verschiedene Schlüssel mit Zufallswerten, danach jede Abfrage in
zufälliger Reihenfolge; gewertet wird nur der Wert nach der Abfrage. Training N ∈ [1, 16] (3.000 Schritte, Batch 16),
Auswertung N = 4, 8, 16 sowie 24 und 32 (länger als im Training), je 200 identische Sequenzen.
Varianten: D-S, G-S, T-S, L-S sowie die **größeren** G-M (d = 64) und G-X (d = 128).
**Forschungsfrage:** Löst ein kleines System mit Gedächtnisstruktur (D-S) die Aufgabe besser als ein deutlich
größeres ohne sie (G-X)? Nachgewiesen, wenn acc16 von D-S in jedem Seed um ≥ 0,10 über G-X liegt.

## 4. NF-1 – Mehrgeschwindigkeits-Gedächtnis

**Architektur.** Drei Ebenen mit verschiedenen Lernzeiten:
- **schnell:** Rekurrenter Zustand bzw. Delta-Gedächtnis, wird je Token umgeschrieben und innerhalb eines Dokuments
  über Segmentgrenzen getragen;
- **mittel:** ein Adapter vom Rang 4 (W + A·B) auf allen Mischer- und MLP-Matrizen, lernt online;
  optional nur bei **Überraschung** (Segment-BPB über dem laufenden Median) – das System entscheidet selbst, was
  dauerhaft gelernt wird und was nur im schnellen Zustand verbleibt;
- **langsam:** der Basis-Checkpoint, **eingefroren**; seine Unversehrtheit wird per SHA-256 nachgewiesen.

**Versuch (präquentiell):** Basismodelle G-S und D-S aus NF-2 (Epoche 6, Seeds 1–3, nur auf DOC trainiert) lesen
einmal einen Strom von ~120 KB CODE-TRAIN-Dokumenten. Jedes Segment wird **zuerst bewertet**, dann darf gelernt werden.
Arme: BASE, CARRY (fester rekurrenter Zustand), FT (alle Gewichte, lr 1e-3), REPLAY (FT + je ein DOC-Segment),
ADAPT (Adapter, lr 3e-3), SURPRISE (Adapter nur bei Überraschung), TWO (CARRY + ADAPT), TWOS (CARRY + SURPRISE).
Danach: CODE VALIDATION (Verallgemeinerung), DOC VALIDATION (Vergessen), bei Adaptern zusätzlich mit abgeschaltetem
Adapter und Hash-Vergleich der Basis.

1. **Mechanismus:** Trennung von schneller Anpassung und langsamer Konsolidierung schützt alte Fähigkeiten.
2. **Bekannt:** Complementary Learning Systems (McClelland et al. 1995), Nested Learning/HOPE (Behrouz et al. 2025),
   Titans (Behrouz et al. 2025, überraschungsgesteuertes Testzeit-Gedächtnis), LoRA (Hu et al. 2021), Experience Replay.
3. **Unsere Variante:** Die mittlere Ebene liegt **außerhalb** des Basis-Checkpoints (abschaltbar, rücknehmbar,
   per Hash nachweisbar unverändert); Konsolidierung über eine einfache, prüfbare Überraschungsschwelle.
4. **Widerlegend:** H1a – ADAPT erreicht < 50 % des Lernzuwachses von FT oder mehr als 25 % von dessen Vergessen.
   H1b – SURPRISE erreicht < 80 % des Zuwachses von ADAPT bei ≥ 60 % der Aktualisierungen.
   H1c – CARRY senkt den Strom-BPB gegenüber BASE nicht. H1d – TWO ist nicht besser als CARRY und ADAPT einzeln.
   (Lernzuwachs = BPB(BASE) − BPB(Arm) auf dem Strom; Vergessen = DOC-VALIDATION-BPB(Arm) − BPB(BASE).)
5. **Kosten:** Adapter ≈ 6 % zusätzliche Parameter bei S; ein Rückwärtsschritt je konsolidiertem Segment.
6. **Abbruch:** Basis-Hash ändert sich in einem Adapter-Arm → Implementierungsfehler, Ergebnis ungültig.

## 5. NF-3 – Adaptive Rechenverteilung (Byte-Latent-Variante)

**Mechanismus.** Ein billiges Byte-Modell läuft für jedes Byte; ein teureres globales Modell rechnet nur an
**Patchgrenzen**, die dort gesetzt werden, wo die Vorhersageentropie eines kleinen Byte-n-Gramms hoch ist (schwierige
Stellen bekommen mehr globale Rechenschritte). Vergleich mit dem unveränderten Byte-BPE-System (G-S/G-M) bei
gemessen gleicher Rechenzeit je Byte (±15 %) und mit festen Patches gleicher mittlerer Länge.

1. **Mechanismus:** Rechenaufwand folgt der Informationsdichte.
2. **Bekannt:** Byte Latent Transformer (Pagnoni et al., ACL 2025), MegaByte (Yu et al. 2023), Mixture-of-Depths.
3. **Unsere Variante:** rein rekurrent (minGRU lokal und global), Entropie aus einem Kneser-Ney-Byte-Modell statt eines
   neuronalen Entropiemodells, auf CPU-Größe skaliert.
4. **Widerlegend:** Entropie-Patches sind nicht besser als feste Patches gleicher Länge, oder nicht ≥ 0,03 BPB besser
   als BPE bei gleicher gemessener Rechenzeit.
5. **Kosten:** n-Gramm-Tabelle im Speicher; zwei Modelle.
6. **Abbruch:** Implementierung in dieser Sitzung nicht vollständig gradientengeprüft → nicht ausführen.

**Präzisierung vom 22.10.2026, eingetragen vor dem ersten NF-3-Lauf** (Code `NfBlt.kt`, Gradienten- und
Kausalitätstest bestanden):
- lokales Byte-Modell d = 32 (Encoder- und Decoder-Block, je minGRU + MLP), globales Modell d = 64 (2 Blöcke) je Patch;
- Entropie aus einem Byte-n-Gramm der Ordnung 4 (absolute Discounting, D = 0,75), nur aus DOC TRAIN;
- Schwelle = Quantil der TRAIN-Entropien, sodass im Mittel jedes 3. Byte eine Grenze ist (Ziel-Patchlänge 3),
  höchstens 16 Bytes je Patch; feste Patches: Länge 3;
- 6 Epochen, gleicher Plan wie NF-2, Seeds 1–3; BPE-Referenzen: G-S (NF-2) und G-M (d = 64), ebenfalls 6 Epochen.
- Rechenaufwand wird als gemessene ms/Byte (1 Thread, inkl. Patch-Berechnung) neben ms/Token × Token/Byte der
  BPE-Modelle berichtet.

## 6. NF-4 – Ereignisgesteuerte Aktivität

**Mechanismus.** Der MLP-Block nutzt ReLU mit gelernter Schwelle (Bias) und einer L1-Strafe auf die Aktivierungen
(λ = 1e-3); exakte Nullen lassen den Kernel ganze Zeilen der zweiten Matrix überspringen (bereits in `Kernels.addMm`).
Varianten: Gr-S mit λ = 0 und λ = 1e-3 gegen G-S (GELU).
1. **Bekannt:** ReLU-Sparsity (Mirzadeh et al. 2023), ProSparse, Deja Vu, TEAL; Spiking-Modelle (SpikingLM).
2. **Unsere Variante:** gemessen auf unserer CPU mit unserem Kernel; keine Annahme neuromorpher Hardware.
3. **Widerlegend/Erfolg:** Erfolg nur, wenn VALIDATION-BPB ≤ G-S + 0,02 **und** gemessene ms/Token ≤ 0,85 × G-S.
4. **Kosten/Abbruch:** keine zusätzlichen Parameter; Abbruch bei NaN.

## 7. NF-5 – Lernender Reasoner mit Beweiskontrolle

Der vorhandene Dialog (PR #75) bekommt eine **lernende Kandidatenrangfolge**: zweisprachige Assoziationen
(deutsche Zielwörter ↔ Bezeichner-Teilwörter), gelernt aus den Projektdokumenten (Wörter neben Code-Bezeichnern) und
ausschließlich aus **unabhängig bestätigten** Quittungen. Vergleich lernend / lexikalisch / zufällig auf **vorher
unbekannten** Aufgaben (Aufgabenteilung vor dem Lauf, Erfolg nur bei bestätigter Quittung).
Erfolgskriterium: lernend löst im ersten Versuch mehr unbekannte Aufgaben als lexikalisch, bei 0 ungewollten
Bestätigungen. Wegen kleiner Aufgabenzahl wird keine statistische Signifikanz behauptet.

## 8. NF-6 – Eigene Kombinationen

**NF-6a: Belegbasierte Zwei-Speicher-Konsolidierung (Receipt-Gated Consolidation).**
1. **Mechanismus:** Ein schneller Assoziationsspeicher (Delta-Regel) nimmt eine Hypothese sofort vorläufig auf; in
   den dauerhaften Speicher gelangt sie **nur** mit einer unabhängig verifizierten Quittung. Eine widerlegte
   Hypothese wird mit `undoDeltaWrite` exakt zurückgenommen.
2. **Bekannt:** Complementary Learning Systems, Self-Training mit Verifizierern (STaR, RL mit verifizierbaren
   Belohnungen), Titans (Überraschungs-Gating).
3. **Anders:** Konsolidierungssignal ist eine kryptografisch gebundene Ausführungsquittung statt eines Modellsignals;
   Rücknahme ist algebraisch exakt statt durch Neu-Training.
4. **Widerlegend:** Bei einem Aufgabenstrom mit absichtlich falschen Beispielen ist die Genauigkeit auf unbekannten
   Aufgaben nicht höher als bei „alles konsolidieren“ oder „nichts lernen“.
5. **Kosten:** ein dh×dh-Speicher je Ebene; Journal (k, v, α, β) je Schreibvorgang.
6. **Abbruch:** Rücknahmetest nicht bestanden → nicht ausführen.

**NF-6b: Zählmodell-verankertes Delta-Gedächtnis (Count-Anchored Delta Memory).**
1. **Mechanismus:** Byte-Modell, dessen Ausgabe mit einem Kneser-Ney-Byte-Modell über ein gelerntes Gate gemischt wird
   und dessen Delta-Schreibstärke β zusätzlich von der n-Gramm-Überraschung abhängt: Was das Zählmodell schon kann,
   muss das neuronale Gedächtnis nicht speichern.
2. **Bekannt:** Neubig & Dyer 2016 (hybride n-Gramm/neuronale Modelle), kNN-LM, Infini-gram-Interpolation, Titans.
3. **Anders:** externe Überraschung steuert das **Schreiben** in Fast Weights, nicht nur die Ausgabemischung.
4. **Widerlegend:** nicht ≥ 0,02 BPB besser als dasselbe Modell mit nachträglich auf VALIDATION gewählter fester
   Mischung (alle Seeds).
5. **Kosten:** n-Gramm-Tabelle; 256 Wahrscheinlichkeiten je Byte-Position.
6. **Abbruch:** wenn NF-3/NF-6b-Infrastruktur in dieser Sitzung nicht fertig wird, bleibt es offen und sichtbar.

## 9. Abschluss des Zyklus und TEST

Am Ende werden die je Experiment besten Varianten (nach den Regeln oben, nur VALIDATION) und ihre Baselines **einmal**
auf DOC TEST bzw. CODE TEST gemessen. Außergewöhnliche Verbesserungen werden vorher unabhängig wiederholt (neuer Seed)
und auf Datenleckage geprüft (Überschneidung von Segmenten zwischen TRAIN und VALIDATION/TEST).

## 10. Ergebnisse

(werden nach den Läufen ergänzt)
