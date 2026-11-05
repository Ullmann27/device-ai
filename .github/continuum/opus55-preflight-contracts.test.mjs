import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const src = (name) => fs.readFileSync(name, 'utf8');
const root = 'app/src/main/java/at/heinz/deviceai/';
const activity = src(root + 'AgentActivity.kt');
const presentation = src(root + 'cognitive/CockpitConversationPresentation.kt');
const latency = src(root + 'cognitive/VoiceTurnLatencyTrace.kt');
const frustum = src(root + 'avatar3d/OrthographicCockpitProbe.kt');
const scene = src(root + 'cockpit/VerifiedHologramWorkGate.kt');
const audio = src(root + 'cognitive/ApprovedSpeechChunkQueue.kt');
const budget = src(root + 'cognitive/LocalChatPromptBudget.kt');
const hologram = src(root + 'ContinuumHologramView.kt');
const filament = src(root + 'avatar3d/FilamentAvatarRenderer.kt');

test('home-only voice does not let status or the keyboard steal the cockpit', () => {
  assert.match(activity, /CockpitConversationPresentation\.showChat\(/);
  assert.match(activity, /keepCockpitDuringHandsFree = continuousConversation\.active/);
  assert.match(activity, /private fun showActionResultWithoutForcingChat\(/);
  assert.equal((activity.match(/showActionResultWithoutForcingChat\(\)/g) || []).length, 3);
  assert.match(presentation, /pinHomeCockpit && !explicitChatSelected/);
  assert.match(activity, /recordCockpitDialog\(false, reply\)/);
});

test('latency is measured without claiming inaudible TTS callbacks are audio', () => {
  assert.match(latency, /TTS_STARTED_CALLBACK/);
  assert.match(latency, /FIRST_APPROVED_SPEECH_CHUNK/);
  assert.match(latency, /System::nanoTime/);
  assert.doesNotMatch(latency, /FIRST_AUDIBLE_FRAME/);
});

test('frustum probe copies current orthographic projection, not a guessed FOV', () => {
  assert.match(filament, /Camera\.Projection\.ORTHO/);
  assert.match(filament, /2\.0 \* horizontal \* \(1\.0 - anchorX\)/);
  assert.match(frustum, /2\.0 \* horizontal \* \(1\.0 - anchorX\)/);
  assert.match(frustum, /projectedMinX/);
  assert.doesNotMatch(frustum, /FieldOfView|Camera\.Projection\.FOV/);
});

test('animation success requires an actual supplied provider receipt', () => {
  assert.match(scene, /event\.providerReceiptId\.isBlank\(\)/);
  assert.match(scene, /event\.toolStartEvidenceId\.isBlank\(\)/);
  assert.match(scene, /phase = Phase\.COMPLETED/);
  assert.match(audio, /approvalEvidenceId\.isBlank\(\)/);
  assert.match(audio, /LAST_CHUNK_FINISHED/);
});

test('existing source pitfalls are documented, not silently patched', () => {
  assert.match(budget, /task\.length !in 1\.\.240/);
  assert.match(hologram, /release3d\(clearAsset = false\)/);
  assert.match(activity, /removeView\(head\)/);
  assert.match(filament, /rotate\("Head", mapper\.headPitch, mapper\.headYaw\)/);
});
