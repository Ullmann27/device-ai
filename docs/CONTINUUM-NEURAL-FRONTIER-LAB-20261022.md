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

## 8a. Vorregistrierung des NF-5/NF-6a-Laufs (eingetragen vor dem Lauf)

- **Sprachkern:** `.github/continuum/assoc-language-core.mjs` (Tests 2/2). Assoziationen deutsches Wort ↔
  Bezeichner-Teil aus Absätzen der Projektdokumentation mit Bezeichnern in Backticks (PMI, Paar mindestens zweimal):
  509 Absätze, 21.670 Paare. Rangwert = lexikalischer Wert + λ · Assoziationswert, **λ = 0,5** (nicht abgestimmt).
  Quittungslernen: CONFIRMED +1, REFUTED −0,5, INCONCLUSIVE 0; jede Änderung mit Vorwert im Journal, Rücknahme bitgenau.
- **Aufgaben:** die 8 regulären und 3 adversarialen Ziele aus Pilot 2 sowie **6 neue Ziele** zu echten Funktionen,
  die noch kein Ranker gesehen hat: `contextCost`, `digest` (capability-research), `bucket`, `relationOf`,
  `subjectOf` (cre-encoder), `wordEvidence` (dialogue-retrieval). Erwartete Werte aus den Funktionen selbst.
  Für die Prüfsummen-Aufgabe zählen die bytegleichen SHA-256-Implementierungen in `portable-subword` und
  `cre-encoder` als richtig. `isKeyword` liegt im geschützten Modul und ist nicht prüfbar (wie im Pilot gewertet).
- **Bedingungen:** `random` (Hash-Rangfolge), `lexical`, `docs` (nur Dokumentation), `gated` (Dokumentation +
  verifizierte Quittungen aller **anderen** Aufgaben aus dem `docs`-Lauf, Leave-one-out), `ungated` (Dokumentation +
  erster Vorschlag aller anderen Aufgaben, als wäre er wahr – keine Prüfung). Je Aufgabe höchstens 3 Runden, echter
  Coding-Job über die CCC1-Laufzeit.
- **Messgrößen:** richtig im 1. Versuch, richtig in ≤ 3 Runden, ungewollte Bestätigungen, korrekte Enthaltungen bei
  adversarialen Zielen, Zahl der Jobs; getrennt ausgewiesen für die 6 neuen Aufgaben.
- **Entscheidung:** „docs besser als lexical“, wenn im 1. Versuch mindestens 2 reguläre Aufgaben mehr richtig sind und
  es 0 ungewollte Bestätigungen gibt. NF-6a gilt als gestützt, wenn `gated` im 1. Versuch mindestens so gut ist wie
  `ungated` und weniger ungewollte Bestätigungen hat oder mehr richtig löst. Keine Signifikanzaussage (n = 14).

## 8b. Research-Radar (Ergebnis C) – `.github/continuum/research-radar.mjs`

- **Quelle:** arXiv-API (kostenlos, ohne Schlüssel), höchstens 25 Treffer je Thema, 3 s Pause zwischen Anfragen,
  20 s Zeitlimit; Netzfehler werden je Thema protokolliert und brechen den Lauf nicht ab.
- **Themen:** je NF-Experiment eine Suchanfrage (Gedächtnis, Fast Weights, adaptive Rechenverteilung, Sparsity,
  verifiziertes Programmieren, On-Device-Effizienz).
- **Dubletten:** Schlüssel = arXiv-Id ohne Version, sonst DOI, sonst normalisierter Titel; neue Versionen
  aktualisieren den vorhandenen Eintrag.
- **Einstufung (vorläufig, mit Begründung):** theoretisch interessant · auf lokaler Hardware umsetzbar (genannte
  Modellgröße ≤ 1 Mrd. oder CPU/Mobil/Laptop) · nicht geeignet/zu teuer (≥ 7 Mrd. Parameter oder Großrechner genannt).
  **„Unabhängig reproduziert“ und „messbar besser als unsere Methode“ gibt es nur mit eingetragenem Beleg** –
  „besser“ ausschließlich über ein eigenes NF-Ergebnis, nie über die Behauptung eines Papers.
- **Vorschläge:** Für die besten umsetzbaren/interessanten Quellen entsteht ein Vorregistrierungs-Gerüst mit den sechs
  Fragen dieses Labors und – nur auf Wunsch – ein Backlog-Eintrag `RESEARCH_PROPOSAL` im Zustand **PROPOSED**.
  Ausgeführt wird nichts: Der Besitzer nimmt an oder lehnt ab; der Teamleiter greift diese Art nicht selbst auf.
- **Rechte:** Ideen dürfen eigene Implementierungen anregen; Code wird nie ohne Lizenzprüfung übernommen
  (`codeLicence: unknown`, bis jemand die Lizenz prüft).
- **Zeitplan:** `tick` läuft nur, wenn `enabled: true`, `approvedBy: "owner"` und das Intervall (Standard 7 Tage)
  abgelaufen ist; `scan --force` ist der manuelle Lauf des Besitzers.
  Vorlage: `.github/continuum/research-radar.config.example.json`.
- **Befehle:** `node .github/continuum/research-radar.mjs tick|scan [--force]|report|propose [--backlog=<datei>]
  --config=<datei> --state=<ordner>`.
- **Tests:** 5/5 mit synthetischen Atom-Fixtures (Parser, Dubletten über Themen und Versionen, Einstufung, Zeitplan,
  Fehlertoleranz, Vorschläge nur PROPOSED).
- **Stand hier:** In dieser Cloud-Umgebung sperrt die Netzwerkrichtlinie `export.arxiv.org`; ein Live-Lauf wurde
  deshalb **nicht** ausgeführt und es werden keine Live-Ergebnisse behauptet. Auf dem lokalen Worker des Besitzers
  (oder nach Freigabe der Domain) ist der Lauf ohne Änderung möglich.

## 8c. Vorregistrierung NF-6b-lite: Zählmodell-verankerte Mischung (eingetragen 23.10.2026, vor jedem Lauf)

**Anlass.**
- Der LM1-TEST zeigt es: Kneser-Ney-7 schlägt jedes neuronale LM1-Modell, und zwar auf VALIDATION wie auf TEST.
- NF-6b sieht vor, dass ein Zählmodell das neuronale Modell verankert. Seine vorregistrierte Vergleichsbasis ist eine feste Mischung.
- Diese vereinfachte Fassung prüft zuerst die Mischung selbst. Das überraschungsgesteuerte Schreiben in Fast Weights (volles NF-6b) bleibt offen.

**Modelle (NFC1-Daten, nur DOC TRAIN)**
- Byte-Varianten G-S-b und G-M-b: minGRU, Vokabular 258, also ein Token je Byte. Gleicher Plan wie NF-2: 6 Epochen, Seeds 1–3; ausgewertet wird Epoche 6.
- Kneser-Ney-Byte-n-Gramm der Ordnung 7, dieselbe Implementierung wie in LM1.

