# Continuum / ChatGPT -> Blender (Android)

The branch `chatgpt-blender-control` is a data-only dispatch channel for the Continuum Blender Bridge 0.2 add-on. It is intentionally kept separate from `main`.

- `latest_scene.json` contains a `job_id`, `title`, `objects`, and `lights`.
- A newer unique `job_id` tells the Blender add-on to build a scene in the running Blender application.
- The Blender add-on reads the public raw GitHub file via HTTPS. It never executes arbitrary Python received over the network.
- The user may press **ChatGPT-Auftrag aus GitHub laden**, or explicitly enable **Auto-Abruf alle 45 Sekunden** in Blender.
- No OpenRouter key is needed for this pathway. The prior OpenRouter workflow can stay as an option.
- No .blend file, project APK, or original assets are automatically overwritten. The created scene can be exported as .glb and incorporated separately after validation.
- Do not put API keys, passwords or private content in this public repository file.

This is not unattended remote GUI control. Blender must be running and the add-on enabled; automatic polling runs only inside the Blender app.
