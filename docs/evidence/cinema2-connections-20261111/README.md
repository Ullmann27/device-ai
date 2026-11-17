# Cinema 2.0 – Verbindungen: Quittungspaket und Lesefaden (11.11.2026)

**Art des Nachweises:** HOST. JVM-Regeltests und Robolectric-API28-Softwarezeichnung (native Canvas) des echten
`HollywoodFuiStageOverlay`/`JarvisWorkWindow`-Paares. **Kein Gerät, keine GPU, kein Filament, kein Fold7.**
Es wurden keine Bilder erzeugt oder eingecheckt; die Pixelprüfungen laufen nur im Test.

Geprüfter Produktionsstand: `be33c2c97` (WIP-Commit „receipt packet back along the orb band and the avatar's reading
thread“), unverändert übernommen über die Integrationsbasis `6c538e2ef`. Der Produktionscode wurde in diesem Zweig
**nicht** geändert; er kompilierte und bestand die Tests im ersten Lauf.

## Was gebaut ist (be33c2c97)

1. **Quittungspaket (EXP_12):** Eine bestätigte, nicht wiederholte Werkzeugquittung
   (`AgentActivity.settleToolScene` → `JarvisWorkWindowHost.verifiedStep` → `HollywoodFuiStageOverlay.verifiedStep`)
   läuft 380 ms als ein Lichtpaket auf dem vorhandenen #747-Band vom Fensterrand zur Originalkugel zurück
   (`HollywoodFuiCinematics.receiptTravel`, `JarvisFluidLight.drawTransfer(…, receipt)`). Kein zweites Band.
2. **Lesefaden:** Solange echte Arbeit läuft (`setWorkActive(true)`), verbindet ein haarfeiner Faden die
   Augenhöhe des Avatar-Gesichts (echte Kopfgeometrie aus `JarvisWorkWindowHost.plan`) mit dem Fensterrand
   (`HollywoodFuiCinematics.attentionLink`, `Painterly.drawAttentionLink`, `setAttentionSource`).
   Uhr: ausschließlich `elapsedMs` des Fensters. Aus bei reduzierter Bewegung, Budget ohne Animation, STOP, Schließen.

## Neue Tests

| Klasse | Fälle | Was bewiesen wird |
|---|---|---|
| `app/src/test/…/ui/CinemaConnectionsTest.kt` | 7 | Laufzeit 0..1 nur innerhalb 380 ms; Paketmitte liegt exakt (≤ 0,001 dp) auf einem Mittelpunkt des gezeichneten Bandes und wandert vom Fenster zur Kugel; ohne Band/bei gesperrtem Budget kein Paket; Band selbst unverändert. Faden beginnt am Gesichtsrand auf Augenhöhe, endet am Fensterrand außerhalb der Eckradien, kein Punkt im Gesichtsinneren, kein Faden ohne echten Abstand oder bei ungültiger Geometrie; Funken bleiben auf der Linie und bewegen sich stetig auf der übergebenen Uhr. |
| `app/src/uiTest/…/ui/HollywoodFuiConnectionsUiTest.kt` | 5 | Robolectric: Faden nur bei laufender Arbeit (> 60 Pixel im Fadenkorridor), 0 Pixel ohne Arbeit, ohne Gesichtsgeometrie, nach STOP, beim Schließen, bei reduzierter Bewegung und ausgesetztem Budget; jedes abgetastete Pixel im geschützten Gesicht bleibt transparent. Paket erst nach `verifiedStep` **und** dem nächsten Fensterbild; jedes abweichende Pixel liegt (außer dem bestehenden EXP_12-Randimpuls) höchstens 12 px neben dem Band; nach 380 ms keine Abweichung mehr; keine Wiederholung derselben Quittung; ohne Arbeit/bei reduzierter Bewegung kein Paket; EXP_12 aus → keine Quittung. |

## Läufe (dieser Zweig, Worktree, `flock` auf die gemeinsame Gradle-Sperre)

| Lauf | Befehl (gekürzt) | Ergebnis |
|---|---|---|
| GRÜN | `:app:testDebugUnitTest -PcontinuumUiWidgetTests=true --tests '*CinemaConnectionsTest' '*HollywoodFui*' '*Cinema2DepthLightTest' '*JarvisFluidLight*' '*JarvisWorkWindow*'` | **86 Tests, 0 Fehler, 0 Errors, 0 übersprungen** (15 Klassen, u. a. `HollywoodFuiWindowUiTest` 9/9, `Cinema2DepthLightTest` 2/2) |
| ROT (Mutationsprobe, nicht eingecheckt) | gleiche zwei neue Klassen gegen absichtlich verdorbene Quelle: Fadenbedingung ohne `active`/Bewegung/Budget; Paket 40 dp neben dem Band | **12 Tests, 5 Fehler** – genau die erwarteten Fälle schlagen an (Faden ohne Arbeit, Faden bei reduzierter Bewegung, Paket nicht auf dem Band ×3). Danach Quelle zurückgesetzt (`git checkout`). |
| GRÜN nach Rücksetzen + Schalter-Matrix | 19 Klassen: beide neuen Klassen + alle Testklassen, die die Matrix als Beleg der 15 Cinema-Schalter nennt | **115 Tests, 0 Fehler, 0 Errors, 0 übersprungen** |

Rohzusammenfassung: `junit-summary.json` (aus den JUnit-XML-Dateien erzeugt).

## Grenzen / NOT_RUN

- Gesamte Android-Suite: **NOT_RUN** (nur gezielte Klassen).
- Fold7 innen/außen, Filament, echte GPU-Bildzeiten, Wärme, Akku, TalkBack: **NOT_RUN**.
- Die Robolectric-Bilder sind Software-Canvas auf dem Host, kein Gerätebild.
- Der Lesefaden hängt an **keinem** der 15 Cinema-Schalter (nur Master-Schalter, Budget, reduzierte Bewegung);
  siehe `docs/architecture/CINEMA-EXPERIMENT-MATRIX-20261111.md`.
