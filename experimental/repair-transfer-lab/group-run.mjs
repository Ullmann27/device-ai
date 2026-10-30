// Runs `node <args>` in its OWN process group and kills the whole group on timeout and after exit, so a mutant
// with an endless loop cannot leave an orphaned test process behind (node --test runs each file in a child).
// Usage: node group-run.mjs <timeoutMs> <node args...>  · exit 124 on timeout, else the child's exit status.
import {spawn} from 'node:child_process';
const [ms,...args]=process.argv.slice(2);
const child=spawn(process.execPath,args,{detached:true,stdio:'inherit'});
let timedOut=false;
const killGroup=()=>{try{process.kill(-child.pid,'SIGKILL');}catch{}};
const timer=setTimeout(()=>{timedOut=true;killGroup();},Number(ms));
child.on('exit',(code,signal)=>{clearTimeout(timer);killGroup();process.exit(timedOut?124:code??(signal?137:1));});
