// Sealed input variants: not executed or provided to the model until after a verified candidate.
import test from 'node:test';import assert from 'node:assert/strict';
import {formatMentorAnswer} from '../../backend/src/mentor-answer-format.js';
for(const [i,open,close,body] of [
 [1,'~~~~~python','~~~~~~','**pow**\n| bitwise | or |'],
 [2,'`````javascript','`````  ','### comment\n```\n~~~\n**x**'],
 [3,'  ~~~ sh',' ~~~','  **space**  \n\n\n# heading'],
 [4,'```md','```','|---|---|\n## marker\n**bold**'],
])test('sealed literal block '+i,()=>{
 const input='### Before\n'+open+'\n'+body+'\n'+close+'\n**After**';
 assert.equal(formatMentorAnswer(input),'Before\n'+open+'\n'+body+'\n'+close+'\nAfter');
});
test('sealed consecutive independent fence types',()=>assert.equal(formatMentorAnswer('```\n**a**\n```\n~~~\n**b**\n~~~'),'```\n**a**\n```\n~~~\n**b**\n~~~'));
test('sealed CRLF normalization preserves fenced payload',()=>assert.equal(formatMentorAnswer('~~~\r\n**a**\r\n\r\n\r\n~~~'),'~~~\n**a**\n\n\n~~~'));
test('sealed ordinary prose still collapses blank lines',()=>assert.equal(formatMentorAnswer('**one**\n\n\n\n### two'),'one\n\ntwo'));
