package at.heinz.deviceai.learning

import java.io.File

/** Host-only compilation boundary: event emission is outside the tested retriever. */
@Suppress("UNUSED_PARAMETER")
class EventEmittingRetriever(private val delegate:ContinuumMemoryRetriever, appDirectory:File):ContinuumMemoryRetriever by delegate
