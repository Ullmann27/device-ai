# CONTINUUM – zentraler Video-Referenzindex für Opus 5.5 und ARGON 4

Datum: 2026-11-05. Dieser Index verwendet die Originaldateien aus dem bestehenden Google-Drive-Ordner „Jarvis Video“. Keine Duplikate, keine neuen Medienrechte, keine Änderung an APK oder Quellversion.

**Originalordner (Drive):** https://drive.google.com/drive/folders/1VVOY4AbD_aTJE2zlI8MlJXH_SjGD4uhK

**ACHTUNG – Zugriff:** Zum Zeitpunkt der Indexierung waren Ordner und Videodateien laut Drive-Metadaten `not_shared`. Unser derzeitiger Google-Drive-Connector kann nur an bekannte Nutzer bzw. Workspace-Domänen freigeben und unterstützt KEINE allgemeine Freigabe `Jeder mit dem Link` für den Ordner. Daher darf kein Agent behaupten, dass diese Links für fremde Konten öffentlich sind. Der Eigentümer kann den Originalordner in Drive unter `Freigeben > Allgemeiner Zugriff > Jeder mit dem Link > Betrachter` verfügbar machen. Falls eine externe Freigabe nicht erwünscht ist, Videodateien dem Opus-Kontext als Anhänge bereitstellen. Keine Originaldateien ungefragt löschen oder verändern.

## Originaldateien

| ID | Originaldatei | Bytes laut Drive | Link |
|---|---|---:|---|
| V01 | `gemini_generated_video_000ea66a.mp4` | 8200147 | https://drive.google.com/file/d/1dF2CXf9e9jZfh-Q4dWqDWazYlFuEXj_s/view?usp=drivesdk |
| V02 | `gemini_generated_video_837e419a.mp4` | 12213241 | https://drive.google.com/file/d/11Kagzg8cburR-5EA7lF_qzLu4TaUS5zJ/view?usp=drivesdk |
| V03 | `f6144be7e261f6fe4e704a1f4014ed76.mp4` | 4868206 | https://drive.google.com/file/d/1HHhP_gtOx8LVbs6ouGFPiJ6_BJAw9Zhk/view?usp=drivesdk |
| V04 | `gemini_generated_video_f6e57d6b.mp4` | 15063998 | https://drive.google.com/file/d/1RZjs0POkzION8zvl2e1dZkxZXX1xxUpN/view?usp=drivesdk |
| V05 | `gemini_generated_video_3d63b568.mp4` | 12170661 | https://drive.google.com/file/d/1zdfW-vcQUeutLA2utOqoNtGd3J7iYREe/view?usp=drivesdk |
| V06 | `gemini_generated_video_33fd6ada.mp4` | 6721417 | https://drive.google.com/file/d/1wtee5SM-c0FeV440NYuJYK4an2h0lYSk/view?usp=drivesdk |
| V07 | `352eb01fc3727a42309837f9dd2b6be9.mp4` | 6687921 | https://drive.google.com/file/d/194Af4-pesBXQxmC0UR7GyoRtZqEHgtWZ/view?usp=drivesdk |
| V08 | `3e5b5b426c8e35ec62254c2359e45100.mp4` | 4013240 | https://drive.google.com/file/d/1aoYF_WerFZezyt8aR6uRJ5Bc3YUBp9yG/view?usp=drivesdk |
| V09 | `eefb15d11b85648f647fb5b5c88b2240.mp4` | 16890467 | https://drive.google.com/file/d/1V0QEzhmIN_Vr1TDLJZspIsljOrbOOqxq/view?usp=drivesdk |
| V10 | `c50ff70ba64cada8e5261bd398df8f07.mp4` | 7211968 | https://drive.google.com/file/d/1X1Pl7jLiAEIMmUUBdoNpmzaGaWCmbntv/view?usp=drivesdk |

Alle zehn Einträge sind als `video/mp4` direkt im verifizierten Drive-Ordner gelistet. Die Titel stellen keine verifizierte inhaltliche Beschreibung dar. Die Analyse ihres tatsächlichen Bildinhalts pro Video/Timecode ist noch ausständig; keine Szenenbezeichnung aus Dateinamen halluzinieren.

## Verbindliches Arbeitsverfahren für Video-Referenzen

1. Öffne jedes Video oder verlange Zugriff/Upload, wenn die Freigabe fehlt. Behaupte nicht, einen Clip gesehen zu haben, wenn er nicht tatsächlich angesehen wurde.
2. Erfasse pro Video: Dauer, relevante Szene mit exakt beobachteten Timecodes, Avatar-Kopfbewegung, Augen, Lippen, 3D-Icons, holografische Karte, Licht, Glas/Parallaxe, Bewegungskurve, Audio, Kameraführung und Übergang.
3. Lege einen Clip-zu-Code-Abgleich an: konkret beobachtete Frames → bestehende Kotlin-Komponenten (`FilamentAvatarRenderer`, `JarvisSequenceEffects`, `JarvisSequenceView`, `ContinuumHologramView`, `CockpitAnimationBus`, `AvatarDirector`) → vorhandene/unvollständige/fehlende Brücke → minimales Experiment.
4. Quellcode-Fakten und visuelle Wunschreferenzen getrennt halten. Diese Videos sind ZIELBILDER, keine Nachweise tatsächlich implementierter, ausgeführter Android-Aktionen.
5. Jede Animation unter derselben monotonic frame clock, ohne zusätzliche konkurrierende TextureViews oder unnötige pro-Frame-Allocations; echte Arbeitsergebnisse nur mit Tool-Receipt, ansonsten 'VORSCHAU'.
6. Die Szene gilt für JEDE sichtbar installierte und tatsächlich unterstützte App – nicht nur Gmail/WhatsApp. App-Catalogue/Capability-Adapter nach dem gekennzeichneten Masterprompt implementieren.

**Handoff-Prompt:** `docs/research/omega/OPUS55_MASTER_PROMPT_WITH_VIDEOS_APP_CATALOG_20261105.md`.
**Opus-Branch / Code-Vorarbeit:** Draft PR #564, `research/opus55-continuum-voice-hud-preflight-20261105`.
**Signierte Code43-Basis:** `ff8f6070bb3a6f4dfbb6215ce3e3e269974c1d53`. Keine Signier- oder Releaseänderung.