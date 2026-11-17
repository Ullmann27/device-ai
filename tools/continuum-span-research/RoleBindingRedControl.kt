package at.heinz.deviceai.learning

import at.heinz.deviceai.learning.span.*
import kotlin.math.abs
import kotlin.math.sqrt

/** Expected RED for the legacy representation; separate GREEN for optional ordered SHADOW. */
object RoleBindingRedControl {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1 && args[0] in setOf("legacy","shadow"))
        val left = DecoderEvidencePair("Anna ist in Perg. Emma ist in Berg.","Wo ist Anna?","Perg")
        val right = DecoderEvidencePair("Anna ist in Berg. Emma ist in Perg.","Wo ist Anna?","Berg")
        val encoder = TrainableSubwordEncoder(73)
        val before = encoder.save()
        val a = encoder.representation(left.conditionerText()); val b = encoder.representation(right.conditionerText())
        val ab = TrainableSubwordEncoder.subwordBuckets(left.conditionerText()).toSet()
        val bb = TrainableSubwordEncoder.subwordBuckets(right.conditionerText()).toSet()
        val decoder = TinyContextDecoder(91)
        val p = decoder.nextTokenProbabilities(left.prefix(),a); val q = decoder.nextTokenProbabilities(right.prefix(),b)
        println("buckets=${ab.size}/${bb.size} symmetricDifference=${(ab-bb).size+(bb-ab).size}")
        println("conditionLeft=${a.toList()} conditionRight=${b.toList()}")
        println("conditionL2=${sqrt(a.indices.sumOf { (a[it]-b[it])*(a[it]-b[it]) })}")
        println("directContext=${left.prefix().toByteArray().takeLast(8)} probabilityMaxDifference=${p.indices.maxOf { abs(p[it]-q[it]) }}")
        check(before.contentEquals(encoder.save()))
        if (args[0] == "legacy") {
            if (ab == bb) throw AssertionError("EXPECTED_LEGACY_COLLISION_RED: opposite person/place bindings have identical bucket sets")
            error("Legacy representation changed; review this frozen negative control")
        }
        fun encode(pair: DecoderEvidencePair): EntityRelationLocationShadowAdapter.Encoding {
            val e=EvidenceBoundNaturalLocationProbe.Evidence("audit",1,EvidenceBoundNaturalLocationProbe.digest(pair.evidence),pair.evidence)
            return requireNotNull(EntityRelationLocationShadowAdapter.encode(pair.question,LocationEvidenceSnapshot.freeze(listOf(e)),enabled=true))
        }
        val la=encode(left); val lb=encode(right)
        check(la.location.text == "Perg" && lb.location.text == "Berg" && la.orderedSha256 != lb.orderedSha256)
        println("SHADOW_GREEN: ${la.entity}/${la.relation}/${la.location.text} -> ${lb.entity}/${lb.relation}/${lb.location.text}")
        println("No legacy checkpoint change, decoder repair or normal-chat promotion")
    }
}