**Mischung je Byte:** `p = λ · p_neuronal + (1 − λ) · p_KN`.
- **fest:** λ auf einem Raster mit Schritt 0,025.
- **verankert:** `λ = σ(a + b · ln(1 + n) + c · o / 7)`.
  - n ist die Zahl der Beobachtungen des längsten bekannten n-Gramm-Kontexts, o dessen Ordnung.
  - Das Gate vertraut dem Zählmodell also dort, wo es viel gesehen hat.

**Faire Anpassung:**
- VALIDATION wird nach Dokument-Hash in zwei Hälften geteilt.
- λ bzw. (a, b, c) werden auf der einen Hälfte bestimmt und auf der anderen gemessen, dann umgekehrt (Cross-Fitting).
- Berichtet wird der BPB über beide Hälften.

**Entscheidungsregeln (je Seed, alle Seeds müssen erfüllen)**
- H6b-1 „Die Mischung ist besser als das Zählmodell allein“: feste Mischung ≤ KN-7 − 0,05 BPB.
- H6b-2 „Die Verankerung hilft“: verankert ≤ fest − 0,02 BPB. Das ist die NF-6b-Schwelle aus Abschnitt 8.
- Berichtet, ohne Regel: Vergleich mit dem besten BPE-Modell G-M (2,392).

**TEST:** Erfüllt eine Variante H6b-1, kommt die beste Mischung beim Zyklusabschluss (Abschnitt 9) zusammen mit KN-7 und G-M einmal in den DOC TEST.

**Grenzen**
- Byte-Modelle mit 6 Epochen sind schwächer als BPE-Modelle (LM1: v258 2,246 gegen v512 2,225 bei M).
- Gewonnen werden kann also nur durch die Mischung, nicht durch das neuronale Modell allein.

## 8d. Vorregistrierung NF-5b: Ausführungsgeleitete Vorprüfung (eingetragen 23.10.2026, vor dem Lauf)

**Anlass:** Die NF-5-Fehleranalyse in 10.6 zeigt: Wortzuordnung allein reicht nicht. Erst das Verhalten einer Funktion auf den Beispielen trennt die Kandidaten.

1. **Mechanismus** (`.github/continuum/behaviour-probe.mjs`, Tests 5/5)
   - Vor dem Job wird jeder Kandidat mit passender Stelligkeit auf den Beispielen des Ziels ausgeführt, in einem eigenen Node-Prozess je Modul.
   - Dabei gilt das Berechtigungsmodell: nur Lesen im Modulordner, kein Schreiben, keine Prozesse oder Worker, 3 s Zeitlimit. Module mit Netzwerk- oder Prozessimporten werden nicht ausgeführt.
   - Kandidaten, die alle Beispiele treffen, kommen nach vorn; sonst gilt die lexikalische Reihenfolge.
2. **Bekannt:** ausführungsgeleitetes Programming-by-Example (Verbruggen et al., ICLR 2025) und die Rangbildung klassischer PBE-Systeme.
3. **Anders**
   - Die Vorprüfung ordnet nur. Entschieden wird weiter allein durch die unabhängige Vertragstest-Quittung im isolierten Worktree.
   - Die Ausführung ist abgeschottet. Die Tests weisen nach, dass ein schreibender Kandidat gestoppt wird und gedruckter Text kein Ergebnis vortäuschen kann.
4. **Widerlegend**
   - `probe` löst im 1. Versuch nicht mindestens 3 der 14 regulären Aufgaben mehr als `lexical`; oder
   - es gibt mindestens eine ungewollte Bestätigung; oder
   - es gibt weniger als 3 von 3 korrekten Enthaltungen.
   - Die 6 neuen Aufgaben werden getrennt berichtet.
5. **Kosten:** ein Kindprozess je Modul und Aufgabe; die Dauer wird gemessen.
6. **Grenzen**
   - Das Berechtigungsmodell prüft Pfade. Bereits offene Deskriptoren bleiben beschreibbar, und ein Kandidat könnte seine eigene Rangfolge manipulieren.
   - Beides ändert nur die Reihenfolge der Hypothesen, nie eine Bestätigung.
   - Das Netzwerk wird nur über die statische Importprüfung ausgeschlossen.

**Lauf:** `scripts/nf5-ranker-study.mjs <out> lexical probe`, gleiche Aufgaben, gleiche CCC1-Laufzeit, höchstens 3 Runden.

## 8e. Vorregistrierung NF-5c: Mehrdeutigkeit erkennen statt falsch bestätigen (eingetragen 23.10.2026, vor jedem Studienlauf)

**Anlass:** In NF-5b (10.8) wurde eine Funktion falsch bestätigt, weil die Beispiele zwei verschiedene Funktionen offenließen. Der Vertragstest prüft nur die Beispiele.

1. **Mechanismus** (Commit `884caecd`; Tests `behaviour-disambiguation` 5/5, davon einer mit echtem Coding-Job)
   - Vor der ersten Hypothese wird geprüft, ob mindestens zwei prüfbare Kandidaten alle Beispiele erfüllen.
   - Ist das so, laufen sie in derselben Sandbox wie NF-5b auf weiteren einfachen Eingaben. Das sind erst Varianten der Beispielwerte, dann Standardwerte desselben Typs, dann andere Typen; deterministisch, höchstens 48.
   - **Weichen die typisierten Ergebnisse ab:** Der Reasoner enthält sich und stellt eine präzise Rückfrage mit der Eingabe und dem Ergebnis jedes Kandidaten. Es läuft kein Job.
   - **Stimmen sie überein:** Die Kandidaten gelten im geprüften Bereich als verhaltensgleich, und der Dialog läuft wie in NF-5b.
   - Das Protokoll ändert sich nicht; der Besitzer antwortet mit einem weiteren Beispiel.
2. **Bekannt:**
   - orakelgeführte Synthese mit unterscheidenden Eingaben (Jha, Gulwani, Seshia, Tiwari, ICSE 2010);
   - ausführungsgeleitetes Programming-by-Example (Verbruggen et al., ICLR 2025).
