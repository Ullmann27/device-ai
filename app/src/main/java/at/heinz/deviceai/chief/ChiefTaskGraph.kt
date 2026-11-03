package at.heinz.deviceai.chief

/** A pure, deterministic plan: it never executes tools, grants rights or declares a patch verified. */
enum class ChiefTaskKind { READ_ONLY, CODE_CHANGE, VERIFICATION }

data class ChiefPlannedTask(
    val delegation: ChiefDelegation,
    val kind: ChiefTaskKind,
    val readPaths: Set<String> = emptySet(),
    val writePaths: Set<String> = emptySet(),
    val dependsOn: Set<String> = emptySet(),
    val verifierTaskId: String? = null
) {
    init {
        require(readPaths.size <= 32 && writePaths.size <= 32)
        require((readPaths + writePaths).all(::validTaskPath)) { "Unsafe or ambiguous repository path." }
        require(dependsOn.size <= 64 && dependsOn.all(ChiefSpecialist.ID::matches))
        require(verifierTaskId == null || ChiefSpecialist.ID.matches(verifierTaskId))
        when (kind) {
            ChiefTaskKind.READ_ONLY -> require(writePaths.isEmpty() && verifierTaskId == null)
            ChiefTaskKind.CODE_CHANGE -> require(writePaths.isNotEmpty() && verifierTaskId != null)
            ChiefTaskKind.VERIFICATION -> require(writePaths.isEmpty() && verifierTaskId == null)
        }
    }
}

private fun validTaskPath(path: String): Boolean {
    if (path.isEmpty() || path.length > 240 || path.contains('\\') || path.contains(':') || path.startsWith('/')) return false
    return path.split('/').all { part ->
        part.isNotEmpty() && part != "." && part != ".." && part.length <= 100 &&
            part.all { c -> (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c in "._-" }
    }
}

/** Segment-aware overlap: src/foo must not conflict with src/foobar, but src/foo/bar must. */
private fun pathsOverlap(a: String, b: String): Boolean =
    a == b || a.startsWith("$b/") || b.startsWith("$a/")

private fun sharesPath(a: Set<String>, b: Set<String>): Boolean =
    a.any { left -> b.any { right -> pathsOverlap(left, right) } }

/**
 * Immutable, cycle-free job graph. 'acceptedResults' are IDs supplied by the TRUSTED job-result verifier;
 * model output and WorkerStatus.DONE alone must never populate them. A plan does not authorize execution.
 */
class ChiefTaskGraph private constructor(
    val tasks: List<ChiefPlannedTask>,
    val prerequisites: Map<String, Set<String>>
) {
    private val ids = tasks.map { it.delegation.taskId }.toSet()

    fun ready(acceptedResults: Set<String>, running: Set<String> = emptySet()): List<ChiefPlannedTask> {
        require(acceptedResults.all(ids::contains) && running.all(ids::contains))
        require(acceptedResults.intersect(running).isEmpty())
        return tasks.filter { task ->
            val id = task.delegation.taskId
            id !in acceptedResults && id !in running && prerequisites.getValue(id).all(acceptedResults::contains)
        }
    }

    fun releaseReady(acceptedResults: Set<String>): Boolean {
        require(acceptedResults.all(ids::contains))
        return ids.all(acceptedResults::contains)
    }

    companion object {
        /** One bounded plan. All inter-task file access conflicts are serialized in input order. */
        fun compile(input: List<ChiefPlannedTask>): ChiefTaskGraph {
            require(input.isNotEmpty() && input.size <= 64)
            val byId = input.associateBy { it.delegation.taskId }
            require(byId.size == input.size) { "Duplicate task IDs." }
            val dependencies = input.associate { task ->
                task.delegation.taskId to task.dependsOn.toMutableSet()
            }
            for (task in input) {
                val id = task.delegation.taskId
                require(dependencies.getValue(id).all { it in byId && it != id }) { "Unknown/self dependency." }
                if (task.kind == ChiefTaskKind.CODE_CHANGE) {
                    val verifier = byId[task.verifierTaskId] ?: throw IllegalArgumentException("Missing independent verifier task.")
                    require(verifier.kind == ChiefTaskKind.VERIFICATION)
                    require(verifier.delegation.capability != task.delegation.capability) {
                        "A coder and verifier must request different specialist capabilities."
                    }
                    require(task.writePaths.all { changed ->
                        verifier.readPaths.any { inspected -> changed == inspected || changed.startsWith("$inspected/") }
                    }) { "Verifier must cover every changed path." }
                    dependencies.getValue(verifier.delegation.taskId).add(id)
                }
            }
            for (i in input.indices) {
                val early = input[i]
                for (j in i + 1 until input.size) {
                    val late = input[j]
                    val conflict = sharesPath(early.writePaths, late.readPaths + late.writePaths) ||
                        sharesPath(late.writePaths, early.readPaths)
                    if (conflict) dependencies.getValue(late.delegation.taskId).add(early.delegation.taskId)
                }
            }
            // Kahn's algorithm: reject explicit cycles, including cycles caused by conflicting path leases.
            val remaining = dependencies.mapValues { (_, deps) -> deps.toMutableSet() }.toMutableMap()
            val visited = HashSet<String>()
            while (visited.size < input.size) {
                val ready = input.map { it.delegation.taskId }.filter { it !in visited && remaining.getValue(it).isEmpty() }
                require(ready.isNotEmpty()) { "Cyclic or contradictory task dependencies." }
                visited.addAll(ready)
                remaining.values.forEach { it.removeAll(ready.toSet()) }
            }
            return ChiefTaskGraph(input.toList(), dependencies.mapValues { (_, deps) -> deps.toSet() })
        }
    }
}
