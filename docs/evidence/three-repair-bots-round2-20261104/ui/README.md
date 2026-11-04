# Reparatur-Bot3 – Sequenzabschluss über Motion-/View-Grenzen

Stand: 04.11.2026. Endgültiger Quellkandidat: `0b9c651440a43d446bb116c0794381422be4ff99`.
Produktionsblob: `86d39aedf0fadb6e0633bec42112b36f3a6fea9c`.
Erster Motion-Kandidat: `3516f1cda681944cede74732dd68e5a47d991e3d`;
zusätzlicher Test-only-RED-Stand: `d24ad51b0235826cf0baaa998a2fb4af80a17404`.
Der Kandidat ist bis zur Veröffentlichung durch den koordinierenden Root-Agenten LOCAL_ONLY.

## Nachgewiesener Fehler und begrenzte Korrektur

Eine weiterbestehende `JarvisSequenceView` erhielt `TurnEnded`, während
`setMotionAllowed(false)` aktiv war. Sowohl `render()` als auch `apply()` kehrten
vor `retireTurn()` zurück. Nach `setMotionAllowed(true)` konnte daher ein späterer
Worker-, Timeline- oder Command-Callback desselben tatsächlich abgeschlossenen
Auftrags wieder Karten erzeugen. Der visuelle Reset selbst ist kein Beleg für
Auftragsabschluss und darf laufende Aufträge nicht künstlich historisieren.

Die Korrektur nimmt ausschließlich `TurnEnded` von den visuellen Eingangs- und
Generationssperren aus. Der Abschluss wird weiterhin auf dem UI-Thread in die
bestehende begrenzte Historie aufgenommen. Andere Ereignisse behalten ihre
Motion-, Detach- und Generationssperre. Der historische Abschlusszweig steht vor
der visuellen Sperre; bei normalem aktivem Abschluss bleibt das bisherige
Dismiss-Verhalten erhalten. `clear()` sowie die maximale Historie von 24 IDs
bleiben unverändert. Es gibt keine neue Worker-Ausführung, Wiederholung,
Abonnements, Threads, globalen Speicher oder Hintergrundschleife.

### Anschlusskorrektur nach unabhängiger Prüfung

Der erste Motion-Kandidat verwendete auch für den neu zugelassenen Hintergrund-
Abschluss noch `View.post`. Bei einer nie angehängten oder dauerhaft entfernten
View hängt dessen Verarbeitung vom späteren Attach ab. Der empfangene Abschluss
kann damit in der View-RunQueue liegen bleiben; die 24-ID-Grenze des bereits
verarbeiteten Verlaufs begrenzt diese aufgeschobenen Callbacks nicht.

Der endgültige Kandidat verwendet deshalb ausschließlich für Hintergrund-
`TurnEnded` einen expliziten `Handler(Looper.getMainLooper())`. Abschlüsse werden
auf dem bestehenden UI-Thread verarbeitet, unabhängig davon, ob die View später
noch angehängt wird. Normale visuelle Ereignisse behalten `View.post` und ihren
Generationsschutz. Es wird kein zusätzlicher Thread gestartet. Der neue Host-
Handler ist ausschließlich kompilierbarer Stand-in; jeder Aufruf von `post`
wirft, statt eine Android-Queue zu simulieren.

## Tatsächlich gelesener Anschlussvertrag

| Stelle | Tatsächliches Verhalten und Grenze |
| --- | --- |
| `cockpit/CockpitAnimationEvent.kt`, `CockpitAnimationBus.emit` | Zustellung an aktuelle Subscriber auf dem emittierenden Thread; Fehlerisolierung pro Listener; keine persistente oder nachträglich abspielbare Ereignishistorie. |
| `ui/CockpitAnimationPresenter.render/present` | Stellt über `home.post` auf den UI-Thread zu. Ein nicht angehängtes Home beendet `present()` vor der Avatarzustellung. Nur bei eigenem Avatar-Slot wird `onAnimationEvent` aufgerufen. |
| `avatar/JarvisHoloAvatar.onAnimationEvent` | Leitet an seine aktuell vorhandene Sequence weiter. `detach()` entfernt die alte Sequence; ein späteres neues `attach()` erstellt eine neue Instanz. |
| `ContinuumHologramView.setCockpitSequence` | Fügt die Sequence als echtes Child-View hinzu. Dies ist keine nur separat gezeichnete, unangehängte Datenstruktur. |
| `ContinuumHologramView.setForegroundActive/updateMotionPolicy` | Schaltet für dieselbe Sequence `setMotionAllowed(canAnimateFrame())`; Foreground, Motion, Sichtbarkeit und Attachment bestimmen die Animationserlaubnis. |
| `AgentActivity.onPause/onResume` | Schaltet Foreground für die bestehende Hologrammoberfläche aus/ein. Die Reparatur ändert diese Activity und ihre TTS-/Avatarverantwortung nicht. |
| `JarvisSequenceView.render/onAttachedToWindow/onDetachedFromWindow` | Hintergrund-Abschlüsse werden über den Main-Handler verarbeitet; normale visuelle Ereignisse verwenden weiterhin `View.post` samt Generationsschutz. Die Abschlussverarbeitung benötigt kein späteres View-Attach. |

