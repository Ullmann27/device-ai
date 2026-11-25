extends SceneTree
## Evidence only: real VillagePerson legionary resident, before/after the authored sole lift.
const Person = preload("res://scripts/visuals/village_person.gd")
func _initialize() -> void:
	_run.call_deferred()
func _run() -> void:
	var folder := OS.get_environment("IMPERATHEN_EVIDENCE_OUTPUT")
	root.size = Vector2i(1280,720)
	var env := WorldEnvironment.new(); env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR; env.environment.background_color = Color(.78,.8,.84)
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR; env.environment.ambient_light_color = Color(.55,.55,.6)
	root.add_child(env)
	var sun := DirectionalLight3D.new(); sun.rotation_degrees = Vector3(-55,30,0); sun.shadow_enabled = true; root.add_child(sun)
	var floor := MeshInstance3D.new(); var plane := PlaneMesh.new(); plane.size = Vector2(6,6); floor.mesh = plane
	var mat := StandardMaterial3D.new(); mat.albedo_color = Color(.55,.57,.6); floor.material_override = mat; root.add_child(floor)
	var cam := Camera3D.new(); root.add_child(cam); cam.current = true
	var results := []
	for who in ["legionary","civilian_man"]:
		var actor := Person.new(); root.add_child(actor); actor.set_process(false)
		actor.position = Vector3(0,.9,0)
		actor.configure_resident("italia",32,who,0)
		actor._reload(0); actor.set_process(false)
		actor.sample_pose("idle",.25)
		for variant in ["nachher","vorher"]:
			if variant=="vorher":
				actor.body.position.y = -0.9   # exactly the previous placement
			for view in ["feet","body"]:
				if view=="feet":
					cam.position = Vector3(.55,.18,-.85); cam.look_at(Vector3(0,.07,0))
				else:
					cam.position = Vector3(1.2,1.0,-2.2); cam.look_at(Vector3(0,.85,0))
				for i in 6: await RenderingServer.frame_post_draw
				var name := "godot_%s_%s_%s.png" % [who,variant,view]
				root.get_texture().get_image().save_png(folder.path_join(name))
			results.append({"who":who,"variant":variant,"body_y":actor.body.position.y,"model":actor.active_model_path})
		actor.free()
	print("GODOT_LEGIONARY "+JSON.stringify(results))
	quit(0)
