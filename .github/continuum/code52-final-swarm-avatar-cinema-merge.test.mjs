import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (file) => readFileSync(file, 'utf8');
const app = 'app/src/main/java/at/heinz/deviceai/';
const ui = app + 'ui/';
const avatar = app + 'avatar/';
const avatar3d = app + 'avatar3d/';

test('original avatar bridge survives cinema branch integration', () => {
  const activity = read(app + 'AgentActivity.kt');
  const bridge = read(avatar + 'CinemaWindowGazeBridge.kt');
  const director = read(avatar + 'AvatarDirector.kt');
  const presenter = read(ui + 'CockpitAnimationPresenter.kt');
  const shell = read(ui + 'JarvisShellView.kt');
  assert.match(activity, /cockpitBus\.subscribe\(at\.heinz\.deviceai\.avatar\.CinemaWindowGazeBridge\(avatarDirector\)\)/);
  assert.match(activity, /android\.os\.SystemClock::elapsedRealtime/);
  assert.match(bridge, /is WindowProjectedMotion -> onMotion\(event\)/);
  assert.match(bridge, /director\.windowMoved\(event\.windowId, target\)/);
  assert.match(bridge, /CinemaWindowLightState\.reset\(\)/);
  assert.match(director, /fun windowMoved\(windowId: String, target: GazeTarget\)/);
  assert.match(presenter, /onProjectedMotion = \{ bus\.emit\(it\) \}/);
  assert.match(shell, /projectedMotion\.consider\(/);
  assert.match(shell, /onProjectedMotion\?\.invoke\(it\)/);
});

test('real PCM anticipation and existing Filament light remain wired', () => {
  const speech = read(app + 'TtsHologramSignal.kt');
  const light = read(avatar3d + 'SpatialWindowLight.kt');
  const lightState = read(avatar3d + 'CinemaWindowLightState.kt');
  const filament = read(avatar3d + 'FilamentAvatarRenderer.kt');
  const morph = read(avatar3d + 'AvatarMorphMapper.kt');
  assert.match(speech, /const val LOOK_AHEAD_MS = 80L/);
  assert.match(speech, /readLevel\(nowMillis \+ LOOK_AHEAD_MS\)/);
  assert.match(light, /fun tint\(accentArgb: Int\?/);
  assert.match(lightState, /fun reset\(/);
  assert.match(filament, /SpatialWindowLight/);
  assert.match(morph, /splitNeck\(/);
});

test('thermal and document protections must survive visual branch merges', () => {
  const quality = read(ui + 'JarvisRenderQuality.kt');
  const orchestrator = read(ui + 'Airbus2100CockpitOrchestrator.kt');
  const document = read(ui + 'DocumentBlueprintProjection.kt');
  assert.match(quality, /if \(!cinemaOptIn \|\| !experimentEnabled \|\| !resolved\.animate\) return resolved/);
  assert.doesNotMatch(quality, /return Budget\(Tier\.HIGH, 16L, 70, 96, true\)/);
  assert.match(orchestrator, /private var documentTurnId: String\? = null/);
  assert.match(orchestrator, /if \(event\.turnId != documentTurnId \|\| !document\.hasDocument\) return/);
  assert.match(orchestrator, /is CockpitAnimationEvent\.DocumentLearned -> Unit/);
  assert.doesNotMatch(orchestrator, /document\.onLearned\(/);
  assert.match(document, /fun onLearned\([\s\S]*?event: CockpitAnimationEvent\.DocumentLearned\) = Unit/);
  assert.doesNotMatch(document, /entriesAdded = event\.entriesAdded/);
});
