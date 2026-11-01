import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const root = new URL("../", import.meta.url);
const read = (p) => readFileSync(new URL(p, root), "utf8");
const activity = read("app/src/main/java/at/heinz/deviceai/ContinuumAvatarLabActivity.kt");
const manifest = read("app/src/main/AndroidManifest.xml");
const agent = read("app/src/main/java/at/heinz/deviceai/AgentActivity.kt");
const signal = read("app/src/main/java/at/heinz/deviceai/TtsHologramSignal.kt");

test("private gallery-based avatar route exists in Systemstatus", () => {
  assert.match(agent, /AVATAR-LAB · OFFLINE-SPRECHTEST/);
  assert.match(agent, /startActivity\(Intent\(this, ContinuumAvatarLabActivity::class\.java\)\)/);
  assert.match(manifest, /android:name="\.ContinuumAvatarLabActivity"\s+android:exported="false"/);
});

test("avatar is strictly local and does not replace the branded orb", () => {
  assert.match(activity, /Intent\.ACTION_OPEN_DOCUMENT/);
  assert.match(activity, /takePersistableUriPermission/);
  assert.match(activity, /Continuum orb is still the primary brand/);
  assert.doesNotMatch(activity, /HttpURLConnection|OkHttpClient|Retrofit|WebView|upload/i);
});

test("TTS PCM feeds existing playback-anchored signal and stops with Activity lifecycle", () => {
  assert.match(activity, /TtsHologramSignal\(\)/);
  assert.match(activity, /onAudioAvailable/);
  assert.match(activity, /signal\.append\(id, audio\)/);
  assert.match(activity, /signal\.anchor\(id, frame, SystemClock\.elapsedRealtime\(\)\)/);
  assert.match(activity, /override fun onPause\(\) \{\s+stopSpeech\(\)/);
  assert.match(activity, /voice\?\.shutdown\(\)/);
  assert.match(signal, /@Synchronized fun stop\(\)/);
});

test("portrait imports have explicit downsample and safety limit", () => {
  assert.match(activity, /MAX_IMAGE_SIDE = 1600/);
  assert.match(activity, /MAX_DECODE_PIXELS = 2_560_000L/);
  assert.match(activity, /BitmapFactory\.Options\(\)\.apply \{ inJustDecodeBounds = true \}/);
  assert.match(activity, /inSampleSize = sample/);
});

test("image prototype does not misrepresent amplitude as true viseme rigging", () => {
  assert.match(activity, /NOT phoneme rigging/);
  assert.match(activity, /does not claim a 3D rig/);
});
