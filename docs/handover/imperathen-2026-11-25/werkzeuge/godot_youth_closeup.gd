extends SceneTree
## Evidence capture only (not part of the game): the real game Person with the
## original youth, sampled poses, in-game sole grounding, GL Compatibility frames.
const Person = preload("res://scripts/visuals/village_person.gd")
const SHOTS := [["idle",.25,"feet_front"],["march",.20,"feet_front"],["idle",.25,"body_front"],["jab_right",.62,"body_front"],["block",.50,"body_front"],["uppercut_right",.55,"body_front"],["idle",.25,"feet"],["idle",.25,"body"],["march",.20,"feet"],["march",.70,"feet"],["march",.20,"body"],["run",.30,"body"],["jab_right",.62,"body"],["block",.50,"body"]]

func _initialize() -> void:
	_run.call_deferred()

func _run() -> void:
	var folder := OS.get_environment("IMPERATHEN_EVIDENCE_OUTPUT")
	root.size = Vector2i(1280,720)
	var env := WorldEnvironment.new(); env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR
	env.environment.background_color = Color(.78,.8,.84)
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color(.55,.55,.6)
	root.add_child(env)
	var sun := DirectionalLight3D.new(); sun.rotation_degrees = Vector3(-50,35,0); sun.shadow_enabled = true; root.add_child(sun)
	var floor := MeshInstance3D.new(); var plane := PlaneMesh.new(); plane.size = Vector2(6,6); floor.mesh = plane
	var mat := StandardMaterial3D.new(); mat.albedo_color = Color(.55,.57,.6); floor.material_override = mat; root.add_child(floor)
	var cam := Camera3D.new(); root.add_child(cam); cam.current = true
	var actor := Person.new(); root.add_child(actor); actor.set_process(false)
	actor.configure("player","italia",12)
	var ok := actor.configure_meshy_youth(true)
	actor._reload(0); actor.set_process(false)
	var results := []
	for shot in SHOTS:
		actor.sample_pose(shot[0],shot[1])
		# The game parents the figure to a capsule body; here the stand-in
		# root is lifted by the measured lowest sole once, then the in-game
		# sole correction runs exactly as in village_person.gd.
		actor.position.y -= actor._youth_soles.measure_lowest()
		actor._youth_soles.apply_to_floor(0.0,shot[0],shot[1])
		if shot[2]=="feet_front":
			cam.position = Vector3(.45,.35,-.95); cam.look_at(Vector3(0,.06,-.05))
		elif shot[2]=="body_front":
			cam.position = Vector3(.9,1.0,-1.8); cam.look_at(Vector3(0,.72,0))
		elif shot[2]=="feet":
			cam.position = Vector3(.55,.35,.95); cam.look_at(Vector3(0,.06,.05))
		else:
			cam.position = Vector3(1.05,1.0,1.75); cam.look_at(Vector3(0,.72,0))
		for i in 6:
			await RenderingServer.frame_post_draw
		var image := root.get_texture().get_image()
		var name := "godot_youth_%s_%.2f_%s.png" % [shot[0],shot[1],shot[2]]
		var saved := image.save_png(folder.path_join(name)) == OK
		results.append({"file":name,"saved":saved,"lowest_sole_world_y":actor._youth_soles.measure_lowest(),"last_correction_m":actor._youth_soles.last_correction_m,"model":actor.active_model_path})
	print("GODOT_YOUTH_CLOSEUP "+JSON.stringify({"youth":ok,"renderer":RenderingServer.get_current_rendering_method(),"adapter":RenderingServer.get_video_adapter_name(),"shots":results}))
	quit(0)
