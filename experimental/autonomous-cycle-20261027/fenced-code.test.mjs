// Frozen before inference. Target is the exact production formatter, not a planted bug.
import test from 'node:test';
import assert from 'node:assert/strict';
import {formatMentorAnswer,MAX_MENTOR_ANSWER_CHARS} from '../../backend/src/mentor-answer-format.js';
const fences = [
 ['backtick code', '```js\n**literal**\n### literal\n| x | y |\n```'],
 ['tilde code', '~~~text\n**literal**\n### literal\n| x | y |\n~~~'],
 ['long fence with shorter interior run', '````md\n```\n**literal**\n````'],
 ['wrong fence character inside', '```text\n~~~\n**literal**\n```'],
 ['nonclosing suffix', '```text\n```not-a-close\n**literal**\n```'],
 ['blank lines and trailing spaces', '```\n  x  \n\n\n\n  **y**  \n```'],
 ['unclosed fence', '~~~\n**literal**\n### literal'],
 ['indented fence', '   ```txt\n**literal**\n   ```'],
];
for (const [name,input] of fences) test(name,()=>assert.equal(formatMentorAnswer(input),input));
test('format prose on both sides of a code block',()=>{
 const input='### Heading\n**before**\n```\n**literal**\n```\n**after**';
 assert.equal(formatMentorAnswer(input),'Heading\nbefore\n```\n**literal**\n```\nafter');
});
test('existing table presentation',()=>assert.equal(formatMentorAnswer('| A | B |\n|---|---|\n| **x** | y |'),'A: B\n\nx: y'));
test('existing heading and emphasis',()=>assert.equal(formatMentorAnswer('### Title\n**Value**'),'Title\nValue'));
test('existing empty input',()=>{for(const v of [null,42,'  '])assert.equal(formatMentorAnswer(v),'');});
test('existing bound and truncation notice',()=>{
 const text=formatMentorAnswer('Full sentence. '.repeat(500));
 assert.ok(text.length<=MAX_MENTOR_ANSWER_CHARS);assert.match(text,/Antwort gekürzt/);
});
test('existing incomplete marker',()=>assert.match(formatMentorAnswer('Hello',{incomplete:true}),/Tokenlimit/));
