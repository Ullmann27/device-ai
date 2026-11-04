# Drei Forschungsbots: Eigenständigkeit, eigene Sprachmodelle, Live-Avatar

Datum: 2026-11-04 (Europe/Vienna). Status: RESEARCH_SHADOW / ERSTE RUNDE ABGESCHLOSSEN.
Auftrag: drei tatsächlich gestartete, getrennte Forschungs- und Experimentieraufträge; keine produktive Integration.

## Quellenbindung

| Bereich | Verifizierter PR | Quell-SHA |
| --- | --- | --- |
| Gemeinsame Architektur / Ausgangsbranch | #491 | e101e393dd6c80803ac9d9ac91fccd9812c4a18d |
| Eigene Gewichte / korrigierte Trainingsgates | #492 | 30cd3a89810ed14fa84eca95236d3177e016118d |
| Referenzen / Hostwerkzeuge | #500 | 0e3685a3bc6cb3a4af61988450371f11e9cb0f3c |
| Avatar-Blick und Blinzeln | #502 | 6cb9e760f0a16a6f34a8442abf9b79dcf2705799 |
| Sprachgrenzen / Artikulation | #503 | 2582be86c121d7f6d1179c7edc49e979bee8ba9c |

GitHub-PR-Metadaten wurden frisch gelesen. Frühere Berichte werden nicht als neue Ausführung ausgegeben. Diese Quellen bleiben getrennte Forschungsbaselines; kein stiller Gesamtmerge.

## Rollen

1. Forschung Eigenständigkeit: aktuelle lokale Runtime, ein kanonischer Backlog/Repair-/Evidenzweg, verifizierbare Erfahrungsnutzung und begrenzte Workerzyklen. Ein reproduzierbarer Versuch am bestehenden Quellcode, keine erfundene Modellinferenz.
2. Forschung eigenes Sprachmodell: aktuelle LM1-Trainings- und Wachstumsmethode; Fokus auf beobachtetes sofortiges EOS nach Fortsetzungstraining. Mehrere Methoden vergleichen, Qualitätsgates erhalten, Datenlecks vermeiden. 16.011.776 analytisch gezählte Parameter sind kein trainierter Sprachfähigkeitsnachweis.
3. Forschung Avatar: vorhandene Referenzbilder, GLB/Morphs, Echtzeit-Sprach-/Blick-/Mimikpfad. Lokale Erzeugungswege und tatsächliche Geometrie-/Zeitverhalten untersuchen. Hostvorschau ist kein Fold7-Nachweis.

## Verbindliche Zielrichtung

Bestehende Haupt-App, Originalkopf/Referenzen, dunkles Cyan-/Violett-Cockpit, plastischer holografischer Avatar, Gehirnkugel unter dem Kinn, Fold7 geöffnet/geschlossen. Mundbewegung und Ausdruck werden von tatsächlichen Dialog-/Audioereignissen gesteuert. Referenzvideos ersetzen keinen interaktiven Avatar. Android bleibt Zielgerät; ein Hostversuch begründet keine PC-Pflicht.

## Versuchsregeln

- Betroffene Register: EXP-01, EXP-02, EXP-04, EXP-05, EXP-06, EXP-07, EXP-11, EXP-12; Avatar als vorhandene Dialogausgabe.
- Isolierte neue Dateien unter experimental/three-bot-research-20261104/.
- Bestehende Trainingsläufe, Checkpoints, Holdouts, Appquellen und produktive Gewichte bleiben unverändert.
- Originalquellen/Provenienz, Commands, Rohmessungen, Hashes, negative Ergebnisse und Einschränkungen dokumentieren.
- Nur vorhandene lokale CPU-/Werkzeugkapazität; keine bezahlten APIs/Runner, keine GitHub Actions.
- Kein main-Merge, Release, Signieren, Installieren oder Verändern der Nutzerdateien.
- Kein zusätzlicher produktiver Scheduler oder Gate-Umgehung.
- Frühere Forschungs-Freeze-Texte vom 31.10. ändern den expliziten heutigen Forschungsauftrag nicht; alle Schutz- und Freigabegrenzen gelten weiter.

