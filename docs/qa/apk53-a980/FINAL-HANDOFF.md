# Code53 - geprüfter, unsignierter APK-Kandidat

## Gemeinsamer Stand

- App: [PR739](https://github.com/Ullmann27/device-ai/pull/739), Commit `a980a19ed3ab44323009ae27eb299d322ff11679`.
- App-Tree: `a9d3da18e774ccc1d3c7cd61c493ad65928c4d2e`.
- Separater QA-Zweig: `qa/apk53-a980-evidence-20261111`, PR746.
- CI: [42934541308](https://github.com/Ullmann27/device-ai/actions/runs/42934541308), Job `validate-frozen-source`, **SUCCESS**.
- Workflow-SHA `edff51d7c124e0a036c5b4a2abb23625c102cf4b`; geprüft wurde ausdrücklich die App-SHA a980, nicht die spätere Dokumentations-SHA.

## Übernommene Arbeiten

PRs **#715, #720, #724, #725, #726, #730, #731, #732, #733, #734, #741, #742, #743, #744, #745**.
Exakte Herkunft und Datei-/Abstammungsvergleiche stehen in QA.json. #741/#742 wurden durch die frühere Integrationsabstammung automatisch geschlossen; ihre Zweige bleiben erhalten. Kein Merge nach main.

Repariert: Fenster-/Ergebniszuordnung bei wartenden Aufträgen, sofortiger STOP mit kontrollierter Rückkehr, Avatarlicht bei fehlendem Farbakzent, Priorität des Dokument-Leseblicks, Zuordnung natürlicher Recherchefreigaben/ASR und sichtbare Rückmeldung bei nicht ausgeführten Anfragen. Mehrdeutige Bezüge werden nicht als sichere Erinnerung ausgegeben.

## Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Android vollständig, lokal und GitHub | 4.464 PASS /17 SKIP /0 FAIL, 4.481 insgesamt |
| Node vollständig, lokal | 2.462 PASS /31 SKIP /0 FAIL |
| Node vollständig + kritischer Teil, GitHub | PASS, exakte Quellbindung; Zahlen nicht im Job-Log ausgegeben |
| Kritischer Node-Teil lokal | 136 PASS, Teilmenge, nicht addieren |
| Backend lokal und GitHub | 64 PASS |
| Kotlin-Quellprüfungen lokal und GitHub | 12 PASS |
| Lint lokal und GitHub | PASS, nur konfigurierte NewApi-Prüfung |
| ARM64 assembleRelease + APK-Validator | PASS, lokal und GitHub |

Kein Test-Skip wird als bestanden gezählt. SKIPS.md und ci/receipt.json nennen die Grenzen.

## Tatsächlich gebaute APK

`at.heinz.deviceai.continuumlocal`, **0.3.52 / versionCode53**, ARM64,
**102.588.096 Bytes**. Status **UNSIGNED_NOT_INSTALLABLE**.

SHA-256 lokal und im unabhängigen CI-Validator:
`f138b92a99ea9aab67103490f0a249fd14adbc84c3c5346cd2169f36a40f1921`

CRC, Originalassets, fünf ARM64-Bibliotheken, 16-KiB-Ausrichtung, Paketkennung und fehlende Signatur sind geprüft.

- [CI-APK-Archiv](https://github.com/Ullmann27/device-ai/actions/runs/42934541308/artifacts/13820816734) - 73299437 Bytes, Archiv-SHA-256 `33fd4578bcf561bac68b3566fbd98d9ee0da9f99585517d3204cb846def870d7`.
- [CI-Prüfarchiv](https://github.com/Ullmann27/device-ai/actions/runs/42934541308/artifacts/13820732031) - 423301506 Bytes, Archiv-SHA-256 `a110b3ef28370951c99de304cfdeb7b3d20c2fc84ffec6d8d381c93a68c26265`.

GitHub-Artefakte laut Metadaten gültig bis 25.11.2026. Zusätzlich stehen dauerhaft gespeicherte Besitzerdownloads der identischen APK, aller lokalen Bild-/Testnachweise, der acht Originalavatar-Posen und des siebenseitigen Bildnachweises bereit.

## Bilder und Abnahmegrenzen

- [Acht Originalavatar-PNGs](original-avatar): unveränderter GLB, 21 Morphs, Blender Cycles CPU, geskriptete Timeline.
- [Sechs Android-PNGs](images/native): Robolectric API28 Canvas, vorhandener 2D-Avatarersatz, geskriptete Ereignisse.
- [Bildnachweis-Erzeugung](images/README.md): alle neun verwendeten Bilder hashgebunden, fünf positive/negative Eingangsprüfungen PASS.

**Fold7, echte Filament/AGSL-GPU, natürliche Sprachqualität, Live-Lippensynchronität, Modellinferenz und thermische/FPS-Messung bleiben NOT_RUN.** Die Bilder beweisen keine Geräteabnahme.

## Nächster geschützter Schritt

Der Code52-Signierweg ist unverändert. Für ein installierbares Code53-Update sind die Code53-Bindung an exakt diesen Quellstand, QA-Lauf und Artefakt sowie die ausdrückliche Besitzerfreigabe erforderlich. Vorher die dann aktuelle installierte/veröffentlichte Versionsnummer erneut prüfen. Originalzertifikat und bestehende Reviewer-/Branch-Schutzregeln erhalten; keine Umgehung. Keine Signierung, Veröffentlichung, Installation oder main-Änderung ausgeführt.

Unfertige Gehirn-/Modellzweige #717-719 und Android-Systemzweig #735 wurden nicht in diesen Kandidaten befördert. Die separate Weiterentwicklung in PR740 ist nicht durch diesen a980-Nachweis abgedeckt.

## Unterbrechungsnachweis

Die lokale Arbeitsumgebung fiel nach erfolgreichem CI-Abschluss aus. Alle abgeschlossenen Quelländerungen, die 14 ausgewählten Original-PNGs und Besitzerdownloads waren zuvor gesichert. Ein zusätzliches lokales Entpacken des CI-Archivs ist daher NOT_RUN; CI-Ergebnis und APK-Hash sind aus dem abgeschlossenen Runner-Log und den GitHub-Artefaktmetadaten zurückgelesen. Der Abschluss wurde direkt im QA-Zweig gesichert.
