import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const read = path => readFileSync(resolve(root, path), 'utf8');
const src = 'app/src/main/java/at/heinz/deviceai/learning/';
const research = read(src + 'ContinuumPublicStudyScheduler.kt');
const offline = read(src + 'ContinuumAutonomousScheduler.kt');
const budget = read(src + 'ContinuumOfflineLearningBudget.kt');
const practice = read(src + 'NeuralPracticeScheduler.kt');
const widget = read('app/src/main/java/at/heinz/deviceai/ContinuumTrainingWidget.kt');
const activity = read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');

test('public research is internet-enabled via cellular with no charger or system-low-battery constraint', () => {
  assert.match(research, /\.setRequiredNetworkType\(JobInfo\.NETWORK_TYPE_ANY\)/);
  assert.match(research, /\.setRequiresCharging\(false\)/);
  assert.match(research, /\.setRequiresBatteryNotLow\(false\)/);
  assert.match(research, /!pending\.isRequireCharging/);
  assert.match(research, /!pending\.isRequireBatteryNotLow/);
  assert.match(research, /percent<ContinuumOfflineLearningBudget\.MIN_BATTERY_PERCENT/);
});

test('unplugged research deferral does not falsify attempts or source counts', () => {
  assert.match(research, /percent<0L -> "DEFERRED_BATTERY_UNKNOWN"/);
  assert.match(research, /"DEFERRED_BATTERY_LOW"/);
  assert.match(research, /scheduler\.recordBatteryDeferral\(deferral\)/);
  assert.match(research, /ContinuumTrainingMonitor\.end\(this,ContinuumTrainingMonitor\.PUBLIC,/);
  assert.match(research, /"DEFERRED",deferral/);
  assert.match(research, /if\(deferral!=null\) \{[\s\S]*?return false/);
  assert.match(research, /val edit=prefs\.edit\(\)[\s\S]*?"attempts"/);
});

test('SWB1 offline training uses exact 20% locally enforced threshold', () => {
  assert.match(budget, /MIN_BATTERY_PERCENT = 20/);
  assert.match(budget, /batteryPercent == null -> "BATTERY_UNKNOWN"/);
  assert.match(budget, /batteryPercent < MIN_BATTERY_PERCENT/);
  assert.match(offline, /requiresCharging=false,networkType=JobInfo\.NETWORK_TYPE_NONE,persisted=true,/);
  assert.match(offline, /requiresBatteryNotLow=false/);
  assert.match(offline, /ContinuumOfflineLearningBudget\.deferralReason\(currentResources\(\)\)/);
});

test('explicitly opted-in neural practice migrates away from the legacy charging gate', () => {
  assert.match(practice, /\.setRequiresCharging\(false\)/);
  assert.match(practice, /\.setRequiresBatteryNotLow\(false\)/);
  assert.match(practice, /if \(!enabled\) return/);
  assert.match(practice, /existing\.isRequireCharging/);
  assert.match(practice, /ContinuumOfflineLearningBudget\.MIN_BATTERY_PERCENT/);
  assert.match(activity, /charge >= 20/);
});

test('widget migrates saved charging-only jobs without replacing identical running jobs repeatedly', () => {
  assert.match(widget, /needsMigration = recovery\.getInt\("schedule_policy_revision", 0\) != 2/);
  assert.match(widget, /if \(research\.enabled\) research\.restore\(\)/);
  assert.match(widget, /ContinuumAutonomousScheduler\(app\)\.restore\(\)/);
  assert.match(research, /if\(pending!=null &&/);
  assert.match(offline, /jobs\.pending\(\)==WANTED \|\| jobs\.schedule\(WANTED\)/);
});

test('all public research permissions remain restricted and no source commands become executable', () => {
  assert.match(research, /url\.protocol=="https" && url\.host=="de\.wikipedia\.org"/);
  assert.match(research, /source\.subject/);
  assert.match(research, /MentorSourceValidation\.checkSource\(result\)/);
  assert.match(research, /MAX_ROUND_WALL_MS/);
  assert.doesNotMatch(research, /startForegroundService\(/);
});
