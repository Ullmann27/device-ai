package at.heinz.deviceai.learning

import java.io.File

/** Unsupported event path fails immediately. */
@Suppress("UNUSED_PARAMETER")
class EventEmittingRetriever(delegate: ContinuumMemoryRetriever, appDirectory: File) : ContinuumMemoryRetriever by delegate {
    init { error("HOST_EVENT_PATH_NOT_AVAILABLE") }
}
