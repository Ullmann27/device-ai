package at.heinz.deviceai.learning

/**
 * Deterministic fictional evidence → question → short-answer examples.
 * Labels are intentionally fictional research cases, NOT factual knowledge.
 * New cases are partitioned by robot ID; the frozen audit never contributes
 * a gradient and is unchanged across all stages.
 */
object ContinuumCdxLargeCurriculum {
    const val VERSION="cdx-pairs-v1"
    const val STAGES=12
    const val PAIRS_PER_STAGE=48
    const val TOTAL_TRAIN=STAGES*PAIRS_PER_STAGE
    const val TRAIN_STEPS=240

    private val cities=listOf("Graz","Linz","Wien","Salzburg","Innsbruck",
        "Klagenfurt","Villach","St. Pölten","Eisenstadt","Bregenz","Steyr","Leoben")
    private val colors=listOf("rot","blau","grün","gelb","violett","weiß",
        "schwarz","orange","silber","gold","türkis","braun")
    private val energy=listOf("Solarstrom","Windstrom","Akkustrom","Wasserkraft",
        "Netzstrom","Brennstoffzelle","Solarzelle","Generator",
        "Batterie","Turbine","Photovoltaik","Ladegerät")
    private val tools=listOf("Hammer","Zange","Bohrer","Säge","Pinsel","Schlüssel",
        "Schere","Lineal","Lupe","Feile","Bürste","Meißel")

    private fun cases(id:Int):List<DecoderEvidencePair> {
        require(id in 1..96)
        val robot="R"+id.toString().padStart(2,'0')
        val city=cities[(id-1)%cities.size]
        val color=colors[(id+3)%colors.size]
        val source=energy[(id+6)%energy.size]
        val tool=tools[(id+9)%tools.size]
        fun item(e:String,q:String,a:String)=DecoderEvidencePair(e,q,a)
        return listOf(
            item("Roboter $robot steht in $city.","Wo steht $robot?",city),
            item("Laut Liste befindet sich $robot in $city.","In welcher Stadt ist $robot?",city),
            item("Für $robot ist der Ort $city notiert.","Welcher Ort gilt für $robot?",city),
            item("Die Farbe von $robot ist $color.","Welche Farbe hat $robot?",color),
            item("Im Protokoll wird $robot als $color bezeichnet.","Wie ist $robot gefärbt?",color),
            item("Die Farbkarte nennt für $robot $color.","Was steht bei der Farbe von $robot?",color),
            item("Roboter $robot nutzt $source als Energiequelle.","Was versorgt $robot mit Energie?",source),
            item("Im Datenblatt von $robot steht als Energie $source.","Welche Energie nutzt $robot?",source),
            item("Für $robot ist $source als Antrieb eingetragen.","Welche Versorgung ist für $robot notiert?",source),
            item("Roboter $robot hält einen $tool.","Welches Werkzeug hält $robot?",tool),
            item("Die Werkzeugliste ordnet $robot einen $tool zu.","Was gehört zu $robot?",tool),
            item("Für $robot liegt ein $tool bereit.","Was ist für $robot bereitgelegt?",tool)
        )
    }

    val train:List<DecoderEvidencePair> by lazy {
        (1..48).flatMap(::cases).also { require(it.size==TOTAL_TRAIN) }
    }

    val audit:DecoderEvidenceCorpus by lazy {
        // 4 entirely unseen robots, 12 patterns each = 48 frozen cases.
        val unseen=(91..94).flatMap(::cases)
        DecoderEvidenceCorpus(unseen,DecoderSplit.AUDIT)
    }

    val historicAudit:List<DecoderEvidencePair> = listOf(
        DecoderEvidencePair("Tim wohnt in Wien.","Wo wohnt Tim?","Tim wohnt in Wien."),
        DecoderEvidencePair("Eva ist Köchin.","Was arbeitet Eva?","Eva ist Köchin.")
    )

    fun trainingStage(stage:Int):DecoderEvidenceCorpus {
        require(stage in 0 until STAGES)
        return DecoderEvidenceCorpus(
            train.subList(stage*PAIRS_PER_STAGE,(stage+1)*PAIRS_PER_STAGE),
            DecoderSplit.TRAIN)
    }

    /** Frozen audit combines new unknown persons and existing legacy checks. */
    fun protectedAudit():DecoderEvidenceCorpus =
        DecoderEvidenceCorpus(audit.pairs+historicAudit,DecoderSplit.AUDIT)

    fun validate() {
        val training=train.map { it.conditionerText() }
        val tests=protectedAudit().pairs.map { it.conditionerText() }
        require(training.size==TOTAL_TRAIN && training.toSet().size==training.size)
        require(tests.size==50 && tests.toSet().size==tests.size)
        require(training.toSet().intersect(tests.toSet()).isEmpty())
        require((0 until STAGES).sumOf { trainingStage(it).pairs.size }==TOTAL_TRAIN)
        require(train.none { it.answer.contains("\n") })
    }
}
