import datetime, hashlib, json, pathlib, re, subprocess, sys, urllib.request, zipfile
base=pathlib.Path(__file__).resolve().parent
out=base/'status-history-20261104/release-readback'
expected_commit='a8e58ae09f6f1a8f43bd0a6f3b30b7e81bb79be6'
expected_cert='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23'
host='continuum-owner-v23-release-preflight.onrender.com'
url='https://'+host+'/continuum-local/latest.json'
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None
opener=urllib.request.build_opener(NoRedirect)
def get(url):
    return opener.open(urllib.request.Request(url,headers={'Accept-Encoding':'identity','Cache-Control':'no-cache'}),timeout=30)
with get(url) as r:
    assert r.status==200
    data=r.read(8193)
    assert len(data)<=8192 and r.headers.get('Content-Encoding','identity')=='identity'
    doc=json.loads(data)
    manifest_headers=dict(r.headers)
keys={'schema','channel','packageName','versionCode','versionName','apkUrl','apkSha256','byteLength','sourceCommit','signerSha256','approved','notes'}
assert set(doc)==keys
assert doc['schema']==1 and doc['channel']=='OWNER_RELEASE' and doc['approved'] is True
assert doc['sourceCommit']==expected_commit and doc['versionCode']==41 and doc['versionName']=='0.3.40'
assert doc['packageName']=='at.heinz.deviceai.continuumlocal' and doc['signerSha256']==expected_cert
assert re.fullmatch('[a-f0-9]{64}',doc['apkSha256'])
assert doc['apkUrl']=='https://'+host+'/continuum-local/Continuum-Lokal-'+doc['apkSha256']+'.apk'
assert type(doc['byteLength']) is int and 1_000_000<=doc['byteLength']<=90_000_000
out.mkdir(exist_ok=True)
path=out/('Continuum-Lokal-'+doc['apkSha256']+'.apk')
partial=path.with_suffix('.partial')
sha=hashlib.sha256(); size=0
with get(doc['apkUrl']) as r, partial.open('wb') as f:
    assert r.status==200 and r.headers.get('Content-Encoding','identity')=='identity'
    apk_headers=dict(r.headers)
    if r.headers.get('Content-Length') is not None: assert int(r.headers['Content-Length'])==doc['byteLength']
    while True:
        chunk=r.read(256*1024)
        if not chunk: break
        size+=len(chunk)
        assert size<=doc['byteLength']
        sha.update(chunk); f.write(chunk)
assert size==doc['byteLength'] and sha.hexdigest()==doc['apkSha256']
partial.replace(path)
with zipfile.ZipFile(path) as z:
    assert z.testzip() is None
    abis=sorted({p.split('/')[1] for p in z.namelist() if p.startswith('lib/') and p.endswith('.so')})
signer=subprocess.run([str(base/'android-sdk/build-tools/35.0.0/apksigner'),'verify','--verbose','--print-certs',str(path)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
(out/'apksigner.txt').write_text(signer.stdout)
assert signer.returncode==0, 'ACTUAL_APK_SIGNATURE_VERIFICATION_FAILED'
cert=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)',signer.stdout)
assert len(cert)==1 and cert[0].lower()==expected_cert, 'WRONG_ORIGINAL_CERTIFICATE'
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signer.stdout
badging=subprocess.run([str(base/'android-sdk/build-tools/35.0.0/aapt2'),'dump','badging',str(path)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,check=True).stdout
(out/'badging.txt').write_text(badging)
assert "package: name='at.heinz.deviceai.continuumlocal' versionCode='41' versionName='0.3.40'" in badging
(out/'after-manifest.json').write_bytes(data)
(out/'http.json').write_text(json.dumps({'manifest':manifest_headers,'apk':apk_headers},indent=2)+'\n')
result={'status':'PUBLISHED_APK_BYTES_PACKAGE_VERSION_AND_ORIGINAL_CERTIFICATE_VERIFIED','checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceCommit':expected_commit,'versionCode':41,'versionName':'0.3.40','packageName':doc['packageName'],'signerSha256':expected_cert,'apkSha256':sha.hexdigest(),'bytes':size,'apkUrl':doc['apkUrl'],'catalogUrl':url,'nativeAbis':abis,'apkSignatureV2':True,'physicalDeviceInstall':'NOT_RUN','S01ModelAcceptance':'UNRESOLVED_NO_RUN','localPath':str(path)}
(out/'verification.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
