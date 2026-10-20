package at.heinz.deviceai.learning

/**
 * Deterministically constructed additional public research fixtures.
 * They are grammar exercises and paired-topic similarity fixtures,
 * NOT web knowledge, owner facts, or proven world facts.
 *
 * Each stage selects a disjoint balanced, bounded mini-batch. The
 * independent audit is frozen across all stages and never trained on.
 */
object ContinuumExtendedCurriculum {
    const val VERSION = "public-replay-expansion-20261020-v1"
    const val STAGES = 8

    private val greetingStarts=listOf(
        "hallo", "servus", "griaß di", "grüß gott", "guten morgen",
        "guten tag", "guten abend", "herzliche grüße", "liebe grüße",
        "schönen guten morgen", "ein freundliches hallo", "schön dich zu sehen",
        "willkommen", "ich grüße dich", "einen schönen tag",
        "einen wundervollen abend", "servus miteinander", "hey hallo",
        "guten morgen allerseits", "freut mich dich zu sehen",
        "beste grüße", "hallo zurück", "einen herzlichen gruß",
        "einen sonnigen morgen"
    )
    private val greetingEnds=listOf(
        "aus unserer werkstatt", "an euch alle", "an meine freunde",
        "aus dem garten", "aus meiner stadt", "von uns daheim",
        "an die ganze runde", "vom see", "aus dem büro",
        "an die nachbarn", "aus der bibliothek", "vom berg",
        "aus dem urlaub", "von der arbeit", "an die familie",
        "aus dem zug", "an die kollegen", "aus der schule",
        "vom balkon", "aus der küche", "an unsere gäste",
        "aus der gemeinde", "von unterwegs", "aus dem park"
    )

    private val questionStarts=listOf(
        "was bedeutet", "wie funktioniert", "kannst du erklären",
        "wieso ist wichtig", "warum beschäftigen wir uns mit",
        "was versteht man unter", "wie lerne ich etwas über",
        "welche beispiele gibt es für", "welchen nutzen hat",
        "wann verwendet man", "wo finde ich grundlagen über",
        "wer untersucht", "welche rolle spielt",
        "kannst du mir helfen bei", "was sind die grenzen von",
        "welche schritte gehören zu", "wodurch erkennt man",
        "wie kann man prüfen", "ist eine erklärung möglich für",
        "welche fragen stellt man zu", "was solltest du wissen über",
        "kannst du fragen beantworten zu", "welche probleme gibt es bei",
        "wie entwickelt sich"
    )
    private val questionTopics=listOf(
        "lokalem maschinellem lernen", "datenstrukturen", "softwaretests",
        "fehlerbehandlung", "android anwendungen", "quellcode",
        "programmplanung", "neuronalen netzen", "mathematik",
        "wahrscheinlichkeiten", "sprachverarbeitung", "robotern",
        "autonomen systemen", "elektrischen motoren", "energiespeichern",
        "physikalischen kräften", "umweltschutz", "menschlicher sprache",
        "digitalen bildern", "sicheren netzwerken", "öffentlichen dokumenten",
        "lernmethoden", "algorithmischen entscheidungen", "versionierten dateien"
    )

    private val auditGreetings=listOf(
        "moin und beste wünsche", "ein freundlicher gruß", "schöne grüße von hier",
        "ein herzliches hallöchen", "guten mittag ihr lieben",
        "willkommen zu unserem gespräch", "ich wünsche dir einen frohen tag",
        "guten morgen in die runde"
    )
    private val auditGreetingSuffix=listOf(
        "aus dem museum", "aus dem theater", "vom spielplatz", "aus der bäckerei"
    )
    private val auditQuestions=listOf(
        "könntest du mir bitte zeigen", "darf ich dich etwas fragen über",
        "wie lässt sich erklären", "wer weiß etwas über",
        "was muss ich beachten bei", "welche erfahrungen gibt es mit",
        "weshalb sollte man verstehen", "welche aussagen passen zu"
    )
    private val auditTopics=listOf(
        "medienkompetenz", "wasserreinigung", "wettervorhersagen", "kleinen satelliten"
    )

