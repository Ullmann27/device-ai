# Continuum: drei Tuning-Bots — geprüfte Runde vom 2026-11-04

## Ergebnis und Auftrag

**Drei bestehende Funktionen wurden gezielt optimiert. Die kombinierte Quellfassung besteht die unabhängige Host-Gegenprüfung.** Die Arbeit liegt auf `tuning/continuum-three-bots-20261104` in [Draft-PR #511](https://github.com/Ullmann27/device-ai/pull/511).

Genau drei Helfer bearbeiteten getrennte Bereiche: `tuning_model_inference`, `tuning_memory_context` und `tuning_runtime_workers`. Ihre erste begrenzte Runde ist abgeschlossen. Es wurde kein dauerhaft laufender Hintergrunddienst eingerichtet.

Gemessen wurden einzelne vorhandene Komponenten auf einem geteilten Linux-Host. Eine Überlegenheit gegenüber Frontier-Modellen, bessere Antwortqualität oder eine Beschleunigung der gesamten Android-App ist damit **nicht nachgewiesen**. Status: `HOST_VERIFIED / DRAFT`. Android-/Fold7-Abnahme und Produktivfreigabe sind nicht enthalten.

## Feste Basis und veröffentlichte Beiträge

- Repository: `Ullmann27/device-ai`.
- Ausgangspunkt: PR #491, `codex/astra-jarvis-takeover-20261104`.
- Basiscommit: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.
- Basisbaum: `72e62267ba22309d60ad13abb3e2a5c6a155e544`.
- Unabhängig getesteter lokaler Gesamtstand: `9c111c3dac8f655eb82071289c8294689b909e2d`.
- Inhaltsgleicher veröffentlichter Gesamtstand: `db88716f899953c0bdf0fdf8430972fd6addac0d`.
- Identischer Git-Baum beider Gesamtstände: `5d59380abc5339e2232fd84ddc3a5cc20eb0f12a`.

Die unterschiedliche Commit-ID entsteht durch neue Metadaten bei der Veröffentlichung. Die vollständigen Git-Bäume wurden vor jedem Commit auf Gleichheit geprüft. Der anschließende Abschlusscommit ergänzt ausschließlich Berichte und Prüfnachweise.

| Beitrag | Veröffentlichter Commit | Ursprünglicher lokaler Bot-Commit |
| --- | --- | --- |
| Worker-Auswahl, Regressionen und erster Prüfstand | [`963ac4b`](https://github.com/Ullmann27/device-ai/commit/963ac4b37c271380f113469d8610d3937ac10d45) | `0e0ba1b0f9394a7af8fb626b70a1338f4279f633` |
| Worker-Messungen und korrigierter Messaufbau | [`ab95ee3`](https://github.com/Ullmann27/device-ai/commit/ab95ee346e365588118828713e075c7c14151edd) | `1e3787ca2c248f8261d946ee7ef87db382ee48fc` |
| LM1-Sampler | [`f7c3076`](https://github.com/Ullmann27/device-ai/commit/f7c30760005c3c2614c8abe623304f27b6bcd1ef) | `0f7417e606a524ff0f357f5c1d3e40faac914997` |
| Kontext-Bytebudget | [`db88716`](https://github.com/Ullmann27/device-ai/commit/db88716f899953c0bdf0fdf8430972fd6addac0d) | `92bd349200b461d8465cbf360e95c9f941f84c50` |

## Bot 1: bestehende LM1-Inferenz

**Produktivänderung:** `LmEval.sample` in `learning/lm/LmModel.kt`. Bei `temperature <= 0` sucht eine Schleife den ersten maximalen Wert. Die vollständige Sortierung des Vokabulars und ihre temporären Sammlungen entfallen. Float-Ordnung, Gleichstände, Top-k-Validierung, Exceptions und Zufallszustand bleiben in den geprüften Fällen identisch.

Beispiel des abschließenden Host-Mikrobenchmarks, Vokabular 512, zufällige Werte, deterministische Auswahl:

- Thread-CPU-Median: **61,615 → 0,566 µs je Sampler-Aufruf**.
- Allokation: **14.120,055 → 0,055 Bytes je Aufruf**. Der Rest entspricht festem Messaufwand von 56 Bytes pro Batch.
- 5 JUnit-Methoden enthalten **35.624 direkte Paritätsfälle** und **50 Tokenvergleiche** über echte kleine Transformer-/minGRU-Vorwärtsläufe mit Unicode-Prompts.

**Bekannter Nachteil:** Der Kontrollfall mit Temperatur 1 und Top-k 50 allokiert konsistent **24 Bytes mehr je Aufruf (+0,152 %)**. Sein CPU-Median liegt bei 69,546 → 71,028 µs (+2,13 %); der geteilte Host erlaubt keine belastbare Aussage über die genaue Zeitdifferenz. Der Allokationsnachteil war im ursprünglichen Vergleich algorithmisch gleicher Implementierungen nicht vorhanden und wird nicht als vorbestehendes Messrauschen erklärt.

**Aufnahmeentscheidung des Koordinators:** als isolierter, prüfbarer Draft übernommen. Die bestehende Standardanfrage in `LmRuntime` ist deterministisch und die entsprechende Sortierarbeit entfällt. Der gemessene Nachteil des Zufallsmodus bleibt Bestandteil der Bewertung. Dies ist keine pauschale Regressionsfreiheit oder Gerätefreigabe.

Aufrufer sind `LmRuntime.generate` und `LmEval.generate`. Im untersuchten `app/src/main/java` wurde kein externer Produktionsaufrufer gefunden, der `LmRuntime` lädt. Eine Beschleunigung des aktiven Gemma-/LiteRT-Chatpfads wird deshalb nicht behauptet.

[Vollständiger LM-Bericht mit allen neun Szenarien und Rohdaten](../../evidence/tuning-inference-20261104/RESULTS.md).

## Bot 2: bestehender Kontextaufbau

**Produktivänderung:** `AiContextCompiler.compile/appendSection`. Bereits aufgenommene UTF-8-Bytes werden fortlaufend gezählt. Der vollständige bisherige Puffer muss nicht für jeden Abschnitt erneut kopiert und kodiert werden. Der Zähler wächst ausschließlich nach erfolgreicher Aufnahme; ASCII-Abschnittsgrenzen verhindern neue Surrogatpaare über eine Anfügegrenze hinweg.

| Vorab festgelegte Last | Erzeugte Heap-Bytes je Aufruf: vorher → nachher | Verringerung |
| --- | ---: | ---: |
| Minimaler Kontext | 15.856 → 14.912 | 5,95 % |
| Gemischtes ASCII, 16k-Bytebudget | 845.048,1 → 723.384 | 14,40 % |
| Gemischtes Unicode, 64k-Bytebudget | 2.288.028,7 → 1.604.999,7 | 29,85 % |
| Verworfene Erinnerungsgruppe, 8k-Bytebudget | 449.544 → 410.416 | 8,70 % |

Redaction, ganze Abschnitte, Reihenfolge, Negationen, Privacy-/Zeitregeln, Quellen und Erinnerungs-IDs bleiben erhalten. **21 Tests** bestehen sowohl mit dem Original als auch mit dem Kandidaten. **462 direkte Vergleiche** bestehen: 408 vollständige Kontexte und 54 identische Exceptions, einschließlich UTF-16/UTF-8, Metadaten, `truncated` und Clock-Aufrufzahl.

**Zeitmessungsgrenze:** Der ursprüngliche minimale Kontrollfall zeigte bei den getrennten Wall-Medianen einen Nachteil von 34,55 %. Genau ein begrenzter Nachtest mit unverändertem Code und längerem Warmup ergab niedrigere Wall-/CPU-Mediane (6,70 % / 4,25 %) und durchgehend geringere Allokation (5,55 %). Beide Läufe bleiben erhalten. Der belastbare Befund ist weniger erzeugte Heap-Allokation; eine allgemeine Latenz-, Spitzen-RAM- oder Tokenreduktion ist nicht belegt.

[Vollständiger Kontext-Bericht und Reproduktion](TUNING-MEMORY-CONTEXT-HANDOFF-20261104.md).

## Bot 3: bestehende lokale Worker-Auswahl

**Produktivänderung:** `nextEligible` in `.github/continuum/development-backlog.mjs`. Die Auswahl behält während eines Durchlaufs die beste zulässige Aufgabe, statt alle Treffer zu sammeln und vollständig zu sortieren. Eligibility, Filterreihenfolge, Priorität, Erstellzeit, ID-Reihenfolge und exakte Ergebnisobjektidentität bleiben für gültige kanonische Backlogs gleich.

Bei 500 zulässigen Aufgaben mit gemischten Prioritäten:

- Median je Auswahl: **81,6812 → 15,4194 µs**.
- Median der gepaarten Beschleunigungsfaktoren: **5,258**; kein Quotient der zwei getrennten Mediane.
- Ordnungsvergleiche: **3.833 → 499**, entsprechend **86,98 % weniger**.
- **52/52 Tests** und **40.960 direkte Paritätsfälle** bestehen.

**Grenze:** Für stark gefilterte Fälle ist kein allgemeiner Zeitgewinn nachgewiesen. Einige Kontrollfälle bevorzugen das Original. Die Abhängigkeitssuche bleibt unverändert; die gesamte Funktion wird nicht pauschal als linear bezeichnet. Tatsächliche Aufgabenausführung, Persistenz und vollständige Worker-Zyklen wurden nicht gemessen.

Der erste Messlauf bleibt als vorläufig dokumentiert: Eine zunächst als selektiv bezeichnete Last hatte keine Treffer; Vergleichszähler zwischen Zeitmessungen konnten JIT-Rückmeldungen beeinflussen. Der abschließende Aufbau behält den Nulltrefferfall, ergänzt tatsächlich passende Aufgaben und zählt Vergleiche erst nach allen Zeitmessungen. Zwischen den Läufen wurde kein Produktivcode geändert.

[Vollständiger Worker-Bericht mit allen 20 Fällen](../../evidence/three-tuning-bots-20261104/workers/README.md).

## Unabhängige Gegenprüfung des zusammengeführten Standes

Der Koordinator hat nach Übernahme der drei Beiträge die relevanten Tests und Paritätsprüfungen erneut ausgeführt. Es wurden keine weiteren Zeitmessschleifen gestartet.

| Prüfung auf dem kombinierten Kandidaten | Ergebnis |
| --- | ---: |
| Node-Teamleiter-/Backlog-Suites | 52/52 bestanden |
| Kontext-Suites, tatsächlicher 40-Dateien-Quellgraph | 21/21 bestanden |
| LM1-Sampling-Suite | 5/5 bestanden |
| **Eindeutige Kandidaten-Testmethoden insgesamt** | **78/78 bestanden** |
| Zusätzlich: Kontext-Suites mit Originalcompiler | 21/21 bestanden |
| Direkte Worker-Parität | 40.960/40.960 bestanden |
| Direkte Kontext-Parität | 462/462 bestanden |
| In den fünf LM-Tests enthaltene Vergleiche | 35.624 Samplerfälle + 50 Modell-Tokenvergleiche bestanden |

Die zusätzlichen Originalcompiler-Ausführungen werden nicht als weitere einzigartige Kandidatentests gezählt. Die Zahlen sind deterministische Prüffälle, keine statistische Aussage über alle möglichen Eingaben.

Der Worker-Bot prüfte die beiden Kotlin-Änderungen zusätzlich unabhängig statisch: kein bestätigter Blocker im begrenzten Review. Der Koordinator kontrollierte alle drei Produktivdifferenzen, Quellbindungen und Messgrenzen. [Koordinator-Nachweise](../../evidence/three-tuning-bots-20261104/coordinator/README.md) enthalten tatsächliche Ausgaben, Quellhashes und Prüfquittung.

| Getestete Produktivdatei | SHA-256 |
| --- | --- |
| `learning/lm/LmModel.kt` | `d9fc7493360e526323505ff1637981a6e024f6c61a47e4d6f24df22d248df65d` |
| `cognitive/AiContextCompiler.kt` | `2100c6d1b4ecd0e2398ef4039266d9456cc33a69b4ea3700b5e4b79b9c7a5a13` |
| `.github/continuum/development-backlog.mjs` | `d0cb68413ff89063b685395c0e633a10c04f038e8329658c085ce0c8748f3a1d` |

## Zusammenarbeit mit anderen Arbeitsständen

Repository-Anweisungen, Experimentvertrag/-register, Issue #41 und PR #287 wurden gelesen. Die bestehenden Grenzen von EXP-05/06/12/14/01/08/11 bleiben bestehen. Der unveränderte Node-Ausgangspunkt bestand 48/48 Tests; dieser historische Basislauf ist keine Kandidatenabnahme.

PR #494 besitzt bereits Retriever-Optimierungen und wird nicht als Leistung dieses Teams gezählt. #496/#506 bearbeiten Worker-Pool-/Routine-Zuständigkeiten; #505 besitzt separate Kalender-/Sequenzreparaturen. Die Dateilisten von #493, #494, #496, #505, #506, #508 und #510 wurden zur Abgrenzung verglichen.

Der Bytebudget-Patch lässt sich mit `git apply --check` auf den exakt abgerufenen `AiContextCompiler` aus PR #506 anwenden: PR-Head `8abea828833d8833d40e3d3d36feaa6d3685b82e`, Datei-Blob `f42f526cefaa96295205f161241fbc8035f27c01`. Das beweist nur konfliktfreie Patch-Anwendbarkeit, **keine kombinierte Integration oder getestete Freigabe dieses anderen PRs**. Andere Branches wurden nicht verändert.

## Abschlussgrenzen und Rücknahme

Diese Runde ergänzt keine Produktfunktion, kein Modelltraining und keine neue externe KI-API. Hauptbranch, Releases, APK/Installation, Modellgewichte, Datenformate, ToolGate, Offline-Einstellung, Worker-Autorität, Sperren und Abbruchregeln liegen außerhalb der Änderung. Kleine synthetische Testmodelle/-Tokenizer dienen ausschließlich den Regressionsprüfungen.

Nicht ausgeführt: vollständiger Gradle-/Android-/Lint-Lauf, APK-Bau, Fold7-/ART-Abnahme, Ende-zu-Ende-Inferenz, Akku-/Temperaturmessungen oder Frontier-Vergleich. Ein späterer Gerätevergleich muss den tatsächlich verwendeten Aufrufer und beide Sampling-Modi erfassen; er wird hier nicht als erledigt ausgegeben.

Die drei Produktivänderungen sind getrennt versioniert. Eine Rücknahme führt die jeweilige kleine Produktivdifferenz in einem eigenen Review-Commit zurück; Messbelege bleiben erhalten. Es gibt keine Daten- oder Modellmigration.

Methodischer Primärbezug: [Android Microbenchmark overview](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview). Die Zahlen dieses Berichts stammen ausschließlich aus den beigefügten Host-Rohdaten.
