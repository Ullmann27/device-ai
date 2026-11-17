import { createHash } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const LIMIT_BYTES = 20 * 1024 * 1024;
const MAX_EXAMPLES = 20_000;
const HEX64 = /^[a-f0-9]{64}$/;
const ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const EVIDENCE_KINDS = new Set(["OWNER_REVIEW", "CODE_TEST"]);
const SENSITIVE = [
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/i,
  /\bBearer\s+[A-Za-z0-9._~+/-]{12,}/i,
  /\b(?:api[_ -]?key|password|passwd|secret|access[_ -]?token)\s*[:=]\s*["']?[^\s"',]{8,}/i,
  /\bsk-(?:proj-)?[A-Za-z0-9_-]{16,}\b/,
  /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/
];
const digest = value => createHash("sha256").update(value).digest("hex");
const normalized = text => text.normalize("NFC").trim().replace(/\s+/g, " ");
const isObject = value => value !== null && typeof value === "object" && !Array.isArray(value);

function exactKeys(value, keys, label) {
  if (!isObject(value) || Object.keys(value).sort().join("|") !== keys.slice().sort().join("|")) {
    throw new Error("Invalid " + label + " fields");
  }
}
function boundedString(value, limit, label) {
  if (typeof value !== "string" || value.length === 0 || value.length > limit ||
      !value.trim() || value.includes("\u0000")) throw new Error("Invalid " + label);
  if (SENSITIVE.some(regex => regex.test(value))) throw new Error("Sensitive text in " + label);
  return value.normalize("NFC");
}

/**
 * Build a reproducible candidate corpus. Review fields are declarations supplied
 * by the caller, NOT independent cryptographic proof of quality or ownership.
 * No teacher answer is automatically factual, executable or approved for use.
 */
export function buildCorpus(rows) {
  if (!Array.isArray(rows) || rows.length > MAX_EXAMPLES) throw new Error("Too many examples");
  const byId = new Set();
  const byPrompt = new Map();
  const outputs = { train: [], validation: [], test: [] };
  for (const raw of rows) {
    exactKeys(raw, ["id", "group", "prompt", "completion", "teacher", "review"], "example");
    if (!ID.test(raw.id) || !ID.test(raw.group)) throw new Error("Invalid example identity");
    if (byId.has(raw.id)) throw new Error("Duplicate example ID");
    byId.add(raw.id);
    const prompt = boundedString(raw.prompt, 4096, "prompt");
    const completion = boundedString(raw.completion, 8192, "completion");
    exactKeys(raw.teacher, ["model", "sha256"], "teacher");
    if (!ID.test(raw.teacher.model) || !HEX64.test(raw.teacher.sha256)) {
      throw new Error("Invalid teacher checkpoint identity");
    }
    exactKeys(raw.review, ["kind", "referenceSha256", "passed"], "review");
    if (!EVIDENCE_KINDS.has(raw.review.kind) || raw.review.passed !== true ||
        !HEX64.test(raw.review.referenceSha256)) {
      throw new Error("Unreviewed example cannot enter the candidate corpus");
    }
    const promptKey = digest(normalized(prompt).toLowerCase());
    const previous = byPrompt.get(promptKey);
    const answerKey = digest(normalized(completion));
    if (previous && (previous.answer !== answerKey || previous.group !== raw.group)) {
      throw new Error("Conflicting or cross-group duplicate prompt");
    }
    if (previous) continue;
    byPrompt.set(promptKey, { answer: answerKey, group: raw.group });

    const bucket = parseInt(digest("continuum-group-v1:" + raw.group).slice(0,8), 16) % 10;
    const split = bucket === 0 ? "test" : bucket === 1 ? "validation" : "train";
    outputs[split].push({
      id: raw.id, group: raw.group, prompt, completion,
      teacher: { model: raw.teacher.model, sha256: raw.teacher.sha256 },
      review: { kind: raw.review.kind, referenceSha256: raw.review.referenceSha256,
        independentlyVerified: false }
    });
  }
  for (const split of Object.keys(outputs)) {
    outputs[split].sort((a,b) => a.id.localeCompare(b.id,"en"));
  }
  return outputs;
}

export async function curateFile(inputPath, outputDirectory) {
  const info = await stat(inputPath);
  if (!info.isFile() || info.size < 1 || info.size > LIMIT_BYTES) {
    throw new Error("Invalid candidate corpus size");
  }
  const bytes = await readFile(inputPath);
  if (bytes.includes(0)) throw new Error("Binary input is not valid JSONL");
  const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  const lines = text.split(/\r?\n/).filter(line => line.trim() !== "");
  if (lines.length > MAX_EXAMPLES) throw new Error("Too many examples");
  const entries = lines.map((line,i) => {
    if (line.length > 20_000) throw new Error("Oversized row " + (i + 1));
    try { return JSON.parse(line); } catch { throw new Error("Invalid JSON row " + (i + 1)); }
  });
  const corpus = buildCorpus(entries);
  const destination = resolve(outputDirectory);
  await mkdir(destination, { recursive: false, mode: 0o700 });
  const counts = {};
  const outputHashes = {};
  for (const split of ["train","validation","test"]) {
    const data = corpus[split].map(entry => JSON.stringify(entry)).join("\n");
    const body = data ? data + "\n" : "";
    const raw = Buffer.from(body,"utf8");
    await writeFile(join(destination,split + ".jsonl"),raw,{flag:"wx",mode:0o600});
    counts[split] = corpus[split].length;
    outputHashes[split] = digest(raw);
  }
  const manifest = {
    schema: "CONTINUUM_NATIVE_CANDIDATE_DATASET_V1",
    inputSha256: digest(bytes),
    counts, sha256: outputHashes,
    reviewAssurance: "DECLARED_NOT_INDEPENDENTLY_AUTHENTICATED",
    teacherOutputsAreGroundTruth: false,
    trainingStatus: "CANDIDATE_ONLY",
    deploymentPromotion: "BLOCKED_REQUIRES_INDEPENDENT_EVALUATION",
    splitMethod: "sha256-group-v1"
  };
  await writeFile(join(destination,"manifest.json"),JSON.stringify(manifest,null,2) + "\n",
    {flag:"wx",mode:0o600});
  return manifest;
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  if (process.argv.length !== 4) {
    process.stderr.write("Usage: node scripts/continuum-native-corpus.mjs <candidates.jsonl> <new-output-dir>\n");
    process.exitCode = 2;
  } else {
    curateFile(process.argv[2],process.argv[3])
      .then(result => process.stdout.write(JSON.stringify(result,null,2) + "\n"))
      .catch(error => {process.stderr.write(error.message + "\n");process.exitCode = 1;});
  }
}
