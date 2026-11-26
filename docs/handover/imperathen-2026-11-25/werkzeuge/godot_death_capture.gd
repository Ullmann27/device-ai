extends SceneTree
## Evidence only: the exported death clip of real company-skeleton GLBs in
## Godot 4.7.2 (GL Compatibility), side view over an opaque floor at the
## standing sole height. Run once on the tree before and once after the
## death grounding; frames are compared side by side.
const SUBJECTS := [
	["res://assets/village_characters/villager_0.glb", 0.0],
	["res://assets/soldiers/heavy_near.glb", 0.011],
	["res://assets/characters/frontier_civilian_man_0.glb", 0.0],
	["res://assets/meshy/characters/unit_roman_signifer_0.glb", 0.0],
]
const PHASES := [0.0, 0.35, 0.55, 0.75, 1.0]


func _initialize() -> void:
	_run.call_deferred()


func _run() -> void:
	var folder := OS.get_environment("IMPERATHEN_EVIDENCE_OUTPUT")
	var tag := OS.get_environment("IMPERATHEN_EVIDENCE_TAG")
	root.size = Vector2i(960, 640)
	var env := WorldEnvironment.new()
	env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR
	env.environment.background_color = Color(.78, .8, .84)
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color(.55, .55, .6)
	root.add_child(env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-50, 60, 0)
	sun.shadow_enabled = true
	root.add_child(sun)
	var floor := MeshInstance3D.new()
	var plane := PlaneMesh.new()
	plane.size = Vector2(8, 8)
	floor.mesh = plane
	var material := StandardMaterial3D.new()
	material.albedo_color = Color(.5, .52, .55)
	floor.material_override = material
	root.add_child(floor)
	var camera := Camera3D.new()
	camera.fov = 42
	root.add_child(camera)
	camera.current = true
	camera.position = Vector3(3.4, .75, -.15)
	camera.look_at(Vector3(0, .45, -.15))
	var results := []
	for subject in SUBJECTS:
		var path: String = subject[0]
		floor.position.y = float(subject[1])
		var model := (load(path) as PackedScene).instantiate() as Node3D
		root.add_child(model)
		var players := model.find_children("*", "AnimationPlayer", true, false)
		var player := players[0] as AnimationPlayer
		player.play("death")
		player.pause()
		var length := player.current_animation_length
		var index := 0
		for phase in PHASES:
			player.seek(length * phase, true)
			for i in 4:
				await RenderingServer.frame_post_draw
			var name := "%s_%s_%d.png" % [path.get_file().get_basename(), tag, index]
			root.get_texture().get_image().save_png(folder.path_join(name))
			index += 1
		results.append({"model": path, "floor_y": subject[1], "length_s": length})
		model.free()
	print("GODOT_DEATH_CAPTURE " + JSON.stringify(results))
	quit(0)
