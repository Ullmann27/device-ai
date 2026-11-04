# Reparaturartefakt: Nachkapazitätsbeleg vor Freeze und Test

Auftrag: `G1-INTEGRATE-WEIGHT-002`, eigene Integration in PR #492.
Geprüfte Fremdquelle: PR #508, Commit
[`be4e49600fed1db4c697230bb8e42c150dbfbdaf`](https://github.com/Ullmann27/device-ai/commit/be4e49600fed1db4c697230bb8e42c150dbfbdaf).

## Umfang und Status

Dieser Ordner enthält einen implementierten, reproduzierbaren Reparaturpatch,
originalgetreue Textfixtures und unabhängige Kontrollflusstests. Der Patch ist
**nicht auf den fremden aktiven Forschungsbranch angewendet**. Alle tatsächlich
geschriebenen Dateien liegen innerhalb von
`experimental/weight-space-20261104/recovery-gate-repair/`.

**Abgeschlossene Prüfung:** Der unabhängige Testbot und anschließend Root haben
jeweils **165/165** Erwartungen an der Reparatur bestätigt: je 52 an den drei
Runnern und neun am tatsächlichen v2/v3-Auswahlhelfer. Dieselben Erwartungen
ergeben an den Originalen 33 bestandene Kontrollen und 132 reproduzierte
Fehlverhalten, die auf die gemeinsame fehlende Abhängigkeit zurückgehen.
Die Quellenbindung und exakte Patchanwendung wurden zusätzlich geprüft.
Der vollständige Abschluss einschließlich der neuen V2-Rohbelege steht in
[HANDOFF.md](HANDOFF.md).

Die drei betroffenen Runner sind:

| Variante | Originalpfad unter `experimental/lm1-training-recovery-20261104/` | Original-Git-Blob |
|---|---|---|
| Intent v1 | `run_registered_study.py` | `28908e07d6217f10882429047c853d1dd8667912` |
| Intent long | `long-study/run_registered_study.py` | `30119435feed1728c5d22fec40830654a7a19861` |
| Intent suffix | `suffix-study/run_registered_study.py` | `417e4b3289bb692e72dc80398b51b67e106b8907` |

V1 und long wurden am neu gefundenen Commit `be4e4960…` erneut gelesen; ihre
Bytes entsprechen den zuvor geprüften Quellen. Die mit diesem Commit neu
hinzugekommene Suffix-Kopie enthält denselben fehlenden Prüfpfad. Original- und
Reparatur-SHA-256 stehen vollständig in [source-manifest.json](source-manifest.json).
Der darin gebundene Patch-SHA-256 lautet
`c69cb427e4ba0624720380edef9f7f0ce3e418591c10bd0a35f56590ad67e38f`.

## Belegter Fehler und konkrete Korrektur

Alle drei Originalrunner führen beim normalen `intent`-Ablauf nach jedem
Training einen Kapazitätslauf aus. `freeze_evidence()` und `test()` prüfen diese
Nachweise jedoch nicht. Nach einer Unterbrechung zwischen Trainingsabschluss
und Nachkapazitätslauf können daher gültige Trainingsquittungen allein den
Freeze und anschließend einen Testprozess autorisieren. Ein vorhandenes,
aber beschädigtes Kapazitätsprotokoll verhindert das ebenfalls nicht.

Der Patch ergänzt in den drei Originalquellen dieselbe schreibgeschützte
Methode `Study.post_capacity_evidence(variant, selected)` und bindet sie an die
bereits vorhandenen Kontrollpunkte:

1. `freeze_evidence()` validiert beide ausgewählten trainierten Arme, S1 und W1,
   bevor es ein Freeze-Payload oder `NO_DEVELOPMENT_SIGNAL` zurückgeben darf.
2. Das Freeze-Payload enthält unter `postTrainingCapacity` für beide Arme den
   überprüften Status, originale Protokoll-/Modell-/Ergebnisfelder, die vollständige
   Prozessquittung sowie Log-, Quittungs- und Standardbibliothek-Hashes.
3. `test()` rekonstruiert und vergleicht die vollständige Freigabe vor jedem der
   vier Testprozessaufrufe erneut. Nach dem letzten Arm prüft es sie erneut, bevor
   eine Zusammenfassung geschrieben wird.
4. `intent-summary.json` übernimmt exakt dieselben gebundenen Kapazitätsbelege.
   Ein fehlgeschlagenes Kapazitätsresultat bleibt somit neben einem begrenzten
   Intent-Ergebnis sichtbar.

Der vorhandene neue Auswahlhelfer
`suffix-study/choose_development_study.py` enthält bereits einen gröberen
Nachweischeck. Er ruft anschließend `study.freeze_evidence()` auf. Damit erhält
auch sein `freezePayload` durch die reparierten Runner dieselbe strenge Prüfung
und Bindung. Der Auswahlhelfer selbst wird durch diesen Patch nicht verändert.

## Was der neue Prüfpfad bindet

Der Validator prüft die gespeicherten Rohbelege gegen den unveränderten
[`LmCapacityBenchmark.kt`](../LmCapacityBenchmark.kt), SHA-256
`d88541b436089b6df04f50035cffa79378b939dabbf03ef77a9ca1acd52bbd9c`:

- ausgewählter Checkpointhash, kanonischer Pfad, aktuelle Bytes und Größe;
- Tokenizerpfad, Tokenizerhash und Größe aus der Registrierung;
- passender Trainingsarm, Parameterzahl aus dem Trainingsmanifest und
  Kapazitätsmodell, vier Bytes je geladenem FP32-Parameter;
- registrierter Quellcommit, Registrierungs-, JAR-, Standardbibliothek- und Loghash;
- exakter Prozessaufruf für `LmCapacityBenchmark`, `-Xms64m`, `-Xmx1g`, Tokenizer
  und ausgewählten Checkpoint; abgeschlossene Quittung mit UTC-Startzeit,
  gültiger Dauer, `timeout=false`, unveränderten registrierten Bytes und Exit 0/1;
- genau ein ursprüngliches Protokoll, ein Modellbericht, acht geordnete
  Generierungsaufrufe, elf Speicherbeobachtungen und ein abschließendes Ergebnis;
- feste Promptidentität und ursprüngliche Schwellen, vollständige Messfelder,
  endliche Zeitmessungen, eindeutige Felder, konsistente Tokenzähler,
  zusammengehörige Maxima und konsistente PASS-/FAIL-Gründe;
- größter beobachteter VmHWM-Wert über alle Speicherzeilen, einschließlich der
  Schlussmessung; der letzte Wert allein kann einen früheren Peak nicht verbergen;
- zeitliche Konsistenz: innere Laufzeit nicht größer als die vollständige
  Generierungsdauer und Summe aller Aufrufe plus Ladezeit nicht größer als die
  Prozessdauer, mit einer Millisekunde Rundungstoleranz;
- unveränderte Checkpoint-, Log- und Quittungsbytes nach der Validierung.

Fehlende, vertauschte, abgebrochene, fehlerhaft gebundene oder widersprüchliche
Nachweise lösen `INVALID_POST_CAPACITY_EVIDENCE` aus. Der Validator startet keinen
Ersatzlauf, erzeugt keine fehlende Quittung und repariert keine historischen Daten.

## Vollständiger FAIL bleibt zulässig

Die Abschnitte 5, 7 und 8 des ursprünglichen
[`INTENT_PROTOCOL.md`](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/experimental/lm1-training-recovery-20261104/INTENT_PROTOCOL.md)
erlauben einen begrenzten Intent-Score zusammen mit einem korrekt dokumentierten
nachträglichen Kapazitäts-FAIL. Diese Semantik bleibt erhalten:

| Beobachtung | Behandlung vor Freeze/Test |
|---|---|
| Acht vollständige Messaufrufe, alle ursprünglichen Anforderungen erfüllt | PASS mit gebundenen Rohbelegen |
| Acht vollständige Messaufrufe; EOS erzeugt zu wenig oder keine neuen Tokens | Gültiger FAIL; begrenzte Intent-Auswertung bleibt möglich |
| Vollständiger Lauf überschreitet 1.500 ms für den ersten Token oder 5.000 ms für 32 Tokens | Gültiger Latenz-FAIL; begrenzte Intent-Auswertung bleibt möglich |
| Vollständiger Lauf überschreitet 768 MiB beobachteten VmHWM | Gültiger Ressourcen-FAIL; begrenzte Intent-Auswertung bleibt möglich |
| Vollständiger Bericht kann RSS nicht beobachten und weist dies konsistent aus | Gültiger `PROCESS_RSS_UNAVAILABLE`-FAIL, ohne Ressourcenfreigabe |
| Prozess-Timeout, fehlende Messaufrufe, abgebrochene Datei oder widersprüchliche Identität | Keine Freigabe; vorhandener Forschungsstand bleibt bestehen |

„Vollständiger Messablauf“ bedeutet, dass alle acht vorgeschriebenen Aufrufe samt
ihren Ergebnissen beobachtet wurden. Das ist auch bei null ausgegebenen Tokens
möglich. Eine nicht erfüllte 32-Token-Anforderung bleibt dabei ein sichtbarer
FAIL; sie wird weder zum PASS erklärt noch mit einem fehlenden Messablauf verwechselt.

Die ursprünglichen Qualitäts- und Ressourcenanforderungen bleiben bestehen:
64 Promptinhaltstokens, ein Warmup-Paar, drei gemessene Paare mit 1/32 angeforderten
Tokens, 1.500/5.000 ms, 768 MiB VmHWM, 120 Sekunden Prozessfrist, 1-GiB-JVM-Heap
und höchstens 64 MiB Checkpoint. Trainingscode, Optimierer, Daten, Lernraten,
Parameterzahlen und die je Variante registrierten Trainingsfristen werden nicht
verändert. Für diese drei Studien bleiben die Modelle bei 132.160 und 6.562.304
Parametern. Dev-Auswahl und ihre Gleichstandsregel bleiben unverändert.

## Reproduktion und unabhängige Prüfung

Die Originalquellen liegen unter `fixtures/<Variante>/original_run_registered_study.py.txt`,
die gepatchten Quellen unter `fixtures/<Variante>/run_registered_study.py.txt`.
Die Endung `.txt` kennzeichnet Test- und Reviewartefakte. Es wird kein produktiver
oder fremder Runner ersetzt.

Aus dem Repositorywurzelverzeichnis prüft dieser Befehl alle Artefakte gegen die
gepinnten Originalbytes und den gemeinsamen Reparaturfragment:

```sh
python experimental/weight-space-20261104/recovery-gate-repair/build_patch.py
```

`--write-artifacts` erzeugt ausschließlich die drei gepatchten Textfixtures, den
Unified-Patch und das Quellmanifest in diesem Ordner neu. Vor jeder Erzeugung
müssen die Original-SHA-256 exakt stimmen. Die erzeugten Quellen werden nur
kompiliert, nicht ausgeführt. Der Generator startet keinen Unterprozess.

Die unabhängig verfasste Testsuite, die Ergebnisse gegen Original und Reparatur
und ihre eigenen exakten Quellhashes stehen in
[independent-tests/](independent-tests/). Der endgültige gemeinsame Prüfstand
hat diese Ergebnisse:

| Runner | Originalquelle | Reparierte Quelle |
|---|---|---|
| v1 | 10/52 bestanden, 42/52 Fehlverhalten reproduziert | 52/52 bestanden |
| long | 10/52 bestanden, 42/52 Fehlverhalten reproduziert | 52/52 bestanden |
| suffix | 10/52 bestanden, 42/52 Fehlverhalten reproduziert | 52/52 bestanden |

Die gleiche unabhängige Suite prüft jeweils Original und Reparatur, einschließlich
gültigem PASS, vollständigem EOS-/RSS-/Latenz-FAIL, früherer Dev-Auswahl bei
Gleichstand, Resume, vollständiger Summary-Bindung, Mutation nach einem Testarm
und Mutation vor der Summary. Ihr SHA-256 lautet
`4dddb6459c578c1feefbaee0bbff72eaad7a469eb799a01e078b8b4d33d51aec`.

Die Suite benutzt synthetische Checkpoint-,
JAR-, Tokenizer- und Datenbytes. Die eigentlichen `Study`-Methoden werden ausgeführt;
abgefangen wird erst `subprocess.Popen`, bevor ein echter Prozess entstehen kann.
Die vollständigen Summary-Kontrollen simulieren an derselben Grenze nur die
erwarteten Prozessausgaben. Das beweist den Kontrollfluss und die Bindungen,
keine tatsächliche Modellleistung. Es wurden dabei keine Modelle ausgeführt,
trainiert oder neu bewertet und keine realen Holdoutinhalte gelesen.

## Registrierung, Wiederaufnahme und Grenzen

Die tatsächliche Integration der fremden Runnerpfade liegt außerhalb des aktuell
erlaubten Schreibbereichs. Der Patch bleibt deshalb als reviewbares Artefakt auf
dem eigenen Forschungsbranch. Eine spätere autorisierte Einbindung muss zuerst
die dann aktuellen Fremdquellen gegen die gezeigten Originalhashes prüfen.

Jede neue Ausführung erfordert eine **vorher veröffentlichte, passende neue
Registrierung** der geänderten Runnerquellen. Die vorhandene `verify()`-Logik
verweigert geänderte Runner unter alten Quellhashes. Historische Registrierungen,
Prozessquittungen, Auswahlquittungen, Checkpoints oder Resultate dürfen nicht
umgeschrieben werden, um ihnen nachträglich die neue Quellidentität zuzuschreiben.
Dieser Ordner nimmt keine solche Umschreibung vor.

Bereits unter der neuen Quelle vollständig erzeugte, korrekt gebundene Belege
können nach Prozessneustart wieder gelesen werden. Fehlt ein Beleg oder ändert
er sich, bleibt die Wiederaufnahme vor dem nächsten Testprozess gesperrt. Es gibt
keinen automatischen Replay, keine Modell-Promotion und kein produktives Training.

Die Hashprüfungen belegen Byteidentität an den jeweiligen Prüfpunkten. Sie sind
keine signierte Prozessattestierung und keine atomare Dateisperre zwischen
Prüfung und anschließendem JVM-Dateizugriff. Die ursprüngliche Quittung bindet
den Java-Aufruf und die JAR-/Standardbibliothekbytes, nicht eine kryptografische
Identität der ausführbaren JVM-Datei oder des Hosts. Der Patch behauptet diese
zusätzlichen Eigenschaften nicht.
