import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

// Static wiring contract only. Successful tests do not replace Android instrumentation
// or real Fold7 taps. This test prevents disappearing buttons and misrouted categories.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const read = relative => readFileSync(path.join(root, relative), "utf8");
const activity = read("app/src/main/java/at/heinz/deviceai/AgentActivity.kt");
const nav = read("app/src/main/java/at/heinz/deviceai/ContinuumWorkAreaNavigationSpec.kt");
const route = read("app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderRoute.kt");
const manifest = read("app/src/main/AndroidManifest.xml");
const builder = read("app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt");
const sections = new Map([
  ["navigateWorkArea", "private fun navigateWorkArea("],
  ["openHomeSlot", "private fun openHomeSlot("],
  ["showLearningCenter", "private fun showLearningCenter()"],
  ["showDevelopmentCenter", "private fun showDevelopmentCenter()"],
  ["showMemoryCenter", "private fun showMemoryCenter()"],
  ["showSystemCenter", "private fun showSystemCenter()"]
]);
function section(name) {
  const start = activity.indexOf(sections.get(name));
  assert.ok(start >= 0, "Missing section: " + name);
  const after = activity.indexOf("\n    private fun ", start + sections.get(name).length);
  return activity.slice(start, after < 0 ? undefined : after);
}
function has(source, expression, label) {
  assert.match(source, expression, label);
}

test("all five bottom destinations open their own category", () => {
  const target = section("navigateWorkArea");
  for (const [category, action] of [
    ["START", "showHomeDashboard"],
    ["CHAT", "focusChatComposer"],
    ["LERNEN", "showLearningCenter"],
    ["ENTWICKELN", "showDevelopmentCenter"],
    ["MEHR", "showMoreWorkAreas"]
  ]) {
    has(nav, new RegExp("ContinuumWorkArea\\." + category), "Missing destination " + category);
    has(target, new RegExp("ContinuumWorkArea\\." + category + "\\s*->\\s*" + action + "\\(\\)"),
      "Wrong destination " + category);
  }
  has(activity, /setOnClickListener\s*\{\s*navigateWorkArea\(destination\)\s*\}/,
    "Bottom navigation lacks a live click target");
});

test("Fold rail has actual click targets for every visible category", () => {
  const labels = ["Start", "Chat", "Lernen", "Entwickeln", "Analysieren", "Speicher", "System", "Mehr"];
  for (const label of labels) {
    assert.ok(activity.includes('"' + label + '" to {'), "Missing Fold rail target " + label);
  }
  has(activity, /setOnClickListener\s*\{\s*action\(\)\s*\}/, "Rail targets cannot dispatch");
});

test("dashboard cards open the matching owner-controlled centers", () => {
  const home = section("openHomeSlot");
  for (const [slot, action] of [
    ["SEARCH", "showKnowledgeArchiveSearch"],
    ["NOTIFICATIONS", "showDiagnosticsCenter"],
    ["PROFILE", "showNexusPersonalization"],
    ["LABEL_CONNECTED", "showInternetSettings"],
    ["LABEL_THINKING", "navigateWorkArea"],
    ["LABEL_LEARNING", "showLearningCenter"],
    ["LABEL_ANALYSING", "showResearchModeDialog"],
    ["LABEL_DEVELOPING", "showDevelopmentCenter"],
    ["LABEL_EVOLVING", "showContinuumLearningDashboard"],
    ["SYSTEM_STATUS", "showSystemCenter"],
    ["SYSTEM_ACTIVITY", "showDiagnosticsCenter"]
  ]) {
    assert.ok(home.includes("Slot." + slot), "No action for dashboard slot " + slot);
    assert.ok(home.includes(action + "("), "No destination method " + action);
  }
  for (const marker of ["Slot.MODEL", "Slot.LEARNING", "Slot.STORAGE", "Slot.DEVELOPMENT"]) {
    assert.ok(home.includes(marker), "Missing dashboard card: " + marker);
  }
});

test("category center buttons invoke real handlers and CCC1 has a direct development entry", () => {
  for (const name of ["showLearningCenter", "showDevelopmentCenter", "showMemoryCenter", "showSystemCenter"]) {
    const body = section(name);
    has(body, /CenterAction\(/, name + " has no actions");
    assert.doesNotMatch(body, /CenterAction\([^\n]+\{\s*\}/, name + " contains a no-op button");
  }
  const development = section("showDevelopmentCenter");
  assert.ok(development.includes('CenterAction("CCC1 · REPARATUR-AGENT (TERMUX)") { openMobileBuilder() }'),
    "Coding agent is not reachable from its category");
  has(activity, /dialog\.dismiss\(\)\s*\n\s*action\.run\(\)/,
    "Center UI does not dispatch to its action");
});

test("signed owner package uses its own builder Activity, not a missing external preview", () => {
  has(route, /const val MENTOR_PREVIEW_PACKAGE = "at\.heinz\.deviceai\.mentorpaired"/);
  has(route, /currentApplicationId == MENTOR_PREVIEW_PACKAGE/);
  has(route, /externalPackage = !internal/);
  has(manifest, /android:name="\.ContinuumMobileBuilderActivity"[\s\S]*?android:enabled="\$\{mobileBuilderEnabled\}"/);
  has(builder, /ContinuumCcc1Bridge\.Operation\.entries/);
  has(builder, /ContinuumMobileBuildPlan\.Operation\.entries/);
});
