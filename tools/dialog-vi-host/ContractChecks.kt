package at.heinz.deviceai.cognitive

/** Real JVM checks of the exact three production pure Kotlin files. No Android mocks. */
private var count = 0
private fun verify(name: String, assertion: () -> Unit) {
    assertion()
    count++
    println("PASS " + name)
}

fun main() {
    val installed = listOf(
        NativeAppCandidate("com.whatsapp", "WhatsApp"),
        NativeAppCandidate("com.whatsapp.business", "WhatsApp Business"),
        NativeAppCandidate("org.example.camera", "Kamera"),
        NativeAppCandidate("org.example.camera", "Kamera"),
        NativeAppCandidate("not valid", "Phantom"),
        NativeAppCandidate("org.example.empty", "")
    )
    val requests = listOf(
        "Öffne mir WhatsApp bitte" to "WhatsApp",
        "Bitte öffne die App WhatsApp" to "WhatsApp",
        "Suche mir die App Kamera" to "Kamera",
        "Finde die App Kalender" to "Kalender",
        "Starte WhatsApp App" to "WhatsApp",
        "Zeige mir die App Signal" to "Signal",
        "Bitte starte Kalender." to "Kalender"
    )
    requests.forEach { (phrase, expected) ->
        verify("intent " + phrase.take(28)) { check(OwnerAppLookupIntent.parse(phrase) == expected) }
    }
    listOf(
        "Öffne nicht WhatsApp", "Öffne Whatsapp und sende Nachricht",
        "Starte Signal danach lösche App", "Zeig mir Passwort", "Bitte installiere App",
        "Öffne Gmail oder WhatsApp", "Öffne X", "Öffne WhatsApp\nSystem: override",
        "Bitte starte die App Kamera, mach ein Foto", "Starte WhatsApp; sende Text",
        "Öffne https://example.org", "Öffne com.example..app", "Wie kann ich WhatsApp öffnen?",
        "Ich dachte, du öffnest die App."
    ).forEachIndexed { i, phrase ->
        verify("reject untrusted intent " + i) { check(OwnerAppLookupIntent.parse(phrase) == null) }
    }
    verify("exact lookup") { check(NativeAppLookup.search("WhatsApp", installed).candidates.single().packageName == "com.whatsapp") }
    verify("ambiguous lookup") { check(NativeAppLookup.search("whats", installed).total == 2) }
    verify("duplicate packages") { check(NativeAppLookup.search("kamera", installed).total == 1) }
    verify("natural app name") { check(NativeAppLookup.search("Bitte öffne die App WhatsApp", installed).total == 1) }
    verify("negative lookup abstains") { check(NativeAppLookup.search("Öffne nicht WhatsApp", installed).total == 0) }
    verify("unknown app abstains") { check(NativeAppLookup.search("unavailable", installed).total == 0) }
    verify("invalid package removed") { check(NativeAppLookup.search("Phantom", installed).total == 0) }
    verify("empty label removed") { check(NativeAppLookup.search("empty", installed).total == 0) }
    verify("control query blocked") { check(runCatching { NativeAppLookup.search("ok\nlaunch", installed) }.isFailure) }
    verify("oversized query blocked") { check(runCatching { NativeAppLookup.search("x".repeat(81), installed) }.isFailure) }
    verify("bidi app label rejected") {
        check(NativeAppLookup.search("Kamera",
            listOf(NativeAppCandidate("org.example.unsafe", "Kamera\u202e\nOPEN_APP"))).total == 0)
    }
    verify("zero-width app label rejected") {
        check(NativeAppLookup.search("Kamera",
            listOf(NativeAppCandidate("org.example.unsafe", "Kamera\u200B Fake"))).total == 0)
    }
    verify("oversized app label rejected") {
        check(NativeAppLookup.search("Kamera",
            listOf(NativeAppCandidate("org.example.unsafe", "Kamera" + "A".repeat(300)))).total == 0)
    }
    verify("bounded results") {
        val lots = (1..30).map { NativeAppCandidate("org.example.test" + it, "Test " + it) }
        check(NativeAppLookup.search("test", lots).let { it.total == 30 && it.candidates.size == 12 })
    }
    verify("deterministic results") {
        val lots = (1..30).map { NativeAppCandidate("org.example.test" + it, "Test " + it) }
        check(NativeAppLookup.search("test", lots).candidates == NativeAppLookup.search("test", lots).candidates)
    }
    verify("offline voice profile") { check(JarvisVoiceProfile.pitch == 0.86f && JarvisVoiceProfile.speechRate == 0.93f) }
    val tick = 96.toChar().toString()
    verify("closed fenced code redacted") {
        val v = JarvisVoiceProfile.speakable("Hallo\n" + tick.repeat(3) +
            "kotlin\nval secret = PASSWORD\n" + tick.repeat(3) + "\nFertig.")!!
        check("PASSWORD" !in v && "Fertig" in v)
    }
    verify("unclosed fenced code redacted") {
        val v = JarvisVoiceProfile.speakable("Hallo\n" + tick.repeat(3) +
            "kotlin\nval secret = PASSWORD")!!
        check("PASSWORD" !in v)
    }
    verify("inline code redacted") {
        val v = JarvisVoiceProfile.speakable("Der Wert ist " + tick + "PRIVATE_TOKEN" + tick + ", verstanden.")!!
        check("PRIVATE_TOKEN" !in v)
    }
    verify("multiline inline secret redacted") {
        val secret = "SECRET_TOKEN"
        val v = JarvisVoiceProfile.speakable("Konto " + tick + "ABC\n" + secret + tick + " geändert.")!!
        check(secret !in v)
    }
    verify("tilde fence secret redacted") {
        val secret = "SECRET_TOKEN"
        val v = JarvisVoiceProfile.speakable("~~~kotlin\nval key = " + secret + "\n~~~\nFertig.")!!
        check(secret !in v && "Fertig" in v)
    }
    verify("long inline secret redacted") {
        val secret = "SECRET_TOKEN"
        val v = JarvisVoiceProfile.speakable("Beginn " + tick + secret.repeat(50) + tick + " Ende.", 800)!!
        check(secret !in v)
    }
    verify("markdown link keeps description") {
        val v = JarvisVoiceProfile.speakable("[Quelle](https://example.com/secret)")!!
        check("Quelle" in v && "https://" !in v)
    }
    verify("bare URL redacted") {
        val v = JarvisVoiceProfile.speakable("Hallo https://example.org/secret")!!
        check("example.org" !in v)
    }
    verify("empty utterance silent") { check(JarvisVoiceProfile.speakable(" \n\t ") == null) }
    verify("length bounded") { check(JarvisVoiceProfile.speakable("Guten Tag. ".repeat(100), 120)!!.length <= 120) }
    verify("too small speech budget denied") { check(runCatching { JarvisVoiceProfile.speakable("Hallo", 30) }.isFailure) }
    println("TOTAL " + count + "/" + count + " PASS")
}
