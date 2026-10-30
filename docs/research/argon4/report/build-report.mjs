// ARGON4 report builder: joins part1..part4.html, fills result tables from the committed JSON files, inlines every listed
// source file with line numbers, and prints ONE A4 PDF with Playwright's Chromium. Nothing is hand-copied: every number in a
// generated table comes from a file in this branch, so anyone can change the data or the text and rebuild.
// Usage (repo root): node docs/research/argon4/report/build-report.mjs [out.pdf]
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {createRequire} from 'node:module';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const R = p => path.join(ROOT, p);
const OUT = process.argv[2] || R('docs/research/argon4/ARGON4-Continuum-Gutachten-20261029.pdf');
const json = p => JSON.parse(fs.readFileSync(R(p), 'utf8'));
const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const de = (x, d = 2) => (x === null || x === undefined) ? '—' : Number(x).toLocaleString('de-AT', {maximumFractionDigits: d, minimumFractionDigits: 0});
const table = (head, rows, cls = 'small') =>
  `<table class="${cls}"><tr>${head.map(h => `<th>${h}</th>`).join('')}</tr>` +
  rows.map(r => `<tr>${r.map(c => `<td>${c}</td>`).join('')}</tr>`).join('') + '</table>';

function f1Table() {
  const run1 = json('docs/research/argon4/f1-dialog-benchmark-run1-20261029.json');
  const dev = json('docs/research/argon4/f1-dialog-benchmark-dev-final-20261029.json');
  const ho = json('docs/research/argon4/f1-dialog-benchmark-holdout-20261029.json');
  const cell = c => c.notApplicable ? `n/a (${c.notApplicable})` : `${c.correct} / ${c.wrong} / ${c.clarify}`;
  const cats = Object.keys(ho.kernel);
  const rows = cats.map(k => [k, cell(run1.kernel[k]), cell(dev.kernel[k]), `<b>${cell(ho.kernel[k])}</b>`,
    cell(ho.ctrlA_localConversationContext[k]), cell(ho.ctrlB_naiveRecency[k])]);
  const tot = t => `${t.correct} / ${t.wrong} / ${t.clarify}${t.notApplicable ? ` (n/a ${t.notApplicable})` : ''}`;
  rows.push(['<b>Summe (108)</b>', tot(run1.kernelTotal), tot(dev.kernelTotal), `<b>${tot(ho.kernelTotal)}</b>`, tot(ho.ctrlA_total), tot(ho.ctrlB_total)]);
  return table(['Kategorie (je 12)', 'Kernel DEV Lauf 1', 'Kernel DEV final', 'Kernel HOLDOUT', 'CTRL-A Kontext (Holdout)', 'CTRL-B naiv (Holdout)'], rows) +
    `<p class="small">Zellen: richtig / falsch / Rückfrage. CTRL-A = Referent steht noch im <code>LocalConversationContext</code> (4 Paare); n/a = Kategorie
    hat für CTRL-A keine prüfbare Erwartung. CTRL-B = jüngste Entität. Kernel-Laufzeit im Holdout: ${de(ho.kernelMicrosPerDialog / 1000, 2)} ms je Dialog (JVM, Cloud-Host).</p>`;
}

