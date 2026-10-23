package at.heinz.continuum.m4notes

internal object NotePolicy {
    const val MAX_LENGTH = 4000
    fun normalized(input: String): String = input.trim().take(MAX_LENGTH)
}
