extends SceneTree
## Evidence only: right hand / hip membrane of the Meshy axe fighter in Godot 4.7.2.
const SUBJECTS := ["res://assets/meshy/characters/unit_germanic_axe_fighter_0.glb", "res://assets/meshy/characters/unit_germanic_axe_fighter_1.glb"]
const POSES := [["attack", 0.265], ["train", 0.283], ["block", 0.5], ["hit", 0.5], ["work", 0.5], ["gather", 0.5], ["run", 0.25], ["idle", 0.0]]
const VIEWS := {"right": [Vector3(-3.2, 1.2, .3), Vector3(0, .95, 0)], "back": [Vector3(-1.0, 1.3, -3.2), Vector3(-.1, .95, 0)], "front": [Vector3(-1.0, 1.3, 3.2), Vector3(-.1, .95, 0)]}


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
				var name := "%s_%s_%s_%s.png" % [path.get_file().get_basename(), pose[0], view, tag]
				root.get_texture().get_image().save_png(folder.path_join(name))
				done.append(name)
		model.free()
	print("GODOT_MESHY_HAND_CAPTURE " + JSON.stringify(done))
	quit(0)
