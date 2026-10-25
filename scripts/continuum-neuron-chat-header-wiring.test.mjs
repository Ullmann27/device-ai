import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const activity = readFileSync(new URL(
  "../app/src/main/java/at/heinz/deviceai/AgentActivity.kt", import.meta.url
), "utf8");

test("neuronal auto-training status never expands over the chat transcript", () => {
  const start = activity.indexOf('neuralMonitor = textView("NEURON · Bereit');
  const end = activity.indexOf('column.addView(neuralMonitor, matchWidth())', start);
  assert.ok(start >= 0 && end > start);
  const section = activity.slice(start, end);
  assert.match(section, /maxLines = 1/);
  assert.match(section, /ellipsize = android\.text\.TextUtils\.TruncateAt\.END/);
  assert.match(section, /setOnClickListener/);
  assert.match(section, /setMessage\(neuralMonitor\.text\)/);
});
test("full learning status is preserved, only collapsed visually", () => {
  assert.match(activity, /practice\.lastStatus\.take\(110\)/);
  assert.match(activity, /refreshNeuralPracticeUi\(\)/);
  const matches = activity.match(/neuralMonitor\.text = if \(practice\.enabled\)/g) ?? [];
  assert.equal(matches.length, 1);
  assert.match(activity, /column\.addView\(chatScroll, LinearLayout\.LayoutParams\(LinearLayout\.LayoutParams\.MATCH_PARENT, 0, 1f\)\)/);
});
