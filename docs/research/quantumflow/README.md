# Quantumflow – verifizierter Open-Source-Codekatalog

Forschungsbranch: `research/continuum-quantumflow-upstreams-20261102` · Draft-PR #350. Basis: Grand Challenge PR #349 @ `93422eb6a4f10fa34edb6efb5bfc45fa65dd0c13`.

**Achtung:** Während der Recherche entwickelte sich PR #349 weiter zum Commit `df3b6930695abe6ddffc1388c941aaba6122be5e` mit einer bereits realen animierbaren Arbeitsphasen-/Modul-/Laufzeitanzeige. PR #350 ist bewusst noch nicht auf diesen neueren Commit synchronisiert: Erst bei stabilem Integrationsstand kontrolliert aktualisieren; nicht parallel laufende Änderungen überschreiben.

## Bereits in GitHub gesichert

- `UPSTREAM-PROVENANCE.json`: 8 Versions-Pins mit Original-Lizenz und Git-Blob-SHA.
- `docs/evidence/quantumflow-20261102/upstream/`: 16 Quell- und Lizenzdateien aus TalkingHead, HeadAudio, Google MediaPipe, SceneView, Three-VRM, JARVIS-HUD, Lottie Android und dotLottie Android. **Nur statische Auditsnapshots, kein Produktcodeimport.**
- `QUANTUMFLOW-ARCHITEKTUR-UND-UEBERNAHMEPLAN.md`: konkrete, gestufte Umsetzung und bekannte Grenzen.
- `scripts/continuum-quantumflow-verify-upstreams.mjs`: SHA-1-Git-Blob-Herkunftsprüfung aller 16 lokalen Originaldateien, keine Netzwerkverbindung und kein Code-Import. Ausführen nach Checkout mit `node scripts/continuum-quantumflow-verify-upstreams.mjs`.

## Tatsächlich getestet

- Alle 16 Originaldateien haben im verbundenen GitHub-Repository die im Manifest gespeicherten Blob-Hashes (einzelne `fetch_file`-Abfragen).
- Der reine SHA-1-Verifier wurde mit Node.js 22 auf Syntax geprüft. In einem **separaten synthetischen temporären Testrepository**: 1/1 Originaldatei wurde akzeptiert; eine nachträgliche Inhaltsveränderung wurde korrekt verworfen. **Der Commit-verifizierte vollständige 16-Dateien-Checkout-Lauf ist noch NOT_RUN.**
- Neues Android UI/Filament/GLB/Lip-Sync/FaceLandmarker/Render/Fold7: **NOT_RUN / NOT_IMPLEMENTED**. PR #350 ist deshalb bewusst Draft.
- Alle Originalsnapshots liegen unter docs/evidence; kein externes JavaScript wird automatisch ausgeführt.

## Bevor Astra weiterarbeitet

1. Neuesten PR349-HEAD, PR338 Avatar-Lab und aktuelles TtsHologramSignal erneut lesen.
2. Den 16-Dateien-Source-Check aus dem vollständigen Checkout ausführen und echte Lizenz-/GLB-Asset-Verträge nachprüfen.
3. Ohne zweiten Launcher zuerst isoliertes Quantumflow-Avatar-Lab mit eigener GLB-Datei, tatsächlicher Audio-Synchronisation und klarer Kamera-Opt-in-Logik erproben.
4. Die echten UI-Arbeitsereignisse aus PR349 wiederverwenden. Kein erfundener Fortschritt, keine fingierten Terminalbefehle, keine angeblichen internen KI-Gedanken.
5. Android-Gradle, LiteRT, Lint, Render und Fold7 getrennt überprüfen. Kein Merge in main und kein Release ohne Besitzerfreigabe.