## Prüfstand beim Start (historischer Ausgangspunkt)

Drei Bots gestartet. Lokaler eingefrorener #491-Checkout sauber. Python/NumPy, Node, Java und Blender vorhanden; adb nicht vorhanden, Torch nicht vorhanden. Neue Experimente: NOT_RUN. Geräte-/GPU-/Sprachqualität: NOT_RUN.

## Nächster Einstieg und Rückfall

Pro Bot erst Quellen prüfen, dann kleinsten aussagekräftigen Versuch durchführen, danach unabhängige Koordinatorprüfung. Ergebnisse werden im selben Branch gesichert. Bei Fehlschlag Erkenntnisse behalten, keine Kandidatenpromotion. Rückfall: Forschungsbranch nicht übernehmen; keine Produktionsmigration nötig.

## Koordinator-Checkpoint: Trainingsgates unabhängig wiederholt

Die vier vorhandenen Prüfprogramme aus #492 bei `30cd3a89810ed14fa84eca95236d3177e016118d` wurden unverändert gegen den exakten Treiberhash `8f2f49bda56ce1f63e86fecf262229acff3d6f5d1ada1d97270b6c1731cda9be` erneut ausgeführt: **36 + 6 + 4 + 29 = 75 PASS, 0 FAIL**. Alle vier Prozesse endeten mit Exitcode 0. Dies sind synthetische Steuerungs-/Negativprüfungen, **null echte Trainingsläufe**, keine Geräteabnahme. Rohlogs und Befehle: `experimental/three-bot-research-20261104/verification/`. Die Forschungsversuche der drei Bots bleiben separat zu berichten.

## Abschluss und verbindlicher nächster Einstieg

Alle drei Forschungsbots haben ihre Versuche abgeschlossen. Vollständiger Gesamtbericht, Programme und Nachweise: [experimental/three-bot-research-20261104/README.md](../../../experimental/three-bot-research-20261104/README.md). Draft-PR: [#509](https://github.com/Ullmann27/device-ai/pull/509).

- Eigenständigkeit: 51 vorhandene Tests und 9 zusätzliche Manipulations-/Neustartproben bestanden. Der Koordinator hat die 9 Probes separat wiederholt. Keine echte Modellreparatur oder Leistungssteigerung gemessen.
- Sprachmodell: 9 echte kurze Trainingsläufe auf dem Originalkern mit 132.160 Parametern und 77 originalen TRAIN-Dokumenten. Drei Seeds, zwei wirksame Trainingsbedingungen plus Absatz-Nullkontrolle. Promptabhängiger EOS-Rangwechsel bestätigt; Sprache bleibt unbrauchbar. Das unveränderte Original-Kapazitätsgate des trainierten Kontrollarms fällt auch in unabhängiger Wiederholung durch. Drei reale Wachstumsparitätsprüfungen bestanden; kein trainiertes 16M-Modell.
- Avatar: gleicher echter GLB in #491/#503, 57.000 Dreiecke und 21 Morphziele. Je Stand 10.000 synthetische Wechsel mit 20.017 Assertions. PP-Schluss und idempotenter Framezeitpunkt in #503 reproduziert. Sechs tatsächliche Blender-Bilder; Referenztreue noch nicht erreicht. NOTICE-Metadaten widersprechen den realen GLB-Bytes.
- Reproduktion: 50 gepinnte Quellen und 77 TRAIN-Dokumente mit dem neuen Forschungshelfer aus vorhandenen lokalen Git-Objekten rekonstruiert. Hashes, Rohlogs und bewusst ausgelassene reproduzierbare Binärzwischenprodukte sind im Gesamtbericht erläutert.

Die nächsten Hypothesen sind im Gesamtbericht für dieselben drei Rollen formuliert. Keine Dauerläufe installiert, keine App-/Produktquellen geändert, kein Merge, Release, Signieren oder Installieren. Geräte-/Filament-/Lautsprecherabnahme bleibt NOT_RUN. Vor jeder Folgerunde Quellenstand erneut binden; diese bekannten Diagnoseprompts nicht als neuen unberührten Endtest ausgeben.
