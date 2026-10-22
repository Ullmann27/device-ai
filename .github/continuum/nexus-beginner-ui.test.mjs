import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const activity = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt', import.meta.url), 'utf8');
const spec = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/ContinuumNexusHomeSpec.kt', import.meta.url), 'utf8');
const hologram = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt', import.meta.url), 'utf8');
const homeView = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt', import.meta.url), 'utf8');
const layout = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt', import.meta.url), 'utf8');

function section(start, end) {
  const first = activity.indexOf(start), last = activity.indexOf(end, first + start.length);
  assert.ok(first >= 0 && last > first, 'Missing Android source method boundary');
  return activity.slice(first, last);
}
// The reference start screen: its drawn slots (routed in openHomeSlot), the work-area navigation and the chat composer.
const home = section('private fun buildConversationPane(wide: Boolean): View', 'private fun refreshDashboard()') +
  section('private fun navigateWorkArea(', 'private fun refreshConversationLayout()') +
  section('private fun buildComposer(): View', 'private fun sendCurrentInput()');

test('the five beginner destinations stay reachable from the reference start screen', () => {
  for (const title of [
    'Mit meiner KI sprechen',
    'App erstellen',
    'Meine Dateien & Wissen',
    'KI personalisieren',
    'Meine Aufgaben'
  ]) assert.ok(spec.includes('"'+title+'"'), title);
  for (const route of ['startVoiceInput()', 'showDevelopmentDialog()', 'showMemoryCenter()',
    'showNexusPersonalization()', 'showDevelopmentBacklog()']) assert.ok(home.includes(route), 'Beginner destination lost: '+route);
});

test('start-screen routes use real existing Android screens, not placeholder success actions', () => {
  assert.match(home, /Slot\.PROFILE -> showNexusPersonalization\(\)/);
  assert.match(home, /showMentorSources\(\) else showMemoryCenter\(\)/);
  assert.match(home, /showDevelopmentBacklog\(\) else showDevelopmentCenter\(\)/);
  assert.doesNotMatch(home + homeView, /TRIPLE_PHASE_VERIFIED|APK_INSTALLED|632 Tests bestanden/);
});

test('advanced routes remain available from the start screen, its rail and the More sheet', () => {
  const more = section('private fun showMoreWorkAreas()', 'private fun refreshConversationLayout()');
  for (const target of ['showResearchModeDialog()', 'showLearningCenter()',
    'showModelCenter()', 'showDevelopmentCenter()', 'showMemoryCenter()',
    'showSystemCenter()']) assert.ok((home + more).includes(target), 'Expert action lost: '+target);
});

test('Nexus keeps real hologram and voice entry tied to Android speech and system state', () => {
  assert.match(home, /ContinuumHologramView\(this\)/);
  assert.match(activity, /RecognizerIntent\.ACTION_RECOGNIZE_SPEECH/);
  assert.match(activity, /voiceListening = true/);
  assert.match(activity, /refreshHologramState\(\)/);
  assert.match(hologram, /setAudioLevel/);
});

test('fold, small display and enlarged-font behaviour are expressed by a pure policy', () => {
  assert.match(home, /resources\.configuration\.screenWidthDp/);
  assert.match(homeView, /resources\.configuration\.fontScale\.coerceIn\(\.85f, 1\.3f\)/);
  assert.match(layout, /fun isWide\(widthDp: Float, heightDp: Float\)/);
  assert.match(layout, /fun phone\(widthDp: Float\): Layout/);
  assert.match(layout, /fun wide\(widthDp: Float\): Layout/);
  assert.match(spec, /usableWidthDp >= 320/);
  assert.match(spec, /fontScale\.isFinite\(\)/);
  assert.match(spec, /fontScale <= 1\.25f/);
});

test('personalisation is a real choice and cannot silently train models or install apps', () => {
  const choice=section('private fun showNexusPersonalization()','private fun violetTint(): Int');
  assert.match(choice, /Gemerkte Fakten ansehen oder löschen/);
  assert.match(choice, /Lernzentrum öffnen/);
  assert.match(choice, /Lokales KI-Modell verwalten/);
  assert.match(choice, /nicht automatisch trainiert/);
  assert.doesNotMatch(choice, /trainSgdStep|installPackage|launchApkInstall|exec\(/);
});

test('start screen and chat never overlap after leaving or returning from chat', () => {
  const refresh=section('private fun refreshConversationLayout()', 'private fun showHomeDashboard()');
  assert.match(refresh, /dashboardSection\?\.visibility = if \(chat\) View\.GONE else View\.VISIBLE/);
  assert.match(refresh, /chatColumn\?\.visibility = if \(chat\) View\.VISIBLE else View\.GONE/);
  assert.doesNotMatch(activity, /nexusExpertPanel|nexusExpertExpanded/);
});