Die Reparatur behauptet ausdrücklich **kein** Nachholen eines Abschlusses, der
wegen eines abgemeldeten Subscribers, eines abgetrennten Home oder einer neu
erzeugten Sequence nie bei dieser Instanz ankam. Die Detach-Tests behalten einen
direkten Subscriber für dieselbe View und schicken ihr tatsächlich Ereignisse;
das ist der dokumentierte direkte `bus.subscribe(view)`-Vertrag. Eine neue View
übernimmt keine Historie ihrer Vorgängerin. Diese Grenze bleibt bestehen.

## Reproduzierbarer RED → GREEN-Nachweis

| Phase | Ergebnis | Produktionsquelle |
| --- | --- | --- |
| Baseline | PASS 20/20; 15 vorhandene Sequenzfälle plus 5 unveränderte BrainOrb-Regressionsfälle | Originalblob `bd943129a52c92ccb46022c0f083e328acbf24b9` |
| RED | 25 Tests; exakt 3 erwartete Fehler; die vorhandenen 20 und zwei neuen Kontrollfälle bestehen | Derselbe Originalblob mit fünf zusätzlichen Tests |
| Erster GREEN | PASS 25/25 | Erster Motion-Kandidatenblob `e892cdc13a6dbc30e9b11275861a6826ffea978f` |
| Handler-GREEN | PASS 25/25 | Endgültiger Kandidatenblob `86d39aedf0fadb6e0633bec42112b36f3a6fea9c` |

Die drei RED-Fehler sind jeweils `expected 0 but was 1` sichtbare Karten:

- `completionReceivedWhileMotionIsPausedRejectsLateWorker`
- `completionReceivedWhileMotionIsPausedRejectsLateTimeline`
- `unseenCompletionReceivedDuringPauseRejectsDelayedCommand`

Die neuen Kontrollen prüfen, dass ein anderer, weiterhin laufender Auftrag nach
einer Pause fortsetzen kann und ein neuer Worker nach dem während der Pause
empfangenen Abschluss einen freien Slot erhält. Sie prüfen außerdem, dass der
Abschluss während der Pause keine Animation erzeugt. Die bisherigen Kontrollen
für Ende vor der Pause, noch aktiven Auftrag nach der Pause, globale Memory-/
Alarmereignisse, unverwandte Worker und lange Sitzungen bleiben unverändert.

`baseline/`, `red/`, `green/` und `handler-green/` enthalten die Original-Compilerlogs, JUnitlogs,
SHA-256-Eingangsmanifeste und unmittelbar nach dem Lauf geprüfte Integritätslogs.
`manifest.json` benennt Source-Commit, Produktions- und Testhash sowie jede Phase.
Zusätzlich wurde geprüft: Baseline- und RED-Produktionshash sind identisch;
RED- und GREEN-Hosttesthash sind identisch; der GREEN-Produktionshash entspricht
der jeweils geprüften Kandidatendatei. Der Handler-GREEN-Lauf enthält zusätzlich
den neuen fail-fast-Handler als gehashten Kompilierungseingang.

Ausführung aus dem zugehörigen Checkout:

```bash
CONTINUUM_KOTLIN_COMPILER_LIB=/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib \
  bash tools/sequence-turn-check/run.sh /absoluter/neuer/ausgabeordner
```

