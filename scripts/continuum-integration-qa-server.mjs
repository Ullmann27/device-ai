import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Read-only QA receipts. The real signed owner APK must be published by its
// original pinned Render service, NEVER by this test server.
const root=path.resolve(fileURLToPath(new URL('../continuum-all-systems-site/',import.meta.url)));
const manifest=JSON.parse(fs.readFileSync(path.join(root,'build.json'),'utf8'));
if(manifest.status!=='UNSIGNED_BUILD_VERIFIED_DEVICE_NOT_RUN' ||
  manifest.packageName!=='at.heinz.deviceai.continuumlocal' || manifest.versionCode!==38 ||
  manifest.versionName!=='0.3.37' || manifest.ownerSigned!==false ||
  manifest.apkPublished!==false || !/^[a-f0-9]{40}$/.test(manifest.sourceCommit) ||
  !/^[a-f0-9]{64}$/.test(manifest.unsignedArtifactSha256))
  throw Error('Invalid original-package QA receipt');
const entries=new Map([
 ['/',{name:'index.html',type:'text/html; charset=utf-8'}],
 ['/build.json',{name:'build.json',type:'application/json'}]
]);
const port=Number(process.env.PORT||10000);
if(!Number.isSafeInteger(port)||port<1||port>65535)throw Error('Invalid port');
http.createServer((req,res)=>{
 if(req.method!=='GET'&&req.method!=='HEAD'){res.writeHead(405,{'Allow':'GET, HEAD'});res.end();return;}
 const entry=entries.get(req.url||'');
 if(!entry){res.writeHead(404);res.end();return;}
 const file=path.join(root,entry.name);
 let stat;
 try{stat=fs.lstatSync(file);if(!stat.isFile()||stat.isSymbolicLink())throw Error('Not regular');}
 catch{res.writeHead(503);res.end();return;}
 res.writeHead(200,{'Content-Type':entry.type,'Content-Length':stat.size,
 'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
 if(req.method==='HEAD'){res.end();return;}
 fs.createReadStream(file).on('error',()=>res.destroy()).pipe(res);
}).listen(port,'0.0.0.0',()=>process.stdout.write('CONTINUUM_OWNER_QA_STATUS_READY '+manifest.sourceCommit+'\n'));
