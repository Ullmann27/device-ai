package at.heinz.deviceai

/** Android device adapter, kept separate from the platform-independent tool contracts. */
class DeviceToolExecutorRunner(private val executor: DeviceToolExecutor) : ToolRunner {
    override fun execute(request: ToolCallRequest): String {
        val completed = java.util.concurrent.CountDownLatch(1)
        var output: String? = null
        executor.execute(request) {
            output = it
            completed.countDown()
        }
        completed.await()
        return output.orEmpty()
    }
}
