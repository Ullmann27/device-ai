package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.LocalModelThermalGuard
import at.heinz.deviceai.localmodel.LocalModelGenerationPolicy

/** Safe terminal reason from the native boundary, never an inferred diagnosis. */
enum class LocalGenerationFailureCause(val ownerMessage: String) {
    THERMAL_PAUSED("Android meldet starke Gerätewärme. Die lokale Generierung wurde nicht gestartet."),
    THERMAL_STOPPED("Android meldet starke Gerätewärme. Die laufende Textgenerierung wurde zum Schutz gestoppt."),
    OUTPUT_LIMIT("Das lokale Ausgabelimit wurde erreicht; die Antwort ist unvollständig."),
    STALLED("Das lokale Modell hat längere Zeit keinen neuen Text ausgegeben."),
    CONTEXT_UNVERIFIED("Der Modellkontext konnte nicht zuverlässig übernommen werden."),
    RUNTIME_FAILED("Die lokale Modelllaufzeit meldete einen Fehler. Die genaue Ursache ist ohne Geräteprotokoll nicht bestätigt."),
    UNKNOWN("Die lokale Generierung wurde nicht vollständig abgeschlossen. Die genaue Ursache ist nicht bestätigt.");

    companion object {
        fun classify(completion: LocalGenerationCompletion, failure: String?): LocalGenerationFailureCause =
            when (completion) {
                LocalGenerationCompletion.OUTPUT_LIMIT -> OUTPUT_LIMIT
                LocalGenerationCompletion.STALLED -> STALLED
                LocalGenerationCompletion.FAILED -> when (failure) {
                    LocalModelGenerationPolicy.OUTPUT_TOKEN_LIMIT,
                    LocalModelGenerationPolicy.OUTPUT_CHARACTER_LIMIT -> OUTPUT_LIMIT
                    LocalModelThermalGuard.PAUSED_THERMAL -> THERMAL_PAUSED
                    LocalModelThermalGuard.STOPPED_THERMAL -> THERMAL_STOPPED
                    "LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL" -> CONTEXT_UNVERIFIED
                    null, "" -> UNKNOWN
                    else -> RUNTIME_FAILED
                }
                LocalGenerationCompletion.CANCELLED, LocalGenerationCompletion.COMPLETE -> UNKNOWN
            }
    }
}
