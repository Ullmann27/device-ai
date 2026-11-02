package at.heinz.deviceai.learning

/** An expandable interdisciplinary syllabus; topics are research questions, not answers. */
object MentorCurriculum {
    data class Topic(val area:String, val query:String)
    val topics=listOf(
        Topic("Biologie","Fotosynthese"), Topic("Physik","Gravitation"),
        Topic("Geschichte","Geschichte der Antike"), Topic("Mathematik","Primzahlen"),
        Topic("Informatik","Algorithmen"), Topic("Chemie","Periodensystem"),
        Topic("Geografie","Plattentektonik"), Topic("Astronomie","Sonnensystem"),
        Topic("Sprache","Deutsche Grammatik"), Topic("Kunst","Perspektive Malerei"),
        Topic("Literatur","Roman Literatur"), Topic("Philosophie","Erkenntnistheorie"),
        Topic("Musik","Musiktheorie"), Topic("Ökonomie","Angebot und Nachfrage"),
        Topic("Umwelt","Biodiversität"), Topic("Technik","Elektromotor"),
        Topic("Geometrie","Euklidische Geometrie"), Topic("Journalismus","Quellenkritik"),
        Topic("Psychologie","Gedächtnis"), Topic("Architektur","Baustatik"),
        Topic("Statistik","Wahrscheinlichkeit"), Topic("Elektronik","Transistor"),
        Topic("Ernährung","Makronährstoff"), Topic("Verkehr","Eisenbahntechnik"),
        Topic("Recht","Rechtsstaat"), Topic("Gesellschaft","Soziologie"),
        Topic("Meteorologie","Wolken"), Topic("Anatomie","Blutkreislauf"),
        Topic("Linguistik","Sprachfamilie"), Topic("Forschung","Wissenschaftliche Methode")
    )
    fun select(index:Long):Topic { require(index>=0);return topics[(index % topics.size).toInt()] }
}
