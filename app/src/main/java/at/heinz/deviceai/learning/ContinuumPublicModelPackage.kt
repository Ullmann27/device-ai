package at.heinz.deviceai.learning

import java.security.MessageDigest

/**
 * Byte-exact provenance binding for the public GitHub research model.
 * A SHA-256 digest provides integrity, NOT publisher authentication.
 * Import remains explicitly user selected; no network requests occur here.
 */
object ContinuumPublicModelPackage {
    data class Verified(val modelSha256:String,val codeSha:String?,val updates:Int)

    fun verify(model:ByteArray,manifestText:String):Verified {
        require(model.size==32960) { "Ungültige Modellgröße" }
        require(model.copyOfRange(0,4).contentEquals(byteArrayOf(83,87,66,49))) { "Ungültiger SWB1-Header" }
        require(manifestText.length in 30..16384) { "Ungültige Manifestgröße" }
        // Strict parser: duplicate keys or lenient syntax could otherwise make the
        // verified value differ from the value another reader would see.
        val json=StrictJson.parseObject(manifestText)
        require(json.optionalLong("schemaVersion",0L..1000L)==1L) { "Unbekannte Paketversion" }
        require(json.optionalString("format",16)=="SWB1") { "Unbekanntes Modellformat" }
        require(json.optionalString("privacy",64)=="PUBLIC_AND_CURATED_TRAINING_ONLY") {
            "Nicht-öffentliches Trainingspaket"
        }
        require(json.optionalLong("modelBytes",0L..1_000_000L)==model.size.toLong()) { "Modellgröße stimmt nicht" }
        val hash=json.requiredString("modelSha256",64)
        require(hash.matches(Regex("[a-f0-9]{64}"))) { "Ungültiger Modellhash" }
        json.optionalString("modelHash",64)?.let { require(it==hash) { "Manifest-Hashes widersprechen sich" } }
        val actual=MessageDigest.getInstance("SHA-256").digest(model)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(MessageDigest.isEqual(hash.toByteArray(Charsets.US_ASCII),
            actual.toByteArray(Charsets.US_ASCII))) { "Modell und Manifest stimmen nicht überein" }
        val codeSha=json.optionalString("codeSha",64)
        require(codeSha==null || codeSha.matches(Regex("[a-fA-F0-9]{40}"))) {
            "Ungültiger Code-Commit"
        }
        val updates=json.optionalLong("updates",0L..1_000_000L) ?: 0L
        return Verified(actual,codeSha,updates.toInt())
    }
}
