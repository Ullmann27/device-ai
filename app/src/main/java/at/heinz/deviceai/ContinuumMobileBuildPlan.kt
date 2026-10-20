package at.heinz.deviceai

/** Android-first EXP-00: only pre-approved local builder operations may reach Termux. */
internal object ContinuumMobileBuildPlan {
    enum class Operation(val title: String, val cli: String) {
        DOCTOR("Werkzeuge prüfen", "doctor"),
        BUILD("APK auf dem Handy bauen", "build"),
        STATUS("Letzten Buildbericht anzeigen", "status")
    }

    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    const val TERMUX_ACTION = "com.termux.RUN_COMMAND"
    const val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val NODE_EXECUTABLE = "/data/data/com.termux/files/usr/bin/node"
    const val PROJECT_DIR = "/data/data/com.termux/files/home/device-ai"
    const val BUILDER_SCRIPT = "$PROJECT_DIR/scripts/continuum-mobile-builder.mjs"

    data class Command(val executable: String, val args: List<String>, val workDir: String)

    fun command(operation: Operation): Command {
        val args = mutableListOf(BUILDER_SCRIPT, operation.cli)
        if (operation == Operation.BUILD) {
            args.add("--project")
            args.add(PROJECT_DIR)
        }
        return Command(NODE_EXECUTABLE, args.toList(), PROJECT_DIR)
    }
}
