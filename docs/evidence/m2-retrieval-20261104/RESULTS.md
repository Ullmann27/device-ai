# M2.8 / M2.9 – Append-only-Ledger und Abruf-Experiment (2026-11-04)

Messumgebung: Box-JVM (JDK 17, Unit-Tests), nicht Fold7. Je 2 Läufe.

## M2.8 Ledger-Schreiblatenz (ein `append` inkl. Lesen/Validieren, 35 Messungen nach 5 Warm-up)
| Einträge | vorher LEX3 p50 / p95 (ms) | nachher LEX4 p50 / p95 (ms) |
|---|---|---|
| 100 | 7,0–7,3 / 9,5–11,3 | 0,21 / 0,47–0,60 |
| 1000 | 19,5–20,9 / 24,7–27,0 | 1,2–1,4 / 1,7–1,8 |
| 4000 | 72,9–74,5 / 83,3–85,7 | 3,3–3,9 / 4,4–4,9 |

LEX4: Header `LEX4`+Version 4; Datensatz = `[len][PUT|DEL payload][SHA-256(prevHash‖payload)]` (Hash-Kette).
Halber Datensatz am Ende wird erkannt und abgeschnitten; beschädigter vollständiger Datensatz → Abbruch (fail closed).
Kompaktierung (voller Neuschrieb + LKG-Sicherung), wenn Log-Datensätze > max(64, 2×lebende). LEX2/LEX3 werden gelesen und beim ersten Schreiben nach LEX4 kompaktiert.
Der verbleibende Lesepfad hasht die ganze Datei (SHA-256), das Dekodieren entfällt bei unverändertem Inhalt (Cache mit inkrementellem Digest).
Datei ist ~15 % größer (1 105 858 statt 956 414 Bytes bei 4040 Einträgen) wegen 4+32+1 Bytes pro Datensatz.

## M2.9 Abruf (1000 Erinnerungen: 44 Ziele, 3 Hypothesen, 953 Distraktoren; 44 Fragen mit Umschreibungen, Tippfehlern, Komposita, Zeitbezug)
| Variante | recall@1 | recall@3 | MRR | p95 ms | Index (≈Bytes) | Indexaufbau ms |
|---|---|---|---|---|---|---|
| A BM25-de (Normalisierung, Stamm, Komposita, Tippfehler-Fallback) | 0,773 | 0,818 | 0,792 | 0,06 | 119 380 | 7–33 |
| A + Zeitgewichtung | 0,818 | 0,841 | 0,830 | 0,08 | 119 380 | 7 |
| B Zeichen-3–5-Gramm-Hash (2^15), TF-IDF, Kosinus | 0,841 | 0,909 | 0,871 | 0,63 | 624 856 | 38 |
| **B + Zeitgewichtung (Standard)** | **0,886** | **0,977** | **0,924** | 0,63 | 624 856 | 34 |

Gewinner: **B + Zeit**. Grund: Zeichen-n-Gramme fangen Tippfehler („Zahnartz“, „Waschmaschiene“) und Komposita („Zahnarzttermin“ ↔ „Termin beim Zahnarzt“, „Radschloss“ ↔ „Fahrradschloss“) ohne Wörterbuch; BM25 scheitert dort (Kompositum-Split braucht beide Teile im Korpus). Die Zeitgewichtung (gestern/vorgestern/heute/letzte Woche/letzter Monat/vor N Tagen) löst die Duplikat-Paare (Brot gestern vs. vor 10 Tagen).
Preis: ~10× Latenz (0,63 ms, weiterhin gering) und ~5× Speicher (~0,6 MB bei 1000).
Restfehler B+Zeit: „Welche Blumen mag Lisa?“ (Rang 3), „Wo habe ich geparkt?“ (kein lexikalischer Bezug), „gestern mit Thomas“ (Rang 3), „letzten Monat mit Thomas“ / „bei Mama in Linz“ (Rang 4).
Hypothesen: in allen Treffern wird `confirmed` unverändert übernommen; Hypothesen-Dokumente kamen nie als bestätigt zurück (Test).
Einschränkung: Testset selbst erstellt, synthetische Distraktoren, kein Blind-Holdout; Zahlen sind ein Richtwert, kein Beweis.
