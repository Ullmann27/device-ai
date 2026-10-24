# CDX1 – Abschlussprotokoll nach 12/12 Stufen (24.10.2026)

## Provenienz, Status und Grenzen

Quelle der Messungen: vom Besitzer am 24.10.2026 wortgetreu aus den Android-CDX1-Labordialogen übermittelte Stufen 5–12. Keine rückwirkend erfundenen Ergebnisse für Stufen 1–4. Werte und Modell-Hash wurden in diesem Commit **nicht** aus einem Samsung-Checkpoint neu gemessen. Live-Training oder Gewichte sind hier **nicht** geändert.

Gemeldeter aktiver Modell-Hash (16-stelliger UI-Präfix): `79fcc856bbfe2230`. Für alle acht vorhandenen Versuche wurden jeweils 600/600 Updates ausgeführt, alle acht neuen Kandidaten verworfen; das UI meldet SWB1 unverändert, CDX1 getrennt, Chatfreigabe NEIN.

| Stufe | Training | Neues Audit | Historisches Audit | Historische Veränderung | Ergebnis |
|---:|---:|---:|---:|---:|---|
| 5 | 4.01238 → 3.70568 | 3.89856 → 3.70911 | 4.42868 → 4.78287 | +8.00 % | Verworfen |
| 6 | 3.65302 → 3.29747 | 3.89856 → 3.71969 | 4.42868 → 5.00612 | +13.04 % | Verworfen |
| 7 | 3.52725 → 3.35019 | 3.89856 → 3.81066 | 4.42868 → 4.58026 | +3.42 % | Verworfen |
| 8 | 4.01278 → 3.70417 | 3.89856 → 3.70842 | 4.42868 → 4.78325 | +8.01 % | Verworfen |
| 9 | 3.65296 → 3.29704 | 3.89856 → 3.71963 | 4.42868 → 5.00629 | +13.04 % | Verworfen |
| 10 | 3.52822 → 3.34990 | 3.89856 → 3.81107 | 4.42868 → 4.58158 | +3.45 % | Verworfen |
| 11 | 4.01210 → 3.70581 | 3.89856 → 3.70897 | 4.42868 → 4.78321 | +8.01 % | Verworfen |
| 12 | 3.65204 → 3.29750 | 3.89856 → 3.71904 | 4.42868 → 5.00394 | +12.99 % | Verworfen |

Alle acht Versuche verbessern den Trainingsverlust sowie den Verlust auf 48 unbekannten fiktiven Forschungsfällen. Alle acht verschlechtern das historische Audit, weshalb die sichere Mehrfach-Audit-Schranke korrekterweise die Gewichtsübernahme verhindert. Das allein belegt weder katastrophales Vergessen noch freie Sprachgeneralisierung.

## Reproduzierbarer Quellcodebefund: dreistufige Zielperiodizität

Die Kotlin-Quelle `ContinuumCdxLargeCurriculum.kt` generiert 48 Roboter (je zwölf Beleg-Frage-Antwort-Aufgaben), gruppiert als vier Roboter pro Stufe. Alle vier Eigenschaftstabellen (`cities`, `colors`, `energy`, `tools`) enthalten exakt zwölf Einträge. Der Eigenschaftsindex wird mit `id % 12` oder verschobenen Formen modulo 12 gebildet.

Daher wiederholt sich für Stufe `s + 3` gegenüber `s` **jede einzelne erwartete Antwort an derselben Aufgabenposition**, während die Roboter-ID eine andere ist: 4 Roboter/Stufe × 3 Stufen = 12 Roboter, also ein voller Tabellenumlauf. Dieser Befund ist über den neuen `ContinuumCdxLargeCurriculumCycleTest` auf allen neun Stufenpaaren falsifizierbar. Er bietet eine konkrete Erklärung für die Ähnlichkeit von Stufen 5/8/11, 6/9/12 und 7/10. Ein Kausalbeweis dafür, dass dies die komplette Regression auslöst, ist es nicht.

## Zweiter Befund: historische Ausgabeform unterscheidet sich

Die 576 v1-Trainingsziele sind **kurze Werte** (Orte, Farben, Energiequellen, Werkzeuge). Die zwei v1-Historien-Auditziele sind dagegen **ganze Sätze**:

