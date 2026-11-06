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
const menu = read("app/src/main/java/at/heinz/deviceai/ui/JarvisMenu.kt");
const tiles = read("app/src/main/java/at/heinz/deviceai/ui/JarvisTiles.kt");
const shellContent = read("app/src/main/java/at/heinz/deviceai/ui/JarvisShellContent.kt");
const sections = new Map([
  ["navigateWorkArea", "private fun navigateWorkArea("],
  ["showDashboardCategory", "private fun showDashboardCategory("],
  ["navigateDashboardTarget", "private fun navigateDashboardTarget("],
  ["navigateDashboardTargetNow", "private fun navigateDashboardTargetNow("],
  ["openHomeSlot", "private fun openHomeSlot("],
  ["showCenter", "private fun showCenter("],
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
function dashboardTargets() {
  has(section("navigateDashboardTarget"),
    /touchedCockpitTarget = target\s+try \{\s*navigateDashboardTargetNow\(target\)\s*\} finally \{\s*touchedCockpitTarget = null\s*\}/,
    "Scene adapter must delegate and clear the touched target");
  return section("navigateDashboardTargetNow");
}

test("all five bottom destinations open their own category", () => {
  const target = section("navigateWorkArea");
  for (const [category, action] of [
    ["START", "showHomeDashboard"],
    ["CHAT", "focusChatComposer"],
    ["MEHR", "showMoreWorkAreas"]
  ]) {
    has(nav, new RegExp("ContinuumWorkArea\\." + category), "Missing destination " + category);
    has(target, new RegExp("ContinuumWorkArea\\." + category + "\\s*->\\s*" + action + "\\(\\)"),
      "Wrong destination " + category);
  }
  has(nav, /ContinuumWorkArea\.LERNEN/);
  has(nav, /ContinuumWorkArea\.ENTWICKELN/);
  has(target, /ContinuumWorkArea\.LERNEN\s*->\s*showDashboardCategory\("KI-System", ContinuumWorkAreaNavigationSpec\.systemEntries\)/);
  has(target, /ContinuumWorkArea\.ENTWICKELN\s*->\s*showDashboardCategory\("Werkzeuge", ContinuumWorkAreaNavigationSpec\.toolEntries\)/);
  const category = section("showDashboardCategory");
  has(category, /setItems\(entries\.map \{ it\.title \}\.toTypedArray\(\)\)/);
  has(category, /navigateDashboardTarget\(entries\[index\]\.target\)/);
  has(nav, /CategoryEntry\("Lernen", "learning"\)/);
  has(nav, /CategoryEntry\("Entwicklerwerkzeuge", "development"\)/);
  has(dashboardTargets(), /else -> menuNavigator\.navigate\(target\)/);
  has(shellContent, /"learning" to ContinuumHomeLayout\.Slot\.LABEL_LEARNING/);
  has(menu, /"development" to Slot\.LABEL_DEVELOPING/);
  has(section("openHomeSlot"), /Slot\.LEARNING -> showLearningCenter\(\)/);
  has(section("openHomeSlot"), /Slot\.LABEL_DEVELOPING -> showDevelopmentCenter\(\)/);
  has(activity, /setOnClickListener\s*\{\s*navigateWorkArea\(destination\)\s*\}/,
    "Bottom navigation lacks a live click target");
});

test("Fold rail has actual click targets for every visible category", () => {
  // The latest owner reference shares eleven stable destinations between painting and native controls.
  const expected = [
    ["home", "BRAND"], ["chat", "LABEL_THINKING"], ["memory", "ACTION_MEMORY"], ["logic", "MODEL"],
    ["research", "LABEL_ANALYSING"], ["agents", "ACTION_AGENTS"], ["tasks", "ACTION_TASK"],
    ["web", "LABEL_CONNECTED"], ["learning", "LABEL_LEARNING"], ["device", "ACTION_DEVICE"],
    ["system", "SYSTEM_STATUS"]
  ];
  const entries = tiles.split("val railNav = listOf(")[1]?.split("val bottomNav")[0];
  assert.ok(entries, "Shared rail destinations are missing");
  assert.deepEqual([...entries.matchAll(/NavItem\("([^"]+)"/g)].map(m => m[1]), expected.map(e => e[0]));
  for (const [target, slot] of expected) {
    has(shellContent, new RegExp('"' + target + '" to ContinuumHomeLayout\\.Slot\\.' + slot + "\\b"),
      "Wrong canonical rail route " + target);
  }
  has(menu, /val railEntries: List<Entry> = JarvisTiles\.railNav\.map/);
  has(menu, /val slot = routes\[target\] \?: return/);
  has(menu, /if \(openTarget\?\.invoke\(target\) != true\) open\(slot\)/);
  has(activity, /JarvisMenuNavigator\(cockpitTriggers, \{ slot -> openHomeSlot\(slot, 0f, 0f\) \}/);
  const start = activity.indexOf("private fun buildRail(): View");
  const end = activity.indexOf("private fun cockpitActionFor(", start);
  assert.ok(start >= 0 && end > start);
  const rail = activity.slice(start, end);
  has(rail, /val entries = at\.heinz\.deviceai\.ui\.JarvisMenu\.railEntries/);
  has(rail, /for \(entry in entries\)/);
  has(rail, /contentDescription = entry\.label/);
  has(rail, /setOnClickListener \{ menuNavigator\.navigate\(entry\.target\) \}/,
    "Native rail targets cannot dispatch");
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
  const center = section("showCenter");
  has(center, /fun makeAction\(action: CenterAction\): View =\s*cockpit\.action\(this, action\.label, primary = action\.accent,\s*critical = action\.label\.contains\("LÖSCHEN", ignoreCase = true\)\) \{\s*dismissCenter\(true\)\s+action\.run\(\)\s*\}/,
    "Center button must close its presentation before dispatching its action");
  has(center, /cockpit\.grid\(this, box, actions\.map\(::makeAction\)/,
    "Center actions must use the live button callback");
  has(center, /cockpit\.grid\(this, box, experimental\.map\(::makeAction\)/,
    "Experimental actions must use the same callback");
  has(center, /val workWindow = if \(home != null && scene != null\)\s*workWindowHost\.show\(home, box, title, scene\)/,
    "Work window must present the actual center content");
  has(center, /if \(workWindow != null\) \{\s*dismissCenter = \{ immediate -> workWindow\.close\(immediate\) \}/,
    "Work-window actions must close their own window");
  has(center, /val dialog = cockpit\.show\(this, box\)\s*dismissCenter = \{ _ -> dialog\.dismiss\(\) \}/,
    "Dialog fallback actions must dismiss their own dialog");
});

test("signed owner package uses its own builder Activity, not a missing external preview", () => {
  has(route, /const val MENTOR_PREVIEW_PACKAGE = "at\.heinz\.deviceai\.mentorpaired"/);
  has(route, /currentApplicationId == MENTOR_PREVIEW_PACKAGE/);
  has(route, /externalPackage = !internal/);
  has(manifest, /android:name="\.ContinuumMobileBuilderActivity"[\s\S]*?android:enabled="\$\{mobileBuilderEnabled\}"/);
  has(builder, /ContinuumCcc1Bridge\.Operation\.entries/);
  has(builder, /ContinuumMobileBuildPlan\.Operation\.entries/);
});
