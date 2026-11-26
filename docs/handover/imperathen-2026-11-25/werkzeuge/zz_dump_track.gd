extends SceneTree
func _initialize() -> void:
	for path in ["res://assets/meshy/characters/unit_roman_signifer_0.glb", "res://assets/soldiers/heavy_mid.glb"]:
		var model := (load(path) as PackedScene).instantiate()
		var player := model.find_children("*", "AnimationPlayer", true, false)[0] as AnimationPlayer
		var anim := player.get_animation("death")
		for t in anim.get_track_count():
			var p := str(anim.track_get_path(t))
			if p.ends_with(":Pelvis") and anim.track_get_type(t) == Animation.TYPE_POSITION_3D:
				var n := anim.track_get_key_count(t)
				var out := []
				for time in [0.0, 0.5667, 0.575, 0.5833, 0.5918, 0.6, 1.2]:
					out.append([time, anim.position_track_interpolate(t, time)])
				print("TRACK ", path, " keys=", n, " first=", anim.track_get_key_time(t, 0), " second=", anim.track_get_key_time(t, min(1, n - 1)), " last=", anim.track_get_key_time(t, n - 1), " interp=", anim.track_get_interpolation_type(t), " ", out)
			if p.ends_with(":Pelvis") and anim.track_get_type(t) == Animation.TYPE_ROTATION_3D:
				print("ROT ", path, " keys=", anim.track_get_key_count(t))
		model.free()
	quit(0)
