package at.heinz.deviceai.learning
import java.io.File
/** Explicit host-only boundary; no event plumbing tested. */
@Suppress("UNUSED_PARAMETER")
class EventEmittingRetriever(private val delegate:ContinuumMemoryRetriever, appDirectory:File):ContinuumMemoryRetriever by delegate