3. **Aufgaben** (eingefroren in `docs/evidence/nf-20261022/nf5c-tasks.json`, SHA-256 `30066739591d761c6c4f78830fb000e0b078f0702aa53a8c3c3c46dfb1906640`; Bauer `scripts/nf5c-tasks.mjs`)
   - **Bank (15):** Paare plausibler Implementierungen in `scripts/nf5c-bank/`. 13 Paare unterscheiden sich an Randfällen, 2 verhalten sich gleich.
     - Die gemeinte Variante bestimmt ein Münzwurf mit festem Seed.
     - Beispiele sind die ersten zwei Werte des Typ-Pools, auf denen beide Varianten übereinstimmen.
     - Die Ziele sind neutral formuliert.
   - **Echte Funktionen (4):** mechanisch geschürft mit `scripts/nf5c-task-miner.mjs` am Commit `9fb4361c`: `integrityOf`, `insertImport`, `normalizeTitle`, `editDistance`. Die Beispiele lassen hier keine zweite Funktion offen.
     - Vor jedem Lauf wurde die Regel einmal geändert. Regel 1 (ungenutzte Module, ein Argument) ergab 1 von 117 aufrufbaren Exporten. Regel 2 (alle Module, nie genutzte Ziel-Funktionen, ein oder zwei Argumente) ist die gültige.
     - **Befund:** Nur wenige Projektfunktionen passen in die CCC1-Vertragssprache, die einfache Werte verlangt.
     - Gründe bei Regel 1: 36 Exporte importieren Dateien außerhalb ihres Ordners, 34 liefern nie einfache Werte, 35 werfen immer einen Fehler.
   - **Neue Gegenproben (2):** falsches Beispiel; kein Beispiel.
   - **Historisch (17):** nur Vergleich; Module wie in NF-5b, Commit `14702b0d`. Das sind keine unabhängigen Testdaten, denn der Mechanismus entstand nach diesem Fehler.
   - **Gleichwertig (zählt als richtig):** gleiche Ergebnisse auf Pool, NF-5c-Eingaben und 40 zufälligen Eingaben. Das ist breiter, als NF-5c selbst prüft.
4. **Bedingungen**
   - `lexical`, `probe` (NF-5b) und `disambiguate` (NF-5c); echte Coding-Jobs über CCC1; höchstens 3 Runden.
   - Rückfragen beantwortet ein simulierter Besitzer mit dem Ergebnis der gemeinten Funktion auf der gefragten Eingabe. Danach läuft die Aufgabe erneut.
   - Höchstens zwei Antworten je Aufgabe, denn CCC1 prüft höchstens 4 Beispiele. Ist das Ergebnis der gemeinten Funktion kein einfacher Wert (etwa ein Fehler), gilt die Frage als nicht beantwortbar und wird getrennt gezählt.
5. **Entscheidungsregeln für NF-5c**
   - **H5c-1 Sicherheit:** 0 ungewollte Bestätigungen in allen Gruppen, auch nach der Antwort.
   - **H5c-2 Erkennung:** Rückfrage in mindestens 12 der 13 mehrdeutigen Bank-Paare.
   - **H5c-3 keine unnötigen Fragen:** höchstens 1 Rückfrage in Bank- oder echten Aufgaben, bei denen keine verschiedene Funktion die Beispiele erfüllt.
   - **H5c-4 Auflösung:** Mindestens 80 % der beantworteten Rückfragen (Bank und echte Aufgaben) enden mit der Bestätigung der gemeinten oder einer gleichwertigen Funktion.
   - **H5c-5 historisch, nur berichtet:** Nutzen im Vergleich zu NF-5b (12 von 14). Als Nutzen zählt eine richtige Bestätigung oder eine Rückfrage, die die gemeinte Funktion enthält.
   - Gegenproben: Alle müssen sich enthalten.
   - **NF-5c gilt als gestützt, wenn H5c-1 bis H5c-4 erfüllt sind.**
6. **Erwartung (vorab):**
   - Bei ganzen Prozent zeigt sich der Unterschied zwischen Abrunden und Runden erst bei Anteilen wie 0,125. Solche Eingaben erzeugt NF-5c vermutlich nicht.
   - Dort droht eine ungewollte Bestätigung, je nach Münzwurf.
7. **Vor dem Einfrieren korrigiert:** `-0` geht beim JSON-Transport aus der Sandbox verloren. Ein Beispiel `(-3) → 0` wäre für jede Funktion unerfüllbar gewesen. Erkannt wird `-0` jetzt an der typisierten Darstellung.
8. **Rauchprobe des Studienskripts:** nur mit einer eigenen, nicht eingefrorenen Aufgabe (`Gib den Text unverändert zurück`). Keine eingefrorene Aufgabe lief vor diesem Eintrag.
9. **Grenzen**
   - Die Bank habe ich selbst geschrieben, nach dem Mechanismus.
   - Es gibt nur wenige echte Aufgaben.
   - Der Besitzer ist simuliert.
   - Es gibt einen einzigen Lauf.

## 8f. Vorregistrierung NF-7: neuronales Modell gezielt für den Rest von KN-7 trainieren (eingetragen 23.10.2026, vor jedem Lauf)

**Anlass:** In NF-6b-lite (10.9) war die nachträgliche Mischung aus Byte-minGRU und KN-7 das beste Ergebnis des Zyklus. Der Gewinn wuchs mit dem neuronalen Modell, aber dieses wurde unabhängig vom Zählmodell trainiert.

1. **Mechanismus** (`NfOps.mixtureCrossEntropy`, `NfLm residual=λ`; Tests `NfResidualTest` 5/5)
   - Trainiert wird direkt auf dem Mischungsverlust −log(λ·p_nn(y) + (1−λ)·p_KN(y)).
   - Der exakte Gradient ist die Kreuzentropie, gewichtet mit der neuronalen Verantwortung λ·p_nn(y)/(λ·p_nn(y) + (1−λ)·p_KN(y)).
   - Positionen, die KN-7 schon gut vorhersagt, lehren das Netz also weniger.
   - λ beim Training ist fest auf 0,5 gesetzt und wird nicht abgestimmt.
   - **Bekannt:** Residual-Lernen mit einem n-Gramm-Modell (Li et al., Findings EMNLP 2022). Dort geschieht das im Log-Raum, hier im Wahrscheinlichkeitsraum. Die Log-Variante bräuchte je Position alle 256 KN-Wahrscheinlichkeiten und wird nicht geprüft.
2. **Neue Datentrennung NFC2** (Protokoll `nf7-nfc2-freeze.txt`)
   - Markdown-Dokumentation am signierten Stand `9fb4361c`, gefiltert und segmentiert wie LM1/NFC1, aufgeteilt nach SHA-256("nfc2" + Pfad).
   - Draußen bleiben: Protokolle, Lizenztexte, die Berichte des Experimentators und alle Dokumente, die bei LM1 (verbraucht) oder NFC1 (reserviert) TEST sind.

   | Teil | Dokumente | Segmente | Bytes | SHA-256 |
   |---|---:|---:|---:|---|
   | TRAIN | 71 | 2.506 | 422.096 | `e09a7cda…` |
   | VALIDATION | 5 | 180 | 34.313 | `73ec893c…0146` |
   | **TEST (eingefroren, unberührt)** | 7 | 219 | 38.346 | `3deec29e…68bb` |

   - Der Code bricht ab, wenn VALIDATION oder TEST vom Pin abweichen.
