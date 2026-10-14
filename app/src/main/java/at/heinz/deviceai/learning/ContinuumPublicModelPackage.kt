package at.heinz.deviceai.learning

import java.security.MessageDigest
import org.json.JSONObject

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
        val json=JSONObject(manifestText)
        require(json.optInt("schemaVersion")==1) { "Unbekannte Paketversion" }
        require(json.optString("format")=="SWB1") { "Unbekanntes Modellformat" }
        require(json.optString("privacy")=="PUBLIC_AND_CURATED_TRAINING_ONLY") {
            "Nicht-öffentliches Trainingspaket"
        }
        require(json.optInt("modelBytes")==model.size) { "Modellgröße stimmt nicht" }
        val hash=json.optString("modelSha256")
        require(hash.matches(Regex("[a-f0-9]{64}"))) { "Ungültiger Modellhash" }
        val actual=MessageDigest.getInstance("SHA-256").digest(model)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(MessageDigest.isEqual(hash.toByteArray(Charsets.US_ASCII),
            actual.toByteArray(Charsets.US_ASCII))) { "Modell und Manifest stimmen nicht überein" }
        val codeSha=json.optString("codeSha").takeIf { it.isNotEmpty() && it!="null" }
        require(codeSha==null || codeSha.matches(Regex("[a-fA-F0-9]{40}"))) {
            "Ungültiger Code-Commit"
        }
        return Verified(actual,codeSha,json.optInt("updates"))
    }
}
