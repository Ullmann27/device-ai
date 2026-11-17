# Referenzkorrektur – frühe Stilprobe

**DRAFT / HOST_COMPOSITE**, 12.11.2026. Gezeigter Quellstand:
`6c951be2a165b658f16fff63dced8a5a3a9e9ef7`.

Diese Stilprobe entstand vor dem anschließenden Port auf die gemeinsame
Unified3D-Szene. Sie belegt ausschließlich den genannten Freeze, keine
spätere Hauptteam-Fassung und keinen Android-/Fold7-Gerätelauf.

- [Fold](fold-idle.png), 884 × 760 plus Herkunftshinweis.
- [Phone](phone-idle.png), 411 × 960 plus Herkunftshinweis.

Blender 4.3.2 Cycles CPU, zwei Threads, acht Samples, kein Denoising.
Kotlinexport und Renderlauf bestanden; Quellen blieben stabil und sauber.
Die beiden Monitoreck-Prüfungen hatten maximal
0,00033553207396153084 Pixel Abweichung. Alle sechs im Manifest erfassten
Ausgabedateihashes wurden nach dem Lauf erneut geprüft.

Die Originalfigur erscheint vor der Originalkugel. Die Kamerarahmung verwendet
`AvatarFraming.pullBack` einmal mit dem tatsächlichen Produktionswert.
`AvatarPresentation` und `CockpitWorkstationChrome` sind die kompilierten
Produktions-Painter. Dokument- und Dateiinhalte sind ausschließlich
**VORSCHAU · BEISPIELINHALT**, deckend auf der tatsächlichen Monitor-Quad;
es gibt keine Live-Bildquelle und keine damit behauptete App-Aktion.

Sichtbefund: Gesicht und Dokumenttext sind klar erkennbar. Die Phone-Kopfgröße
und die schmale Tastaturprojektion bleiben vor einem Finalrender zu prüfen.
Diese Serie enthält keinen Videonachweis.

[Quelle, Werkzeughashes und Ausgaben](preview-manifest.json) ·
[Geometrie und Renderparameter](blender-manifest.json) ·
[Kotlinexport](scene-export.json).

Reproduktion: [Preview-Anleitung](../../../tools/cockpit-workstation/README.md)
mit `--samples 8 --draft --content example` und exakt diesem Source-Commit.
