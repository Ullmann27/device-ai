# Unabhängiger, begrenzter Callback-Review

Datum: 2026-11-04. Prüfer: Helfer 3 (`avatar_independent_review`).

**Ergebnis: keine blockierenden Befunde im statischen Review.**

## Exakter Prüfgegenstand

Geprüft wurde der Diff von `7dc53797669f8621ea0be557ed2fb8b6936632c9` nach
`67ea15145510df5f41781ba019ea559df8491dbf` im Checkout
`/workspace/scratch/b6ee64d418cf/device-ai-avatar-integration`:

- `app/src/main/java/at/heinz/deviceai/AgentActivity.kt`
- `app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt`
- `app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechAcceptanceTest.kt`
- `app/src/uiTest/java/at/heinz/deviceai/AvatarTtsLiveConnectionUiTest.kt`

Die beiden Acceptance-Testmethoden und alle sechs Listener-Testmethoden wurden
inhaltlich gelesen; ihre Anzahl ist keine Behauptung eines eigenen Testlaufs.

## Beurteilung

`speechStarted` entscheidet unter dem bestehenden Director-Monitor über eine neue
Anfrage. Leere, laufende und kürzlich abgeschlossene IDs ergeben `false`, bevor
Zustand oder Ereignisse verändert werden. Die Activity prüft diesen Rückgabewert
vor `voiceSignal.anchor(..., 0, ...)` und den weiteren Startfolgen. Ein wiederholter
Start kann damit den PCM-Anker nicht mehr über diesen Pfad neu setzen.

`wordRange` prüft aktive ID, Textgrenzen, Bereichsreihenfolge und den optionalen
Audiomarker vor der Aktualisierung seiner Reihenfolgedaten. Die Activity reicht
den tatsächlich empfangenen `frame` weiter und ankert nur bei `true`. Ein neuerer
Textbereich mit rückwärtslaufendem Audiomarker wird deshalb gemeinsam für die
Visemverarbeitung und die anschließende Ankerentscheidung abgelehnt. Gleiche
Audiomarker bleiben bei einem gültigen Folgebereich zulässig.

Ein gültiger Zahlenbereich aktualisiert Text- und Audioreihenfolge und liefert
`true`, auch wenn die Buchstabenapproximation keine Viseme erzeugt. Dadurch kann
PCM weiter synchronisiert werden, ohne einen Buchstabenmund für Zahlen zu
erfinden. Die Entscheidung bleibt im Director; die Activity führt keine zweite
Reihenfolgezustandsmaschine ein.

Die Annahmeentscheidung und ihre Zustandsänderungen sind im Director synchronisiert.
Der gezeigte Activity-Pfad führt die Entscheidung und das anschließende Ankern
auf dem UI-Thread aus. Dies ist **keine Behauptung einer gemeinsamen atomaren
Transaktion** über Director und PCM-Signal mit ihren getrennten Monitoren.

## Gelesene Kontrollen

| Kontrolle | Aussage des Testcodes |
| --- | --- |
| Acceptance: neue, doppelte, abgeschlossene ID | Rückgabewert und unveränderte Ereignisliste bei Ablehnung; Nachfolger wird angenommen. |
| Acceptance: `A 42 O` | Negativer Marker, Wiederholung, Rücksprung und fremde ID werden abgelehnt; Zahlen setzen die Reihenfolge ohne zusätzliches Visemereignis. |
| Installierter Listener: PCM / Abschluss | Synthetisches PCM gelangt durch den echten Activity-Listener und den geplanten `voiceFrame` zum sichtbaren Reducer; AA-Bereich und Abschluss werden geprüft. |
| Fremde / abgeschlossene ID | Start, PCM, Bereich und Abschluss einer alten ID verändern die aktuelle Pose nicht. |
| Wiederholter Start | PCM-Anker und aktuelle Mundpose müssen unverändert bleiben. |
| Bereichsreihenfolge | Rückwärtsmarker dürfen weder Anker noch Pose ändern; gültige Zahlenmarker und der anschließende O-Bereich werden angenommen. |
| Unterbrochener Stop | Beendet die aktuelle Sprache; späte alte Callbacks und ein alter Stop dürfen den Nachfolger nicht beenden. |
| Reparent / Pause | Dieselbe sichtbare View wird weiterverwendet; der zweite Bereich `2..3` in `A A` ist ein echter Folgebereich. AA- und PCM-Energieassertions bleiben erhalten; Pause weist späte Callbacks ab. |

Die Reparent-Änderung korrigiert den Testreiz auf einen Folgebereich. Sie entfernt
keine bestehende PCM- oder AA-Assertion und benötigt keine Ausnahme vom Replayguard.

## Evidenzgrenze und reproduzierbare Lesebefehle

Es wurden in diesem Review **keine Gradle-Suite, kein Gerät und kein zusätzlicher
Testapparat gestartet**. Die von Root gemeldeten 33/33 bestandenen Tests sind
Root-Evidenz, kein unabhängiger Ausführungslauf dieses Helfers. Der spätere gemeinsame
28-Klassen-Lauf gehört ebenfalls nicht zu diesem statischen Review. Die Listener-
Tests verwenden synthetische Callback-IDs und PCM unter Robolectric mit LEGACY-
Grafik; sie belegen weder hörbare Geräteausgabe noch GPU-Pixel oder Naturtreue.

Die zuvor gelesene Root-Datei
`/workspace/scratch/b6ee64d418cf/avatar-round2-evidence/callback-baseline/summary.json`
protokolliert zwei Fehler unter sechs Listener-Tests: Rückwärtsmarker änderte den
Anker von `(100, 389)` auf `(90, 559)`; ein doppelter Start von `(0, 219)` auf
`(0, 389)`. Der geprüfte Patch adressiert unmittelbar diese beiden Pfade.

```sh
git diff 7dc5379..67ea151 -- app/src/main/java/at/heinz/deviceai/AgentActivity.kt app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechAcceptanceTest.kt app/src/uiTest/java/at/heinz/deviceai/AvatarTtsLiveConnectionUiTest.kt
git show 67ea151:app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt
git show 67ea151:app/src/uiTest/java/at/heinz/deviceai/AvatarTtsLiveConnectionUiTest.kt
git rev-parse 7dc5379 67ea151
```

Keine Aussage dieses Berichts erweitert die Prüfung auf nachfolgende Renderer-
Änderungen, Gerätefreigabe oder die Gültigkeit des GLB. Die Asset-V1-Einordnung ist
separat dokumentiert.
