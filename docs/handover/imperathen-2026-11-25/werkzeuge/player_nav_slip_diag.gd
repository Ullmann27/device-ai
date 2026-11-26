extends SceneTree
## Diagnostic only: the motion gate's Player walk/run/stop fixture
## (motion_world_measurement.gd, _navigation) with a per-frame log of clip,
## phase, LOD, speed, the planted-foot flags and the Foot_R/Foot_L world
## positions, to locate the 18.5 cm support slip of the youth player.
const World = preload("res://scripts/world/game_world.gd")
const Model = preload("res://scripts/domain/world_model.gd")
const DT := 1.0 / 60.0


func _initialize() -> void:
	_run.call_deferred()


func _run() -> void:
	var model := Model.new()
	model.new_game("italia", true, true)
	var world := World.new()
	root.add_child(world)
	world.setup(model.data)
	world.set_active(true)
	world.set_physics_process(false)
	world.village_life_world.set_physics_process(false)
	for figure: Node in world.find_children("*", "Node3D", true, false):
		if figure.has_method("drive") and figure.has_method("sample_pose"):
			figure.set_process(false)
	await physics_frame
	await physics_frame
	world._player.position = Vector3(-5, .91, -62)
	world._player.velocity = Vector3.ZERO
	world._figure.set_process(false)
	var anchors := {}
	for frame in 240:
		world.set_running(frame >= 80 and frame < 160)
		world.set_touch_move(Vector2(1, 0) if frame < 160 else Vector2.ZERO)
		world._move_player(DT)
		world._animate_figure()
		world._figure._process(DT)
		var figure: Node3D = world._figure
		var rig: Skeleton3D = figure.find_children("*", "Skeleton3D", true, false)[0]
		rig.force_update_all_bone_transforms()
		var phase := 0.0
		if figure.animator != null and figure.animator.current_animation_length > 0:
			phase = figure.animator.current_animation_position / figure.animator.current_animation_length
		var line := "NAV %d clip=%s phase=%.3f speed=%.2f model=%s" % [frame, figure.clip, phase, figure.speed, str(figure.active_model_path).get_file()]
		for side: String in ["L", "R"]:
			var bone := rig.find_bone("Foot_" + side)
			var foot: Vector3 = (rig.global_transform * rig.get_bone_global_pose(bone)).origin
			var supported: bool = figure.world_foot_supported(side)
			var slip := 0.0
			if supported:
				if not anchors.has(side):
					anchors[side] = foot
				slip = Vector2(foot.x - anchors[side].x, foot.z - anchors[side].z).length()
			else:
				anchors.erase(side)
			line += " %s[sup=%s y=%.3f slip=%.3f]" % [side, supported, foot.y, slip]
		print(line)
	quit(0)
