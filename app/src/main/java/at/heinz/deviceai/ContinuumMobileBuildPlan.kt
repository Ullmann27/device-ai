package at.heinz.deviceai

/** Android-first EXP-00: only pre-approved local builder operations may reach Termux. */
internal object ContinuumMobileBuildPlan {
    enum class Operation(val title: String, val cli: String) {
        DOCTOR("Werkzeuge prüfen", "doctor"),
        BUILD("APK auf dem Handy bauen", "build"),
        STATUS("Letzten Buildbericht anzeigen", "status"),
        PROJECT_CREATE("M4 · Eigene Notiz-App erzeugen", "create"),
        PROJECT_VERIFY("M4 · App-Quellen prüfen", "verify"),
        PROJECT_BUILD("M4 · Eigene Notiz-App bauen", "build"),
        PROJECT_STATUS("M4 · Gespeicherten Projektbeleg prüfen", "status")
    }

    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    const val TERMUX_ACTION = "com.termux.RUN_COMMAND"
    const val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val NODE_EXECUTABLE = "/data/data/com.termux/files/usr/bin/node"
    const val PROJECT_DIR = "/data/data/com.termux/files/home/device-ai"
    const val BUILDER_SCRIPT = "$PROJECT_DIR/scripts/continuum-mobile-builder.mjs"

    data class Command(val executable: String, val args: List<String>, val workDir: String)

    fun command(operation: Operation, project: ContinuumOwnedProject = ContinuumOwnedProject.NOTES): Command {
        if (operation == Operation.PROJECT_CREATE || operation == Operation.PROJECT_VERIFY ||
            operation == Operation.PROJECT_BUILD || operation == Operation.PROJECT_STATUS) {
            val args = mutableListOf("$PROJECT_DIR/scripts/continuum-owned-app.mjs", operation.cli)
            if (project != ContinuumOwnedProject.NOTES) args.addAll(listOf("--recipe", project.id))
            return Command(NODE_EXECUTABLE, args, PROJECT_DIR)
        }
        val args = mutableListOf(BUILDER_SCRIPT, operation.cli)
        if (operation == Operation.BUILD) {
            args.add("--project")
            args.add(PROJECT_DIR)
        }
        return Command(NODE_EXECUTABLE, args.toList(), PROJECT_DIR)
    }
}
