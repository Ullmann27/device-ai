import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const file = 'app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceView.kt';
const source = fs.readFileSync(file, 'utf8');
const eventSource = fs.readFileSync('app/src/main/java/at/heinz/deviceai/cockpit/CockpitAnimationEvent.kt', 'utf8');
const effects = fs.readFileSync('app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceEffects.kt', 'utf8');
const eventStart = eventSource.indexOf('data class DocumentQuestion(');
const eventEnd = eventSource.indexOf(') : CockpitAnimationEvent()', eventStart);
const eventContract = eventSource.slice(eventStart, eventEnd);
const start = source.indexOf('is CockpitAnimationEvent.DocumentQuestion ->');
const end = source.indexOf('else -> Unit', start);
const branch = source.slice(start, end);

test('document questions render from real event text, not invented selectable answers', () => {
  assert.ok(start >= 0 && end > start);
  assert.ok(eventStart >= 0 && eventEnd > eventStart);
  assert.match(eventContract, /val question: String/);
  assert.doesNotMatch(eventContract, /val options: List/);
  assert.match(branch, /effects\.clearPills\(\)/);
  assert.match(branch, /card\(key\("doc"\), JarvisSequenceEffects\.Kind\.DOCUMENT, event\.documentName,[\s\S]*?event\.question\.takeIf \{ it\.isNotBlank\(\) \}/);
  assert.doesNotMatch(branch, /questionPills\(listOf\(event\.question\)/);
});

test('real future answer-pills remain gated by typed, explicitly authorized actions', () => {
  assert.match(effects, /fun answerPills\(pills: List<at\.heinz\.deviceai\.cockpit\.AnswerPill>/);
  assert.match(effects, /if \(landed && pill\.enabled/);
  assert.match(source, /init \{ isClickable = false; isFocusable = false;/);
});
