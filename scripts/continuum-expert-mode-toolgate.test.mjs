import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const code = (p) => fs.readFileSync(path.join(root, p), 'utf8');

const interpreter = code('app/src/main/java/at/heinz/deviceai/CommandInterpreter.kt');
const activity = code('app/src/main/java/at/heinz/deviceai/MainActivity.kt');
const consent = code('app/src/main/java/at/heinz/deviceai/ExpertModeConsent.kt');
const agent = code('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const registry = code('app/src/main/java/at/heinz/deviceai/ToolRegistry.kt');
const ignore = code('.gitignore');
const unit = code('app/src/test/java/at/heinz/deviceai/ExpertModeConsentTest.kt');

test('ToolRegistry keeps screenshot and app launch on the CONFIRM path', () => {
  assert.match(registry, /ToolSpec\("take_screenshot"[\s\S]*?Risk\.CONFIRM/);
  assert.match(registry, /ToolSpec\("launch_app"[\s\S]*?Risk\.CONFIRM/);
});

test('expert-mode planner binds screenshot and launch risks to ToolRegistry', () => {
  const shot = interpreter.slice(
    interpreter.indexOf('"Screenshot speichern"'),
    interpreter.indexOf('"Screenshot speichern"') + 450
  );
  assert.match(shot, /ToolRegistry\.risk\("take_screenshot"\)/);
  const launch = interpreter.slice(
    interpreter.indexOf('"App starten: $actualStartName"'),
    interpreter.indexOf('"App starten: $actualStartName"') + 420
  );
  assert.match(launch, /ToolRegistry\.risk\("launch_app"\)/);
  assert.match(interpreter, /ToolRegistry\.risk\("shell"\)/);
  assert.match(interpreter, /ToolRegistry\.risk\("uninstall_app"\)/);
});

test('expert-mode confirmation dialogs show the exact shell command and the same strong destructive pair as AgentActivity', () => {
  assert.match(consent, /fun stepBlock\(step: CommandStep\): String = "• \$\{step\.label\}\\n\$\{step\.shellCommand\}"/);
  assert.match(activity, /ExpertModeConsent\.confirmMessage\(plan\)/);
  assert.match(activity, /ExpertModeConsent\.destructiveFirstMessage\(plan\)/);
  assert.match(activity, /ExpertModeConsent\.destructiveStrongMessage\(plan\)/);
  assert.match(activity, /ExpertModeConsent\.DESTRUCTIVE_TITLE/);
  assert.match(activity, /setPositiveButton\(ExpertModeConsent\.DESTRUCTIVE_CONTINUE\)/);
  assert.match(activity, /setPositiveButton\(ExpertModeConsent\.STRONG_CONFIRM\)/);
  assert.doesNotMatch(activity, /Trotzdem ausführen/);
  assert.doesNotMatch(activity, /plan\.steps\.joinToString\("\\n"\) \{ "• \$\{it\.label\}" \}/);
  for (const token of [
    'Kritische Aktion bestätigen',
    'Weiter prüfen',
    'Starke Bestätigung erforderlich',
    'Diese Aktion kann Daten verändern oder löschen.',
    'Destruktiv ausführen'
  ]) {
    assert.match(consent, new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    assert.match(agent, new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
});

test('host and unit evidence for the expert-mode gate stay in source', () => {
  assert.match(unit, /confirmationTextShowsTheExactShellCommandNotOnlyTheLabel/);
  assert.match(unit, /destructiveDialogsShowTheExactCommandAndReuseAgentActivityWording/);
  assert.match(ignore, /^\*\.p12$/m);
});
