package at.heinz.deviceai.cognitive

/**
 * Which accessibility content events may advance the live observation revision (Code56 field fix).
 *
 * Field failure 13.11.2026 (Fold7, split screen, „Öffne Downloads“): the monitor never became LIVE and showed
 * „Verdeckt · Bildquelle nicht bestätigt“. Cause: Continuum's own monitor label sets a new contentDescription on every
 * state change; Android reports that as TYPE_WINDOW_CONTENT_CHANGED from Continuum's package; the bus counted it as a
 * new observation; the projection service then rejected the frame it was copying because the revision changed, paused
 * the monitor, which changed the label again – a self-sustaining loop. Actions were blocked the same way, because a
 * click must bind to the frame's exact observation revision.
 *
 * Android stamps the sender package on every event and AccessibilityManagerService replaces a package the calling UID
 * does not own, so a well-formed sender equal to Continuum's own package really comes from Continuum. Its content
 * changes cannot alter a foreign app's nodes or pixels (the projection crop excludes Continuum's window). Window
 * structure events (state/windows changed) are NOT covered here and still advance every revision.
 */
internal object LiveObservationEventPolicy {
    /**
     * True when a content event is Continuum's own UI and no read currently binds Continuum itself, so it must not
     * invalidate observations of the foreign target app.
     */
    fun ignoreOwnContent(sender: String?, ownPackage: String, readingPackage: String?): Boolean =
        sender != null && ownPackage.isNotBlank() && sender == ownPackage && readingPackage != ownPackage
}
