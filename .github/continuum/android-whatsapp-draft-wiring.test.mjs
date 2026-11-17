import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const activity = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'), 'utf8');
const policy = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/WhatsAppDraftPolicy.kt'), 'utf8');
const registry = fs.readFileSync(path.join(root, 'app/src/main/java/at/heinz/deviceai/ToolRegistry.kt'), 'utf8');

test('Owner-accessible WhatsApp draft does not claim message delivery', () => {
  assert.match(activity, /CenterAction\("WHATSAPP · NACHRICHT ENTWERFEN"\) \{ showWhatsAppDraftDialog\(\) \}/);
  const start = activity.indexOf('private fun showWhatsAppDraftDialog()');
  const end = activity.indexOf('private fun showReadinessGuide()', start);
  assert.ok(start >= 0 && end > start);
  const flow = activity.slice(start, end);
  for (const required of [
    'WhatsAppDraftPolicy.validated(draft.text.toString())',
    'Intent(Intent.ACTION_SEND)',
    'setPackage(WhatsAppDraftPolicy.TARGET_PACKAGE)',
    'putExtra(Intent.EXTRA_TEXT, text)',
    'setPositiveButton("In WhatsApp öffnen", null)',
    'nicht als gesendet bestätigt'
  ]) assert.ok(flow.includes(required), required);
  assert.doesNotMatch(flow, /send_message|sendMessage|performAction|ShizukuBridge/);
  assert.match(policy, /const val TARGET_PACKAGE = "com\.whatsapp"/);
  assert.doesNotMatch(registry, /ToolSpec\("send_message"/);
});