Der Hostlauf kompiliert vollständige Original-Produktionsdateien. Android-
Stand-ins bleiben explizit und unverändert; sie bilden weder Lifecycle noch
Hintergrundqueue, Rendering, echten Looper oder Geräteverhalten nach. Die äußere
Fehlererfassung um den echten Eventbus bleibt erhalten: Ein intern abgefangener
Renderer-/Fixturefehler kann keinen negativen Test scheinbar bestehen lassen.

## Robolectric-Kandidat und unabhängige Abnahme

`JarvisSequenceTurnLifecycleUiTest.kt` enthält nun 25 Fälle: die elf vorhandenen,
die fünf neuen Motion-Fälle, sieben Queue-/View-Lifecycle-Szenarien des ersten
Kandidaten und zwei ergänzende Fälle ohne späteres Attach.
`@LooperMode(PAUSED)` macht die Reihenfolge von Threadzustellung und Lifecycle-
Wechsel kontrollierbar. Die Queue-/Detach-Fälle hängen die echte View in ein
`FrameLayout`, überprüfen den tatsächlichen Attachzustand, benutzen einen echten
Hintergrundthread und lassen anschließend den Robolectric-Main-Looper laufen.
Es werden keine manuellen Lifecycle-Callbacks oder neuen Android-Stand-ins benutzt.

Die neun ergänzenden Queue-/Lifecycle-Szenarien sind:

1. Bereits vor der Motionpause geposteter Abschluss übersteht den Generationswechsel.
2. Bereits vor Detach geposteter Abschluss übersteht Reattach derselben View.
3. Direkt abonnierte View behält einen auf dem UI-Thread während Detach empfangenen Abschluss.
4. Dasselbe mit Hintergrundzustellung während Detach.
5. Nicht abgeschlossener Auftrag darf nach Detach/Reattach fortsetzen.
6. Alter visueller Worker-Callback wird über die Motiongrenze weiterhin verworfen; ein frischer Callback desselben lebenden Auftrags bleibt erlaubt.
7. Dasselbe über die Detach-/Reattach-Grenze.
8. Nie angehängter direkter Subscriber: Motion aus, Hintergrund-Abschluss, Main-Looper abarbeiten, Motion an, späterer Worker; es darf keine erledigte Karte erscheinen.
9. Dauerhaft entfernter direkter Subscriber: 1.000 Hintergrund-Abschlüsse, Main-Looper abarbeiten, kein Reattach; der vorhandene interne Verlauf muss genau die letzten 24 IDs enthalten. Hier wird der begrenzte Verlauf gezielt per Reflection gelesen, weil Reattach selbst die falsche View-Queue abarbeiten und den Fehler maskieren würde.

Status bei Aktualisierung dieses Belegs: **NOT_RUN_BY_THIS_AGENT** für die 25
Robolectric-Fälle. Der Android-QA-Agent führt die beiden zuletzt ergänzten Fälle
auf dem Test-only-RED-Stand `d24ad51b0235826cf0baaa998a2fb4af80a17404` aus und
prüft danach den eingefrorenen Handler-Kandidaten unabhängig. Ergebnisse dieses
noch laufenden Android-Laufs werden hier nicht vorweggenommen. Ein fremder Testbericht darf nur
bei nachgewiesen identischen Quell-/Testbytes als Kandidatenabnahme ergänzt werden.
Die 25 Hosttests sind kein Ersatz für diesen Nachweis.

## Verbleibende Grenzen

- Historie bleibt auf 24 zuletzt aufgenommene IDs begrenzt; dies ist kein dauerhaftes Worker-Ledger und keine Garantie für beliebig alte Ereignisse.
- Ereignisse, die nie bei dieser Subscriber-Instanz ankamen, werden nicht rekonstruiert. Echte neue Avatar-/View-Instanzen beginnen ohne den alten Verlauf.
- Kein vollständiger Android-Build, APK-, Device-/Fold7-, Rendering-, Performance- oder Release-Nachweis aus diesem Hostlauf.
- EXP07/EXP11/EXP12, bestehende Reparaturisolation, Modell-/Paket-/Versions-/Offline-/ToolGate-Regeln und aktive Worker-Ausführung wurden nicht geändert.
- Keine eigenen GitHub-Schreibvorgänge, Actions, Releases, Signierung oder Installation. Veröffentlichung erfolgt ausschließlich über Root in bestehendem PR505.
