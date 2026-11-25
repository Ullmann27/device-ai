extends SceneTree
const Person = preload("res://scripts/visuals/village_person.gd")
func _initialize() -> void:
	_run.call_deferred()
func _run() -> void:
	var floor := StaticBody3D.new(); var col := CollisionShape3D.new(); var box := BoxShape3D.new(); box.size = Vector3(200,1,200); col.shape = box; floor.add_child(col); floor.position.y = -0.5; root.add_child(floor)
	for setup in [["companion","italia",12],["companion","germania",12]]:
		var actor := Person.new(); root.add_child(actor)
		actor.position = Vector3(0,.9,0)
		actor.configure(setup[0],setup[1],setup[2])
		await process_frame
		actor.set_process(false)
		print("MODEL ",actor.active_model_path)
		for scenario in [["run",3.6,0.0],["run",3.6,1.2],["march",1.45,0.0],["march->run",0.0,0.0]]:
			actor.reset_motion(); actor.position = Vector3(0,.9,0); actor.rotation = Vector3.ZERO
			var rig: Skeleton3D = actor._motion.skeleton
			var prev := {}; var worst := 0.0; var worst_ctx := ""
			for f in 240:
				var speed: float = scenario[1]
				var clip: String = scenario[0]
				if clip == "march->run":
					clip = "march" if f < 120 else "run"; speed = 1.45 if f < 120 else 3.6
				actor.drive(speed)
				if actor.clip != clip: actor._play(clip,0.0,0.15)
				var turn: float = scenario[2]
				actor.rotation.y += turn/60.0
				actor.position += -actor.global_transform.basis.z*speed/60.0
				actor._process(1.0/60.0)
				rig.force_update_all_bone_transforms()
				for name in ["Foot_L","Foot_R"]:
					var p: Vector3 = (rig.global_transform*rig.get_bone_global_pose(rig.find_bone(name))).origin
					if prev.has(name):
						# remove the body's own translation this frame
						var d: float = (p - prev[name] - (-actor.global_transform.basis.z*speed/60.0)).length()
						if d > worst:
							worst = d; worst_ctx = "f=%d %s clip=%s phase=%.3f supported=%s" % [f,name,actor.clip,actor.phase,str(actor.world_foot_supported(name.right(1)))]
					prev[name] = p
			print("JUMP ",scenario," worst_extra_per_frame=%.3f m @ %s" % [worst,worst_ctx])
		actor.free()
	quit(0)