3. **Modelle**
   - G-M als Byte-Modell wie G-M-b in NF-6b-lite: minGRU, Vokabular 258, d = 64, 2 Schichten.
   - 6 Epochen, Seeds 1–3, gleiche Hyperparameter.
   - Je Seed zwei Trainingsarten: unabhängig (Kreuzentropie) und Residual (Mischungsverlust mit λ = 0,5).
   - KN-7 wird aus NFC2 TRAIN gebaut.
4. **Messung** (wie NF-6b-lite, `NfHybrid`, Epoche 6)
   - BPB auf NFC2 VALIDATION für KN-7, das neuronale Modell allein und die feste Mischung.
   - λ der Mischung wird per Cross-Fitting über die Dokument-Hälften bestimmt; für beide Trainingsarten gleich.
5. **Entscheidungsregeln (je Seed; alle Seeds müssen erfüllen)**
   - **H7-1 „Residual-Training hilft“:** Mischung(Residual) ≤ Mischung(unabhängig) − 0,02 BPB.
   - **H7-2 „deutlich besser als das Zählmodell“:** Mischung(Residual) ≤ KN-7 − 0,05 BPB. Das ist die Schwelle, die NF-6b-lite knapp verfehlte.
   - **Berichtet, ohne Regel:** das neuronale Modell allein (bei Residual-Training vermutlich schlechter), das gewählte λ, die Parameter und die Zeit je Token.
6. **TEST:** Nur wenn H7-1 und H7-2 gestützt sind, werden die Mischungen beider Trainingsarten und KN-7 einmal auf NFC2 TEST gemessen. Gilt dort für jeden Seed Residual < unabhängig, ist das Ergebnis bestätigt.
7. **Grenzen**
   - λ beim Training ist nicht abgestimmt.
   - VALIDATION ist klein (5 Dokumente).
   - Es gibt einen einzigen Zyklus, und die Zeit wird auf x86_64 gemessen, nicht auf ARM64.

**Nachtrag 8f-A (23.10.2026, vor jedem Modelllauf auf NFC2)**

Bis dahin lief auf NFC2 nur das Einfrieren (Aufteilungsstatistik, keine Modellbewertung).

- **Fehler im Plan**
  - Die Trainingswahrscheinlichkeiten des Zählmodells kämen von einem KN-7, das auf genau diesen Texten gebaut ist.
  - Dort zählt jedes 7-Gramm sich selbst mit, die Wahrscheinlichkeiten sind also überhöht.
  - Der Mischungsverlust würde das Netz dann fast überall entlasten, auch dort, wo KN-7 auf neuem Text schwach ist.
- **Korrektur, Hauptarm „Residual“ (`G-M-res`)**
  - Die Trainingswahrscheinlichkeiten werden über die Dokumente kreuzvalidiert (`NfResidual.crossFitted`).
  - Es gibt 5 Teile: Dokument-Index nach Pfad modulo 5.
  - Jedes Dokument wird von einem KN-7 aus den anderen 4 Teilen bewertet.
  - Test: `crossFittedProbabilitiesComeFromCountModelsWithoutTheDocumentsOwnFold`.
  - Für die Messung bleibt KN-7 aus ganz NFC2 TRAIN.
- **Zusatzarm ohne Regel (`G-M-resin`, `residualQ=insample`)**
  - Residual mit den In-Sample-Wahrscheinlichkeiten, wie ursprünglich geschrieben.
  - Er zeigt, ob die Kreuzvalidierung zählt.
- **Unverändert:** H7-1, H7-2 und die TEST-Regel gelten für den Hauptarm.
- **Präzisierung:** 9fb4361c ist die signierte Quelle 6706da14 plus deren Release-Protokoll (ein zusätzliches Dokument), nicht die signierte Quelle selbst.

## 9. Abschluss des Zyklus und TEST

Am Ende werden die je Experiment besten Varianten (nach den Regeln oben, nur VALIDATION) und ihre Baselines **einmal**
auf DOC TEST bzw. CODE TEST gemessen. Außergewöhnliche Verbesserungen werden vorher unabhängig wiederholt (neuer Seed)
und auf Datenleckage geprüft (Überschneidung von Segmenten zwischen TRAIN und VALIDATION/TEST).

## 10. Ergebnisse (eingetragen 23.10.2026, nach allen Läufen; nur VALIDATION, TEST unberührt)

Alle Zahlen berechnet `docs/evidence/nf-20261022/evaluate.py` aus den gesicherten Protokollen desselben Ordners;
die Ausgabe steht in `evaluation.txt`. Alle Läufe endeten mit `exit=0`, kein NaN. Die 162 Checkpoints liegen auf
`opus/nf-checkpoints-20261022`. Die Regeln aus den Abschnitten 2–8a wurden unverändert angewandt; was danach
analysiert wurde, ist als **nachträglich** gekennzeichnet.

### 10.1 NF-2 – Gated Delta im Sprachmodell (Größe S, 6 Epochen, DOC VALIDATION, Seeds 1–3)

| Variante | BPB (Mittel ± Std.) | einzelne Seeds |
|---|---|---|
| G-S (minGRU) | **2,9607 ± 0,0121** | 2,9518 · 2,9744 · 2,9558 |
| D-S (Gated Delta) | 2,9945 ± 0,0011 | 2,9954 · 2,9948 · 2,9933 |
| Dnb-S (ohne Schreibgate) | 2,9960 ± 0,0039 | |
| L-S (ohne Löschterm) | 3,0529 ± 0,0158 | |
| Dna-S (ohne Vergessensgate) | 3,2449 ± 0,0058 | |
| T-S (Transformer) | 3,4090 ± 0,0020 | |

- **„D besser als G“: widerlegt.** Umgekehrt ist G um 0,034 BPB besser, bei vollständiger Seed-Trennung.
- **Löschterm:** D ist um 0,058 besser als L (gestützt).
- **Vergessensgate:** unverzichtbar, D ist um 0,250 besser als Dna (gestützt).
- **Schreibgate:** kein messbarer Beitrag (Abstand 0,0015, nicht gestützt).

### 10.2 NF-2 – Assoziatives Gedächtnis (MQAR, 3.000 Schritte, Seeds 1–3)

| Variante | Parameter | Zustand (Floats) | N = 4 | N = 8 | N = 16 | N = 32 |
|---|---:|---:|---:|---:|---:|---:|
| D-S | 31.528 | 1.024 | 0,307 | 0,164 | 0,085 | 0,041 |
| G-S | 29.280 | 64 | 0,141 | 0,088 | 0,051 | 0,026 |
| L-S | 31.528 | 1.024 | 0,201 | 0,103 | 0,069 | 0,041 |
| T-S | 34.336 | – | 0,263 | 0,130 | 0,096 | 0,037 |
| G-M | 107.712 | 128 | 0,237 | 0,153 | 0,077 | 0,034 |
| G-X | 412.032 | 256 | 0,319 | 0,175 | 0,086 | 0,038 |

