# EXP-20 M6-B4 · Vorprüfung der vorhandenen EWM-Ersetzungslogik (31.10.2026)

**Art:** strukturelle Vorprüfung, auch vor M5 erlaubt.
- Kein neuer Speicher, keine semantische Erfolgsbehauptung.
- **Produktivcode unverändert** (`Ewm1EvidenceWorkspace.kt`, Blob `5539516c…f6d4`).
- Probe: `app/src/test/java/at/heinz/deviceai/research/exp20/m6/M6B4EwmValidityProbe.kt`, Host-Lauf Exit 0.
- Beleg: [`m6b4-ewm-precheck.txt`](../../evidence/exp20-m6-20261031/m6b4-ewm-precheck.txt).

## Ergebnis: 3/5 wie erwartet

| Fall | erwartet | beobachtet |
|---|---|---|
| „Ich wohne in Wien.“ → „Seit Dienstag wohne ich in Graz.“ (dazu Schwester in Linz, Arbeit bei der Post) | f4 ersetzt f1 | **nichts ersetzt** |
| „Ich wohne in Wien.“ → „Am Montag war ich in Graz.“ (Besuch) | nichts | nichts ✓ |
| … → „Seit Dienstag wohnt meine Schwester Anna in Graz.“ | nichts | nichts ✓ |
| „Ich wohne in Wien.“ → „Inzwischen wohne ich in Graz.“ | f3 ersetzt f1 | **nichts ersetzt** |
| … → „Seit Dienstag spiele ich Tennis.“ | nichts | nichts ✓ |

**Gut:** keine falsche Ersetzung unbeteiligter Fakten. Besuch, Umzug einer anderen Person und unverwandte Aussagen bleiben unberührt.

**Lücke 1, Subjekt bei Ich-Aussagen:**
- `Ewm1Memory` setzt bei Ich-Aussagen den **Ort** als Subjekt (`subject=wien`, `subject=graz`). Steht ein Adverb am Satzanfang, wird **dieses** zum Subjekt (`subject=inzwischen`).
- Dadurch ist `sameSubject` in `replaces()` immer falsch. Die Ersetzung greift nie, obwohl beide Fakten `groups=[wohnen]` teilen und der Update-Marker erkannt wird (`updateMarker=true`).
- **Folge:** Eine veraltete Wohnortsangabe des Besitzers wird nicht als `outdated` markiert. Das ist ein Risiko für veraltete Fakten (STALE_FACT_LEAKAGE).

**Lücke 2, keine Gültigkeit ab Datum:** „seit Dienstag“ wird nur als Update-Marker gelesen. Ein Gültigkeitsbeginn oder -intervall wird nicht gespeichert; eine zeitlich begrenzte Gültigkeit ist damit nicht abbildbar.

## Vorschlag (nicht umgesetzt, braucht Review/Besitzerfreigabe, da Produktivlogik)
1. **Minimaler Fix für Lücke 1:** Ich- und Mein-Aussagen erhalten das feste Subjekt `ich` (Besitzer). Satzanfangs-Adverben aus `Ewm1Lexicon.UPDATE`/`TIME` werden nie Subjekt.
2. **Rot/Grün-Test:** Diese Probe dient als roter Test. Grün heißt, dass Fall 1 und 4 ersetzen **und** die drei Negativkontrollen unverändert bleiben.
3. **Nachher Pflicht:** die vollständige EWM-Regression (Kotlin-Harness, „1218/1218“ laut #227) und die Owner-Fakten-Tests.
4. **Lücke 2:** Ein optionales `validFrom` an `Ewm1Fact` ist erst nach Lücke 1 und einem eigenen Vertrag sinnvoll. Es braucht **keinen** zweiten Faktenspeicher.

## Nachtrag 30.10.: Fix als Patchkandidat
Siehe [EXP-20-M6-B4-EWM-FIX-20261030.md](EXP-20-M6-B4-EWM-FIX-20261030.md): Vorprüfung 3/5 → 5/5, Raster FP 195 → 48, FN 228 → 0, Regression 772/772; auf eigenem Branch, nicht übernommen.

## Bedeutung für M6
Der geplante selektive Reset (M6-B4) braucht kein neuronales Signal, solange die vorhandene, deterministische Ersetzungslogik bei Ich-Aussagen nicht greift. **Zuerst diesen nachgewiesenen Fehler beheben**, dann Predictive-Coding- oder EMA-Varianten dagegen vergleichen.
