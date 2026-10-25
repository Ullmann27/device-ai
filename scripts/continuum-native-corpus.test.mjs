import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { buildCorpus, curateFile } from "./continuum-native-corpus.mjs";

const H = "a".repeat(64);
const example = (id, group, prompt = "Wie lautet " + id + "?") => ({
  id, group, prompt, completion: "Antwort " + id,
  teacher: { model: "Qwen3-offline", sha256: H },
  review: { kind: "CODE_TEST", referenceSha256: H, passed: true }
});
const all = result => [...result.train, ...result.validation, ...result.test];

test("teacher examples are only candidates and declared review never becomes verified", () => {
  const result = buildCorpus([example("one","a"),example("two","b")]);
  assert.equal(all(result).length,2);
  assert.ok(all(result).every(x => x.review.independentlyVerified === false));
  assert.ok(all(result).every(x => x.teacher.sha256 === H));
});

test("split is deterministic, input-order independent and prevents group leakage", () => {
  const rows = [
    example("a1","paraphrase-family","Frage A"),
    example("a2","paraphrase-family","Frage B"),
    example("b1","independent-family","Frage C")
  ];
  const first = buildCorpus(rows), second = buildCorpus(rows.slice().reverse());
  assert.deepEqual(first, second);
  const positions = ["train","validation","test"].filter(k =>
    first[k].some(x => x.group === "paraphrase-family"));
  assert.equal(positions.length,1);
  assert.equal(first[positions[0]].filter(x => x.group === "paraphrase-family").length,2);
});

test("duplicate prompts with conflicting answers or disjoint groups fail closed", () => {
  const a = example("a","group1"," Gleiche   Frage ");
  const conflicting = { ...example("b","group1","gleiche frage"),completion:"Gegenteil" };
  assert.throws(() => buildCorpus([a,conflicting]),/Conflicting/);
  assert.throws(() => buildCorpus([a,example("b","group2","gleiche frage")]),/cross-group/);
  assert.throws(() => buildCorpus([a,{...a}]),/Duplicate example ID/);
  assert.equal(all(buildCorpus([a,example("b","group1","gleiche frage")])).length,1);
});

test("reject unsupported reviews, bad source IDs, secrets and personally identifying emails", () => {
  assert.throws(()=>buildCorpus([{...example("a","g"),review:{kind:"CODE_TEST",referenceSha256:H,passed:false}}]),/Unreviewed/);
  assert.throws(()=>buildCorpus([{...example("a","g"),review:{kind:"AUTO",referenceSha256:H,passed:true}}]),/Unreviewed/);
  assert.throws(()=>buildCorpus([{...example("a","g"),teacher:{model:"Qwen",sha256:"bad"}}]),/teacher/);
  assert.throws(()=>buildCorpus([{...example("a","g"),prompt:"Bearer secret_token_ABCDEF9876543210"}]),/Sensitive/);
  assert.throws(()=>buildCorpus([{...example("a","g"),completion:"mail: owner@example.org"}]),/Sensitive/);
  assert.throws(()=>buildCorpus([{...example("a","g"), unexpected:true}]),/fields/);
});

test("file pipeline writes distinct frozen candidate splits without overwriting files", async () => {
  const root = await mkdtemp(join(tmpdir(),"continuum-native-corpus-"));
  const input = join(root,"source.jsonl"), output=join(root,"candidate");
  try {
    const rows=[example("a","family-a"),example("b","family-b"),example("c","family-c")];
    await writeFile(input,rows.map(JSON.stringify).join("\n") + "\n");
    const manifest = await curateFile(input,output);
    assert.equal(manifest.trainingStatus,"CANDIDATE_ONLY");
    assert.equal(manifest.deploymentPromotion,"BLOCKED_REQUIRES_INDEPENDENT_EVALUATION");
    assert.equal(manifest.reviewAssurance,"DECLARED_NOT_INDEPENDENTLY_AUTHENTICATED");
    assert.equal(Object.values(manifest.counts).reduce((a,b)=>a+b,0),3);
    const disk=JSON.parse(await readFile(join(output,"manifest.json"),"utf8"));
    assert.deepEqual(disk,manifest);
    assert.ok((await readFile(join(output,"train.jsonl"),"utf8")).endsWith("\n") ||
      manifest.counts.train===0);
    await assert.rejects(()=>curateFile(input,output),{code:"EEXIST"});
  } finally { await rm(root,{recursive:true,force:true}); }
});