function f2Table() {
  const d = json('docs/research/argon4/f2-bandwidth-model-20261029.json');
  const pick = (model, dev, bits, hit) => d.rows.find(r => r.model.startsWith(model) && r.device.startsWith(dev) && r.bits === bits && r.hit_rate === hit);
  const sel = [
    ['DeepSeek', 'Galaxy', 8, 0], ['DeepSeek', 'Galaxy', 6, 0], ['DeepSeek', 'Galaxy', 4, 0], ['DeepSeek', 'Galaxy', 3, 0],
    ['DeepSeek', 'Galaxy', 2, 0], ['DeepSeek', 'Galaxy', 2, 0.8], ['DeepSeek', 'Fold7 + PC', 6, 0], ['DeepSeek', 'Fold7 + PC', 2, 0.8],
    ['DeepSeek', 'Own PC', 6, 0.8], ['DeepSeek', 'Own PC', 4, 0.8], ['DeepSeek', 'Own PC', 2, 0.8],
    ['Dense', 'Galaxy', 6, 0], ['Dense', 'Galaxy', 2, 0], ['Dense', 'Own PC', 4, 0]];
  const short = r => r.model.startsWith('DeepSeek') ? 'MoE 671B/37B' : 'Dicht 500B';
  const dev = r => r.device.startsWith('Galaxy') ? 'Fold7' : r.device.startsWith('Fold7 +') ? 'Fold7, PC als USB-Speicher' : 'PC 64 GB';
  const rows = sel.map(s => pick(...s)).filter(Boolean).map(r => [short(r), dev(r), r.bits, de(r.hit_rate * 100, 0) + ' %', de(r.stored_gb, 1),
    de(r.resident_gb, 2) + (r.fits_resident ? ' ✓' : ' ✗'), de(r.flash_gb_per_token, 2), de(r.t_lower_s, 3) + ` (${r.bound_by})`,
    '≤ ' + de(r.tokens_per_s_upper, 3), de(r.answer_200_tokens_s / 60, 1) + ' min', r.battery_pct_200_tokens === null ? '—' : de(r.battery_pct_200_tokens, 2) + ' %']);
  return table(['Modell', 'Gerät', 'Bit', 'Experten-Cache-Treffer', 'Speicher GB', 'resident GB (passt)', 'nachzuladen GB/Token',
    'Untergrenze s/Token', 'Token/s', '200 Token', 'Akku (200 Token, Annahme)'], rows) +
    `<p class="small">Quelle: <code>f2-bandwidth-model-20261029.json</code> aus <code>scripts/argon4/bandwidth_model.py</code>. „Treffer“ wirkt nur, wenn der residente Teil passt.</p>`;
}

function f2Split() {
  const d = json('docs/research/argon4/f2-bandwidth-model-20261029.json');
  return table(['Bit', 'Fold7 resident GB (passt in 6 GB)', 'Leitung', 'Aktivierungen MB/Token', 't Leitung s', 't Fold7 s', 't PC s', 't gesamt s', 'Token/s (Obergrenze)'],
    d.split_moe.map(r => [r.bits, de(r.phone_resident_gb, 2) + (r.phone_fits ? ' ✓' : ' ✗'), `${de(r.link_gbps, 2)} GB/s, RTT ${de(r.link_rtt_ms, 1)} ms`,
      de(r.activation_mb_per_token, 2), de(r.t_link_s, 3), de(r.t_phone_s, 3), de(r.t_pc_s, 3), de(r.t_total_s, 3), de(r.tokens_per_s, 2)]));
}

function f2Stream() {
  const s = json('docs/research/argon4/f2-stream-bench-cloudhost-20261029.json');
  return table(['Muster', 'GB/s', 'Bemerkung'], [
    ['sequenziell 8 MiB, Cache verworfen', de(s.seq_8m_gbps, 3), 'Blocklesen, z. B. ganze Schicht'],
    ['sequenziell 8 MiB, Cache warm (Kontrolle)', de(s.seq_8m_warm_cache_gbps, 3), 'zeigt Seiten-Cache, nicht Speicher'],
    ['zufällige 22-MiB-Expertenblöcke', de(s.expert_22m_gbps, 3), 'Größe eines DeepSeek-V3-Experten bei 4 Bit'],
    ['zufällige 4 KiB', de(s.rand_4k_gbps, 3), `${de(s.rand_4k_iops, 0)} IOPS`],
    ['mmap, zufällige Seitenfehler', de(s.mmap_touch_gbps, 4), `${de(s.mmap_faults_per_s, 0)} Fehler/s – der „virtueller Speicher“-Pfad`]], 'small') +
    `<p class="small">${esc(s.platform)}, Datei ${de(s.size_mib, 0)} MiB. ${esc(s.note)}.</p>`;
}