- **(b) D-S ≥ G-S + 0,10 bei N = 16: widerlegt.** Der Vorsprung beträgt nur +0,034.
- **Forschungsfrage D-S ≥ G-X + 0,10: widerlegt.** Beide liegen bei N = 16 gleichauf (0,085 zu 0,086).
- *Nachträgliche Beobachtung, keine Erfolgsbehauptung:* D-S erreicht mit 31.528 Parametern das Niveau von G-X, das 13-mal mehr Parameter hat. Bei N = 4 liegt D-S mehr als doppelt so hoch wie G-S.
- Alle Trefferquoten sind niedrig. 3.000 Trainingsschritte reichen für diese Aufgabe offensichtlich nicht; das ist eine Grenze des Versuchs, kein Befund über die Architektur.

**Bewertung NF-2 nach Abbruchregel 6:**
- D ist im Sprachmodell schlechter als G, im Gedächtnistest aber besser als G, wenn auch unter der vorgegebenen Schwelle.
- Damit ist D nicht in beiden Tests schlechter, und die Abbruchregel greift nicht.
- Die Stärke von D liegt dort, wo assoziatives Gedächtnis zählt, nicht im allgemeinen Sprachmodell.

### 10.3 NF-1 – Mehrgeschwindigkeits-Gedächtnis (CODE-Strom präquentiell, Basen G-S und D-S, je 3 Seeds)

| Arm (Basis G-S) | Lernzuwachs Strom | Vergessen DOC | Aktualisierungen |
|---|---:|---:|---:|
| FT (alle Gewichte) | +0,981 | +1,359 | 572 |
| REPLAY | +0,976 | **+0,346** | 572 |
| ADAPT (Rang 4, außerhalb der Basis) | +0,582 | +0,741 | 572 |
| SURPRISE | +0,468 | +0,698 | 246 |
| TWO | +0,581 | +0,732 | 572 |
| CARRY | +0,001 | 0 | 0 |

- **H1a (ADAPT ≥ 50 % des FT-Zuwachses und ≤ 25 % seines Vergessens): widerlegt** für beide Basen. Bei G-S erreicht ADAPT 59 % des Zuwachses, aber 55 % des Vergessens.
- **Das Vergessen der mittleren Ebene ist vollständig umkehrbar.** Mit abgeschaltetem Adapter ist DOC VALIDATION exakt wieder der Basiswert, und der Basis-Hash bleibt in allen Adapter-Armen unverändert. Genau dafür liegt die mittlere Ebene außerhalb des Checkpoints.
- **H1b (SURPRISE):** bei G-S knapp gestützt (0,468 bei geforderten ≥ 0,465, mit 43 % der Aktualisierungen), bei D-S knapp widerlegt (0,442 statt 0,445). Damit gilt H1b **nicht robust**.
- **H1c (CARRY senkt den Strom-BPB):** formal gestützt, aber vernachlässigbar (+0,001 bzw. +0,005 BPB).
- **H1d (TWO besser als beide einzeln): widerlegt.**
- Bester Arm gegen das Vergessen ist das bekannte Replay.

### 10.4 NF-3 – Adaptive Rechenverteilung (Byte-Latent, 6 Epochen, DOC VALIDATION)

| System | BPB | Anmerkung |
|---|---|---|
| BLT mit Entropie-Patches | 3,0060 ± 0,0167 | 0,080 ms/Byte (unter Parallellast) |
| BLT mit festen Patches (Länge 3) | 3,0448 ± 0,0096 | 0,067 ms/Byte |
| BPE G-S | 2,9607 ± 0,0121 | |
| BPE G-M | 2,3919 ± 0,0136 | |

- **Entropie-Patches schlagen feste Patches:** Abstand 0,039, vollständige Trennung. Die Teilhypothese „Rechenaufwand folgt der Informationsdichte“ ist damit **gestützt**.
- **≥ 0,03 BPB besser als BPE bei gleicher Rechenzeit: widerlegt.** Das Byte-Latent-System ist schlechter als G-S und braucht je Byte mehr Zeit. Bei unserer Größe lohnt sich der Ansatz gegenüber BPE nicht.

### 10.5 NF-4 – Ereignisgesteuerte Aktivität (faire Zeitmessung: ein Prozess, ein Thread, 5 verschränkte Runden)

| Variante | BPB | ms/Token (Median) | aktiver Anteil der MLP-Neuronen |
|---|---|---:|---:|
| G-S (GELU) | 2,9607 | 0,0435 | – |
| Gr-S, ReLU, λ = 0 | 2,9888 | 0,0336 | 0,436 |
| **Gr-S, ReLU, λ = 1e-3** | **2,9714** | **0,0320** | **0,131** |

- **Vorregistriertes Erfolgskriterium für λ = 1e-3 erfüllt:** BPB ≤ G-S + 0,02 (2,9714 ≤ 2,9807) und ms/Token ≤ 0,85 × G-S (0,0320 ≤ 0,0370).
- **Ehrliche Zerlegung des Zeitgewinns (nachträglich):**
  - Der Wechsel von GELU auf ReLU, ohne Strafe, spart schon 23 %; das liegt an der billigeren Aktivierung.
  - Die Sparsity allein (aktiver Anteil 0,44 → 0,13, also 87 % übersprungene Zeilen der zweiten MLP-Matrix) spart weitere 4,8 % (Mediane) bzw. 8 % (Minima). Das MLP ist nur ein Teil der Rechenzeit.
  - Die L1-Strafe verbessert zugleich den BPB (2,9888 → 2,9714).
- Gemessen auf JVM x86_64, nicht auf ARM64. Energie wurde nicht gemessen.

### 10.6 NF-5 / NF-6a – Lernender Reasoner (echte Coding-Jobs über CCC1; 14 reguläre Aufgaben, davon 6 neue, und 3 adversariale)

| Bedingung | richtig im 1. Versuch | davon neue | richtig in ≤ 3 Runden | ungewollt bestätigt | adversarial korrekt | Jobs |
|---|---:|---:|---:|---:|---:|---:|
| random | 0/14 | 0/6 | 1/14 | 0 | 3/3 | 44 |
| lexical | 5/14 | 0/6 | 5/14 | 0 | 3/3 | 35 |
| docs | 5/14 | 0/6 | 5/14 | 0 | 3/3 | 35 |
| gated | 5/14 | 0/6 | 5/14 | 0 | 3/3 | 35 |
| ungated | 5/14 | 0/6 | 5/14 | 0 | 3/3 | 35 |

