extends SceneTree
const Person = preload("res://scripts/visuals/village_person.gd")
const SkinMeasurement = preload("res://tests/motion_skin_measurement.gd")
class SourcePerson extends Person:
	var exact_source := ""
	func _path(_level: int) -> String:
		return exact_source
var PATHS: Array = []
func _initialize() -> void:
	for who in ["archer","civilian_man","civilian_woman","elder","legionary","officer","spearman"]:
		for lod in [0,2]:
			PATHS.append("assets/characters/frontier_%s_%d.glb" % [who,lod])
	_run.call_deferred()
func _run() -> void:
	for path in PATHS:
		var actor := SourcePerson.new()
		root.add_child(actor)
		actor.exact_source = "res://"+path
		actor.position = Vector3(0,.9,0)
		actor.set_process(false)
		actor._check_lod = 100000.0
		var identity: String = path.get_file().trim_prefix("frontier_").get_basename()
		identity = identity.substr(0,identity.length()-2)
		actor.configure_resident("italia",32,identity,0)
		actor._check_lod = 100000.0
		actor.set_process(false)
		await process_frame
		var skin := SkinMeasurement.new()
		skin.bind(actor.body)
		for action in ["idle","march"]:
			actor.position = Vector3(0,.9,0)
			actor.reset_motion()
			var speed := 1.45 if action=="march" else 0.0
			actor.drive(speed)
			actor._play(action,0.0,0.0)
			var sup := {"L":0,"R":0}; var cont := 0; var lows := []
			for f in 60:
				if action=="march": actor.position.z -= speed/60.0
				actor._process(1.0/60.0)
				var o: Dictionary = skin.sample()
				for s in ["L","R"]:
					if actor.world_foot_supported(s): sup[s]+=1
				cont += int(o.contact_points.size()>0)
				if f%15==0: lows.append(o.lowest_sole_m)
			print("DIAG ",path," ",action," supported=",sup," frames_with_contacts=",cont,"/60 lowest_sole_samples=",lows," world_init=",actor._motion._world_initialized," skin_errors=",skin.errors)
		actor.free()
	quit(0)
