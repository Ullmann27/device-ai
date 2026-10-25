import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const activity = readFileSync(new URL("../app/src/main/java/at/heinz/deviceai/AgentActivity.kt", import.meta.url), "utf8");
const dialogue = readFileSync(new URL("../app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryDialogue.kt", import.meta.url), "utf8");
const manifest = readFileSync(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");

test("finished experiment review is reachable in primary learning center", () => {
  assert.match(activity, /CenterAction\("EXPERIMENTE · ANSCHLÜSSE UND SKILLS PRÜFEN", accent = true\)/);
  assert.match(activity, /private fun showExperimentIntegrationReview\(\)/);
  assert.match(activity, /ExperienceCompiler\(\)\.compile\(experiences\)/);
  assert.match(activity, /val experiences=experienceStore\.all\(\)/);
  assert.match(activity, /Keine autonome Skill-Freigabe/);
});
test("CTX3 test is discoverable and stays confined to existing nonexported lab", () => {
  assert.match(activity, /CenterAction\("CTX3 · KONTEXT-SHADOW DIREKT TESTEN"\)/);
  assert.match(manifest, /<activity android:name="\.ContinuumM5LabActivity" android:exported="false"/);
  assert.match(activity, /ContinuumM5LabActivity::class\.java/);
});
test("Orion personal memory is not mistaken for an entity definition", () => {
  assert.match(dialogue, /val answerableHits=hits\.filter/);
  assert.match(dialogue, /ownerMemoryAnswersDefinition\(text,it\.text\)/);
  assert.match(dialogue, /normalize\(query\.groupValues\[1\]\)==normalize\(claim\.groupValues\[1\]\)/);
});
