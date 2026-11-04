# Meilenstein 5: Lernsignalzugriff bei 6,56M und 9,71M Parametern

Vor der Ausführung veröffentlichter Code und Vertrag:
`c966de3b9c0e12f0f3eb469d964c79ba41ba0138`.
Registrierung: `3ee5dc40b67fad6c86c0c0b8487740834526530637e5ee9a9f992691d26f4f88`.
Status: **GRADIENT_ACCESS_DIAGNOSTIC_PASS_NO_OPTIMIZER**.

## Tatsächliche Messung

Die unveränderten S0/W0-Checkpointdateien wurden geladen. D0 wurde durch die
bereits vorhandene Funktion `deepenMinGru(W0, 1, 20261104)` ausschließlich
im Speicher erzeugt. Vier vorher festgelegte allgemeine Texte liefern je
einen vollständigen nächsten-Token-Verlust einschließlich EOS. Die Texte
sind technische Proben, kein neuer Trainings- oder Qualitätstestdatensatz.

| Profil | Parameter | Reine Float32-Bytes | Koordinaten mit Gradient ungleich null, bei jeder der vier Proben |
|---|---:|---:|---:|
| S0 | 132.160 | 528.640 | 132.160 |
| W0 | 6.562.304 | 26.249.216 | 6.562.304 |
| D0 | 9.712.128 | 38.848.512 | 7.873.536 |

Alle 6.562.304 W0-Parameter erhalten auf jeder Probe ein endliches, von null
verschiedenes Lernsignal. Das weist konkreten Gradientenzugriff nach; es
beweist keine höhere Qualität oder statistische Unabhängigkeit aller Parameter.

Die zusätzliche D0-Schicht besitzt 3.149.824 Parameter. Ihre Ausgangspfade
`wo`, `w2` und `b2` mit zusammen 1.311.232 Parametern erhalten sofort Gradienten.
Die übrigen **1.838.592** Parameter haben beim ersten Rückwärtslauf exakt null
Gradient. Das ist die erwartete Folge der nullgesetzten Ausgangsgewichte,
durch die die neue Schicht anfänglich eine Identität bleibt. Ein späterer
Gradientenzugriff nach Öffnen dieser Pfade ist damit noch nicht gemessen.

## Funktions- und Ableitungskontrollen

- Alle 36 initialen Paritätsfälle bestehen innerhalb `1e-5` gegenüber S0.
  D0 und W0 liefern auf den zwölf Proben bitgleiche Wahrscheinlichkeiten.
- Die durch die Erweiterungsabbildung zurückgerechneten W0-Gradienten stimmen
  über alle 132.160 ursprünglichen Koordinaten mit den separat berechneten
  S0-Gradienten überein: größte absolute Abweichung **3,50177e-6**, größte
  relative L2-Abweichung **2,05920e-6**; beide Grenzen waren vorher `2e-4`.
- Die Gradienten aller gemeinsamen D0/W0-Parameter sind auf allen vier
  Verlustproben bitgleich. Das bestätigt die anfängliche Identität auch im
  Rückwärtslauf.
- Alle **20 gerichteten Differenzenprüfungen** bestehen. Die größte
  absolute Abweichung beträgt 0,00120657; kein Fall nutzt mehr als 9,564 %
  seiner vorher festgelegten Fehlertoleranz. Alle temporär veränderten Arrays
  werden wiederhergestellt und anschließend gehasht.

332 Tensorgradienten-Protokolle, zwölf Verlustproben und sämtliche
Einzelkontrollen stehen in `gradient-evidence/`. Die unabhängige arithmetische
Nachprüfung steht in `gradient-audit.json`. Dieser Python-Prüfer rechnet die
Belege nach; er behauptet keine zweite vollständige Rückwärtsimplementierung.

## Gemessene Skalierungsfrage

Das Verhältnis `Gradient-L2 / Parameter-L2` der finalen Normierung `nf` ist
bei W0 auf allen vier Proben ungefähr **achtmal** so groß wie bei S0
(Bereich 7,9999959–8,0000024). Die Erweiterung teilt diese Gain-Werte durch
acht; die Funktion bleibt trotzdem zunächst erhalten.

Daher ist dieselbe numerische Lernrate über beide Parametrisierungen hinweg
keine belegte Kontrolle derselben relativen Änderung. Eine eigene
Lernraten-/Retentionstudie ist begründet. Diese Messung wählt keine neue
Lernrate aus und erklärt nicht allein die zuvor beobachtete EOS-Spezialisierung.

## Gates, Ressourcen und Erhalt

Sieben neue Testmethoden mit positiven Profilen und gezielten Mutationen
bestehen. Acht weitere quellengebundene Wiederaufnahmefälle bestehen mit
null JVM-Starts: tatsächlicher fertiger Lauf, gültige private Kopie,
fehlende erste/letzte Receipts, neu hashgebundene falsche Ableitung,
nicht wiederhergestelltes Array, geänderter Quellcode und erneut gehashte
Budgeterhöhung. Originaldateien bleiben unverändert.

Die drei seriellen Messprozesse benötigen zusammen **10,978 Sekunden**.
Maximaler Prozess-RSS: **474.728 KiB** (etwa **463,60 MiB**). Der separate
Rückwärtsanalysevertrag mit 3-GiB-Heap, 120 Sekunden je Profil und 360 Sekunden
insgesamt besteht. Die bisherigen Inferenz-, Qualitäts- und Ressourcenregeln
werden dadurch nicht geändert.

Null Optimizerupdates, null neu serialisierte Modellcheckpoints, kein Finaltest,
keine Promotion. D0-Checkpointgröße und Android-Verhalten wurden nicht gemessen.
Die alten Forschungsmodelle und ihre FAIL-Ergebnisse bleiben unverändert.

Die beiden exakten Diagnose-JARs wurden als separates kleines Reproduktionspaket
gesichert. `diagnostic-runtime-archive.json` bindet dessen Namen, Größe,
SHA-256 und die enthaltenen Binärdateien. Quellen und Belege bleiben im Git-PR.

Nächste begrenzte Frage: Erhalten die zunächst inaktiven 1.838.592 Parameter
nach einer einzigen vorab festgelegten kleinen Öffnung der Ausgangspfade
tatsächlich Gradienten? Auch dieser Kontrollversuch erfolgt ohne Optimizer.
