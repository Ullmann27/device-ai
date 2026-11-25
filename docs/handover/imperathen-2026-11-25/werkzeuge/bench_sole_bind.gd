extends SceneTree
const YouthSoleContact := preload("res://scripts/visuals/youth_sole_contact.gd")
func _initialize() -> void:
	_run.call_deferred()

func _run() -> void:
	await process_frame
	for level in 4:
		var scene: PackedScene = load("res://assets/characters/hero_meshy_italia_%d.glb"%level)
		var model: Node3D = scene.instantiate()
		root.add_child(model)
		await process_frame
		var soles := YouthSoleContact.new()
		var t0 := Time.get_ticks_usec()
		for i in 5:
			soles.bind(model,level)
		var bind_ms := float(Time.get_ticks_usec()-t0)/5000.0
		var t1 := Time.get_ticks_usec()
		for i in 300:
			soles.measure_lowest()
		var measure_ms := float(Time.get_ticks_usec()-t1)/300000.0
		var slab := 0
		if "slab_sample_count" in soles: slab = soles.slab_sample_count
		print("SOLE_BENCH lod=%d samples=%d slab=%d bind_ms=%.2f measure_ms=%.3f"%[level,soles.samples.size(),slab,bind_ms,measure_ms])
		model.queue_free()
	quit()
