# Continuum Preview: sicherer Chrome-Download auf demselben Samsung

Stand 20.10.2026. Ausschließlich Device AI Continuum, Experimentbranch und Draft-PR #42.

Der lokale Builder speichert nach erfolgreichem APK-Build, Signaturprüfung und
SHA-256-Abgleich ein geschütztes Original im Termux-State unter
`~/.local/state/device-ai-continuum/mobile-builder`. Der
Chrome-Downloadhelfer gibt **nur genau dieses Artefakt** über
`127.0.0.1:8765` frei; er erzeugt keine neue APK, lädt keine Dateien
ins Internet, startet keine Installation und gibt das Arbeitsverzeichnis nicht frei.

## Einmal starten

Nur in einem bekannten, sauberen Checkout des Experimentbranches:

```sh
cd "$HOME/device-ai"
test "$(git branch --show-current)" = "astra/continuum-experiment-charter-20261019" &&
test -z "$(git status --porcelain)" &&
GIT_SSH_COMMAND="ssh -i $HOME/.ssh/continuum_device_ai_readonly -o IdentitiesOnly=yes" git pull --ff-only &&
node --test scripts/continuum-preview-chrome-download.test.mjs &&
node scripts/continuum-preview-chrome-download.mjs
```

Termux offen lassen. Auf **demselben Smartphone** in Google Chrome
`http://127.0.0.1:8765/` eingeben und **„APK in Chrome herunterladen“**
wählen. Der Android-Installationsdialog bleibt ausdrücklich eine eigene
Nutzerentscheidung. Der lokale Downloadserver beendet sich nach 20 Minuten
oder mit Ctrl+C. Andere Geräte können ihn nicht erreichen.

## Abnahmekriterien

- Bei `NO_BUILD_REPORT`, fehlenden privaten Dateien, manipuliertem SHA-256,
  falschem Status, falscher Dateigröße, Symlink oder nicht bestätigter
  Signaturmeldung startet bzw. erfolgt kein APK-Download.
- HTTP bedient ausschließlich `/` und eine feste APK-Download-URL.
  Anfragen über fremde Hostnamen werden verweigert.
- `node --test scripts/continuum-preview-chrome-download.test.mjs` prüft
  diese Grenzen und einen vollständigen Download mit mehr als sechs Megabyte.
- Ein erfolgreich heruntergeladenes Paket ist noch **kein erfolgreich
  installiertes oder am Gerät getestetes Paket**.

Die Preview ist nicht mit der älteren SOL-6.1-GitHub-Release-APK zu verwechseln.
Letztere enthält nicht den aktuellen Nexus-/ANSK-Experimentstand.