function f2Cache() {
  const d = json('docs/research/argon4/f2-expert-cache-sim-20261029.json');
  const out = [];
  for (const pers of [0, 0.35, 0.7]) {
    const rows = [...new Set(d.rows.filter(r => r.persistence === pers).map(r => r.capacity))].map(cap => {
      const g = p => d.rows.find(r => r.persistence === pers && r.capacity === cap && r.policy === p);
      const lru = g('LRU'), lfu = g('LFU'), pf = g('PREFETCH'), b = g('BELADY');
      return [de(lru.cache_share * 100, 0) + ` % (${cap})`, `${de(lru.fetches_per_token, 2)} · ${de(lru.hit_rate * 100, 1)} %`,
        `${de(lfu.fetches_per_token, 2)} · ${de(lfu.hit_rate * 100, 1)} %`,
        `${de(pf.fetches_per_token, 2)} gesamt / ${de(pf.stall_fetches_per_token, 2)} wartend`, `<b>${de(b.fetches_per_token, 2)}</b> · ${de(b.hit_rate * 100, 1)} %`];
    });
    out.push(`<p class="small"><b>Persistenz ${de(pers, 2)}</b> (Wahrscheinlichkeit, dass ein Experte im nächsten Token wieder gewählt wird)</p>` +
      table(['Cache-Anteil (Experten)', 'LRU Lesevorgänge/Token · Treffer', 'LFU', 'Markov-PREFETCH', 'Belady (Optimum)'], rows));
  }
  return out.join('') + `<p class="small">SIMULATION mit synthetischer Spur (${d.trace.tokens} Token, ${d.trace.experts} Experten, k = ${d.trace.k}, Zipf ${d.trace.zipf}).
    Echte Router-Spuren fehlen; vor jeder Planung ersetzen.</p>`;
}

function protocol() {
  const p = json('docs/research/argon4/report/protocol.json');
  return `<p class="small">Umgebung: ${esc(p.environment)}. Codezustand: <code>${esc(p.codeState)}</code>. Datum: ${esc(p.date)}.</p>` +
    table(['Prüfung', 'Befehl', 'Ergebnis', 'Status'], p.runs.map(r => [esc(r.check), `<code>${esc(r.command)}</code>`, esc(r.result), `<b>${esc(r.status)}</b>`]));
}

function listing(rel) {
  const text = fs.readFileSync(R(rel), 'utf8').replace(/\n$/, '');
  const lines = text.split('\n');
  const w = String(lines.length).length;
  const sha = crypto.createHash('sha256').update(fs.readFileSync(R(rel))).digest('hex').slice(0, 16);
  return `<div class="listing"><h4>${esc(rel)} <span class="meta">${lines.length} Zeilen · sha256 ${sha}…</span></h4><pre class="code">` +
    lines.map((l, i) => `<span class="no">${String(i + 1).padStart(w, ' ')}</span> ${esc(l)}`).join('\n') + '</pre></div>';
}

const CSS = `
@page { size: A4; margin: 16mm 14mm 18mm 14mm; }
:root { --ink:#1b2733; --muted:#5b6b7b; --accent:#1f5f8b; --line:#ccd6e0; --soft:#f2f6fa; --red:#b03a2e; --green:#2e7d4f; }
* { box-sizing: border-box; }
html, body { background:#fff; color:var(--ink); font-family:"DejaVu Sans","Liberation Sans",Arial,sans-serif; font-size:9.6pt; line-height:1.42; }
h1 { font-size:26pt; margin:0 0 6pt; color:var(--accent); letter-spacing:.5pt; }
h2 { font-size:15pt; color:var(--accent); border-bottom:2px solid var(--accent); padding-bottom:3pt; margin:0 0 8pt; }
h3 { font-size:11.5pt; margin:12pt 0 5pt; color:#24425c; }
h4 { font-size:10pt; margin:9pt 0 4pt; }
p { margin:4pt 0 6pt; }
section { page-break-before: always; }
section.cover { page-break-before: auto; padding-top:40mm; }
.badge { display:inline-block; background:var(--accent); color:#fff; padding:3pt 8pt; border-radius:3pt; font-size:8.5pt; letter-spacing:1pt; margin-bottom:10pt; }
.sub { font-size:12pt; color:var(--muted); margin-bottom:6pt; }
.lead { font-size:13pt; font-style:italic; margin:10pt 0 18pt; }
table { width:100%; border-collapse:collapse; margin:5pt 0 8pt; page-break-inside:auto; }
tr { page-break-inside:avoid; }
th, td { border:1px solid var(--line); padding:3pt 4pt; vertical-align:top; text-align:left; }
th { background:var(--soft); font-weight:bold; }
table.small, .small { font-size:8.2pt; }
table.meta th { width:28%; }
code { font-family:"DejaVu Sans Mono","Liberation Mono",monospace; font-size:.9em; background:var(--soft); padding:0 2pt; border-radius:2pt; word-break:break-word; }
.scene { border-left:4px solid #c08a2b; background:#fbf6ec; padding:6pt 9pt; margin:6pt 0 10pt; font-style:italic; }
.scene b { font-style:normal; }
.note { border:1px solid var(--accent); background:#eef5fb; padding:6pt 9pt; margin:10pt 0; }
.formula { background:var(--soft); border:1px solid var(--line); padding:6pt 9pt; margin:6pt 0; font-family:"DejaVu Serif",serif; }
ol.toc li { margin:3pt 0; font-size:11pt; }
svg.diagram { width:100%; height:auto; margin:6pt 0; font-family:"DejaVu Sans",sans-serif; }
svg .box rect { fill:#f2f6fa; stroke:#3a5068; stroke-width:1.2; rx:5; }
svg .new rect { fill:#eef8f1; stroke:var(--green); stroke-width:1.4; stroke-dasharray:6 4; rx:5; }
svg .lrbox rect { fill:#fbeeee; stroke:var(--red); stroke-width:1.2; rx:5; }
svg text { font-size:12px; text-anchor:middle; fill:var(--ink); }
svg text.s { font-size:10px; fill:var(--muted); }
svg text.dh { font-size:14px; font-weight:bold; fill:var(--accent); }
svg text.red { font-size:10px; fill:var(--red); }
svg path.ln { stroke:#3a5068; stroke-width:1.3; fill:none; }
svg path.lr { stroke:var(--red); stroke-width:1.6; fill:none; stroke-dasharray:4 3; }
.listing { page-break-inside:auto; }
.listing h4 { font-family:"DejaVu Sans Mono",monospace; font-size:8.6pt; background:var(--soft); padding:3pt 5pt; border:1px solid var(--line); margin:10pt 0 0; }
.listing .meta { color:var(--muted); font-weight:normal; }
pre.code { font-family:"DejaVu Sans Mono","Liberation Mono",monospace; font-size:6.6pt; line-height:1.32; white-space:pre-wrap; word-break:break-all;
  border:1px solid var(--line); border-top:none; padding:4pt 5pt; margin:0 0 6pt; background:#fcfdfe; }
pre.code .no { color:#97a6b4; user-select:none; }
`;