- **„docs besser als lexical“: nicht gestützt.** **NF-6a: nicht gestützt.** Keine Bedingung löst eine der 6 neuen Aufgaben.
- Die Sicherheitseigenschaften halten in allen 85 Läufen: 0 ungewollte Bestätigungen, 3/3 Enthaltungen bei falschen Zielen.
- **Fehleranalyse (nachträglich, `nf5-gold-rank-posthoc.txt`):**
  - Die gemeinte Funktion lag bei keinem Ranker unter den ersten drei.
  - Die Dokumentationsassoziation verbessert den Rang mancher Aufgaben: `clip` 21 → 7, `canonical` 29 → 13, `context-cost` 25 → 17.
  - Bei anderen verschlechtert sie ihn: `json-digest` 13 → 33, `memory-relation` 28 → 41.
  - Ein Verfahren, das nur Wörter zuordnet, reicht für diese Ziele nicht. Erst das Verhalten einer Funktion auf den Beispielen trennt die Kandidaten.
  - Nächster falsifizierbarer Schritt: Kandidaten **vor** dem teuren Job mit den Beispielen des Ziels in einer abgeschotteten Probe vorprüfen, ohne Dateizugriff und mit Zeitlimit.

### 10.7 Zusammenfassung

| Experiment | vorregistrierte Hauptaussage | Ergebnis |
|---|---|---|
| NF-2 Sprachmodell | D besser als G | **widerlegt** (G besser) |
| NF-2 Gedächtnis | D-S ≥ G-S + 0,10 | **widerlegt** (+0,034); D-S erreicht das 13-mal größere G-X |
| NF-1 | ADAPT schützt alte Fähigkeiten | **widerlegt**; umkehrbar per Abschalten, Basis unverändert |
| NF-3 | Entropie-Patches > feste Patches | **gestützt** |
| NF-3 | BLT > BPE bei gleicher Zeit | **widerlegt** |
| NF-4 | BPB ≤ +0,02 und ≤ 0,85 Zeit | **erfüllt**, vor allem durch ReLU, Sparsity allein 5–8 % |
| NF-5 | Dokumentassoziation > lexikalisch | **nicht gestützt** |
| NF-6a | Quittungs-Gating > alles konsolidieren | **nicht gestützt** |
| NF-5b | Vorprüfung: ≥ 3 mehr im 1. Versuch, 0 ungewollt, 3/3 Enthaltungen | **widerlegt**: +7, aber 1 ungewollte Bestätigung (10.8) |
| NF-6b-lite | feste Mischung ≤ KN-7 − 0,05 (alle Seeds); Verankerung ≤ fest − 0,02 | **beide widerlegt** (G-M-b: 0,042–0,052; Verankerung ±0,0006), aber bestes BPB des Zyklus (10.9) |
| NF-5c | Rückfrage statt falscher Bestätigung (H5c-1 bis H5c-4) | **gestützt**: 0 falsche Bestätigungen (NF-5b auf der Bank: 7), 12/13 erkannt, 0 unnötige Fragen, 9/11 aufgelöst (10.10) |
| NF-7 (NFC2) | Residual-Training: Mischung ≤ unabhängig − 0,02 und ≤ KN-7 − 0,05 (alle Seeds) | **beide widerlegt**: Residual-Mischung 0,010–0,017 *schlechter* als unabhängig, nur 0,021–0,023 besser als KN-7; NFC2 TEST nicht ausgeführt (10.11) |
| NF-6b | Zählmodell-verankertes Delta-Gedächtnis | **offen**, nicht implementiert |

Der NFC1-TEST wurde nicht ausgeführt. Nach Abschnitt 9 kommen dafür zuerst die bestätigten Varianten in Frage: NF-4 λ = 1e-3 gegen G-S und NF-3 Entropie gegen feste Patches. Die außergewöhnliche Wiederholung mit neuem Seed und die Leckprüfung stehen davor noch aus.

### 10.8 NF-5b – Ausführungsgeleitete Vorprüfung (Lauf 23.10.2026, 01:52–01:53 UTC, Commit `1bfb5d00`)

Gleiche 17 Aufgaben, gleiche CCC1-Laufzeit, echte Coding-Jobs. Auswertung: `evaluate.py`, Protokolle `nf5b-study-run.txt` und `nf5b-results.json`.

| Bedingung | richtig im 1. Versuch | davon neue | richtig in ≤ 3 Runden | ungewollt bestätigt | adversarial korrekt | Coding-Jobs | Dauer gesamt |
|---|---:|---:|---:|---:|---:|---:|---:|
| lexical | 5/14 | 0/6 | 5/14 | 0 | 3/3 | 35 | 24,2 s |
| probe | 12/14 | 6/6 | 12/14 | **1** (`canonical`) | 3/3 | 19 | 29,2 s |

- **Nach der vorab festgelegten Regel: widerlegt.**
  - Kriterium 1 erfüllt: 7 Aufgaben mehr statt der geforderten 3.
  - Kriterium 3 erfüllt: 3/3 korrekte Enthaltungen.
  - Kriterium 2 verletzt: Es gab eine ungewollte Bestätigung, und schon eine genügt.
- **Der Fall `canonical`.** Das Ziel lautet „Erzeuge die kanonische JSON-Schreibweise eines Werts: ("x") → "\"x\"", (5) → "5" und (true) → "true"“.
  - Bestätigt wurde `capability-research.mjs#canonical` statt der gemeinten `development-backlog.mjs#canonical`.
  - Beide liefern auf den drei Beispielen dasselbe. Der Vertragstest prüft genau diese Beispiele und quittiert deshalb CONFIRMED.
  - Die Funktionen sind trotzdem verschieden (nachträglich geprüft):
    - `1.5` ergibt `"1.5"` bzw. einen Fehler;
    - ein einzelnes Surrogat wird maskiert bzw. abgelehnt;
    - 30 Ebenen Verschachtelung ergeben einen Fehler bzw. ein Ergebnis.
  - Die lexikalische Rangfolge hat diese Funktion nie vorgeschlagen. Erst die Vorprüfung macht jede Funktion, die alle Beispiele erfüllt, zum Spitzenkandidaten.
- **Nachträglich, nicht vorregistriert (`nf5b-ambiguity-posthoc.txt`, `scripts/nf5b-ambiguity-posthoc.mjs`).** In 4 von 15 Aufgaben mit Beispielen erfüllen mehrere Funktionen alle Beispiele:
  - `sha256`: vier Funktionen. Drei waren vorab als gleichwertig genannt; die vierte, `repair-diagnostics.mjs#sha256`, rechnet ebenfalls SHA-256.
  - `clip`: zwei Funktionen, beide vorab als gleichwertig genannt.
  - `canonical`: zwei Funktionen, siehe oben.
  - `json-digest`: zwei Funktionen. `relational-memory-data.mjs#fingerprint` hasht `JSON.stringify` ohne sortierte Schlüssel und weicht bei `{b:1,a:2}` ab. Hier hat die Vorprüfung nur dank der lexikalischen Reihenfolge richtig gewählt.
  - Die zweite verfehlte Aufgabe ist `kotlin-keyword`. Die gemeinte Funktion besteht die Beispiele, wird aber wie in 10.6 vom Fähigkeits-Gate nicht zugelassen.
