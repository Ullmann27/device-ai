#!/usr/bin/env python3
"""Fresh targets, frozen C1 dependencies."""
import argparse
import fcntl
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import time

ROOT=pathlib.Path(__file__).resolve().parents[3]
TOOLCHAIN=pathlib.Path('/workspace/scratch/b6ee64d418cf/toolchain')
DEPS=pathlib.Path('/workspace/scratch/bb03c70897d0/avatar-test-deps')
LOCKS=[TOOLCHAIN/'gradle-build.lock',pathlib.Path('/workspace/scratch/d663e8444310/qa/gradle.lock')]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('output',type=pathlib.Path)
    parser.add_argument('--base-jar',type=pathlib.Path,required=True)
    parser.add_argument('--existing-tests',action='store_true')
    args=parser.parse_args()
    if not args.base_jar.is_file():
        raise SystemExit('Frozen C1 dependency JAR required')
    out=args.output.resolve()
    out.mkdir(parents=True,exist_ok=False)
    handles=[]
    deadline=time.monotonic()+45
    for path in LOCKS:
        handle=path.open('a')
        while True:
            try:
                fcntl.flock(handle,fcntl.LOCK_EX|fcntl.LOCK_NB)
                handles.append(handle)
                break
            except BlockingIOError:
                if time.monotonic()>=deadline:
                    (out/'status.json').write_text(json.dumps({'status':'LOCK_BUSY','lock':str(path),'exit':75})+'\n')
                    return 75
                time.sleep(1)
    tests=['at.heinz.deviceai.learning.ContinuumSourceReplayCoverageTest']
    if args.existing_tests:
        tests.append('at.heinz.deviceai.learning.ContinuumLearningCoreTest')
        tests.append('at.heinz.deviceai.learning.LearningIntegrationTest')
    sources=[ROOT/('app/src/main/java/at/heinz/deviceai/learning/'+name+'.kt') for name in
             ['ContinuumLearningCore','ContinuumLearningExperience','TrainableSubwordEncoder','MentorSource']]
    sources += [ROOT/('app/src/test/java/'+name.replace('.','/')+'.kt') for name in tests]
    frozen=out/'sources'
    frozen.mkdir()
    for source in sources:
        shutil.copyfile(source,frozen/source.name)
    deps=sorted(DEPS.glob('*.jar'))
    if not (DEPS/'kotlin-compiler-embeddable-2.4.0.jar').is_file():
        raise RuntimeError('Offline Kotlin compiler unavailable')
    hashes={str(path):sha(path) for path in [args.base_jar,*deps]}
    manifest={'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
              'sources':{str(p.relative_to(ROOT)):sha(p) for p in sources},
              'dependencies':hashes,'locks':list(map(str,LOCKS)),'tests':tests,
              'module':'DeviceAI_app_debug','dependencyScope':'Frozen C1 compiled classes; learning targets rebuilt'}
    (out/'manifest.json').write_text(json.dumps(manifest,sort_keys=True,indent=2)+'\n')
    java=TOOLCHAIN/'jdk17/bin/java'
    cp=':'.join(map(str,[args.base_jar,*deps]))
    classes=out/'classes'
    compile_command=[java,'-Xmx512m','-cp',':'.join(map(str,deps)),
                     'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect',
                     '-jvm-target','17','-module-name','DeviceAI_app_debug',
                     '-Xfriend-paths='+str(args.base_jar),'-classpath',cp,'-d',classes,
                     *[frozen/p.name for p in sources]]
    commands=[list(map(str,compile_command))]
    started=time.monotonic()
    with (out/'compile.txt').open('w') as log:
        compiled=subprocess.run(compile_command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    tested=None
    if compiled.returncode==0:
        command=[java,'-Xmx512m','-cp',str(classes)+':'+str(ROOT/'app/src/test/resources')+':'+cp,
                 'org.junit.runner.JUnitCore',*tests]
        commands.append(list(map(str,command)))
        with (out/'junit.txt').open('w') as log:
            tested=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT).returncode
    unchanged=hashes=={str(path):sha(path) for path in [args.base_jar,*deps]}
    status={'compileExit':compiled.returncode,'junitExit':tested,'dependenciesUnchanged':unchanged,
            'seconds':round(time.monotonic()-started,3)}
    (out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    (out/'status.json').write_text(json.dumps(status,indent=2)+'\n')
    shutil.rmtree(classes,ignore_errors=True)
    print(json.dumps(status))
    print((out/('junit.txt' if tested is not None else 'compile.txt')).read_text())
    return compiled.returncode or tested or (0 if unchanged else 2)

if __name__=='__main__':
    sys.exit(main())
