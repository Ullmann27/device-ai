import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const activity = fs.readFileSync(
  new URL("../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt", import.meta.url), "utf8"
);
const view = fs.readFileSync(
  new URL("../../app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt", import.meta.url), "utf8"
);
const pcm = fs.readFileSync(
  new URL("../../app/src/main/java/at/heinz/deviceai/ContinuumPcmLevel.kt", import.meta.url), "utf8"
);

test("real Android activity drives all six hologram states", () => {
  for (const name of ["READY", "LISTENING", "THINKING", "SPEAKING", "RESEARCHING", "LEARNING"]) {
    assert.match(view, new RegExp("ContinuumVisualState\\." + name));
    assert.match(activity, new RegExp("ContinuumVisualState\\." + name));
  }
  assert.match(activity, /showHologramState\(ContinuumVisualState\.RESEARCHING\)/);
  assert.match(activity, /showHologramState\(ContinuumVisualState\.LEARNING\)/);
  assert.match(activity, /showHologramState\(ContinuumVisualState\.LISTENING\)/);
});

test("TTS derives amplitude exclusively from delivered actual PCM", () => {
  assert.match(activity, /override fun onAudioAvailable\(utteranceId: String\?, audio: ByteArray\?\)/);
  assert.match(activity, /ContinuumPcmLevel\.rms16Le\(audio\)/);
  assert.match(activity, /hologramView\?\.updatePcmLevel\(amplitude\)/);
  assert.match(view, /if \(hasPcmAudio\) 2f \+ 27f \* speechLevel else 2f/);
  assert.match(pcm, /sampleCount = min\(bytes\.size \/ 2, 4096\)/);
  assert.match(activity, /utteranceId == activeSpeechUtterance/);
  assert.match(activity, /finishSpokenUtterance/);
});

test("art is rendered once; animations stop with detached view or reduced motion", () => {
  assert.match(view, /private var staticArtwork: Bitmap\?/);
  assert.match(view, /Bitmap\.createBitmap\(340, 234, Bitmap\.Config\.ARGB_8888\)/);
  assert.match(view, /ValueAnimator\.areAnimatorsEnabled\(\)/);
  assert.match(view, /override fun onDetachedFromWindow\(\)/);
  assert.match(view, /removeCallbacks\(tick\)/);
  assert.match(view, /staticArtwork\?\.recycle\(\)/);
  assert.match(view, /importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO/);
  assert.match(activity, /hologramStatus\?\.apply/);
});

test("primary composer actions meet Android 48dp minimum hit target", () => {
  assert.match(activity, /inputRow\.addView\(newChat, LinearLayout\.LayoutParams\(dp\(48\), dp\(48\)\)\)/);
  assert.match(activity, /inputRow\.addView\(mic, LinearLayout\.LayoutParams\(dp\(48\), dp\(48\)\)/);
  assert.match(activity, /minimumHeight = dp\(48\)/);
});

test("model and security pipelines remain in their existing files", () => {
  assert.doesNotMatch(view, /ToolGate\(|model\.train\(|importSnapshot\(/);
  assert.doesNotMatch(pcm, /NETWORK|WebView|HttpClient/);
});
