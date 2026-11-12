"""Actual owned GLB assets; host Cycles, never device evidence.

blender -b -t 2 --python tools/cockpit-workstation/render_host.py -- ROOT OUT
OUT must contain original-avatar-focus.json and original-orb-source.png,
exported by HostSceneSources from the original production Kotlin sources.
"""
import bpy, json, math, sys, hashlib
from pathlib import Path
from mathutils import Vector
ROOT, OUT = map(Path, sys.argv[sys.argv.index('--')+1:])
OUT.mkdir(parents=True, exist_ok=True)
expected_sources = {
 'app/src/main/java/at/heinz/deviceai/ui/CinematicWorkstationLayout.kt':
 '311bef3070a55c8decc6cefa842b423460900ffdf86ede2b1208821bd439fa1c',
 'app/src/main/java/at/heinz/deviceai/avatar3d/AvatarFraming.kt':
 'd31f824f633e2e24676f1fa05b409f1bc8b8dd101c4a24c8df631c6b53a584e6'
}
for path, expected in expected_sources.items():
 if hashlib.sha256((ROOT/path).read_bytes()).hexdigest() != expected:
  raise RuntimeError('Production geometry changed; revalidate mirrored host camera/layout: '+path)
focus = json.loads((OUT/'original-avatar-focus.json').read_text())
avatar = ROOT/'app/src/main/assets/avatar3d/continuum-avatar.glb'
room = ROOT/'app/src/main/assets/cinema3d/cockpit-workstation.glb'
assert hashlib.sha256(avatar.read_bytes()).hexdigest()==focus['assetSha256']
records=[]

def scene(width, height, transparent=True):
 bpy.ops.wm.read_factory_settings(use_empty=True)
 s=bpy.context.scene;s.render.engine='CYCLES';s.cycles.device='CPU';s.cycles.samples=16;s.cycles.use_denoising=False
 s.render.threads_mode='FIXED';s.render.threads=2
 s.render.resolution_x=round(width);s.render.resolution_y=round(height);s.render.resolution_percentage=100
 s.render.image_settings.file_format='PNG';s.render.image_settings.color_mode='RGBA';s.render.film_transparent=transparent
 s.world=bpy.data.worlds.new('HostStageWorld');s.world.use_nodes=True
 s.world.node_tree.nodes['Background'].inputs[0].default_value=(.01,.02,.05,1)
 s.world.node_tree.nodes['Background'].inputs[1].default_value=.2
 s.view_settings.view_transform='Standard';s.view_settings.look='Medium High Contrast'
 return s

def camera(s, target, eye, kind='PERSP', ortho=None, lens=None):
 data=bpy.data.cameras.new('SharedProjection');data.type=kind;data.clip_end=10000
 if ortho:data.ortho_scale=ortho
 if lens:data.lens=lens;data.sensor_fit='HORIZONTAL';data.sensor_width=36
 obj=bpy.data.objects.new('SharedProjection',data);s.collection.objects.link(obj);obj.location=Vector(eye)
 obj.rotation_euler=(Vector(target)-obj.location).to_track_quat('-Z','Y').to_euler();s.camera=obj
 return obj

def sun(s):
 data=bpy.data.lights.new('DirectionalKey','SUN');data.energy=3.;data.color=(.62,.8,1)
 obj=bpy.data.objects.new('DirectionalKey',data);s.collection.objects.link(obj)
 obj.rotation_euler=(.45,-.30,-.35)

def layout(w,h):
 wide=w>=600; margin=16 if wide else 12; roomw=w-margin*2
 dock=76 if roomw>=576 else 132;dialog=0 if wide else 48
 sh=max(310 if wide else 320,h-(52+56+dock+48+dialog+48))
 hw=min(roomw*(.82 if wide else .98),sh*1.65);hh=sh*.7;os=min(hw*.96,hh*.78)*.76
 mw=roomw*.64 if wide else roomw
 return {'width':roomw,'height':sh,'head':[(roomw-hw)/2,0,hw,hh],
         'orb':[(roomw-os)/2,hh*.035*.76,os,os],'monitor':[(roomw-mw)/2,sh*.54,mw,sh*.46-8],
         'fullWidth':w,'fullHeight':h,'margin':margin}

for name,w,h in [('fold',884,760),('phone',412,915)]:
 g=layout(w,h);sw,sh=g['width'],g['height'];ml,mt,mw,mh=g['monitor'];d=max(sw,sh)*2.4
 s=scene(sw*2,sh*2)
 bpy.ops.import_scene.gltf(filepath=str(room))
 bpy.data.objects['cockpit-room'].scale=(sw,min(sw,sh),sh)
 mon=bpy.data.objects['cockpit-monitor'];mon.location=(ml+mw/2-sw/2,0,sh/2-mt-mh/2)
 mon.rotation_euler[2]=math.radians(-3);mon.scale=(mw,min(mw,mh),mh)
 camera(s,(0,0,0),(0,-d,0),lens=d*36/sw);sun(s)
 s.render.filepath=str(OUT/f'{name}-room.png');bpy.ops.render.render(write_still=True)
 hl,ht,hw,hh=g['head'];aspect=hw/hh
 full=max(focus['halfHeight'],focus['halfWidth']/aspect)*1.08
 half=max(full*.92,focus['reachLeft']/aspect,focus['reachRight']/aspect)/.76
 center=focus['centerY']+full-half
 s=scene(hw*2,hh*2)
 bpy.ops.import_scene.gltf(filepath=str(avatar))
 for obj in bpy.data.objects:
  if obj.name.startswith('Tooth'):obj.hide_render=True
 camera(s,(focus['centerX'],-focus['centerZ'],center),(focus['centerX'],-focus['centerZ']-2,center),kind='ORTHO',ortho=2*half*max(1,aspect))
 # Ortho scale follows larger frame dimension.
 for label,pos,energy,color in [('key',(.35,-.60,.50),6,(.5,.82,1)),('violet',(-.38,-.38,.32),3,(.55,.24,1)),('core',(.32,.16,.35),2,(.18,.82,1))]:
  data=bpy.data.lights.new(label,'AREA');data.energy=energy;data.shape='DISK';data.size=.4;data.color=color
  obj=bpy.data.objects.new(label,data);s.collection.objects.link(obj);obj.location=Vector(pos)
  obj.rotation_euler=(Vector((0,-.03,.25))-obj.location).to_track_quat('-Z','Y').to_euler()
 s.render.filepath=str(OUT/f'{name}-avatar.png');bpy.ops.render.render(write_still=True)
 records.append({'name':name,'layout':g,'avatarProjectionHalfHeight':half,'avatarCameraCenterY':center})
(OUT/'host-render-geometry.json').write_text(json.dumps({'evidence':'HOST_CYCLES_ORIGINAL_ASSETS_NOT_ANDROID_FILAMENT_NOT_LIVE',
 'avatarSha256':hashlib.sha256(avatar.read_bytes()).hexdigest(),'roomSha256':hashlib.sha256(room.read_bytes()).hexdigest(),
 'lighting':'Host Cycles studio lights; Android Filament parity not asserted',
 'layoutSourceSha256':hashlib.sha256((ROOT/'app/src/main/java/at/heinz/deviceai/ui/CinematicWorkstationLayout.kt').read_bytes()).hexdigest(),
 'cameraScale':.76,'avatarOpacity':.62,'records':records},indent=2)+'\n')
print('HOST_GLBS_RENDERED',flush=True)
