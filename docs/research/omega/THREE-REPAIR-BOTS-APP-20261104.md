# Drei Reparatur-Bots — App-Anschluss und Ereignislifecycle

## Auftrag und Basis

Reparatur-Bot 1 arbeitet ausschließlich auf einer eigenen Kopie von
`e101e393dd6c80803ac9d9ac91fccd9812c4a18d` (PR #491). Der gemeinsame
Reparatur-Draft ist #505. Andere Avatar-, Kamera-, Modell- und Gedächtniszweige
bleiben getrennt. Maßgebliche Experimente: EXP-07 und EXP-11.

## Reproduzierte Ursachen und Änderung

`JarvisSequenceView` ließ beliebige Cockpit-Ereignisse die aktuelle Auftrags-ID
setzen. Dadurch belegte bereits ein Boot-, Navigations- oder globales
Alarmstatus-Ereignis den Platz; ein echter Worker ohne `CommandFlyIn` wurde
anschließend ausgeblendet. Nach `TurnEnded` war die ID sofort vergessen, sodass
verspätete Statuskarten oder Commands alte Arbeit wieder sichtbar machen konnten.

Die View lässt jetzt nur ihre tatsächlichen Auftragsereignisse einen Auftrag
übernehmen. Sie merkt sich maximal 24 zuletzt beendete oder durch einen neuen
Command verdrängte IDs und verwirft deren verspätete Ereignisse. Auch ein Ende für
einen gerade nicht sichtbaren Auftrag wird vermerkt. Speicher-/Recall-Ereignisse
und der vorhandene Alarm-Extension-Effekt bleiben unabhängig.

`clear()` und Motion-Pause sind weiterhin visuelle Resets: Sie löschen nicht die
bekannten Abschlüsse und erklären aktive Arbeit nicht für beendet. Die Historie
ist ein begrenzter Präsentationsschutz, kein persistentes oder unbegrenzt
zurückreichendes Wiederholungssperrregister. Sie beeinflusst keine Ausführung,
Freigabe, Werkzeuge, Worker-Zustände oder gespeicherten Besitzerfakten.

## Tatsächliche Prüfung

Der ursprüngliche Gradle-/Robolectric-Versuch scheiterte vor Testausführung,
weil `com.android.application:8.10.0` im verfügbaren Offline-Cache fehlt.
Die elf neuen Android-UI-Tests sind daher **NOT_RUN**.

Der eng begrenzte Host-Ersatz kompiliert 25 vollständige Original-Produktionsdateien,
darunter die echte View, Effekte, Speicher-Renderer, Ereignisbus und Verträge.
Nur Android-Plattformtypen/Painter sowie eine unbenutzte Pillen-Label-Abhängigkeit
sind explizite Test-Stand-ins. Zeichnung, Queue, Lifecycle und Pillenaktionen
werfen bei Nutzung einen Fehler. Der Testwrapper erfasst Rendererfehler vor der
Fehlerisolierung des Busses und prüft sie nach jedem Test außerhalb des Busses.

| Identischer finaler Testbestand | Original e101e393 | Reparierte Quelle |
| --- | ---: | ---: |
| 15 neue Host-Lifecycle-Fälle | 6 PASS / 9 FAIL | 15 PASS |
| 5 unveränderte `BrainOrbSignalsTest` | 5 PASS | 5 PASS |
| Gesamt | 11 PASS / 9 FAIL, Exit 1 | 20 PASS / 0 FAIL, Exit 0 |

Beide Läufe kompilierten erfolgreich. Eingabehashes vor/nach Ausführung stimmen
jeweils überein. Der Vergleich der beiden Manifeste zeigt ausschließlich die
beabsichtigte Änderung an `JarvisSequenceView.kt`; Harness und alle anderen
kompilierten Quellen sind identisch. Der Langzeittest liefert 1.000 Endmeldungen,
weist die jüngsten Wiederholungen ab und erlaubt anschließend einen neuen Worker.
Weitere Kontrollen erhalten Speicherblitze/Recall, den Alarm-Extension-Effekt,
Abgrenzung vom aktiven Auftrag und Wiederaufnahme nach einer Motion-Pause.

Reproduktion:

```bash
CONTINUUM_KOTLIN_COMPILER_LIB=/path/to/gradle-8.11.1/lib \
  bash tools/sequence-turn-check/run.sh /path/to/fresh-output-directory
```

Ergebnisse und Rohlogs: `docs/evidence/three-repair-bots-20261104/app/`.
Diese Zahlen sind **HOST_FIXTURE_VERIFIED**, keine Android-/GPU-/Geräteabnahme.

## Nächster Einstieg und Rückfall

Bei verfügbarer vollständiger Android-Toolchain gezielt
`JarvisSequenceTurnLifecycleUiTest`, `JarvisSequenceViewDockingUiTest` und
`JarvisMemorySignalWiringUiTest` mit `-PcontinuumUiWidgetTests=true` ausführen.
Danach die echten Events im Fold7-Cockpit beobachten. Kein APK-/Release- oder
Installationsversprechen aus diesem Hosttest ableiten.

Rückfall: die Änderung an `JarvisSequenceView.kt` gemeinsam zurücknehmen.
Keine Datenmigration, kein Signatur-/Paketwechsel und keine Modelländerung.
