package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.BoundedResearchTextSanitizer
import at.heinz.deviceai.cognitive.ConstrainedResourceFabric

/** Kurzformen fuer `internal`-Objekte desselben Moduls (keine Produktivcode-Aenderung noetig). */
internal fun sanitizeExternal(text: String): String = BoundedResearchTextSanitizer.sanitize(text, 4_000)

internal typealias ResourcePlan = ConstrainedResourceFabric.Plan

internal fun resourcePlan(freeRam: Long?, peak: Long, model: Long, thermal: Int?): ResourcePlan =
    ConstrainedResourceFabric.plan(ConstrainedResourceFabric.Snapshot(freeRam, peak, model, thermal))