- **Bewertung**
  - Die Vorprüfung trennt Kandidaten nach ihrem Verhalten. Genau das war der Engpass von NF-5.
  - Sie verstärkt aber ein bekanntes Problem des Programmierens aus Beispielen: Wenige Beispiele legen eine Funktion nicht fest.
  - Die Sicherheitsgrenze bleibt die Quittung, und die ist nur so gut wie der Vertrag.
- **Nächster falsifizierbarer Schritt (NF-5c, noch nicht vorregistriert).** Wenn mehrere verschiedene Funktionen alle Beispiele erfüllen, sucht das System selbst nach einer Eingabe, die sie unterscheidet, wie beim orakelgeführten Synthetisieren (Jha et al., ICSE 2010). Statt eine Funktion zu bestätigen, fragt es den Besitzer nach dem gewünschten Ergebnis. Findet es keine solche Eingabe, meldet es die Funktionen als verhaltensgleich im geprüften Bereich.
- Weder eine Quittung noch ein Gewicht noch eine produktive Datei wurde verändert. Die Vorprüfung lief nur auf Kopien der Module.

### 10.9 NF-6b-lite – Zählmodell-verankerte Mischung (Lauf 23.10.2026, 01:46–02:06 UTC, Jar aus `8f175ab1`)

Protokolle `nf6b-byte-models.txt` und `nf6b-hybrid.txt`, Startskript `run-chain3.sh.txt`, Auswertung `evaluate.py`.

| Variante | neuronal allein | KN-7 | feste Mischung (Seeds 1–3) | verankert (Seeds 1–3) | λ (Hälfte 0 / Hälfte 1) |
|---|---:|---:|---|---|---|
| G-S-b (33 k Parameter) | 3,084 | 1,978 | 1,959 · 1,959 · 1,961 | 1,958 · 1,959 · 1,961 | 0,075 / 0,050 |
| G-M-b (116 k Parameter) | 2,438 | 1,978 | 1,926 · 1,930 · 1,936 | 1,926 · 1,930 · 1,936 | 0,200–0,225 / 0,150–0,175 |

- **H6b-1 (feste Mischung ≤ KN-7 − 0,05 bei allen Seeds): widerlegt.**
  - G-S-b gewinnt nur 0,017–0,019.
  - G-M-b gewinnt 0,042–0,052. Nur Seed 1 erreicht die Schwelle.
- **H6b-2 (verankert ≤ fest − 0,02): widerlegt.**
  - Die Verankerung ändert höchstens 0,0006 BPB.
  - Die gelernten Gewichte für Zählung und Ordnung bleiben nahe null. KN-7 nutzt seine Zählungen über die Interpolation offenbar schon selbst.
- **Nach Abschnitt 8c kommt deshalb keine Mischung in den NFC1-TEST.**
- **Berichtet, ohne Regel**
  - Die Mischung ist das beste Ergebnis dieses Zyklus: 1,926–1,936 BPB. KN-7 allein erreicht 1,978, das beste BPE-Modell G-M 2,392.
  - Der Gewinn wächst mit der Stärke des neuronalen Modells: G-S-b (allein 3,08) bringt 0,018, G-M-b (allein 2,44) bringt 0,047.
  - Das neuronale Modell bekommt rund 20 % Gewicht. Es ergänzt das Zählmodell, ersetzt es aber nicht.
- **Folgerung für den nächsten Zyklus**
  - Bisher wird das neuronale Modell unabhängig vom Zählmodell trainiert und erst danach gemischt.
  - Naheliegend ist ein Training gezielt auf das, was KN-7 verfehlt: Residual-Lernen mit einem n-Gramm-Modell (Li, Cai, Xu, Watanabe, Findings of EMNLP 2022, arXiv 2210.14431). Übernommen wird nur die Idee, kein Code.
  - Das wird als eigener Zyklus mit neu eingefrorenen Daten vorregistriert.
- Zeit je Token (ein Thread, unter Last): G-S-b 0,04 ms, G-M-b 0,10 ms, dazu KN-7.
- Die 36 neuen Checkpoints liegen auf dem Datenzweig.

### 10.10 NF-5c – Rückfrage statt falscher Bestätigung (Lauf 23.10.2026, 07:35–07:43 UTC, Commit `b895ed7e`)

Protokolle: `nf5c-study-run.txt` und `nf5c-results.json`; Aufgaben `nf5c-tasks.json` (SHA-256 `30066739…`). Die Urteile berechnen das Studienskript und `evaluate.py` unabhängig voneinander; beide kommen zum selben Ergebnis.

| Gruppe | lexical: richtig / falsch bestätigt | NF-5b: richtig / falsch bestätigt | NF-5c: richtig / falsch bestätigt / Rückfragen | Coding-Jobs (lexical / NF-5b / NF-5c) |
|---|---|---|---|---|
| Bank (15) | 5 / 3 | 8 / **7** | 2 / **0** / 13 | 29 / 15 / 2 |
| echte Funktionen (4) | 1 / 0 | 2 / 0 | 2 / 0 / 0 | 12 / 8 / 8 |
| historisch (14, nur Vergleich) | 5 / 0 | 12 / 1 | 11 / **0** / 2 | 35 / 19 / 17 |
| Gegenproben (5) | alle enthalten | alle enthalten | alle enthalten | – |

**Nach den vorab festgelegten Regeln ist NF-5c gestützt:**
- **H5c-1:** keine einzige falsche Bestätigung in allen Gruppen, auch nach den Antworten des simulierten Besitzers.
- **H5c-2:** Rückfrage in 12 von 13 mehrdeutigen Bank-Paaren.
- **H5c-3:** keine unnötige Frage.
- **H5c-4:** 9 von 11 beantworteten Rückfragen enden mit der gemeinten Funktion. 2 fragen nach zwei Antworten weiter (Schlüssel, Runden; dort erfüllen bis zu 6 Funktionen die Beispiele). Keine endet falsch.

