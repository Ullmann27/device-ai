# Notice-API: Layout, Ebenen und tatsächlicher Tap

Basis: `3ff83387af64bf9f99f4440f657f3d27937ca777`. Ausschließlich `ContinuumHomeView.kt` und `FullStageMemoryReviewUiTest.kt` geändert. Kein Commit, Push oder Gradle-Aufruf durch diesen Auftrag.

## Finale Änderung

- Notice `translationZ = density * .75f`: über Fullstage `.25` und ShellForeground `.5`, unter echten Controls `1`.
- Vorhandener Notice-Layoutblock vor den frühen Shell-Return verschoben.
- `transientBox(widthPx, heightPx)` bekommt die aktuellen Maße explizit aus `onMeasure(width,h)` bzw. `onLayout(r-l,b-t)`.
- Shell-Modus verwendet die aktuelle `shellAvatarBox` ohne nochmaligen Rail-Offset. Legacy verwendet weiterhin `layoutSpec.HERO` und seinen bisherigen Offset.
- Kein statischer Clippingausschnitt, kein weiterer Timer, keine Änderung am Presenter oder am Ereignisrouting.

## Exakt gleiche finale Testdatei

Methode: `actualActivityNoticeApiStaysVisibleAndDismissibleDuringMemoryFlight`.

| Lauf | Produktionsvariante | Ergebnis |
| --- | --- | --- |
| `final-red-layout` | Exakte 3ff-Produktion | RED: Shell legt die neue Notice nicht aus. |
| `final-green` | Verschobener Layoutblock und Z `.75`, aber alter HERO-Bezug | RED: Text sichtbar, tatsächlicher Center-Tap schließt die Notice nicht. Karte liegt über unteren Kontrollflächen. Der historische Verzeichnisname ist kein PASS. |
| `final-geometry-red-occlusion` | Richtige Shell-Geometrie und Layoutposition, Z weiterhin 0 | RED: Textpixel 248,26 wird von der aktiven Szene überzeichnet. |
| `final-geometry-green` | Richtige Shell-Geometrie, Layout und Z `.75` | GREEN: 1 Test, 0 Fehler. |

Alle vier Läufe verwenden exakt Test-SHA-256 `408be078c3d3a13f65b73834d62e8de12edba839a107fde2fee8c111916c365b`. Produktions-/Testkompilierung jeweils Exit 0. Quellen blieben während der Läufe unverändert. Vollständige Bindung und Receipthashes: `final-source-proof.json`.

Der Test arbeitet mit der echten AgentActivity bei hdpi. Ein positives synthetisches Gedächtnisereignis kommt über ihren vorhandenen MEMORY-Bus. Die Notice wird über die bestehende öffentliche Home-API erzeugt. Die normale Root-Messung/-Positionierung legt die Karte aus; der Test positioniert sie nicht selbst. Er prüft echte Einflugtranslation und Alpha, native sichtbare Pixel, tatsächliche opake Textpunkte und einen echten MotionEvent-Dismiss-Tap bei weiterhin aktiver Gedächtnisszene.

Die Textreferenz wird in derselben Root-Hierarchie bei unveränderter Clock erstellt. Nur für diesen Referenzframe ist die Fullstage-View per Alpha 0 unsichtbar; ihr aktiver Zustand wird ausdrücklich geprüft und Alpha sofort restauriert. Verglichen werden nur in dieser tatsächlichen Hierarchie opake Textpunkte. Es gibt keine tolerierte Pixelabweichung oder nach Beobachtung gewählte Prozentgrenze.

## Scopegrenze und frühere Diagnosen

Der erste `CalculationShown`-Versuch erzeugte keine Legacy-Notice, weil der Presenter diese Legacy-Karten im Shell-Modus bewusst nicht weiterleitet. Das wurde nicht verändert. Der finale Test belegt den öffentlichen Notice-API-/Viewhierarchie-Vertrag bei einer echten Gedächtnisszene, keinen neu angeschlossenen Calculation-/DocumentQuestion-/Account-Buspfad.

Alle früheren Diagnose-FAILs bleiben in ihren ursprünglichen Verzeichnissen erhalten, einschließlich der ersten zu strengen separaten Card-Rasterreferenz. Sie werden nicht als RED/GREEN-Nachweis derselben finalen Testdatei ausgegeben.

Finale Home-SHA-256: `79bd15a4713fb56b4d076d8dacb756839ae7f4f8421cc82b4a8208a294528e6a`.

Finale Test-SHA-256: `408be078c3d3a13f65b73834d62e8de12edba839a107fde2fee8c111916c365b`.

Diff: `final-notice.patch`. Exakter fokussierter Runner: `run-fullstage-transient-review.py`. Kompilierte Overlay-Basis bleibt d73. Kein Geräte-/Filament-GPU-Nachweis; gemeinsame Abschlussgates gehören dem Integrationslauf.
