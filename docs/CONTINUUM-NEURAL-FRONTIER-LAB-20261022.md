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
| NF-6b | Zählmodell-verankertes Delta-Gedächtnis | **offen**, nicht implementiert |

Der NFC1-TEST wurde nicht ausgeführt. Nach Abschnitt 9 kommen dafür zuerst die bestätigten Varianten in Frage: NF-4 λ = 1e-3 gegen G-S und NF-3 Entropie gegen feste Patches. Die außergewöhnliche Wiederholung mit neuem Seed und die Leckprüfung stehen davor noch aus.
