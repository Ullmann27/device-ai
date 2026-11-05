const {chromium}=require('playwright'); const fs=require('fs'); const http=require('http'); const path=require('path');
const dir=__dirname; const out=process.argv[2]; const views=JSON.parse(fs.readFileSync(process.argv[3],'utf8'));
const srv=http.createServer((q,r)=>{const p=path.join(dir,decodeURIComponent(q.url.split('?')[0])); if(!fs.existsSync(p)){r.writeHead(404);return r.end();}
 const t=p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':'application/octet-stream'; r.writeHead(200,{'Content-Type':t}); fs.createReadStream(p).pipe(r);}).listen(0,async()=>{
 const port=srv.address().port; const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const pg=await b.newPage({viewport:{width:512,height:640}}); pg.on('console',m=>console.log('console:',m.text()));
 const q=process.argv[4]||""; await pg.setViewportSize({width:+(process.env.W||512),height:+(process.env.H||640)}); await pg.goto(`http://127.0.0.1:${port}/view.html?w=${process.env.W||512}&h=${process.env.H||640}`); await pg.waitForFunction(()=>window.state.ready||window.state.error,null,{timeout:120000});
 const err=await pg.evaluate(()=>window.state.error); if(err){console.log('ERR',err);process.exit(1);}
 console.log('meshes',JSON.stringify(await pg.evaluate(()=>window.meshNames)).slice(0,300)); console.log('bbox',JSON.stringify(await pg.evaluate(()=>window.bbox)));
 fs.mkdirSync(out,{recursive:true});
 for(const v of views){ const d=await pg.evaluate(a=>window.shot(...a),[v.az,v.el,v.dist,v.ty,v.hide||[],v.morph||{},v.fov]); fs.writeFileSync(path.join(out,v.name+'.png'),Buffer.from(d.split(',')[1],'base64')); }
 await b.close(); srv.close(); });
