package at.heinz.deviceai.learning

/** WIP/UNVERIFIED stub for the RED run: classifies everything as ASSERTED. */
enum class StatementModality { ASSERTED, NEGATED, HYPOTHETICAL, UNCERTAIN, INTENTION, QUESTION, REPORTED }

object OwnerStatementModality {
    fun classify(text:String):StatementModality = StatementModality.ASSERTED
    fun mayBecomeConfirmedFact(modality:StatementModality):Boolean = true
}
