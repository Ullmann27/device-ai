import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {spawnSync} from 'node:child_process';

const script='scripts/continuum-teamleader-termux.sh';
const source=fs.readFileSync(script,'utf8');

test('Android Termux development-worker wrapper is valid Bash',()=>{
 const result=spawnSync('bash',['-n',script],{encoding:'utf8'});
 assert.equal(result.status,0,result.stderr);
});

test('Termux uses same Node Teamleader, never a competing agent implementation',()=>{
 assert.match(source,/\.github\/continuum\/teamleader-cli\.mjs/);
 assert.match(source,/--once --root/);
 assert.match(source,/--status --root/);
 assert.match(source,/--budget-minutes 10/);
 assert.match(source,/git -C "\$ROOT" rev-parse/);
 assert.doesNotMatch(source,/\b(?:curl|wget)\b/);
 assert.doesNotMatch(source,/^\s*pkg install /m);
 assert.doesNotMatch(source,/--force|git push|git reset --hard/);
});

test('unattended Termux loop has real battery and heat gates and no implicit wake-lock',()=>{
 assert.match(source,/termux-battery-status/);
 assert.match(source,/status\.percentage <= 25/);
 assert.match(source,/status\.temperature >= 42/);
 assert.match(source,/sleep 3600/);
 assert.doesNotMatch(source,/termux-wake-lock|termux-boot|nohup|setsid/);
});
