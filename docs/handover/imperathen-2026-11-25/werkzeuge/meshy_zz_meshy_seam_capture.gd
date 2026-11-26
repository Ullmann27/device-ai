extends SceneTree
## Evidence only: right hand / hip membrane of the Meshy axe fighter in Godot 4.7.2.
const SUBJECTS := ["res://assets/meshy/characters/unit_germanic_axe_fighter_0.glb"]
const POSES := [["attack", 0.265], ["train", 0.283], ["attack", 0.35], ["block", 0.5]]
const VIEWS := {"rechts": [Vector3(-1.45, 1.05, .35), Vector3(-.3, .95, .05)], "rechts_hinten": [Vector3(-1.15, 1.15, -1.05), Vector3(-.3, .95, 0)], "vorne": [Vector3(-.55, 1.1, 1.5), Vector3(-.25, .95, 0)]}


func _initialize() -> void:
	_run.call_deferred()


func _run() -> void:
	var folder := OS.get_environment("IMPERATHEN_EVIDENCE_OUTPUT")
	var tag := OS.get_environment("IMPERATHEN_EVIDENCE_TAG")
	root.size = Vector2i(640, 800)
	var env := WorldEnvironment.new()
	env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR
	env.environment.background_color = Color(.78, .8, .84)
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color(.6, .6, .65)
	root.add_child(env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-40, 25, 0)
	root.add_child(sun)
	var camera := Camera3D.new()
	camera.fov = 38
	root.add_child(camera)
	camera.current = true
	var done := []
	for path in SUBJECTS:
		var model := (load(path) as PackedScene).instantiate() as Node3D
		root.add_child(model)
		var player := model.find_children("*", "AnimationPlayer", true, false)[0] as AnimationPlayer
		for pose in POSES:
			if not player.has_animation(pose[0]):
				continue
			player.play(pose[0])
			player.pause()
			player.seek(player.current_animation_length * float(pose[1]), true)
			for view in VIEWS:
				camera.position = VIEWS[view][0]
				camera.look_at(VIEWS[view][1])
				for i in 4:
					await RenderingServer.frame_post_draw
				var name := "%s_%s_%03d_%s_%s.png" % [path.get_file().get_basename(), pose[0], int(float(pose[1]) * 1000), view, tag]
				root.get_texture().get_image().save_png(folder.path_join(name))
				done.append(name)
		model.free()
	print("GODOT_MESHY_HAND_CAPTURE " + JSON.stringify(done))
	quit(0)
