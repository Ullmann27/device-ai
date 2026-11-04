#!/usr/bin/env python3
"""Compile actual isolated JVM source; a names-only stub excludes Android GLB parsing."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import time
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--kotlin-lib',type=Path,default=Path('/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib'),
    help='Directory with the Kotlin 2.0.20 compiler and companion JARs')
parser.add_argument('--compile-timeout',type=float,default=120,help='Maximum seconds per compilation')
parser.add_argument('--probe-timeout',type=float,default=30,help='Maximum seconds per JVM probe')
args=parser.parse_args()
if args.compile_timeout<=0 or args.probe_timeout<=0:parser.error('Timeouts must be positive')
LIB=args.kotlin_lib.resolve()
for area in ['tmp','results','scripts']:(ROOT/area).mkdir(parents=True,exist_ok=True)
tool_jars=[LIB/name for name in ['kotlin-compiler-embeddable-2.0.20.jar','kotlin-stdlib-2.0.20.jar',
    'kotlin-reflect-2.0.20.jar','kotlin-script-runtime-2.0.20.jar','trove4j-1.0.20200330.jar','annotations-24.0.1.jar',
    'kotlinx-coroutines-core-jvm-1.6.4.jar']]
assert all(p.is_file() for p in tool_jars)
toolcp=':'.join(map(str,tool_jars));runtimecp=':'.join(str(p) for p in tool_jars if 'compiler' not in p.name)
names=['SIL','PP','FF','TH','DD','KK','CH','SS','NN','RR','AA','E','I','O','U',
       'BlinkLeft','BlinkRight','BrowUp','BrowDown','Smile','JawOpen']
stub=ROOT/'scripts/AvatarGlbContractNamesOnly.kt'
stub.write_text('package at.heinz.deviceai.avatar3d\n// Host-only target names; Android GLB parser deliberately excluded.\nobject AvatarGlbContract { val TARGETS = listOf('+','.join(json.dumps(n) for n in names)+') }\n')
results={'evidenceClass':'HOST_JVM_ACTUAL_SOURCE_SYNTHETIC_INPUT_NOT_DEVICE','snapshots':{},
    'timeoutsSeconds':{'compile':args.compile_timeout,'probe':args.probe_timeout},
    'excluded':['Android GLB contract/parser','Filament native code','AudioTrack/speaker/camera','APK/Android lifecycle'],
    'compilerJars':[{'name':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in tool_jars]}
for label in ['p491','p503']:
    started=time.perf_counter();src=ROOT/'source'/label
    files=[src/n for n in ['AvatarMorphMapper.kt','AvatarContract.kt','TtsHologramSignal.kt','HologramAudio.kt']]
    if label=='p503':files += [src/'AvatarIdleMotion.kt']
    jar=ROOT/'tmp'/f'{label}-probe.jar'
    cmd=['java','-cp',toolcp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect',
        '-classpath',runtimecp,'-jvm-target','17','-d',str(jar),str(stub),str(ROOT/'scripts/HostProbe.kt'),*map(str,files)]
    comp=subprocess.run(cmd,text=True,capture_output=True,timeout=args.compile_timeout)
    (ROOT/'results'/f'{label}-compile.log').write_text(comp.stdout+comp.stderr)
    if comp.returncode:raise RuntimeError(comp.stderr)
    compile_seconds=time.perf_counter()-started
    runstart=time.perf_counter()
    run=subprocess.run(['java','-cp',str(jar)+':'+runtimecp,'at.heinz.deviceai.HostProbeKt',label],text=True,capture_output=True,timeout=args.probe_timeout)
    (ROOT/'results'/f'{label}-probe.log').write_text(run.stdout+run.stderr)
    if run.returncode:raise RuntimeError(run.stderr)
    results['snapshots'][label]={'compileSeconds':compile_seconds,'probeSeconds':time.perf_counter()-runstart,
       'compileCommand':cmd,'observed':json.loads(run.stdout)}
(ROOT/'results/kotlin-probe.json').write_text(json.dumps(results,indent=2)+'\n')
print(json.dumps({k:{'compileSeconds':v['compileSeconds'],'probeSeconds':v['probeSeconds'],'observed':v['observed']} for k,v in results['snapshots'].items()},indent=2))
