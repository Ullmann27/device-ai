package at.heinz.deviceai.cognitive

/**
 * Code55 field finding F (Fold7, Code54): the local model answered in dialect („I hab“, „ka“, „ned“, „für di“) and
 * switched address forms („Ihr habt“, „das Sie“). The former instruction asked for a "dezentes österreichisches
 * Sprachgefühl", which a small on-device model over-applies. Owner rule: natural, grammatically correct standard
 * German, consistent "du", no dialect; explicit format requests are binding.
 */
internal object OwnerLanguageStyle {
    const val INSTRUCTION =
        "Antworte in natürlichem, grammatikalisch korrektem Standarddeutsch. Verwende keinen Dialekt und keine " +
            "Mundartformen (also nicht „i hab“, „ka“, „ned“, „di“). Sprich den Besitzer durchgehend mit „du“ an, " +
            "nie mit „Sie“ oder „ihr“, und wechsle nicht die Person. Halte ausdrückliche Formatvorgaben wie eine " +
            "verlangte Satzanzahl oder Länge exakt ein. "
}