function build() {
  let body = ['part1.html', 'part2.html', 'part3.html', 'part4.html'].map(f => fs.readFileSync(R('docs/research/argon4/report/' + f), 'utf8')).join('\n');
  const fill = {F1TABLE: f1Table, F2TABLE: f2Table, F2SPLIT: f2Split, F2STREAM: f2Stream, F2CACHE: f2Cache, PROTOCOL: protocol};
  for (const [k, fn] of Object.entries(fill)) {
    if (!body.includes(`<!--${k}-->`)) throw new Error('missing placeholder ' + k);
    body = body.replace(`<!--${k}-->`, fn());
  }
  body = body.replace(/<!--CODE:([^>]+?)-->/g, (_, p) => listing(p.trim()));
  if (/<!--[A-Z0-9]+(:[^>]*)?-->/.test(body)) throw new Error('unfilled placeholder left');
  return `<!doctype html><html lang="de"><head><meta charset="utf-8"><title>ARGON4 Continuum Gutachten</title><style>${CSS}</style></head><body>${body}</body></html>`;
}

async function main() {
  const html = build();
  const htmlOut = OUT.replace(/\.pdf$/, '.html');
  if (process.env.ARGON4_KEEP_HTML) fs.writeFileSync(htmlOut, html);
  const require = createRequire(import.meta.url);
  let chromium;
  try { ({chromium} = require('playwright')); } catch { ({chromium} = await import('playwright')); }
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    await page.setContent(html, {waitUntil: 'load'});
    await page.pdf({
      path: OUT, format: 'A4', printBackground: true, displayHeaderFooter: true, preferCSSPageSize: true,
      headerTemplate: '<div style="font-size:7px;width:100%;padding:0 14mm;color:#5b6b7b;font-family:DejaVu Sans,sans-serif">ARGON 4 · Continuum/Jarvis · Forschungsgutachten · Zweig opus/argon4-research-20261029</div>',
      footerTemplate: '<div style="font-size:7px;width:100%;padding:0 14mm;color:#5b6b7b;font-family:DejaVu Sans,sans-serif;text-align:right">Seite <span class="pageNumber"></span> / <span class="totalPages"></span></div>'
    });
  } finally { await browser.close(); }
  const size = fs.statSync(OUT).size;
  const sha = crypto.createHash('sha256').update(fs.readFileSync(OUT)).digest('hex');
  console.log(`PDF ${path.relative(ROOT, OUT)} bytes=${size} sha256=${sha}`);
}

main().catch(e => { console.error(e); process.exit(1); });
