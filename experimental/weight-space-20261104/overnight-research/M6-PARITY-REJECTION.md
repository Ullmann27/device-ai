# Meilenstein 6 verworfen: Pfadöffnung verletzt Paritätsgrenze

Vor der Ausführung veröffentlichter Versuch:
`8e0dadceef6a39cac86817b52340b79e185b160f`.
Registrierung: `0b9b97874c42da9ee1f0c4f4f113790f6c50e960612a961dcac579b42d8daa78`.
Ergebnis: **PARITY_FAIL_RESTORED_NO_BACKWARD**.
Der akzeptierte Forschungsstand bleibt **M5_UNCHANGED**.

## Versuch und Abbruch

Alle zwölf anfänglichen Paritätsprüfungen bestanden. D0 und W0 lieferten
bitgleiche Wahrscheinlichkeiten; die Erweiterung gegenüber S0 lag innerhalb
der unveränderten Grenze `1e-5`.

Danach wurden ausschließlich im privaten D0-Array die vorher festgelegten
2.560 Ausgangskoordinaten geöffnet: die Diagonale von `l2.wo` auf `0.0001`
und die registrierten Einträge von `l2.w2` auf `0.000025`. Es gab keinen
Optimizer, keine Auswahl anhand von Gradienten und keinen zweiten Versuch.

| Vorab festgelegte Probe | Größte Wahrscheinlichkeitsänderung gegenüber W0 | Grenze | Ergebnis |
|---|---:|---:|---|
| `general-next` | 0,0000016093254089355469 | 0,00001 | PASS |
| `general-note` | 0,00000928342342376709 | 0,00001 | PASS |
| `general-tool` | **0,000013113021850585938** | **0,00001** | **FAIL** |

Die dritte Probe überschritt die Grenze. Die JVM beendete den Versuch mit
Exitcode 1 und `IllegalStateException: opened-path parity gate failed`.
Die übrigen neun Paritätsproben und sämtliche vier geplanten
Rückwärtsrechnungen sind **NOT_RUN**. Es gibt keinen Nachweis, dass die
1.838.592 anfangs inaktiven Parameter nach dieser Öffnung Lernsignale erhalten.
Ein solcher Nachweis darf aus den vorbereiteten Testfällen nicht abgeleitet werden.

## Wiederherstellung und Wiederaufnahmesperre

Der `finally`-Block stellte die veränderten Tensoren wieder her und prüfte
auch die unveränderten Elternarrays. Der vollständige D0-Parameterdigest
vor und nach dem Versuch ist identisch:

`d6d3d64b01a12fe8d0e8c81527aff276bfefb776f09c2ed37f7f6eb21fd24640`.

Dieser Wert beschreibt Parameterarrays, keine serialisierte Checkpointdatei.
Der separate Prüfer `audit_open_path_failure.py` überprüfte zusätzlich die
echten Eingabedateien, JARs, Tokenizer und das ursprüngliche Modellarchiv
gegen ihre vorab festgelegten SHA-256-Werte. Die nach dem `finally` geplante
normale Abschlusszeile wurde wegen der Ausnahme nicht erreicht; daher wird
hier ausdrücklich der separate Dateinachweis verwendet.

Drei quellengebundene Wiederaufnahmeprüfungen bestanden:

1. Der echte fehlgeschlagene Versuch wird abgewiesen.
2. Eine private Kopie mit nachträglich auf PASS/Exitcode 0 geänderter Receipt
   scheitert weiterhin an der tatsächlichen Paritätsverletzung im Rohprotokoll.
3. Eine private Kopie mit gelöschter Receipt bleibt wegen der bereits
   begonnenen Ausführung gesperrt.

Dabei startete keine JVM; sämtliche dauerhaften Dateien blieben unverändert.
Der Prüfer verwendet den tatsächlichen, hashgebundenen Controller und keine
nachgebaute Wiederaufnahmelogik. Die numerische Grenzwertprüfung und Prüfung
der Reihenfolge erfolgen zusätzlich separat im Prüfer.

## Ressourcen und Entscheidung

Gemessene Prozessdauer: **2,859959 Sekunden**. Maximaler RSS:
**288.804 KiB**. Weder Zeit noch Speicher lösten den Abbruch aus.
Null Rückwärtsläufe, null Optimizerupdates, null neue Checkpoints,
kein Finaltest und keine Promotion.

Rohbelege: `open-path-evidence/`.
Nachprüfung: `open-path-failure-verification.json`.
Der exakte fehlgeschlagene Diagnosecode ist zusammen mit den beiden früheren
Diagnose-JARs im erweiterten Reproduktionsarchiv gesichert; Identitäten stehen
in `diagnostic-runtime-archive.json`.

**Keine weitere Modellmessung in dieser Runde.** Die Amplitude wird nicht
nachträglich verkleinert und die Grenze nicht erhöht. Quellenprüfung,
Gate-Integration und die Ausarbeitung eines neuen prospektiven Protokolls
können fortgesetzt werden. Der negative Befund und alle früheren
Qualitäts-FAIL-Ergebnisse bleiben erhalten.
