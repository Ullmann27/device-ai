#!/usr/bin/env python3
"""Run native JVM cockpit contracts."""
from pathlib import Path
import argparse,sys,subprocess,os
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--kotlin-lib-dir',type=Path,required=True)
parser.add_argument('--android-jar',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--baseline')
args=parser.parse_args()
root=Path(__file__).resolve().parents[2];ui=root/'app/src/main/java/at/heinz/deviceai/ui';main=ui.parent
sys.path.insert(0,str(root/'tools/cockpit-workstation'))
from preview import UI_SOURCES
baseline=args.baseline
out=args.output.resolve();out.mkdir(parents=True,exist_ok=False)
sources=[ui/f'{n}.kt' for n in UI_SOURCES+['JarvisComputePhasePolicy','AvatarEssentialMotionPolicy']]
for folder,names in [('motion','DampedSpring Easings MotionPresets MotionContracts Staggers FaceMotion'),('cockpit','CockpitAnimationEvent CockpitContracts CockpitTimeline'),('avatar','AvatarContract AvatarSentenceAccentPolicy'),('avatar3d','CockpitStageFrame')]:
 sources += [main/folder/f'{n}.kt' for n in names.split()]
text=(ui/'JarvisCockpitGridContent.kt').read_text(); members=text[text.index('    fun wrap('):];fit=text[text.index('    private fun fit('):text.index('    private val CLOCK_STYLE')]
boundary=out/'HostTileMembers.kt';boundary.write_text('package at.heinz.deviceai.ui\nimport at.heinz.deviceai.ui.JarvisShellLayout.Box\nimport at.heinz.deviceai.ui.JarvisDesign.Colors\nimport at.heinz.deviceai.ui.JarvisDesign.Type\nobject JarvisCockpitGridContent {\n'+fit+members)
stop=next(l for l in (ui/'JarvisCockpitPanels.kt').read_text().splitlines() if 'const val STOP_TARGET =' in l)
const=out/'HostStopConstant.kt';const.write_text('package at.heinz.deviceai.ui\nobject JarvisCockpitPanels {\n'+stop+'\n}\n')
tests=['LuxuryHoloButtonRendererTest','JarvisThermalVisualContractTest']
sources += [root/f'app/src/test/java/at/heinz/deviceai/ui/{n}.kt' for n in tests]
sources += [boundary,const]
if baseline:
 for name in ['CockpitGlassButtons','JarvisTiles']:
  target=out/(name+'.kt');target.write_bytes(subprocess.check_output(['git','show',baseline+':app/src/main/java/at/heinz/deviceai/ui/'+name+'.kt'],cwd=root))
  sources=[target if p==ui/(name+'.kt') else p for p in sources]
libs=args.kotlin_lib_dir.resolve();cp=os.pathsep.join(str(p) for p in [*libs.glob('*.jar'),args.android_jar.resolve()])
cmd=['java','-Xmx768m','-cp',str(libs/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-nowarn','-cp',cp,'-d',str(out/'tests.jar')]+list(map(str,sources))
with (out/'compile.log').open('w') as log:r=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT)
if r.returncode: print((out/'compile.log').read_text());sys.exit(r.returncode)
cmd=['java','-cp',str(out/'tests.jar')+os.pathsep+cp,'org.junit.runner.JUnitCore']+['at.heinz.deviceai.ui.'+n for n in tests]
r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(out/'junit.txt').write_text(r.stdout);print(r.stdout);sys.exit(r.returncode)
