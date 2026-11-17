#!/usr/bin/env python3
"""Read-only capability observation plus synthetic disposable namespace probe.
Never opens a remote endpoint or prints device identifiers/secrets.
"""
import glob,json,os,pathlib,shutil,subprocess,tempfile
r={'classification':'HOST_CAPABILITY_ONLY','fold7':'FOLD7_NOT_RUN',
 'adb_path':shutil.which('adb'),'usb_directory':os.path.isdir('/dev/bus/usb'),
 'usb_nodes':len(glob.glob('/dev/bus/usb/*/*')),'tailscale_path':shutil.which('tailscale'),
 'bwrap_path':shutil.which('bwrap'),'uid':os.getuid()}
r['adb_daemon_present']=subprocess.run(['pgrep','-x','adb'],capture_output=True).returncode==0
try:
 p=json.loads(pathlib.Path('/etc/codex/network-policy.json').read_text())
 r['network_policy']={k:p.get(k) for k in ['version','vpn_configured','automatic_tailscale_access','tcp_network_access']}
except (OSError,ValueError):r['network_policy']='UNAVAILABLE'
r['shared_host_cgroup']={}
for name in ['memory.max','cpu.max','pids.max']:
 try:r['shared_host_cgroup'][name]=pathlib.Path('/sys/fs/cgroup/'+name).read_text().strip()
 except OSError:r['shared_host_cgroup'][name]='UNAVAILABLE'
if r['adb_path']:
 try:
  p=subprocess.run([r['adb_path'],'devices','-l'],capture_output=True,text=True,timeout=10)
  rows=[x.split() for x in p.stdout.splitlines() if x.strip() and not x.startswith('List of devices')]
  r['adb']={'exit':p.returncode,'states':[x[1] for x in rows if len(x)>1]}
 except subprocess.TimeoutExpired:r['adb']={'status':'TIMEOUT'}
if r['bwrap_path']:
 with tempfile.TemporaryDirectory(prefix='momega1-namespace-') as d:
  sentinel=pathlib.Path(d)/'synthetic-sentinel';sentinel.write_text('preserve')
  code='''import json,os,pathlib,socket
r={'netns':os.readlink('/proc/self/ns/net'),'interfaces':[name for _,name in socket.if_nameindex()]}
try:pathlib.Path(%r).write_text('changed');r['host_write']='ALLOWED'
except OSError as e:r['host_write_errno']=e.errno
print(json.dumps(r))
''' % str(sentinel)
  p=subprocess.run([r['bwrap_path'],'--unshare-all','--ro-bind','/','/','--die-with-parent','--new-session',shutil.which('python3'),'-c',code],capture_output=True,text=True,timeout=10)
  r['namespace_probe']={'exit':p.returncode,'parent_netns':os.readlink('/proc/self/ns/net'),'sentinel_unchanged':sentinel.read_text()=='preserve'}
  if p.returncode==0:r['namespace_probe']['child']=json.loads(p.stdout)
  else:r['namespace_probe']['error']=p.stderr[:500]
print(json.dumps(r,indent=2))
