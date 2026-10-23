package at.heinz.deviceai

import at.heinz.deviceai.cognitive.DevelopmentBacklog
import at.heinz.deviceai.learning.StrictJson

/** The two reviewed v1 identities. A model cannot create a third command or permission set. */
internal enum class ContinuumOwnedProject(val id: String, val title: String, val packageName: String, val planSha256: String) {
    NOTES("m4-notes", "Notizen", "at.heinz.continuum.m4notes",
        "dd0bd6218c8e54ca5c8ff44129aabbf92273ca30206c9fadd9daf18e853faf20"),
    COUNTER("m4-counter", "Zähler", "at.heinz.continuum.m4counter",
        "96b616463a68d53fd08769e8cfe44b226b1f3410351c4ade979a53f9dc5fd89a");

    companion object {
        fun ofId(id: String?): ContinuumOwnedProject? = entries.firstOrNull { it.id == id }
    }
}

/** Typed Android view of the same hash-bound plan catalog consumed by Node/CCC1. */
internal data class ContinuumOwnedProjectPlan(
    val project: ContinuumOwnedProject,
    val minSdk: Int,
    val targetSdk: Int,
    val screenIds: List<String>,
    val permissions: List<String>,
    val storageKind: String,
    val testCases: List<String>,
    val artifact: String,
    val maxBuildMillis: Long,
    val maxHeapMiB: Int
) {
    companion object {
        fun readCatalog(text: String): List<ContinuumOwnedProjectPlan> {
            val root = StrictJson.parseObject(text)
            require(root.keys == setOf("schema", "version", "plans"))
            require(root["schema"] == "continuum-project-catalog" && root["version"] == 1L)
            val rawPlans = root["plans"] as? List<*> ?: throw IllegalArgumentException("Missing plans")
            require(rawPlans.size == 2)
            fun obj(value: Any?): Map<*, *> {
                require(value is Map<*, *>)
                return value
            }
            val plans = rawPlans.map { raw ->
                val value = obj(raw)
                val project = requireNotNull(ContinuumOwnedProject.ofId(value["id"] as? String))
                val digest = DevelopmentBacklog.sha256Of(DevelopmentBacklog.canonical(value).toByteArray(Charsets.UTF_8))
                require(digest == project.planSha256) { "Unapproved project plan" }
                val app = obj(value["app"])
                val limits = obj(value["limits"])
                ContinuumOwnedProjectPlan(project,
                    (app["minSdk"] as Long).toInt(), (app["targetSdk"] as Long).toInt(),
                    (value["screens"] as List<*>).map { obj(it)["id"] as String },
                    (value["permissions"] as List<*>).map { it as String },
                    obj(value["storage"])["kind"] as String,
                    (obj(value["tests"])["cases"] as List<*>).map { it as String },
                    obj(value["build"])["artifact"] as String,
                    limits["maxBuildMillis"] as Long, (limits["maxHeapMiB"] as Long).toInt())
            }
            require(plans.map { it.project } == ContinuumOwnedProject.entries)
            return plans
        }
    }
}
