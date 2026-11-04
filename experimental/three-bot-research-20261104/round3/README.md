# Forschungsrunde 3: nächster Lernschritt und durchscheinender Neon-Avatar

4. November 2026 · RESEARCH_SHADOW · laufend, neue Ergebnisse noch nicht abgenommen.

Heinz hat die Fortsetzung der drei Forschungsaufträge beauftragt. Die Runden 1 und 2 bleiben unverändert am geprüften Ergebniscommit `13d7b736c7fdae8c9515802ba16c29386382a791` erhalten. Neue Arbeit findet ausschließlich in diesem Forschungsbereich statt.

## Aktualisierte visuelle Vorgabe

Heinz hat die bisherige V2 als Fortschritt eingeordnet und konkret präzisiert: Der Kopf soll transparenter sein, in den dunklen Hintergrund übergehen und seine Form über Neonfarben, schöne Umrisse, Schattierung und weiche Farbübergänge tragen. Diese Bildwirkung soll auf das echte animierbare 3D-Modell übertragen werden.

Daraus folgen für diese Runde vier überprüfbare Materialziele:

1. Die Haut besitzt wirkliche abgestufte Durchsichtigkeit, nicht nur eine dunklere deckende Farbe.
2. Feine Cyan-/Blau-/Violettlinien und gezielte Randemission machen die Form lesbar; die ganze Fläche wird nicht gleichmäßig aufgehellt.
3. Dunkle plastische Flächen und weiche Farbverläufe bleiben bei einer moderaten Drehung und Mimik erkennbar.
4. Augen, Mund und rückwärtige Flächen verursachen keine unbemerkt als gelungen ausgegebenen Doppelbilder. Hintergrundkontrollen machen echte Transparenz sichtbar.

Die Originalreferenzen bleiben verbindliche Vergleichseingaben. Eine identische vollständige 3D-Rekonstruktion wird nicht aus einem Einzelbild behauptet. Portable GLB-Materialbestandteile, zusätzliche Host-Bloomdarstellung und tatsächliche App-/Geräteabnahme werden getrennt ausgewiesen. Die vorhandene Kopfgeometrie, das Rig und die 21 Morphziele werden soweit technisch möglich erhalten; jede Abweichung muss belegt werden.

## Drei Aufträge

### Avatar

Vorhandene Materialien und neue parallele Beiträge aus #518/#495 prüfen. Zwei tatsächliche lokale GLB-Varianten zum aktualisierten Erscheinungsbild bauen, gegen V2 kontrolliert rendern und mindestens bei moderater Rotation sowie Sprech-/Lidbewegung betrachten. Aussehen, Transparenz, Herkunft, Parameter, Hashes und praktische Grenzen dokumentieren. Keine Produktintegration in dieser Forschungsrunde.

### Eigenes Sprachmodell

Die vollständig negative Besitzzuordnung aus Runde 2 auf eine einfachere einzelne Objekt-Person-Bindung zurückführen. Vorhandene Antwort-Loss-/Suffixforschung aus #508 berücksichtigen. Neuer enger Vergleich: vollständiger Textverlust gegenüber Antwort-/EOS-Verlust, bei gleichen Ausgangsgewichten, Daten und Budgets. Frische Objektwort-Splits und unabhängiger Wahrheitsprüfer. Mathematischer Adapter-Preflight, vollständige prospektive Veröffentlichung vor Training und keine Weiterverwendung gescheiterter Runde-2-Kandidaten. Die genaue endgültige Registrierung ist Voraussetzung für den Beginn des Trainings.

### Eigenständigkeit

Den in Runde 2 gefundenen Recall-Aufwand weiter charakterisieren. Die entscheidende offene Frage lautet, ob im unveränderten Ausgabeformat überhaupt drei vollständige gültige Nachweise in die maximalen 4.096 Zeichen passen können. Mindestlängen müssen aus tatsächlichen Pflichtfeldern folgen. Kürzere/spätere gültige Einträge und manipulierte Nachweise bilden Gegenproben. Eine mögliche Optimierung wird ausschließlich isoliert und gegen die unveränderte Referenz verglichen; kein neuer autonomer Produktworker.

## Anschluss und Grenzen

Zu Rundenbeginn wurden die offenen GitHub-Beiträge erneut gelesen. #518 ist am geprüften Startstand noch WIP ohne neue Produkt-Assetrevision. #495 enthält neue Hostprüfungen am TTS-Anschluss. #508 besitzt bereits Antwort-/EOS-Loss- und Suffixexperimente; eine weitere identische Intentstudie wäre deshalb keine sinnvolle Ergänzung. Alle Quellenvergleiche bleiben an genaue SHAs gebunden.

Der bestehende FilamentRenderer bei Produktbaseline `e101e393dd6c80803ac9d9ac91fccd9812c4a18d` verwendet transparente View/SwapChain und Bloom. Das bisherige Hautmaterial ist dagegen OPAQUE. Eine transparente View macht ein opakes Material nicht durchscheinend. Der untersuchte Appstand bindet Filament/gltfio 1.56.0; ein Hostbild ersetzt dessen Geräteabnahme nicht.

Keine Veränderung von Main, Originalsignatur, App-Offline-Schalter, Benutzerdateien oder produktiven Quellen. Keine bezahlte KI-API, kein fremdes Modell und keine GitHub Actions als Rechenweg. S01- und bestehende Modellgates bleiben unverändert. Konkrete fehlende Host-/Gerätebelege werden offen dokumentiert.
