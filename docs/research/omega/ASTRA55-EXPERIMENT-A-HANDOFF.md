# Code55 Experiment A: Monitor-Uniforms

**Quellstand `3e2bc6d30e9c8ab088a1b7266643825af7e5879c`: Host-Gate PASS, Geräte-/GPU-Abnahme NOT_RUN.**
Referenz `5f87bdc610944b93d3173a83c5beeea315abeb66`. Integration: PR #805 → Sammel-PR #804 → #799 → #791. Bestehende EXP-07/11/12; keine neue Capture-/Rendererarchitektur. Veröffentlichung durch den kanonischen Integrator; kein eigener Remote-Write.

## Ein bestehender Übergabepunkt

`LiveMonitorFeed` → `CockpitLiveTexture` → `FilamentCockpitStage` in der gemeinsamen Engine. Nur `frameAvailable`, `pointerAvailable` und `contentScale` werden bei identischen Werten nicht nochmals gesetzt. Vier kleine Cachefelder: vor jedem Schreibversuch ungültig setzen, erst nach erfolgreichem Materialaufruf bestätigen, Rücksetzen bei Stream-Erstellung. Der interne Konstruktorparameter `coalesceUniforms = false` stellt den ursprünglichen Aufrufpfad wieder her. Frame-Import, Lease, Stream-Lebensdauer, Sitzungs-/Frischeprüfung, Viewport und Capture bleiben unverändert.

## Recherche vor Änderung

