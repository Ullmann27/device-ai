package at.heinz.deviceai.learning.span

/** Explicit read-only lab entry: no reference answer enters either inference path. */
object NaturalLocationLabComparison {
    const val MODEL_ASSET = "continuum-research/natural-location-v3.csx"
    fun readCheckpoint(input: java.io.InputStream): ByteArray {
        val bytes = ByteArray(ContinuumResidualSpanCore.SERIALIZED_BYTES)
        java.io.DataInputStream(input).readFully(bytes)
        require(input.read() == -1) { "Oversized research checkpoint" }
        return bytes
    }
    data class Comparison(val rule: EvidenceBoundNaturalLocationProbe.Decision,
        val learned: EvidenceBoundNaturalLocationProbe.Decision, val snapshotSha256: String,
        val modelSha256: String, val hybrid: LocationSpecialistShadow.Choice,
        val ordered: EntityRelationLocationShadowAdapter.Encoding?)

    fun compare(evidence: String, question: String, checkpoint: ByteArray): Comparison {
        val source = EvidenceBoundNaturalLocationProbe.Evidence("lab-input", 1,
            EvidenceBoundNaturalLocationProbe.digest(evidence), evidence)
        val snapshot = LocationEvidenceSnapshot.freeze(listOf(source))
        val rule = EvidenceBoundNaturalLocationProbe.answer(question, snapshot.sources)
        val learned = VerifiedLocationSpanResearch(checkpoint).answer(question, snapshot)
        return Comparison(rule, learned.decision, snapshot.sha256, learned.modelSha256,
            LocationSpecialistShadow.choose(question,snapshot,learned.decision),
            EntityRelationLocationShadowAdapter.encode(question,snapshot,enabled=true))
    }

    fun render(result: Comparison): String {
        fun decision(d: EvidenceBoundNaturalLocationProbe.Decision): String = when(d) {
            is EvidenceBoundNaturalLocationProbe.Decision.Found -> d.text + " [" + d.start + "," + d.endExclusive + ")"
            is EvidenceBoundNaturalLocationProbe.Decision.Abstained -> "Enthaltung: " + d.reason
        }
        return "EVIDENZSPAN · NAMEN UND ORTE (FORSCHUNG)\nRegelvergleich: " + decision(result.rule) +
            "\nGelernter Ranker + unabhängige Belegprüfung: " + decision(result.learned) +
            "\nHybrid-SHADOW (" + result.hybrid.route + "): " + decision(result.hybrid.decision) +
            "\nRollen-SHADOW: " + (result.ordered?.let { it.entity + " — " + it.relation + " → " + it.location.text } ?: "kein eindeutiger Rollenbeleg") +
            "\nBelegsnapshot-SHA-256: " + result.snapshotSha256 + "\nForschungsmodell-SHA-256: " + result.modelSha256 +
            "\nNur Entnahme aus diesem Beleg. Keine CDX1-Reparatur, Faktenübernahme oder Chatfreigabe."
    }
}
