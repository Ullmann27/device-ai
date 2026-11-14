# ChatGPT → Blender: Continuum / LUMO 3D-Auftragskanal

**Status:** Prototyp, Stand 2026-11-14. Branch: `chatgpt/blender-bridge-20261114`. Keine Änderungen an `main`.

## Ablauf
1. Die installierbare Blender-Erweiterung **Continuum KI Blender Bridge v0.2.0** lädt ausschließlich `blender_bridge/jobs/current.json` von diesem Branch.
2. Ein mit GitHub verbundenes ChatGPT kann den JSON-Auftrag im laufenden Chat aktualisieren. Keine Blender-GUI-Fernsteuerung und keine OpenAI-Chat-API sind nötig.
3. In der Blender-Seitenleiste auf **„ChatGPT-Auftrag laden“** tippen oder nach ausdrücklicher Aktivierung **„Alle 30s neue Auftraege pruefen“** nutzen.
4. Blender erstellt die beschriebene Szene lokal; GLB wird nur nach Betätigung der Export-Schaltfläche gespeichert.

Die JSON-Datei ist öffentlich. **Keine API-Schlüssel, privaten Informationen oder vertraulichen Projektangaben in Aufträge eintragen.**

## Format
```json
{
  "version": 1,
  "job_id": "continuum-20261114-02",
  "project": "Continuum",
  "title": "Beispiel",
  "objects": [
    {
      "kind": "monitor",
      "name": "Hauptmonitor",
      "location": [0, 0, 0],
      "rotation": [0, 0, 0],
      "size": [1, 1, 1],
      "color": "#40D6FF",
      "metallic": 0.8,
      "roughness": 0.2,
      "emission": 0.0,
      "bevel": 0.02,
      "spin": false
    }
  ],
  "lights": [{"location": [2, -4, 8], "color": "#FFFFFF", "power": 1500}]
}
```

**Unterstützte Objekttypen:** `cube`, `sphere`, `ico`, `cylinder`, `cone`, `torus`, `plane`, `text`, `monitor`, `energy_orb`, `tree`, `building`, `road`.

**Grenzen:** max. 40 Einträge, max. 12 Lichter, max. 100 KB JSON, feste Wertebereiche. Nur Daten; **kein `exec`**, keine Skripts, keine Downloads aus dem Auftrag. Ein wiederholter `job_id` wird innerhalb derselben Blender-Szene nicht erneut gebaut.

**Wichtig für ChatGPT:** Bei jedem neuen Auftrag die `job_id` ändern, `current.json` auf diesem Branch mit GitHub-Connector aktualisieren und den Commit prüfen. Nur nach ausdrücklicher Anweisung die Repo-Integration oder den Hauptzweig verändern.

OpenRouter bleibt als **optionaler** Notfallweg in der Erweiterung. Ein Test der Python-Syntax und der Protokollprüfungen ist erfolgt; ein Test im Android-Blender muss auf dem Gerät stattfinden.