**Einzelbefunde**
- **Nicht erkannt wurde wie vorhergesagt das Prozent-Paar** (Runden gegen Abrunden; 23 Eingaben ohne Unterschied). NF-5c bestätigte die lexikalisch erste Variante, und die war zufällig die gemeinte. Das Restrisiko bleibt also: Unterschiede außerhalb der erzeugten Eingaben bleiben unsichtbar.
- **NF-5b bestätigt auf der Bank 7 von 15 Mal die falsche Funktion,** zum Beispiel `parseWholeStrict` für „Runde eine Zahl“ und für „Quadrat“, weil die Beispiele (0) → 0 und (1) → 1 das zulassen. Die reine Vorprüfung ist ohne Rückfrage also gefährlich, sobald die Beispiele schwach sind.
- **Nicht beantwortbar waren 2 Rückfragen auf der Bank und 1 historische** (`canonical`): Dort ist das gewollte Ergebnis ein Fehler oder `undefined`, und das kann die CCC1-Vertragssprache nicht ausdrücken. Es wurde nichts bestätigt.
- **Historisch** löst die Rückfrage `clip` nach einer Antwort richtig auf (`("\n", 4) → "\n"`). `canonical` wird nicht mehr falsch bestätigt. Der Nutzen liegt bei 13 von 14 statt 12 von 14.
- **Gestaltungsfehler der echten Aufgaben:** 2 der 4 geschürften Funktionen (`editDistance`, `insertImport`) liegen in `repair-strategies.mjs`. Dieses Modul schützt das Fähigkeits-Gate vor Eigenveränderung. Alle drei Bedingungen enthielten sich dort korrekt. Das Schürfwerkzeug hätte geschützte Module ausschließen müssen.
- **Kosten:** NF-5c spart auf der Bank Coding-Jobs (2 statt 15), weil vor jedem Job gefragt wird. Die Mehrdeutigkeitsprüfung kostet je Aufgabe rund 0,2 s.

**Grenzen:** Die Bank habe ich nach dem Mechanismus selbst geschrieben. Der Besitzer ist simuliert. Es gibt einen einzigen Lauf und nur 2 lösbare echte Aufgaben ohne Mehrdeutigkeit.

**Nächste Schritte**
- Erwartete Fehler müssen als Beispiel ausdrückbar werden. Dafür muss der Vertrag „soll mit Fehler X abbrechen“ zulassen; das ist eine Protokolländerung und braucht die Freigabe des Besitzers.
- Unterscheidende Eingaben gezielter erzeugen, etwa Brüche bei Zahlenfunktionen.
- Der Worker stellt Rückfragen bereits dauerhaft an den Besitzer (Meilenstein E).

### 10.11 NF-7 – Restmodell gegen Kneser-Ney 7 (Lauf 23.10.2026, 07:59–08:36 UTC, Jar aus `524c1afc`)

**Ablauf** (Belege in `docs/evidence/nf-20261022/`)
- Training: `nf7-independent.txt`, `nf7-residual.txt`, `nf7-residual-insample.txt`.
- Mischung: `nf7-hybrid.txt`.
- Befehle: `run-nf7.sh.txt`.
- Unabhängige Nachrechnung: `evaluate.py` → `nf7-evaluate-20261023.txt`.
- Die 54 Checkpoints (3 Arme × 3 Seeds × 6 Epochen) liegen auf dem Datenzweig `opus/nf-checkpoints-20261022` (`fab239d3`). Ihre Dateinamen sind die SHA-256-Werte, vor dem Hochladen geprüft.

**NFC2 VALIDATION** (5 Dokumente, 34.313 Bytes; λ per Cross-Fitting über die Dokument-Hälften 2/3), Epoche 6. Werte in BPB, je Seed 1, 2, 3.

| Arm | neuronal allein | feste Mischung mit KN-7 | λ (Hälfte 0/1) |
|---|---|---|---|
| KN-7 allein | – | 1,8856 | – |
| unabhängig (`G-M`) | 2,393 · 2,409 · 2,431 | **1,8474 · 1,8502 · 1,8519** | 0,15–0,20 |
| Residual, kreuzvalidierte Ziele (`G-M-res`, Hauptarm) | 3,990 · 4,076 · 4,063 | 1,8640 · 1,8646 · 1,8623 | 0,075–0,10 |
| Residual, In-Sample-Ziele (`G-M-resin`, ohne Regel) | 8,280 · 8,104 · 8,244 | 1,8852 · 1,8857 · 1,8851 | 0–0,025 |

**Regeln (je Seed)**
- **H7-1** (Residual-Mischung ≤ unabhängige Mischung − 0,02): Gewinn −0,0166, −0,0144, −0,0104 → **widerlegt**. Das Residual-Training macht die Mischung schlechter.
- **H7-2** (Residual-Mischung ≤ KN-7 − 0,05): Gewinn +0,0216, +0,0210, +0,0233 → **widerlegt**.
- **TEST:** Nach Abschnitt 8f, Punkt 6 wird NFC2 TEST deshalb **nicht** ausgeführt. Er bleibt unberührt; die Sperrdatei von `hybrid-test` existiert nicht.

**Was die Messung zeigt**
- **Der Nachtrag 8f-A war nötig.** Mit In-Sample-Zielen lernt das Netz praktisch nichts Brauchbares: allein 8,1–8,3 BPB, Mischung = KN-7. Kreuzvalidierte Ziele bringen 0,021–0,023 BPB mehr. Ohne den Nachtrag wäre das Experiment an einem Konstruktionsfehler gescheitert, nicht an der Idee.
- **Bei dieser Modellgröße hilft Residual-Training trotzdem nicht.** Das unabhängig trainierte Netz ergänzt KN-7 besser: Mischung 1,847–1,852 gegen 1,862–1,865. Plausibel ist folgende Erklärung, sie ist aber nicht getestet: Mit λ = 0,5 beim Training verteilt das Netz seine Wahrscheinlichkeit auf Bytes, die KN-7 schlecht vorhersagt. Bei der Messung wählt das Cross-Fitting jedoch λ ≈ 0,1, sodass dieser Teil kaum zählt. λ beim Training war vorab festgelegt und wurde nicht abgestimmt.
- **Das unabhängige Modell erreicht auch auf NFC2 nicht die NF-6b-Schwelle.** Sein Gewinn gegenüber KN-7 beträgt 0,034–0,038 BPB, wie in NF-6b-lite, das 0,042–0,052 erreichte.

**Grenzen**
- Die Zeiten je Token (`SPEED`) liefen neben anderer Arbeit in der Sitzung und sind nicht vergleichbar.
- Alle Arme haben dieselbe Architektur (115.904 Parameter, 128 Zustandswerte), die Inferenzkosten sind also gleich.
- VALIDATION ist klein (5 Dokumente). Es gab einen Zyklus und keine ARM64-Messung.

**Konsequenz:** Residual-Training wird bei dieser Modellgröße nicht weiterverfolgt. Der nächste Schritt im Radar ist ein stärkeres Zählmodell: ∞-Gramm mit Suffix-Array (NF-8).

