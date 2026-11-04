package at.heinz.deviceai.learning

import java.io.File

// Legacy scheduler lacked this boundary.
@Suppress("UNUSED_PARAMETER")
internal fun ContinuumSourceNovelty.markStarted(base:File,sources:List<MentorSource>) = Unit
