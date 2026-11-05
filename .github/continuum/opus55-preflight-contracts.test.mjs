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
const framing = src(root + 'avatar3d/AvatarFraming.kt');
const morphMapper = src(root + 'avatar3d/AvatarMorphMapper.kt');

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

test('frustum probe and renderer use the same orthographic framing', () => {
  assert.match(filament, /val fitted = AvatarFraming\.window\(focus, width\.toDouble\(\) \/ height, anchorX, closeUp, keepOut\)/);
  assert.match(filament, /camera!!\.setProjection\(Camera\.Projection\.ORTHO, fitted\.left, fitted\.right,\s*-fitted\.halfHeight, fitted\.halfHeight, \.05, 10\.0\)/);
  assert.match(frustum, /val fitted = AvatarFraming\.window\(focus, widthPx\.toDouble\(\) \/ heightPx, anchorX, closeUp\)/);
  assert.match(frustum, /return Window\(widthPx, heightPx, fitted\.left, fitted\.right, -fitted\.halfHeight, fitted\.halfHeight\)/);
  assert.match(framing, /return Window\(-2\.0 \* horizontal \* anchor, 2\.0 \* horizontal \* \(1\.0 - anchor\), vertical,/);
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

test('remaining source boundaries and the complete neck/head turn stay connected', () => {
  assert.match(budget, /task\.length !in 1\.\.240/);
  assert.match(hologram, /release3d\(clearAsset = false\)/);
  assert.match(activity, /removeView\(head\)/);
  assert.match(filament, /rotate\("Neck", mapper\.neckPitch, mapper\.neckYaw\)\s*rotate\("Head", mapper\.headPitch - mapper\.neckPitch, mapper\.headYaw - mapper\.neckYaw\)/);
  assert.match(morphMapper, /const val NECK_SHARE = \.65f/);
  assert.match(morphMapper, /neckYaw = headYaw \* NECK_SHARE\s*neckPitch = headPitch \* NECK_SHARE/);
  assert.doesNotMatch(filament, /rotate\("Head", mapper\.headPitch, mapper\.headYaw\)/);
});
