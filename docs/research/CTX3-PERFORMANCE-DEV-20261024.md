# CTX3 – DEV-Lastmessung am tatsächlichen LearningCore

Eingefrorener Code fc1d996135d98165a7102d0a4e67b9ff34a0b1f0. Reproduzierbarer Runner `Ctx3Performance`,
öffentliche synthetische Personenfakten; 0/10/50/128/256 Erinnerungen, Shadow OFF/ON, je fünf Folgen mit drei
und zwanzig Zügen. In der Mitte jeder Folge Widerruf, am Ende explizite erneute Bestätigung für die nächste
Folge; jede Folge hat eine andere Gesprächs-ID. Kein verstecktes Wiederaufleben widerrufener Belege.
Rohwerte: [ctx3-performance-dev-20261024.json](ctx3-performance-dev-20261024.json).

Gesamter tatsächlicher Aufruf `ContinuumLearningCore.replyFromMemory`, einschließlich Laden, aktueller
Erinnerungssicht, Baselineantwort/History und optionaler Shadow-Auswertung. Keine isolierte Mikrosekundenbehauptung.
Feste Reihenfolge, kalte/wärmere JVM und GC beeinflussen Messung; kein randomisierter kausaler Geschwindigkeitsvergleich.
Kein Android-/Fold7-Benchmark, keine Akkumessung und keine Aussage über Echtzeitgarantien.

Lange Folgen (je 100 Aufrufe):

| Fakten | Median OFF/ON ms | p95 OFF/ON ms | beobachteter zusätzlicher Heap-Peak OFF/ON MiB |
|---:|---:|---:|---:|
| 0 | 0,99 / 0,99 | 1,50 / 1,48 | 34,2 / 41,0 |
| 10 | 1,48 / 1,77 | 2,13 / 3,72 | 41,7 / 41,6 |
| 50 | 2,54 / 3,39 | 9,58 / 25,86 | 48,6 / 58,6 |
| 128 | 4,27 / 5,03 | 7,05 / 8,26 | 71,7 / 94,2 |
| 256 | 7,47 / 9,83 | 10,47 / 15,07 | 102,3 / 115,9 |

Heap-Peak bedeutet Wachstum seit dem Start der jeweiligen Sequenzserie, einschließlich nicht eingesammelter
kurzlebiger Allokationen. Das ist **kein** Nachweis entsprechender dauerhafter RAM-Belegung oder eines
isolierten Shadow-Speicherverbrauchs. Deshalb keine feste Telefon-RAM-Zusage daraus ableiten.

20 Konfigurationen, insgesamt 1150 Aufrufe: keine Ausführungsfehler, keine vorgeschlagenen IDs außerhalb der
aktuellen bestätigten Fakten. ON: nach dem Widerruf wird nachgefragt; OFF hat definitionsgemäß keine Shadow-
Rückfragemessung. Parallelität/Profilgrenzen zusätzlich durch `Ctx3SessionTest`, nicht durch diese serielle Tabelle geprüft.
Der 50-Fakten-p95-Ausreißer und wachsender Aufwand sind weitere Gründe gegen ungemessene aktive Promotion.

Qualität wird separat an unabhängigen Gold-Erwartungen gemessen, nicht an bloß abweichenden Quellen-IDs:
TEST3 verfehlt die Vorregistrierung. Die diagnostische Einzelvergleichs-CSV zeigt 26 Verbesserungen,
99 Verschlechterungen, 16 beidseitig richtige und 57 beidseitig unrichtige Fälle (zusätzliche Posthoc-Auswertung,
keine nachträgliche neue Auswahlregel). Quellen ohne expliziten Scope heißen im Runtimebericht
`UNRESOLVED_RETRIEVAL_SCOPE` und im Testreport `explicitSourceScope=false`; keine bereits gelöste Referenz behauptet.