    private val subjectDescriptions=listOf(
        "softwaretest" to "tests kontrollieren die funktionsweise von programmen",
        "android" to "mobiltelefone führen android anwendungen aus",
        "algorithmus" to "algorithmen legen nachvollziehbare rechenschritte fest",
        "datenschutz" to "datenschutz begrenzt den umgang mit personeninformationen",
        "netzwerk" to "netzwerke verbinden rechner zur datenübertragung",
        "batterie" to "akkumulatoren speichern elektrische energie",
        "solarenergie" to "sonnenlicht kann elektrischen strom erzeugen",
        "physik" to "physik untersucht bewegung energie und materie",
        "biologie" to "biologie beschreibt lebende organismen",
        "mathematik" to "mathematik untersucht zahlen formen und muster",
        "grammatik" to "grammatik beschreibt den aufbau von sätzen",
        "astronomie" to "astronomie beobachtet sterne planeten und galaxien",
        "fotografie" to "fotografie zeichnet optische bilder auf",
        "robotik" to "roboter können sensoren und bewegungsantriebe benutzen",
        "speicher" to "datenspeicher halten digitale dateien bereit",
        "statistik" to "statistik beschreibt zahlenbasierte datenanalysen",
        "maschinenbau" to "maschinenbau konstruiert technische geräte",
        "chemie" to "chemie erforscht stoffe und ihre reaktionen",
        "pädagogik" to "pädagogik befasst sich mit bildung und lernen",
        "linguistik" to "sprachwissenschaft untersucht menschliche sprachen",
        "meteorologie" to "meteorologie untersucht wetter und atmosphäre",
        "musik" to "musik nutzt melodie rhythmus und klang",
        "geografie" to "geografie untersucht regionen landschaften und orte",
        "geschichte" to "geschichte untersucht menschliche vergangenheit"
    )
    private val semanticForms=listOf(
        "ein text über", "eine zusammenfassung von",
        "eine fachliche frage über", "informationen zu",
        "lernstoff über", "eine erklärung von",
        "überblick über", "grundlagen zu",
        "wissen über", "ein thema aus"
    )

    val intentTrain:List<SubwordIntentExample> = buildList {
        val positives=greetingStarts.flatMap { a -> greetingEnds.map { b -> "$a $b" } }
        val negatives=questionStarts.flatMap { a -> questionTopics.map { b -> "$a $b?" } }
        require(positives.size==576 && negatives.size==576)
        for(i in positives.indices) {
            add(SubwordIntentExample(positives[i],0))
            add(SubwordIntentExample(negatives[i],1))
        }
    }
    val intentAudit:List<SubwordIntentExample> = buildList {
        for(a in auditGreetings)for(b in auditGreetingSuffix)add(SubwordIntentExample("$a $b",0))
        for(a in auditQuestions)for(b in auditTopics)add(SubwordIntentExample("$a $b?",1))
    }

    val semanticTrain:List<SubwordSemanticExample> = buildList {
        subjectDescriptions.forEachIndexed { i, (subject,meaning) ->
            semanticForms.forEach { form ->
                val query="$form $subject"
                add(SubwordSemanticExample(query,meaning,true))
                val wrong=subjectDescriptions[(i+11)%subjectDescriptions.size].second
                add(SubwordSemanticExample(query,wrong,false))
            }
        }
    }

    val semanticAudit:List<SubwordSemanticExample> = listOf(
        SubwordSemanticExample("wissen über bäume","bäume sind lebende pflanzen",true),
        SubwordSemanticExample("lernen über fahrzeuge","fahrzeuge können menschen transportieren",true),
        SubwordSemanticExample("infos über kochen","kochen verändert lebensmittel durch wärme",true),
        SubwordSemanticExample("frage zur medizin","medizin befasst sich mit der gesundheit",true),
        SubwordSemanticExample("wissen über bäume","fahrzeuge brauchen verkehrswege",false),
        SubwordSemanticExample("lernen über fahrzeuge","kochen erwärmt nahrungsmittel",false),
        SubwordSemanticExample("infos über kochen","bäume besitzen wurzeln",false),
        SubwordSemanticExample("frage zur medizin","flugzeuge besitzen tragflächen",false)
    )

    fun intentBatch(stage:Int):List<SubwordIntentExample> {
        require(stage in 0 until STAGES)
        // 72+72? 1152/8=144 exceeds the train/eval bound of 128.
        // Sample 48 of each label, rotating disjoint slices on each stage.
        val group=stage*72
        val questions=intentTrain.filter { it.label==1 }.subList(group,group+72)
        val greetings=intentTrain.filter { it.label==0 }.subList(group,group+72)
        return (greetings.take(48)+questions.take(48))
            .flatMapIndexed { i,e -> listOf(e) }
    }
    fun semanticBatch(stage:Int):List<SubwordSemanticExample> {
        require(stage in 0 until STAGES)
        // 480 pairs, 60 per stage.
        return semanticTrain.subList(stage*60,(stage+1)*60)
    }

    fun validate() {
        require(intentTrain.size==1152 && intentAudit.size==64)
        require(semanticTrain.size==480 && semanticAudit.size==8)
        val it=intentTrain.map { it.text }.toSet()
        val ia=intentAudit.map { it.text }.toSet()
        require(it.size==intentTrain.size && ia.size==intentAudit.size && it.intersect(ia).isEmpty())
        val st=semanticTrain.map { it.left+"\u0000"+it.right }.toSet()
        val sa=semanticAudit.map { it.left+"\u0000"+it.right }.toSet()
        require(st.size==semanticTrain.size && sa.size==semanticAudit.size && st.intersect(sa).isEmpty())
        for(stage in 0 until STAGES) {
            val ib=intentBatch(stage); val sb=semanticBatch(stage)
            require(ib.size==96 && ib.count{it.label==0}==48 && ib.count{it.label==1}==48)
            require(sb.size==60 && sb.count { it.similar }==30)
        }
    }
}
