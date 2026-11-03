# Living JARVIS3D — isolierter Grafikstrang

Basis: Chief `7e83281a4fc5f7bd925adb56951237a31e560cf6`, geprüfte Android-Quelle64ec06a7. Branch `codex/living-jarvis-3d-20261103`. Status REFERENCE_CREATED / MESH_NOT_YET_CREATED; keine Änderung der Android-App.

Die zwei im Besitzerchat beigefügten Kopfporträts definieren die Identität, drei beigefügte Dashboardbilder das responsive Design. Originaldateien wurden nicht als lokale Pfade erfunden. Die Bildgenerierung verwendete die beiden letzten Besitzerbilder direkt (`num_last_images_to_include=2`). Daraus wurde eine orthografisch angeforderte neutrale Frontalreferenz tatsächlich erzeugt:

- `docs/avatar-live/reference-work/jarvis-neutral-frontal-generated.png`
- SHA256 `a850c508fc5b44700024de0248b799650d160198f0e6a2039550ea266198bb78`
- Dies ist eine generierte2D-Modellierungsvorlage, keine gemeinsame3D-Geometrie, keine Symmetriemessung und keine Besitzerabnahme der Gesichtsübereinstimmung.

Blender4.3.2 ist lokal ausführbar. MeshyCLI0.8.2 wurde installiert, Status unauthenticated; kein bezahlter Meshy-Job und keine neue Anmeldung ausgeführt. Verfügbarer Higgsfield/Tripo-H3.1-Image-to-3D-Connector wurde per Modellkatalog geprüft; ein Kosten-Preflight für einen30kFace-Kandidaten ergibt10,5Credits, kein Job dadurch gestartet. Eine bestehende angebundene3D-Pipeline ist einem zusätzlichen Login vorzuziehen.

## Geplante konkrete Prüfung

Ein neutraler Mesh-Kandidat wird vor Rigging auf tatsächliche Geometrie, Proportionen, Augen/Lippen/Ohren, Rückseite, Materialien und Lizenz geprüft. Weitere30Ansichten kommen aus EINER gemeinsamen Blender-Geometrie, nicht aus separat neu interpretierten Gesichtern. Vor Android-Integration müssen15OVR-MorphTargets, getrennte Augen-/Kopf-/Kiefersteuerung, Gesichtsausdrücke, Symmetrie und echte Animation nachgewiesen sein. Ein generierter Monomesh oder humanoides Körperrig erfüllt Gesichtsmimik nicht automatisch.

Vorhandene AvatarDirector/Sink/PoseReducer/Viseme und TTS bleiben bindend. Originalkugel bleibt erhalten. Kamera nur ausdrücklich freigegeben, sichtbar und widerrufbar, keine biometrische Authentifizierung aus Landmarken. Keine Signierung/Veröffentlichung/Installation. Aktuelle Gehirn-Forschung hat Vorrang; keine konkurrierenden Gradle-Jobs.
