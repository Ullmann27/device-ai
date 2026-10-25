package at.heinz.deviceai.localmodel

/**
 * Published model file metadata, never a substitute for source authentication.
 * The import catalog computes SHA-256 over the actual selected model bytes and
 * refuses modified or incomplete files. Import remains explicitly user-initiated.
 */
internal object LocalModelImportPreset {
    const val GEMMA4_E4B_ID = "gemma4-e4b-it"
    const val GEMMA4_E4B_VERSION = "official-0b2a8980"
    const val GEMMA4_E4B_SHA256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
}
