package at.heinz.deviceai

/** An owner-reviewed draft grants no inbox or send permission. */
internal object WhatsAppDraftPolicy {
    const val TARGET_PACKAGE = "com.whatsapp"
    const val MAX_CHARACTERS = 2_000

    fun validated(raw: String): String? {
        if (raw.length !in 1..MAX_CHARACTERS || raw.count { it == '\n' } > 30) return null
        if (raw.any {
                (it.isISOControl() && it != '\n') ||
                    it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069'
            }) return null
        return raw.trim().takeIf { it.isNotEmpty() }
    }
}
