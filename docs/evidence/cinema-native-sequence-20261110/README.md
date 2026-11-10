# Native Android view sequence

Robolectric API28 native Canvas renders the real AgentActivity, shell, work window and existing 2D avatar fallback. Inputs deliberately invoke the existing command and local-reasoning presentation lifecycle; no model runs. The rendered app label “reale lokale Inferenz” is fixture UI text, not inference evidence.

Exact source: local9fd5d1455a7b765e32660c1be37a0ffffacaefed, remote2eca96cf50df2a4f4538f61cac911aa90563a93d, identical treea122c871d92dcb32f995f62a438077166a28634e.

38/38 targeted Gradle tests pass, no failures/errors/skips. Two complete scripted sequences cover inner884x760 and cover412x915: idle,140/280/650/1180/2100ms, return650ms, returned and interrupted-return. The same avatar view survives. One work window exists while active; none after each return. Distinct PNG hashes do not alone prove animation quality; inspect the phase images and geometry tests.

This test calls scripted lifecycle finish, not the STOP button. Native API28 does not execute the API33 hardware RuntimeShader. AGSL has separate software Skia evidence. Filament, real Fold7, live voice, inference and device timing/thermal metrics are NOT_RUN.
