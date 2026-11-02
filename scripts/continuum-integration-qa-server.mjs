import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Serve ONLY completed test APK and manifest. No private repository files.
const root=path.resolve(fileURLToPath(new URL('../continuum-all-systems-site/',import.meta.url)));
const manifest=JSON.parse(fs.readFileSync(path.join(root,'build.json'),'utf8'));
if(manifest.status!=='HOST_TESTS_PASS_DEVICE_NOT_RUN' ||
  manifest.packageName!=='at.heinz.deviceai.continuumlocal.qa' ||
  !/^[a-f0-9]{40}$/.test(manifest.sourceCommit) ||
  !/^[a-f0-9]{64}$/.test(manifest.sha256) ||
  !/^Continuum-Research-QA-[0-9a-f]{64}\.apk$/.test(manifest.apkFile) ||
  manifest.apkFile!=='Continuum-Research-QA-'+manifest.sha256+'.apk')
  throw Error('QA artifact identity unavailable');
const names=new Map([
  ['/',{file:'index.html',type:'text/html; charset=utf-8'}],
  ['/build.json',{file:'build.json',type:'application/json'}],
  ['/'+encodeURIComponent(manifest.apkFile),{file:manifest.apkFile,type:'application/vnd.android.package-archive'}]
]);
const port=Number(process.env.PORT||10000);
if(!Number.isSafeInteger(port)||port<1||port>65535)throw Error('Invalid port');
http.createServer((req,res)=>{
  if(req.method!=='GET'&&req.method!=='HEAD'){
    res.writeHead(405,{'Allow':'GET, HEAD'});res.end();return;
  }
  const entry=names.get(req.url||'');
  if(!entry){res.writeHead(404);res.end();return;}
  const full=path.join(root,entry.file);
  let stat;
  try{stat=fs.lstatSync(full);if(!stat.isFile()||stat.isSymbolicLink())throw Error('Unsafe artifact');}
  catch{res.writeHead(503);res.end();return;}
  res.writeHead(200,{'Content-Type':entry.type,'Content-Length':stat.size,
    'X-Content-Type-Options':'nosniff','Cache-Control':'no-store'});
  if(req.method==='HEAD'){res.end();return;}
  fs.createReadStream(full).on('error',()=>res.destroy()).pipe(res);
}).listen(port,'0.0.0.0',()=>process.stdout.write('CONTINUUM_QA_DOWNLOAD_READY '+manifest.sourceCommit+'\n'));
