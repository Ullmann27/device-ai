import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const activity = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt', import.meta.url), 'utf8');
const spec = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/ContinuumNexusHomeSpec.kt', import.meta.url), 'utf8');
const hologram = fs.readFileSync(new URL('../../app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt', import.meta.url), 'utf8');

function section(start, end) {
  const first = activity.indexOf(start), last = activity.indexOf(end, first + start.length);
  assert.ok(first >= 0 && last > first, 'Missing Android source method boundary');
  return activity.slice(first, last);
}
const home = section('private fun buildBootstrapCoreCard(): View', 'private fun refreshConversationLayout()');

test('Nexus front page advertises five distinct beginner destinations instead of developer jargon', () => {
  for (const title of [
    'Mit meiner KI sprechen',
    'App erstellen',
    'Meine Dateien & Wissen',
    'KI personalisieren',
    'Meine Aufgaben'
  ]) assert.ok(spec.includes('"'+title+'"'), title);
  assert.match(home, /ContinuumNexusHomeSpec\.essentials\.chunked\(nexusColumns\)/);
  assert.match(home, /CONTINUUM NEXUS/);
});

test('beginner destinations use real existing Android screens, not placeholder success actions', () => {
  assert.match(home, /setOnClickListener \{ startVoiceInput\(\) \}/);
  for (const dest of [
    'CREATE_APP -> showDevelopmentDialog()',
    'KNOWLEDGE -> showMemoryCenter()',
    'PERSONALIZE -> showNexusPersonalization()',
    'TASKS -> showDevelopmentBacklog()'
  ]) assert.ok(home.includes(dest), 'Missing action '+dest);
  assert.doesNotMatch(home, /TRIPLE_PHASE_VERIFIED|APK_INSTALLED|632 Tests bestanden/);
});

test('advanced routes remain available but collapsed until the user requests them', () => {
  assert.match(home, /val moreFunctions = LinearLayout\(this\)\.apply \{/);
  assert.match(home, /visibility = View.GONE/);
  assert.match(home, /Weitere KI-Funktionen anzeigen/);
  for (const target of ['showResearchModeDialog()', 'showLearningCenter()', 
    'showModelCenter()', 'showDevelopmentCenter()', 'showMemoryCenter()',
    'showSystemCenter()']) assert.ok(home.includes(target), 'Expert action lost: '+target);
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
  assert.match(home, /resources\.configuration\.fontScale/);
  assert.match(spec, /usableWidthDp >= 320/);
  assert.match(spec, /fontScale\.isFinite\(\)/);
  assert.match(spec, /fontScale <= 1\.25f/);
  assert.match(home, /LinearLayout\.LayoutParams\.WRAP_CONTENT, 1f/);
});

test('personalisation is a real choice and cannot silently train models or install apps', () => {
  const choice=section('private fun showNexusPersonalization()','private fun violetTint(): Int');
  assert.match(choice, /Gemerkte Fakten ansehen oder löschen/);
  assert.match(choice, /Lernzentrum öffnen/);
  assert.match(choice, /Lokales KI-Modell verwalten/);
  assert.match(choice, /nicht automatisch trainiert/);
  assert.doesNotMatch(choice, /trainSgdStep|installPackage|launchApkInstall|exec\(/);
});
