# Host-Belege: durchgehender Dokumentablauf und App-Kristall (06.11.2026)

**Art:** HOST – Robolectric-Softwarezeichnung (884 × 760 dp, mdpi, Fold7-innen-ähnlich), `ContinuumDocumentFlowUiTest`
und `JarvisWorkWindowLifecycleUiTest` mit `-PcontinuumUiEvidenceDir`. **Kein Gerätebild, kein Filament-GPU-Bild**
(Robolectric zeichnet den 2D-Vorschaukopf). Die Seite ist eine beschriftete Beispielseite („Beispiel · Host-Testseite“),
kein Besitzerdokument. Das Modell ist eine **kontrollierte Test-Laufzeit** (`CONTROLLED_MODEL`): sie nimmt den echten
Prompt des `runLocal`-Pfads entgegen und antwortet, wenn der Test es sagt – keine Aussage über Modellqualität.
Das App-Symbol im Kristallbild ist ein grünes Testquadrat (auf dem Gerät: das echte Symbol aus dem PackageManager).

| Datei | Zeigt | Geprüft durch |
|---|---|---|
| `flow-1-reading-while-the-model-works.png` | Dokumentfenster während der echte Modelllauf läuft: „Entwurf wird lokal erstellt …“, ENTWURF ERSTELLEN gesperrt; JARVIS hält den Blick lesend auf dem Fenster (Director-Absicht `reading`, 4 s nach Start noch aktiv) | `jarvisReadsTheDocumentWhileTheModelWorksAndThenShowsTheDraft` |
| `flow-2-draft-unfolds-out-of-the-document.png` | Erstes Bild nach der Antwort: das Entwurfsfenster entfaltet sich aus der Mitte des Dokumentfensters | derselbe Test (Ursprung liegt im Dokumentfenster, Mittelpunkt ±1 px) |
| `flow-3-draft-with-source-card.png` | Entwurf mit Quellenkarte (eigene erste Seite des Dokuments), Herkunft „lokal erzeugt · vor der Verwendung prüfen“, Grundlage, BEARBEITEN / ALS DATEI SPEICHERN | derselbe Test |
| `flow-4-failed-draft-says-so.png` | Modelllauf scheitert (Wärmestopp): „Entwurf fehlgeschlagen – wegen Gerätewärme gestoppt. Nichts gespeichert.“, Cockpit bleibt, ENTWURF ERSTELLEN wieder frei | `aFailedModelRunReadsAsFailedNeverAsRunning` |
| `flow-5-after-stop-cockpit-restored.png` | Nach STOP während des Entwurfs: Fenster weg, Deck zurück, START bleibt; Chat sagt „Entwurf mit STOP abgebrochen – nichts gespeichert.“ | `stopDuringTheDraftEndsReadingWindowAndRunAsAborted` |
| `statusbar-before-after.png` | Links vorher: Ablaufkarten mitten über Gesicht und Kugel, „Auftrag angenommen“ doppelt. Rechts nachher: Kopf und Kugel frei, die Schritte als Statusleiste im Kopf des Arbeitsfensters („Auftrag angenommen · Bearbeitung läuft“) | `jarvisReadsTheDocumentWhileTheModelWorksAndThenShowsTheDraft` (gezeichnete Kartenboxen schneiden Kopf/Kugel nicht), `JarvisSequenceTurnLifecycleUiTest.anAcknowledgedCommandShowsEachStepOnce` |
| `crystal-before-disc.png` / `crystal-after.png` / `crystal-before-after-crop-4x.png` | Gleicher Zeitpunkt (60 ms nach Start der Lichtbahn), gleiche Route: vorher Glasscheibe, nachher App-Kristall (violett/magenta und cyan Facetten, Gürtelfacetten, Grat, Glanzstreif) um das Symbol | `anInstalledAppRidesTheTrailInsideItsCrystal` (Pixelvergleich) |

Nicht belegt (offen, Gerät): Filament-Kopf mit Lese-Augenbewegung, Kamerafahrt in Bewegung, echte PDF, echtes Modell,
Fold7 innen/außen, 200 % Schrift auf dem Gerät, Bildzeiten/Wärme.
