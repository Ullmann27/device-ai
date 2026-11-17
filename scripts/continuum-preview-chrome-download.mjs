#!/usr/bin/env node
/**
 * Owner-only Chrome download for a locally produced, verified Android Preview.
 * Serves exactly one local APK over Android loopback; it never uploads anything,
 * installs packages, exposes a LAN listener, or starts a build.
 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import http from 'node:http';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const STATUS_OK = 'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED';
const DEFAULT_STATE = path.join(os.homedir(), '.local/state/device-ai-continuum/mobile-builder');
const PORT = 8765;
const DOWNLOAD_ROUTE = '/DeviceAI-Continuum-Preview.apk';

async function sha256(file) {
  const hash = createHash('sha256');
  for await (const part of fs.createReadStream(file)) hash.update(part);
  return hash.digest('hex');
}

/** Never allow a report to select an arbitrary filesystem path for HTTP serving. */
export async function verifiedPreview(stateDir = DEFAULT_STATE) {
  const state = path.resolve(stateDir);
  if (fs.lstatSync(state).isSymbolicLink() || !fs.statSync(state).isDirectory()) {
    throw Error('Unsicherer lokaler APK-State-Ordner.');
  }
  const reportPath = path.join(state, 'latest-build.json');
  if (fs.lstatSync(reportPath).isSymbolicLink()) throw Error('Verknüpfter Buildbericht verweigert.');
  const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
  if (report.schemaVersion !== 1 || report.status !== STATUS_OK ||
      report.productionReady !== false || report.deviceInstallationVerified !== false ||
      !/^[a-f0-9]{40}$/.test(report.commit ?? '') ||
      !/^[a-f0-9]{64}$/.test(report.apkSha256 ?? '') ||
      !/^[a-f0-9]{64}$/.test(report.signerSha256 ?? '')) {
    throw Error('Kein gültiger, signaturgeprüfter experimenteller APK-Buildbericht.');
  }
  const file = path.join(state, `apk-${report.apkSha256}.apk`);
  if (report.apk !== file) throw Error('APK-Pfad weicht vom geschützten Builder-Artefakt ab.');
  const stat = fs.lstatSync(file);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.size < 100 ||
      stat.size > 512 * 1024 * 1024 || stat.size !== report.apkBytes) {
    throw Error('Lokale APK fehlt oder Dateigröße stimmt nicht.');
  }
  if (await sha256(file) !== report.apkSha256) {
    throw Error('APK-SHA256 stimmt nicht mit dem signaturgeprüften Buildbericht überein.');
  }
  return {
    file,
    length: stat.size,
    commit: report.commit,
    digest: report.apkSha256,
    filename: `DeviceAI-Continuum-Preview-${report.commit.slice(0, 7)}-${report.apkSha256.slice(0, 12)}.apk`
  };
}

export function createChromeDownloadServer({stateDir = DEFAULT_STATE, port = PORT} = {}) {
  const server = http.createServer((req, res) => {
    void (async () => {
      const origin = req.headers.host;
      if (origin !== `127.0.0.1:${server.address().port}` &&
          origin !== `localhost:${server.address().port}`) {
        res.writeHead(403, {'Content-Type': 'text/plain; charset=utf-8'});
        res.end('Nur lokale Chrome-Verbindungen erlaubt.');
        return;
      }
      if (req.method !== 'GET' && req.method !== 'HEAD') {
        res.writeHead(405, {Allow: 'GET, HEAD'});
        res.end();
        return;
      }
      if (req.url !== '/' && req.url !== DOWNLOAD_ROUTE) {
        res.writeHead(404, {'Content-Type': 'text/plain; charset=utf-8'});
        res.end('Nicht gefunden.');
        return;
      }
      const apk = await verifiedPreview(stateDir);
      if (req.url === '/') {
        const page = `<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Device AI Preview</title><h1>Device AI Continuum</h1><p>Experimentelle, noch nicht am Gerät abgenommene Preview.</p><p>Build ${apk.commit.slice(0, 7)} · ${apk.length} Bytes</p><p><a href="${DOWNLOAD_ROUTE}">APK in Chrome herunterladen</a></p><p>Der Download kommt nur von deinem eigenen Smartphone. Android verlangt eine gesonderte Installationsfreigabe.</p></html>`;
        res.writeHead(200, {
          'Content-Type': 'text/html; charset=utf-8',
          'Cache-Control': 'no-store',
          'X-Content-Type-Options': 'nosniff'
        });
        res.end(req.method === 'HEAD' ? undefined : page);
        return;
      }
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': `attachment; filename="${apk.filename}"`,
        'Content-Length': apk.length,
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff'
      });
      if (req.method === 'HEAD') { res.end(); return; }
      const stream = fs.createReadStream(apk.file);
      stream.on('error', () => res.destroy());
      res.on('close', () => stream.destroy());
      stream.pipe(res);
    })().catch(error => {
      if (!res.headersSent) {
        res.writeHead(503, {'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store'});
        res.end(`Download gesperrt: ${error.message}`);
      } else res.destroy();
    });
  });
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => {
      server.removeListener('error', reject);
      resolve(server);
    });
  });
}

async function main() {
  if (process.argv.length !== 2) throw Error('Keine Argumente erlaubt.');
  const apk = await verifiedPreview();
  const server = await createChromeDownloadServer();
  console.log(`\nChrome öffnen: http://127.0.0.1:${PORT}/`);
  console.log(`APK: ${apk.filename}\nSHA-256: ${apk.digest}`);
  console.log('Nur dieses Smartphone; kein Internet-Upload. Termux bis zum Download offen lassen.');
  console.log('Server endet automatisch nach 20 Minuten oder mit Ctrl+C.');
  setTimeout(() => server.close(), 20 * 60 * 1000).unref();
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error('Chrome-Download nicht gestartet: ' + error.message); process.exitCode = 2; });
}
