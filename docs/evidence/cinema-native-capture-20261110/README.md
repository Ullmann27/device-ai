# Native Canvas navigation proof

These receipts belong to source 4ac9c9b94b9fb5e391646e2e99e986db32ec7997, published with identical tree as ae3ce34b5aff285ed8c569ca2844975e34131a86. They predate the subsequent viscous-light and speech-deadline changes.

- Real AgentActivity in Robolectric API28 native Canvas; original Android view hierarchy.
- Before: duplicate legacy navigation under the Cinema home dock.
- After: Cinema owns navigation on home; actual touch to Chat restores the legacy navigation; return hides it and retains the same avatar view.
- Regression RED: expected GONE, observed VISIBLE. GREEN: 9 targeted UI tests, zero failures/skips; 17 Node navigation contracts pass.
- Full Android source compilation passed with those UI tests. Configured NewApi lint passed with zero issues.
- Avatar replay uses the unchanged original GLB in Blender CPU Cycles, with independently checked neck/head transforms. Studio lighting is not Filament lighting.
- Host scripted inputs do not establish real model inference, audio synchronization, Android GPU performance or Fold7 acceptance. All of those remain NOT_RUN.

The new CinemaNativeSequenceUiTest is a separate scripted lifecycle capture; its eventual receipts must identify their own exact source, rather than reuse this result.
