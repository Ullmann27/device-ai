# Reproduktion der bekannten offenen CTX2-Fehlertaxonomie

Dieser Diagnosepfad reproduziert ausschließlich die **27 bereits offenen WRONG-Fälle aus TEST2**. Er prüft die [Taxonomie](../../../research/CTX2-OPEN-ERROR-TAXONOMY-20261024.md) gegen den [unveränderten B2-Rohbericht](../../../research/ctx2-open-b2-report-20261024.json). Das ist keine neue Blindmessung und kein CTX3-Test. Versiegelter TEST3 wird weder geöffnet noch ausgeführt.

## Voraussetzungen

- Bash, Git, Python 3.8+ und JDK 17 oder neuer.
- Installierter Kotlin/JVM-Compiler (`kotlinc`); hier verifiziert mit Kotlin 2.4.0 und JDK 21.
- Ein Repository-Checkout, dessen lokale Git-Historie den reparierten B2-Commit `44c4d743e64f41cde2f6489e2e34ee7ba03ea012` enthält. Bei einem flachen Checkout muss dieser Commit vorher verfügbar gemacht werden; das Skript lädt nichts nach.
- Der eingecheckte Rohbericht `docs/research/ctx2-open-b2-report-20261024.json` muss den SHA-256 `be7abf235f5f9539dfbb3c5aa5183c1503faae7fab942f90b74ad4829250ae31` besitzen.

Gradle, Android-SDK, JUnit, vorgebaute Appklassen, SDK-Caches, private Daten und Netzwerk sind nicht erforderlich.

## Aufruf

Im Repositorywurzelverzeichnis mit `kotlinc` auf `PATH`:

```bash
./docs/evidence/sol-m5-20261024/diagnostics/reproduce-ctx2-open-taxonomy.sh
```

Mit explizitem Compiler und optionalem leeren Ausgabeverzeichnis:

```bash
KOTLINC=/pfad/zu/kotlinc \
  ./docs/evidence/sol-m5-20261024/diagnostics/reproduce-ctx2-open-taxonomy.sh \
  /tmp/ctx2-taxonomy-evidence
```

Ohne Ausgabepfad wird ein neues temporäres Ergebnisverzeichnis angelegt und am Ende ausgegeben. Ein explizites Ausgabeverzeichnis muss leer sein. Das Skript schreibt nur in dieses Verzeichnis und ein zusätzliches temporäres Arbeitsverzeichnis, das beim Ende entfernt wird. Repository-, Produkt- und ursprüngliche Berichtdateien bleiben unverändert. Fehlende Werkzeuge ergeben `UNAVAILABLE`/Exit 2; abweichende Belege oder eine fehlgeschlagene Reproduktion einen Fehlerstatus.

Erwartete Schlussmeldung:

```text
PASS: 27/27 exact rewrites+first citations; 13 SWITCH/14 RETURN; taxonomy and scopes verified.
Evidence directory: <Ausgabeverzeichnis>
```

## Ablauf und erzeugte Belege

1. `verify-ctx2-open-taxonomy.py prepare` prüft den Rohberichthash und die Ergebnissummen. Es liest alle 27 WRONG-IDs aus dem vollständigen `failures[]`; die acht unnötigen Rückfragen werden nicht als WRONG umklassifiziert.
2. Es extrahiert **neun ausdrücklich aufgelistete reine JVM-Quelldateien** sowie das öffentliche SWB1-Testfixture mit `git show` aus dem fest gebundenen B2-Commit. Spätere Änderungen an Produkt- oder Testdateien beeinflussen diesen Diagnoselauf daher nicht. Der bekannte Kontextquellhash wird zusätzlich geprüft.
3. Der Shell-Wrapper kompiliert diese Quellen gemeinsam mit `TaxonomyTrace.kt` direkt mit `kotlinc -jvm-target 17 -include-runtime`. Es werden keine Android-Klassen oder Buildausgaben verwendet.
4. Der Trace erzeugt nur den öffentlichen TEST2-Korpus, prüft dessen kanonischen Hash und durchläuft ausschließlich die 27 ausgewählten Fehlerdialoge. Er protokolliert Fakten, Registry, Eingaben, Umschreibungen, Quellenscopes, alle Belege und Gold.
5. `verify` vergleicht bei jedem Fall Eingabe, umgeschriebene Frage, Gold und Erstbeleg exakt mit dem Rohbericht und der archivierten Taxonomie. Es prüft außerdem: kein Goldbeleg im falsch gewählten Scope; alle zitierten Belege im aktuellen Dialogpool; 13 SWITCH-Fälle ohne registrierten Gegenstand; 14 RETURN-Fälle mit M/F-Haustierkandidaten.

Das Ausgabeverzeichnis enthält:

| Datei | Inhalt |
|---|---|
| `verification.json` | PASS-Ergebnis, Bericht-/Korpus-/Commitbindung und geprüfte Zählungen |
| `traces.json` | Vollständige diagnostische Traces der 27 öffentlichen Fehlerdialoge |
| `frozen-sources.json` | Pfad und SHA-256 aller zehn extrahierten Quell-/Fixturedateien |
| `tool-versions.txt` | Tatsächlich verwendete Kotlin-, Java- und Python-Versionen |
| `compile.log` | Ausgabe des reinen JVM-Compilerlaufs |

Die ursprünglichen vollständigen Laufzeitmetriken werden nicht neu gemessen. Der Trace reproduziert bereits bekannte Fehler und beweist keine allgemeine Sprachfähigkeit, keine CTX3-Verbesserung und keine Geräteausführung. Er enthält keine neuen Produktregeln.
