import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read=rel=>fs.readFileSync(path.join(root,rel),'utf8');

test('Continuum production paths are not coupled to another app project',()=>{
  for(const relative of [
    'app/src/main/java/at/heinz/deviceai/AssistantSkill.kt',
    'app/src/main/java/at/heinz/deviceai/CommandInterpreter.kt',
    'app/src/main/java/at/heinz/deviceai/chief/DeviceControl.kt',
    'app/src/main/java/at/heinz/deviceai/AgentActivity.kt',
    'backend/src/server.js',
    'docs/AI_MASTER_PROMPT.md'
  ]) assert.doesNotMatch(read(relative),/\blumo\b/i,relative);
});

test('Generic app actions and tool approval boundary remain available',()=>{
  const registry=read('app/src/main/java/at/heinz/deviceai/ToolRegistry.kt');
  for(const name of ['find_app','launch_app','read_app_screen'])
    assert.ok(registry.includes('ToolSpec("'+name+'"'),name);
  const skill=read('app/src/main/java/at/heinz/deviceai/AssistantSkill.kt');
  assert.match(skill,/interface AssistantSkill/);
  const control=read('app/src/main/java/at/heinz/deviceai/chief/DeviceControl.kt');
  assert.match(control,/not registered in ToolRegistry/);
  assert.match(control,/It grants no screen privileges/);
});
