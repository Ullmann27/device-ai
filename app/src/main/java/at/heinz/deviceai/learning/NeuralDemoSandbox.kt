package at.heinz.deviceai.learning

/**
 * Runs the legacy synthetic Neural Demo only on an independently initialized
 * temporary dialogue model. No existing snapshot or owner lessons are passed
 * into this function, and the temporary model is never returned for saving.
 */
object NeuralDemoSandbox {
    fun run(
        cancelled: () -> Boolean = { false },
        onProgress: (NeuralLearningProgress) -> Unit = {}
    ): NeuralDialogueReport {
        val disposableModel = NeuralDialogue()
        check(disposableModel.lessonCount() == 0)
        return disposableModel.trainDemo(
            approved = true,
            cancelled = cancelled,
            onProgress = onProgress
        )
    }
}
