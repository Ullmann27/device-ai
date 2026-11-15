import http from 'node:http';
import { stat, readFile } from 'node:fs/promises';
import { dirname, extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT || 4173);
const host = process.env.HOST || '127.0.0.1';
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Ungültiger PORT.');
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml',
  '.glb': 'model/gltf-binary', '.ttf': 'font/ttf', '.woff2': 'font/woff2',
  '.mp4': 'video/mp4', '.zip': 'application/zip', '.pdf': 'application/pdf' };

http.createServer(async (req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { Allow: 'GET, HEAD' }).end(); return;
  }
  let pathname;
  try { pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); }
  catch { res.writeHead(400).end('Ungültige Adresse.'); return; }
  if (pathname.split('/').some(part => part.startsWith('.'))) {
    res.writeHead(403).end('Nicht freigegeben.'); return;
  }
  const file = resolve(root, '.' + (pathname === '/' ? '/index.html' : pathname));
  if (!file.startsWith(root + sep)) { res.writeHead(403).end(); return; }
  try {
    const info = await stat(file);
    if (!info.isFile()) { res.writeHead(404).end('Datei nicht gefunden.'); return; }
    res.writeHead(200, { 'Content-Type': types[extname(file)] || 'application/octet-stream',
      'Content-Length': info.size, 'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'no-cache' });
    res.end(req.method === 'HEAD' ? undefined : await readFile(file));
  } catch { res.writeHead(404).end('Datei nicht gefunden.'); }
}).listen(port, host, () => {
  process.stdout.write(`Continuum läuft auf http://${host}:${port}\n`);
});
