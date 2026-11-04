// Static check: hard-coded status values, placeholders and garbled AI-video text in the Jarvis UI.
// Scans string literals in app/src/main/java/**/ui/**/*.kt and app/src/main/res/values*/strings*.xml.
// Comments, numeric constants (dp/sp/alpha/durations) and interpolated live values are ignored.
// Usage: node tools/status-literal-scan/scan.mjs [repoRoot]   (exit 1 when hits remain)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ALLOWED_TEXT = new Set(['—', 'nicht verfügbar', 'Quelle fehlt']);
const STATUS = [
  /(?<![\w%.$])\d+(?:[.,]\d+)?\s*(?:\/\s*\d+(?:[.,]\d+)?\s*)?(?:%|°\s?C|GB|MB|tok\/s|ms)(?![A-Za-z])/,
  /(?<![\w%.$])\d+\s+(?:Aufgaben|Tasks?|Agenten|Running|Active|aktiv|laufend\w*)\b/i,
];
const PLACEHOLDER = [/\bKachel\s*[1-8]\b/, /\bLorem ipsum\b/i, /\bPlatzhalter\b/];
// Known garbled words from the AI-generated reference videos (VIDEO-SPEC.md) – never copy them.
const GARBLED = [/\bWeelther\b/i, /\bSystemtics\b/i];

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out); else out.push(p);
  }
  return out;
}

/** Kotlin string literals with line numbers; comments skipped, interpolations replaced by §. */
function kotlinLiterals(src) {
  const out = []; let line = 1; let i = 0;
  while (i < src.length) {
    const c = src[i], n = src[i + 1];
    if (c === '\n') { line++; i++; continue; }
    if (c === '/' && n === '/') { while (i < src.length && src[i] !== '\n') i++; continue; }
    if (c === '/' && n === '*') { i += 2; while (i < src.length && !(src[i] === '*' && src[i + 1] === '/')) { if (src[i] === '\n') line++; i++; } i += 2; continue; }
    if (c === "'") { i += src[i + 1] === '\\' ? 4 : 3; continue; }
    if (c === '"') {
      const triple = src.startsWith('"""', i); const startLine = line; let s = ''; i += triple ? 3 : 1;
      while (i < src.length) {
        if (triple ? src.startsWith('"""', i) : src[i] === '"') { i += triple ? 3 : 1; break; }
        if (!triple && src[i] === '\\') { s += src[i + 1]; i += 2; continue; }
        if (src[i] === '$' && src[i + 1] === '{') { let d = 0; do { if (src[i] === '{') d++; if (src[i] === '}') d--; i++; } while (i < src.length && d > 0); s += '§'; continue; }
        if (src[i] === '$' && /[A-Za-z_]/.test(src[i + 1] ?? '')) { i++; while (/\w/.test(src[i] ?? '')) i++; s += '§'; continue; }
        if (src[i] === '\n') line++;
        s += src[i++];
      }
      out.push({ line: startLine, text: s });
      continue;
    }
    i++;
  }
  return out;
}

function xmlStrings(src) {
  const out = []; const lines = src.split('\n');
  lines.forEach((l, idx) => { for (const m of l.matchAll(/<(string|item)\b[^>]*>([^<]*)<\/\1>/g)) out.push({ line: idx + 1, text: m[2].trim() }); });
  return out;
}

function classify(text) {
  const t = text.trim();
  if (!t || ALLOWED_TEXT.has(t)) return null;
  if (GARBLED.some((r) => r.test(t))) return 'garbled-video-text';
  if (PLACEHOLDER.some((r) => r.test(t))) return 'placeholder';
  if (STATUS.some((r) => r.test(t))) return 'status-number';
  return null;
}

export function loadAllowlist(file) {
  return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : [];
}

export function scan(root, { allowlist = [] } = {}) {
  for (const a of allowlist) if (!a.reason || !String(a.reason).trim()) throw new Error(`allowlist entry without reason: ${a.file} ${a.text}`);
  const files = [
    ...walk(path.join(root, 'app/src/main/java')).filter((f) => f.endsWith('.kt') && f.split(path.sep).includes('ui')),
    ...walk(path.join(root, 'app/src/main/res')).filter((f) => /[\\/]values[^\\/]*[\\/]strings[^\\/]*\.xml$/.test(f)),
  ].sort();
  const hits = [];
  for (const f of files) {
    const rel = path.relative(root, f).split(path.sep).join('/');
    const src = fs.readFileSync(f, 'utf8');
    for (const { line, text } of f.endsWith('.kt') ? kotlinLiterals(src) : xmlStrings(src)) {
      const kind = classify(text);
      if (!kind) continue;
      if (allowlist.some((a) => a.file === rel && text.includes(a.text))) continue;
      hits.push({ file: rel, line, kind, text });
    }
  }
  return hits;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(process.argv[2] ?? '.');
  const allow = loadAllowlist(path.join(path.dirname(fileURLToPath(import.meta.url)), 'allowlist.json'));
  const hits = scan(root, { allowlist: allow });
  for (const h of hits) console.log(`${h.file}:${h.line}\t${h.kind}\t${JSON.stringify(h.text)}`);
  console.error(`${hits.length} hit(s)`);
  process.exit(hits.length ? 1 : 0);
}
