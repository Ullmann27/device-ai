# Code54 – fortgesetzte Codeprüfung, 12.11.2026

## Verbindlicher Anschluss

Kanonische Integrationslinie: `integrate/omega-apk54-20261111`, [PR #769](https://github.com/Ullmann27/device-ai/pull/769).
Der am 12.11. tatsächlich zurückgelesene Ausgangspunkt ist
`921cc382a1510ae5cf047aafa82900a945def731`.
Die älteren Angaben „APK nicht gebaut“, „Signierung offen“ und „Android läuft“
in den bisherigen Handoffs sind historische Checkpoints.

**Code54 / 0.3.53 existiert bereits als originalsignierter, unveröffentlichter
Fold7-Testentwurf.** In dieser Fortsetzung wurden bestehende Nachweise geprüft;
es wurde keine weitere APK gebaut oder signiert.

## Quellen und tatsächliches Artefakt

| Gegenstand | Verifizierter Stand |
|---|---|
| Signierte App-Quelle | `9713854bfb01d699f666058ac44379735307b66a` |
| Jüngster QA-Quellstand | `921cc382a1510ae5cf047aafa82900a945def731` |
| Gemeinsamer vollständiger `app`-Tree | `2d133946bd211f2c9a34402762e88d21d1c6a869` |
| Einziger Unterschied zwischen beiden Quellen | `.github/workflows/continuum-code54-qa.yml`, bekannte 17 SKIPs exakt inventarisiert |
| Originalsignierung | [43163584123](https://github.com/Ullmann27/device-ai/actions/runs/43163584123), SUCCESS, beide Jobs SUCCESS |
| Signierer-Quelle | `956c526d5c532c8d57a9901d9c8f180f83d96520` |
| Signierabschluss | 12.11.2026, 06:47 Uhr Europe/Vienna |
| Release-Entwurf | [442543223](https://github.com/Ullmann27/device-ai/releases/tag/untagged-8d9da4abaf035618f69c), `draft=true`, `published_at=null` |
| APK-Asset | `738249693`, 102.881.866 Bytes |
| APK SHA-256 | `7bc1af136e9ad4063bf57307ad154f26a5924c493973ceb6736322ffdc514d88` |
| Zertifikat laut Signier-/Releasebeleg | `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` |
| Paket | `at.heinz.deviceai.continuumlocal` |

Die Signierbindung bleibt beim früheren QA-Lauf `43134158265` auf `9713854b`.
Der spätere gleichartige Lauf wird nicht nachträglich als Signiergrundlage
ausgegeben. Das binäre Release-Asset wurde in dieser Fortsetzung nicht erneut
heruntergeladen; Größe und Digest stammen aus GitHubs Asset-Metadaten.

## Letzter vollständiger Android-Lauf

[QA-Lauf 43177154472](https://github.com/Ullmann27/device-ai/actions/runs/43177154472)
ist am 12.11.2026 um 09:45 Uhr Europe/Vienna vollständig beendet.
Das vollständige Android-Joblog `131044077327` wurde unabhängig gelesen;
die Zahlen wurden aus den tatsächlichen `PDF_QA_TEST`-Einträgen gezählt.

| Prüfung | Ergebnis |
|---|---|
| Drei vollständige UI-Klassen | 25 PASS / 0 FAIL / 0 SKIP |
| NewApi-Lint | SUCCESS |
| Vollständige Android-Suite | 4.842 PASS / 1 FAIL / 17 SKIP, 4.860 gesamt |
| Striktes natives PDF-Gate | FAIL |
| Unsignierter ARM64-Transportbau | SUCCESS |

Einziger Fehler:
`PdfIntakeNativeDecodeProbeUiTest.actualProviderReadRendersAndExtractsSyntheticPdf`.
Der Android-35-Aufruf erreicht `PdfRenderer` / `PdfProcessor.create` und scheitert
im Host an `java.io.FileDescriptor.getOwnerId$()`. Der Test erreicht damit
weder echtes Rendering noch Textextraktion. Das vorhandene Gate meldet korrekt
`STRICT_NATIVE_PDF_REQUIRED`; ein voller Android-PASS liegt nicht vor.

Die 17 Auslassungen sind exakt die inventarisierten Fälle: sechs ChiefPattern,
sechs FirstThinkingRun, zwei ChiefWorkspaceHost, zwei ContrastiveParity und
ein Cre1. Vollständige Namen und zurückgelesene IDs:
[`github-readback.json`](../../evidence/omega-resume-20261112/github-readback.json).

## Inhalt der vorhandenen Code54-APK

- Gesprächsgedächtnis und Korrekturverlauf aus #770/#776/#779; zugehörige
  tatsächliche UI-Fälle sind im neuen vollständigen Lauf bestanden.
- App-/Diagnoserouting und Negationskorrekturen aus #772/#778/#781 sowie die
  späteren Code54-Sicherheitskorrekturen bis `9713854b`.
- Cinema-Schalterkorrekturen aus #771; vorhandener Avatar und PDF-Bühne.
- Thermikreparaturen aus #773: ein vollständiger Modell-Prüfdurchlauf pro
  Laden, Wiederverwendung unveränderter Lernstatusauswertungen und identische
  Lichtgeometrie mit weniger wiederholten Berechnungen.

Kühlmodus und ThermalGuard waren bereits in der Code53-Referenz vorhanden.
Die neuen Reparaturen vermeiden belegte Doppelarbeit. Ihre tatsächliche
Temperatur-, Energie- und Geschwindigkeitswirkung auf dem Fold7 ist noch
nicht gemessen. P3, die gemeinsame Filament-Welt, ist in dieser Code54-APK
weiterhin nicht als fertig behauptet.

## Node-Prüfung und nächster Arbeitsschritt

Der letzte vollständige 354-Dateien-Node-Lauf bleibt erhalten:
`5b935b4f`, 2.455 PASS / 6 FAIL / 37 SKIP, 2.498 gesamt.
Spätere kleinere Vertragsprofile ersetzen diesen roten Vollbefund nicht.
Ein konkret identifizierter Testfehler betrifft den Parallelzugriff im MCP-Test:
ein 10-ms-Schlaf garantiert keine Überlappung mit der nur 40 ms laufenden
Erstanfrage. Die gezielte Testkorrektur ist abgeschlossen: **13/13 Fälle
bestanden**, Node24.22.1/uid1000; unabhängige Quellen- und Belegprüfung ohne
blockierenden Befund. Produktionscode und die echte 40-ms-Timeoutprüfung
bleiben unverändert. Ein eigener HTTP-Überlappungstest wartet auf den tatsächlichen
Reader-Eintritt, prüft 429 samt `MCP_READ_BUDGET` und weist die Timeoutgrenze
39 → 40 ms mit kontrollierter Uhr nach.

Unveränderte Baseline: 12/12 bestanden. Eine ausdrücklich kontrolliert
verspätete zweite Anfrage reproduziert die ursprüngliche Assertion
`200 !== 429`, ohne den Server oder seine Grenzen zu ändern. Die Ursache ist
damit als gültige verspätete Reihenfolge belegt; dies wird nicht als
unbeeinflusster Baselinefehler ausgegeben.

Vollständiger Abschluss:
[OMEGA-NODE-OVERLAP-HANDOFF-20261112.md](OMEGA-NODE-OVERLAP-HANDOFF-20261112.md),
Rohbelege unter `docs/evidence/omega-node-overlap-20261112/`.
Der geprüfte Test ist durch SHA256
`14018260eed046f424d95e110b63b12f0b0c4d087990f7eac82b2024864f3f9f`
gebunden. Ein neuer voller Node-PASS wird daraus nicht abgeleitet.

## Offene Abnahme und Fortführung

Native PDF-Dekodierung auf echter Android-Laufzeit sowie GPU, hörbare Sprache,
physische Thermik und Update-Erhaltung bleiben `NOT_RUN`, bis aktuelle
Gerätenachweise vorliegen. Der vorhandene Testentwurf ist genau für diesen
begrenzten Zweck gebunden; kein erneuter identischer Signierlauf ist nötig.

Die parallel bestehende Code55-Entwicklung hat ihre eigene Integrationslinie
#791. Diese Fortsetzung verändert deren Produktcode nicht.
Die abgeschlossene Testkorrektur und dieser aktuelle Readback werden gemeinsam
auf dem eigenen Zweig `work/omega-node-overlap-20261112` als Draft gegen #769
gesichert. Die Integration ist noch offen; es erfolgt kein Merge und kein neuer
Prüf- oder Buildlauf durch diesen Sicherungsschritt. Alte Rohfehler bleiben erhalten.
Die aktive Weiterarbeit folgt dem Besitzerauftrag zu Grafik, zehn Entwürfen
und der 3D-Ausarbeitung mit Opus.

Relevante Experimente: EXP-07 (Prüfqualität), EXP-12 (Fortsetzbarkeit).
Keine Gewichts-, Modell-, Sicherheits- oder Berechtigungsänderung.
Rückfall: nur die neuen Test-/Dokumentationscommits revertieren; die signierte
Code54-APK und ihre Quellenbindung bleiben unverändert.