Offizielle Android-Dokumentation zu [MediaProjection](https://developer.android.com/media/grow/media-projection), [ImageReader](https://developer.android.com/reference/android/media/ImageReader), [Image](https://developer.android.com/reference/android/media/Image), [HardwareBuffer](https://developer.android.com/reference/android/hardware/HardwareBuffer) und [Surface](https://developer.android.com/reference/android/view/Surface), dazu [Googles Filament-Stream-Quelle](https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/Stream.java), abgerufen am **12.11.2026**. Aussagen, Versionsgrenzen und fehlgeschlagene Tag-Abfrage: `docs/evidence/astra55-experiment-a-20261112/sources.json`. Lokales Filament bleibt **1.56.0**; dessen tatsächlich aufgelöste Java-Bindung ruft für bool/float2 direkt native Methoden auf. Kein Bibliotheksupgrade oder Fremdcodeimport.

## Vorab definierter A/B-Vergleich

Die vollständige neu kompilierte Produktionsklasse läuft mit denselben protokollierenden Android-/Filament-Host-Fixtures: unveränderte Referenz, Kandidat EIN, Kandidat AUS. Echtes `LiveComputerFrames`, `CockpitStageFrame` und `CockpitPresentationSequence`; drei weitere Modelltypen werden unverändert aus den Originalquellen extrahiert und gehasht.

Gate: mindestens50% weniger stabile Uniformaufrufe in beiden Primärfolgen, identische resultierende Zustände und Import-/Freigabefolgen, keine zusätzlichen gehaltenen Puffer. Vorregistrierung: `docs/evidence/astra55-experiment-a-20261112/PREREGISTRATION.md`.

| Gemessene Folge | Referenz | Kandidat EIN | Kandidat AUS |
|---|---:|---:|---:|
| 60 synthetische Ticks,10 neue Frames |180|2|180|
| 60 synthetische Ticks,1 frischer Frame |180|2|180|

**180→2 /98,89% betrifft ausschließlich die drei stabilen Uniformaufrufe innerhalb der gemessenen Ticks.** Initialisierung/Abschluss sind aus diesem Zähler ausgenommen. Keine Aussage über sämtliche Filamentaufrufe, native GPU-Zeit, FPS, Energie oder Fold7-Latenz.

**14 Szenarien je Arm,42 Szenarioläufe:** Zustands-/Import-/Release-Spuren vollständig gleich. Negativfälle: Stop/Widerruf, neue Sitzung und alter Callback, alter Publisher, überholte/veraltete Frames, wechselnde Abmessungen/Monitorgeometrie, Quellviewport und Pointerbindung, abgelaufener Pointer, verzögerter GPU-Release, ungültiger Farbraum und Importfehler. Material-Schreibfehler wird beim nächsten Tick erneut ausgeführt. Init/Recreate sowie interner AUS-Pfad geprüft. Höchstens vier simulierte Gesamthandles; die vorhandene Grenze von zwei GPU-Referenzen bleibt unverändert.

Zusätzlich vollständige Kompilation auf dem Ausgangskandidaten `af4ce72` samt unverändertem aktuellen Aufrufer/Geometrie gegen echte SDK36-/Filament1.56.0-Typen: **PASS**, Kotlin2.4.0, maximal512MiB. Die späteren vier internen Cache-Invalidierungen wurden in den finalen Kotlin-Hostläufen kompiliert; der exakte kombinierte Android-Build bleibt das Integrator-Gate. Vorhandene Integrationskompilate liefern nur unveränderte Umgebungsabhängigkeiten. Das ist keine native GPU-Ausführung.

Zwei Vorbereitungsfehler sind erhalten: Der erste Harnesslauf erreichte wegen rückwärts gesetzter Testuhr die Ratengrenze statt den vorgesehenen Stale-Zustand; der erste SDK-Lauf hatte die neue unveränderte Geometrieklasse noch nicht im Klassenpfad. Keine Sicherheitsbedingung wurde abgeschwächt. Einzelheiten/Quellhashes: `docs/evidence/astra55-experiment-a-20261112/RESULT.json`. Rohprotokolle bleiben im lokalen `${OUTPUT}`; ihre Hashes sind im Ergebnis erfasst.

## Separater Review-Holdout

Nach dem ersten Host-Gate meldete der Integrator Teilwirkungen vor einer Materialausnahme. Ein unabhängig hinzugefügter fail-after-write-Spy reproduzierte **3/3 Fehler** im Ausgangskandidaten, während die unveränderte Referenz **3/3** bestand. Der endgültige Kandidat setzt bool-Caches vor einem Schreibversuch auf unbekannt und beide Skalierungswerte auf NaN; nur nach Erfolg werden sie bestätigt.

**Holdout final9/9 PASS** über alle drei Arme. Die unveränderten Primärszenarien danach erneut **42/42 PASS**,180→2 bleibt bestehen. Detail: `docs/evidence/astra55-experiment-a-20261112/HOLDOUT.md`. Ausgangsergebnis separat als `RESULT-INITIAL.json` aufbewahrt und ausdrücklich überholt markiert. Kein behaupteter nativer GPU-Fehler: kontrollierte Host-Grenzfehler.

## Reproduktion und #803-Anschluss

`${REPO}` ist der Checkout, `${TOOLS}` enthält die im Manifest gepinnten Kotlin2.0.20-Compiler-JARs, `${OUTPUT}` ein eigener Scratch-Ausgabeordner. JVM17 verwenden.

```sh
cd "${REPO}"
python3 experimental/code55-display-uniforms/run_experiment.py \
  --compiler-lib "${TOOLS}" --tmp "${OUTPUT}/tmp" \
  --output "${OUTPUT}/run" \
  --baseline 5f87bdc610944b93d3173a83c5beeea315abeb66
```

Mit zusätzlichem `--holdout` laufen die drei getrennten Teilwirkungs-Gegenproben.

Der Integrator verbindet #803. Danach `--baseline` auf dessen unveränderten kombinierten Commit setzen. Falls vorhanden, kompiliert der Runner die **echte** `LiveMonitorSubmission.kt`; nach der simulierten erfolgreichen Rendergrenze ruft er den vorhandenen `frameSubmitted` auf. Die dort bewusst erlaubte ältere, noch frische viewportgleiche Submission wird getrennt vom alten strikten Latest-Frame-Verhalten berücksichtigt. Keine Peerproduktion in Experiment A übernommen. Der kombinierte Lauf ist hier **NOT_RUN**.

Rückfall: `coalesceUniforms=false` oder allein den Uniformvergleich zurücknehmen. Keine Änderungen an Capture, Dispatch, Controller, Activities oder Renderer durch dieses Experiment.
