# Layout018 — unabhängiger Receipt-/Scopeabgleich

**Bestätigt: 7/7 PASS, Exit 0, vier ursprüngliche plus drei neue Fälle.** Dies ist C1s tatsächlicher Lauf, kein eigener erneuter Testlauf.

Archiv-SHA256: `8eab7fdf35f9e9b7cddc54d0eb1dd774bac449db9b45db4c940642d7bbfcdbfa`. Quelle `59d8d7e25a5936403e8579373883fa04bec5c37f`, Tree `90d68760d9a0533c169697902b61eda126e9b20b`. Runner startet 2026-11-04 21:36:46.203154 UTC und endet 21:37:06.999442 UTC; Quellstart sauber, `sourceUnchanged=true`, Prozess-/Gate-Exit 0. Beide XML-Zeitstempel liegen innerhalb des Laufs. Kein FAIL, ERROR oder SKIP. Log enthält sieben START-/END-Paare und BUILD SUCCESSFUL/EXIT_CODE=0.

Zwischen d3487a83 und dieser Quelle ändern sich im App-Baum genau die drei erwarteten Layout-/Präzisionstestdateien. Originale Layouttestdatei bytegleich; strikte 48-dp-Assertion und HERO-Ausdruck unverändert. Die sieben XML-Testnamen entsprechen genau den vier alten und drei neuen Quellmethoden.

Gilt für die beiden ausgewählten Host-Android/JUnit-Klassen. Kein vollständiges Android-Gate, kein NewApi-Lint- oder Geräte-/GPU-Nachweis daraus abgeleitet. Eigene Git-/Archiv-/XML-/Hashprüfungen Exit 0; keine Builds, Tests oder Repoänderungen ausgeführt.
