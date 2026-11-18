package at.heinz.deviceai.localmodel

import android.content.Context

/**
 * Android storage of the owner switch „Gespräch im Modell behalten“ (see [LocalDialogueReuse]). Off unless the
 * owner switched it on; an unreadable preference means off.
 */
object LocalDialogueReuseHost {
    fun enabled(context: Context): Boolean = runCatching {
        context.applicationContext.getSharedPreferences(LocalDialogueReuse.PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(LocalDialogueReuse.KEY_ENABLED, false)
    }.getOrDefault(false)

    /** Returns false when the choice could not be stored; the switch then keeps its previous position. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean = runCatching {
        context.applicationContext.getSharedPreferences(LocalDialogueReuse.PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(LocalDialogueReuse.KEY_ENABLED, enabled).commit()
    }.getOrDefault(false)
}
