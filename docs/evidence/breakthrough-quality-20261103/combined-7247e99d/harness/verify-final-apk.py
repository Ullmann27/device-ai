#!/usr/bin/env python3
import sys,pathlib,hashlib,zipfile,subprocess,json,struct
apk=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2]);sdk=pathlib.Path('/workspace/continuum-build-env/android-sdk/build-tools/35.0.0');out.mkdir(parents=True,exist_ok=True)
meta={'apk':str(apk),'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'expected_glb_sha256':'f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e','native':[]}
for name,args in [('badging',[str(sdk/'aapt'),'dump','badging',str(apk)]),('unsigned-verify',[str(sdk/'apksigner'),'verify','--verbose','--print-certs',str(apk)]),('zipalign-16k',[str(sdk/'zipalign'),'-c','-P','16','-v','4',str(apk)])]:
 r=subprocess.run(args,capture_output=True,text=True);(out/(name+'.txt')).write_text(r.stdout+r.stderr);meta[name+'_exit']=r.returncode
 if name=='badging':meta['package_line']=next((s for s in r.stdout.splitlines() if s.startswith('package:')),None)
with zipfile.ZipFile(apk) as z,apk.open('rb') as raw:
 asset=z.read('assets/avatar3d/continuum-avatar.glb');meta['glb_bytes']=len(asset);meta['glb_sha256']=hashlib.sha256(asset).hexdigest();meta['glb_match']=meta['glb_sha256']==meta['expected_glb_sha256'] and len(asset)==8690400
 meta['asset_notices']=[n for n in z.namelist() if 'avatar3d/' in n and not n.endswith('.glb')]
 meta['v1_signature_entries']=[n for n in z.namelist() if n.startswith('META-INF/') and n.upper().endswith(('.RSA','.DSA','.EC','.SF'))]
 raw.seek(z.start_dir-16);meta['apk_signing_block_present']=raw.read(16)==b'APK Sig Block 42'
 for info in z.infolist():
  if not info.filename.startswith('lib/') or not info.filename.endswith('.so'):continue
  raw.seek(info.header_offset);header=raw.read(30);name_len,extra_len=struct.unpack_from('<HH',header,26);offset=info.header_offset+30+name_len+extra_len
  data=z.read(info);p=out/'elf'/info.filename;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
  r=subprocess.run(['readelf','-lW',str(p)],capture_output=True,text=True);p.with_suffix('.readelf.txt').write_text(r.stdout+r.stderr)
  loads=[]
  for line in r.stdout.splitlines():
   fields=line.split()
   if fields and fields[0]=='LOAD':loads.append({'offset':fields[1],'virtual_address':fields[2],'alignment':fields[-1]})
  row={'path':info.filename,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'zip_compression':info.compress_type,'zip_data_offset':offset,'zip_16k_aligned':offset%16384==0,'readelf_exit':r.returncode,'loads':loads,'elf_16k':bool(loads) and all(int(v['alignment'],16)>=16384 and (int(v['offset'],16)-int(v['virtual_address'],16))%16384==0 for v in loads)}
  meta['native'].append(row)
meta['arm64_all_elf_16k']=all(n['elf_16k'] for n in meta['native'] if '/arm64-v8a/' in n['path']) and any('/arm64-v8a/' in n['path'] for n in meta['native'])
meta['unsigned_expected']=meta['unsigned-verify_exit']!=0 and 'Missing META-INF/MANIFEST.MF' in (out/'unsigned-verify.txt').read_text() and not meta['v1_signature_entries'] and not meta['apk_signing_block_present']
meta['package_expected']=bool(meta['package_line']) and all(x in meta['package_line'] for x in ["name='at.heinz.deviceai.continuumlocal'","versionCode='40'","versionName='0.3.39'"])
(out/'APK-METADATA.json').write_text(json.dumps(meta,indent=2)+'\n')
print(json.dumps({k:v for k,v in meta.items() if k!='native'},indent=2))
for n in meta['native']:print(n['path'],'ELF16K',n['elf_16k'],'ZIP16K',n['zip_16k_aligned'],'compressed',n['zip_compression'])
sys.exit(0 if meta['glb_match'] and meta['package_expected'] and meta['unsigned_expected'] and meta['zipalign-16k_exit']==0 and meta['arm64_all_elf_16k'] else 1)