- `Tim wohnt in Wien.` / `Wo wohnt Tim?` → `Tim wohnt in Wien.`
- `Eva ist Köchin.` / `Was arbeitet Eva?` → `Eva ist Köchin.`

Diese beiden historischen Fälle stammen aus dem bestehenden alten Paarlabor; ihr Schutz ist sinnvoll. Das Format weicht aber systematisch von den v1-Kurzantwortzielen ab. Allein aufgrund dieser Formatdifferenz und zweier historischer Fälle kann man nicht unterscheiden, ob tatsächliche Altkenntnisse verloren gehen oder nur eine unvereinbare Antwortlängen-/Formatstrategie gelernt wird. Die unverändert strenge historische Schranke darf deshalb **nicht** aufgeweicht werden, um künstlich erfolgreiche Stufen zu erhalten.

## Unmittelbar implementierter sicherer Abschluss

`AgentActivity.showCdxLargeLabDialog` zeigt bei 12/12 keinen irreführenden "NÄCHSTE STUFE PRÜFEN"-Aufruf mehr. Stattdessen öffnet **ANTWORTQUALITÄT PRÜFEN** den bereits vorhandenen `ContinuumTrainingMonitorActivity`, wo der **read-only** 20+5-Intelligenzaudit ausgelöst werden kann. Ein eigener Labor-Menüeintrag führt ebenso dorthin. Es werden keine Gewichte, private Erinnerungen, externen KI-APIs oder Produktionschats verändert.

## Entwicklungsentscheidungen für die nächste experimentelle Version

1. Zuerst auf der installierten APK die 25 **tatsächlich generierten** Antworten prüfen, mit dem aktiven Modell-Hash vorher/nachher, getrennten Treffer-, Halluzinations- und ausdrücklichen Enthaltungszahlen. Leere EOS-Ausgabe ist keine korrekte Enthaltung.
2. Die vorhandenen 20+5-Holdouts nach erster Verwendung als verbraucht markieren; künftige Entwicklung nur mit **neu eingefrorenen** andersartigen Blindsätzen bewerten.
3. Für eine neue Forschungsversion `cdx-pairs-v2` ein **neues Cursor-Namespace** und disjunkte Datenquellen verwenden: andere syntaktische Fragetypen, Ausgabestile (Kurzantwort/Vollsatz), Aufgaben zur Negation und Mehrteilschlussfolgerung; historischen v1-Checkpoint nie überschreiben.
4. Rein experimentell mehrere Varianten abgleichen: Replay auf neuen, nicht in einem Holdout vorkommenden Satzformaten, reduzierte Lernraten, gemischte Aufgaben, kleines Regularisierungsgewicht. Nach **jeder** Kandidatenrunde alle unveränderten Audits und ein frisches nur einmal verwendetes Blindset prüfen. Keine negativen Resultate verbergen.
5. Vergleiche mit LM1 und einfachen probabilistischen Baselines nur bei identischen Datentrennungen, Metriken und Laufzeit-/Energiebudgets. LM1 ist noch keine nachgewiesene Chat-KI.
6. Keine neue CDX1-Promotion in SWB1 oder normalen Chat, kein Automatismus nach 12 Stufen, keine Eingriffe in `main`, Paket/Signatur oder Nutzer-Gewichte.

## Nachweise dieser Änderung

- Separate Unit-Tests für deterministische 3-Stufenperiodizität, disjunkte Konditionierer und alten Vollsatz-/Kurzantwort-Kontrast.
- Nachweis der UI-Anschlussstelle: `ContinuumTrainingMonitorActivity` besitzt den bestehenden read-only 25-Fälle-Button.
- Prüfung des Quellcodes durch GitHub-Blob-Rücklesung; **keine** Angabe eines erfolgten Android-Gradle-Builds, Fold-Gerätetests oder tatsächlich gemessenen Intelligenzgewinns.

Nächste Freigabe erst nach `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PdeviceAiMentorPreview=true`, Signatur-/Versionsnachweis und Samsung-Prüfung durch den Besitzer. Dieser PR bleibt Draft.
