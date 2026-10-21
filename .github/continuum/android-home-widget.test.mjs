import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const read = path => readFileSync(resolve(root, path), 'utf8');
const pkg = 'app/src/main/';
const widget = read(pkg + 'java/at/heinz/deviceai/ContinuumTrainingWidget.kt');
const store = read(pkg + 'java/at/heinz/deviceai/learning/ContinuumTrainingMonitor.kt');
const manifest = read(pkg + 'AndroidManifest.xml');
const xml = read(pkg + 'res/layout/continuum_training_widget.xml');
const info = read(pkg + 'res/xml/continuum_training_widget_info.xml');
const monitor = read(pkg + 'java/at/heinz/deviceai/ContinuumTrainingMonitorActivity.kt');
const gradle = read('app/build.gradle.kts');
const render = read('scripts/render-mentor-preview.sh');

test('widget is a genuine native Android launcher widget, not an Activity demo', () => {
  assert.match(manifest, /android:name="\.ContinuumTrainingWidget"\s+android:exported="false"/);
  assert.match(manifest, /android\.appwidget\.action\.APPWIDGET_UPDATE/);
  assert.match(manifest, /@xml\/continuum_training_widget_info/);
  assert.match(info, /android:widgetCategory="home_screen"/);
  assert.match(info, /android:resizeMode="horizontal\|vertical"/);
  for (const id of ['widget_state', 'widget_subject', 'widget_phase', 'widget_public',
    'widget_offline', 'widget_cdx', 'widget_counts', 'widget_footer', 'widget_refresh']) {
    assert.match(xml, new RegExp('android:id="@\\+id/' + id + '"'));
  }
});

test('only real telemetry events and registered Android jobs drive widget statuses', () => {
  assert.match(widget, /ContinuumTrainingMonitor\.read\(app, ContinuumTrainingMonitor\.PUBLIC\)/);
  assert.match(widget, /ContinuumTrainingMonitor\.read\(app, ContinuumTrainingMonitor\.OFFLINE\)/);
  assert.match(widget, /ContinuumTrainingMonitor\.read\(app, ContinuumTrainingMonitor\.CDX\)/);
  assert.match(widget, /jobs\.getPendingJob\(ContinuumPublicStudyScheduler\.JOB_ID\)/);
  assert.match(widget, /jobs\.getPendingJob\(ContinuumAutonomousScheduler\.JOB_ID\)/);
  assert.match(widget, /training != null -> "TRAINIERT GERADE"/);
  assert.match(widget, /working != null -> "FORSCHT \/ PRÜFT"/);
  assert.match(widget, /snapshot\.stale -> "UNKLAR"/);
  assert.match(widget, /stats\.getLong\("accepted", 0L\)/);
  assert.match(widget, /stats\.getLong\("sources", 0L\)/);
  assert.match(widget, /stats\.getLong\("attempts", 0L\)/);
  assert.match(store, /ContinuumTrainingWidget\.requestUpdate\(context\)/);
});

test('notification requires user permission and follows real FAILED result only', () => {
  assert.match(manifest, /android\.permission\.POST_NOTIFICATIONS/);
  assert.match(monitor, /requestPermissions\(arrayOf\(android\.Manifest\.permission\.POST_NOTIFICATIONS\)/);
  assert.match(widget, /Manifest\.permission\.POST_NOTIFICATIONS/);
  assert.match(store, /if \(outcome == "FAILED"\)/);
  assert.match(widget, /ContinuumTrainingMonitorActivity::class\.java/);
  assert.doesNotMatch(widget, /setExact\(|setRepeating\(|startForegroundService\(/);
});

test('safe signing and increased signed preview version are unchanged except monotonic revision', () => {
  assert.match(gradle, /versionCode = if \(deviceAiMentorPreview\) 10 else if \(deviceAiPreview\) 6 else 4/);
  assert.match(gradle, /versionName = if \(deviceAiMentorPreview\) "0\.3\.7"/);
  assert.match(render, /v0\.3\.7\.apk/);
  assert.match(render, /expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"/);
});
