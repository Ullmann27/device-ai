const { chromium } = require('playwright'); const fs = require('fs'); const http = require('http'); const path = require('path');
const root = __dirname, out = process.argv[2], W = +process.argv[3], H = +process.argv[4];
const win = JSON.parse(process.argv[5]);
const types = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.glb': 'model/gltf-binary' };
const server = http.createServer((q, r) => { const f = path.join(root, decodeURIComponent(q.url.split('?')[0]));
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { 'Content-Type': types[path.extname(f)] || 'application/octet-stream' }); r.end(d); }); });
server.listen(0, async () => {
  const port = server.address().port;
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage(); page.on('console', m => console.log('page:', m.text()));
  await page.goto(`http://127.0.0.1:${port}/index.html?w=${W}&h=${H}`);
  await page.waitForFunction(() => typeof window.setup === 'function');
  const info = await page.evaluate(w => window.setup(w), win); console.log(JSON.stringify(info));
  fs.mkdirSync(out, { recursive: true });
  const t0 = Date.now();
  for (let i = 0; i < info.frames; i++) {
    const url = await page.evaluate(i => window.renderFrame(i), i);
    fs.writeFileSync(path.join(out, `h${String(i).padStart(4, '0')}.png`), Buffer.from(url.split(',')[1], 'base64'));
  }
  console.log('rendered', info.frames, 'frames in', ((Date.now() - t0) / 1000).toFixed(1), 's');
  await browser.close(); server.close();
});
