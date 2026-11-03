/** Test-only observation of real host prerequisites. Never grants runtime authority. */
import fs from 'node:fs';

export function inspectBoundedModelTestHost(){
 const unavailable=reason=>({available:false,reason});
 try{
  const root='/sys/fs/cgroup';
  if(fs.statfsSync(root).type!==0x63677270)return unavailable('cgroup v2 filesystem unavailable');
  const membership=fs.readFileSync('/proc/self/cgroup','utf8');
  if(membership.trim()!=='0::/')return unavailable('private cgroup root unavailable');
  if(fs.readFileSync('/proc/'+process.ppid+'/cgroup','utf8')!==membership)
   return unavailable('parent and job cgroups differ');
  const values={};
  for(const name of ['memory.max','memory.swap.max']){
   const file=root+'/'+name,stat=fs.lstatSync(file);
   if(!stat.isFile()||stat.uid!==0||(stat.mode&0o022)!==0||fs.realpathSync(file)!==file)
    return unavailable('kernel memory control has unsupported ownership or type');
   values[name]=fs.readFileSync(file,'utf8').trim();
  }
  const limit=values['memory.max'];
  if(!/^[0-9]+$/.test(limit)||BigInt(limit)<=0n||BigInt(limit)>8589934592n)
   return unavailable('positive memory limit at most 8 GiB unavailable');
  if(values['memory.swap.max']!=='0')return unavailable('zero-swap job bound unavailable');
  return {available:true,memoryBytes:Number(limit),swapBytes:0};
 }catch(error){
  if(['ENOENT','EACCES','EPERM','ENOTDIR'].includes(error.code))
   return unavailable('kernel prerequisite inaccessible: '+error.code);
  throw error;
 }
}
