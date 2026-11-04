package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** M2.9: German retrieval test set (1000 memories, 44 questions: paraphrase, typo, compound, time) — A vs B. */
class ContinuumRetrievalExperimentTest {
    private val zone=ZoneId.of("Europe/Vienna")
    private val now=Instant.parse("2026-11-04T09:00:00Z").toEpochMilli()
    private val day=86_400_000L
    private fun at(daysAgo:Int)=now-daysAgo*day-3_600_000L

    // id, text, daysAgo, confirmed
    private val targets=listOf(
        Triple("t01","Mein Zahnarzttermin bei Dr. Huber ist am Donnerstag um neun.",3),
        Triple("t02","Der WLAN-Schlüssel für das Gästenetz lautet Sonnenblume42.",30),
        Triple("t03","Lisa hat am 14. März Geburtstag und mag Orchideen.",60),
        Triple("t04","Das Auto steht in der Tiefgarage auf Ebene minus zwei, Platz 117.",0),
        Triple("t05","Meine Krankenversicherungsnummer liegt im blauen Ordner im Arbeitszimmer.",90),
        Triple("t06","Der Reisepass läuft im August 2027 ab.",120),
        Triple("t07","Ich nehme morgens eine Tablette Vitamin D.",40),
        Triple("t08","Unser Vermieter heißt Herr Gruber, Telefon im Handy unter Vermieter.",200),
        Triple("t09","Die Mülltonne wird jeden Dienstag geleert.",50),
        Triple("t10","Im Keller steht die Bohrmaschine im Regal links oben.",70),
        Triple("t11","Ich habe gestern beim Bäcker Kaiser zwei Vollkornbrote gekauft.",1),
        Triple("t12","Brot habe ich vor zehn Tagen beim Spar gekauft.",10),
        Triple("t13","Gestern Abend war ich mit Thomas im Kino, wir sahen Dune.",1),
        Triple("t14","Letzten Monat war ich mit Thomas im Theater.",30),
        Triple("t15","Vorgestern habe ich die Stromrechnung über 84 Euro bezahlt.",2),
        Triple("t16","Die Gasrechnung von 120 Euro habe ich letzte Woche überwiesen.",9),
        Triple("t17","Heute früh habe ich die Blumen auf dem Balkon gegossen.",0),
        Triple("t18","Meine Schwester Anna wohnt in Graz in der Herrengasse.",300),
        Triple("t19","Der Code für das Fahrradschloss ist 3-8-1-5.",100),
        Triple("t20","Die Heizung im Wohnzimmer muss im Herbst entlüftet werden.",80),
        Triple("t21","Mein Arbeitgeber zahlt das Gehalt immer am 25. des Monats.",150),
        Triple("t22","Der Hund Bello bekommt zweimal täglich Trockenfutter.",20),
        Triple("t23","Die Winterreifen lagern beim Reifenhändler Pichler in Enns.",45),
        Triple("t24","Ich bin allergisch gegen Penicillin.",400),
        Triple("t25","Das Passwort für den Router steht auf der Unterseite des Geräts.",25),
        Triple("t26","Meine Lieblingspizza ist Diavolo vom Italiener am Stadtplatz.",35),
        Triple("t27","Die Fenster im Schlafzimmer wurden vor drei Tagen geputzt.",3),
        Triple("t28","Am Wochenende fahre ich mit dem Zug nach Salzburg.",4),
        Triple("t29","Der Ersatzschlüssel liegt bei der Nachbarin Frau Berger.",60),
        Triple("t30","Mein Blutdruck war beim letzten Check 125 zu 80.",14),
        Triple("t31","Die Steuererklärung muss bis Ende Juni abgegeben werden.",110),
        Triple("t32","Ich spiele dienstags Tennis im Verein in Steyr.",55),
        Triple("t33","Das Ladegerät für den Laptop liegt im Rucksack.",5),
        Triple("t34","Die Hausratversicherung läuft über die Uniqa.",130),
        Triple("t35","Gestern habe ich Mama angerufen, sie hat Knieschmerzen.",1),
        Triple("t36","Vor einem Monat habe ich Mama in Linz besucht.",31),
        Triple("t37","Die Waschmaschine zeigt Fehler E18, die Pumpe ist verstopft.",6),
        Triple("t38","Der Kühlschrank wurde letzte Woche abgetaut.",8),
        Triple("t39","Ich trinke keinen Kaffee mehr nach 15 Uhr.",65),
        Triple("t40","Die Brille habe ich bei Hartlauer in Auftrag gegeben.",12),
        Triple("t41","Meine Kollegin Sabine hat die Durchwahl 214.",95),
        Triple("t42","Die Garantie für den Fernseher gilt bis 2028.",140),
        Triple("t43","Heute habe ich beim Hofer Milch und Eier gekauft.",0),
        Triple("t44","Die Katze vom Nachbarn heißt Minka.",160),
    )
    // hypothesis memories (never confirmed); share vocabulary with targets on purpose
    private val hypotheses=listOf(
        Triple("h01","Hypothese: Vielleicht ist der Zahnarzttermin verschoben worden.",2),
        Triple("h02","Hypothese: Wenn ich im Lotto gewinne, kaufe ich ein Auto.",5),
        Triple("h03","Hypothese: Möglicherweise wohnt Anna bald in Wien.",7),
    )
    private val queries=listOf(
        "Wann ist mein Termin beim Zahnarzt?" to "t01", "Wann muss ich zum Zahnartz?" to "t01",
        "Wie lautet das WLAN Passwort für Gäste?" to "t02", "Was ist der Wlan Schlüsel?" to "t02",
        "Wann hat Lisa Geburtstag?" to "t03", "Welche Blumen mag Lisa?" to "t03",
        "Wo habe ich geparkt?" to "t04", "Auf welchem Parkplatz in der Garage steht das Auto?" to "t04",
        "Wo ist die Versicherungsnummer der Krankenkasse?" to "t05",
        "Wann läuft mein Pass ab?" to "t06", "Bis wann ist der Reisepas gültig?" to "t06",
        "Welche Vitamine nehme ich?" to "t07", "Wie heißt der Vermieter?" to "t08",
        "An welchem Tag wird der Müll abgeholt?" to "t09", "Wo ist die Bohrmaschine?" to "t10",
        "Wo habe ich gestern Brot gekauft?" to "t11", "Wo kaufte ich vor zehn Tagen Brot?" to "t12",
        "Was habe ich gestern mit Thomas gemacht?" to "t13", "Wohin ging ich letzten Monat mit Thomas?" to "t14",
        "Welche Rechnung habe ich vorgestern bezahlt?" to "t15", "Was habe ich letzte Woche überwiesen?" to "t16",
        "Was habe ich heute auf dem Balkon gemacht?" to "t17", "Wo wohnt meine Schwester?" to "t18",
        "Wie ist die Zahlenkombination vom Radschloss?" to "t19", "Wann muss die Heizung entlüftet werden?" to "t20",
        "Wann kommt das Gehalt?" to "t21", "Was frisst Bello?" to "t22", "Wo sind die Winterreifen eingelagert?" to "t23",
        "Gegen welches Medikament bin ich allergisch?" to "t24", "Wo finde ich das Routerpasswort?" to "t25",
        "Welche Pizza esse ich am liebsten?" to "t26", "Was wurde vor drei Tagen geputzt?" to "t27",
        "Wohin fahre ich am Wochenende?" to "t28", "Wer hat den Ersatzschlüssel?" to "t29",
        "Wie war mein Blutdruck?" to "t30", "Bis wann ist die Steuererklärung fällig?" to "t31",
        "Wann spiele ich Tennis?" to "t32", "Wo ist das Laptop-Ladegerät?" to "t33",
        "Bei wem bin ich hausratversichert?" to "t34", "Was hat Mama gestern erzählt?" to "t35",
        "Wann war ich bei Mama in Linz?" to "t36", "Was ist mit der Waschmaschiene los?" to "t37",
        "Was habe ich heute beim Hofer gekauft?" to "t43", "Wie heißt die Katze der Nachbarn?" to "t44",
    )

    private fun distractors():List<MemoryDoc> {
        val r=Random(7)
        val subj=listOf("Ich","Thomas","Anna","Mama","Sabine","Der Nachbar","Lisa","Herr Gruber","Frau Berger","Mein Chef")
        val verb=listOf("hat","habe","plant","erwähnte","vergaß","bestellte","reparierte","suchte","kaufte","besuchte")
        val obj=listOf("einen neuen Termin","das Paket","die Zeitung","ein Buch über Geschichte","die Rechnung","einen Kuchen","das Fahrrad",
            "die Lampe im Flur","den Drucker","Tomaten und Gurken","ein Konzertticket","die Jacke","den Schlüsselanhänger","eine Pflanze",
            "den Staubsauger","die Kaffeemaschine","ein Geschenk","den Bus","das Formular","die Fotos vom Urlaub","eine Versicherung","den Garten")
        val place=listOf("in Steyr","in Linz","in Wien","im Büro","zu Hause","am Bahnhof","im Supermarkt","in der Stadt","beim Arzt","im Park","online","in Enns")
        return (0 until 1000-targets.size-hypotheses.size).map { i ->
            MemoryDoc("d%04d".format(i),"${subj.random(r)} ${verb.random(r)} ${obj.random(r)} ${place.random(r)}.",at(r.nextInt(0,400)),r.nextBoolean())
        }
    }
    private fun corpus()=targets.map { MemoryDoc(it.first,it.second,at(it.third),true) }+
        hypotheses.map { MemoryDoc(it.first,it.second,at(it.third),false) }+distractors()

    data class Score(val name:String,val r1:Double,val r3:Double,val mrr:Double,val p95:Double,val bytes:Long,val indexMs:Double)

    private fun evaluate(name:String,r:ContinuumMemoryRetriever):Score {
        val docs=corpus(); val t0=System.nanoTime(); r.index(docs); val indexMs=(System.nanoTime()-t0)/1e6
        var r1=0;var r3=0;var mrr=0.0
        val misses=ArrayList<String>()
        for((q,exp) in queries) {
            val hits=r.search(q,now,10)
            val rank=hits.indexOfFirst { it.doc.id==exp }
            if(rank==0)r1++; if(rank in 0..2)r3++; if(rank>=0)mrr+=1.0/(rank+1)
            if(rank!=0)misses+="$q -> ${hits.firstOrNull()?.doc?.id}(rank ${rank+1})"
            hits.forEach { h -> assertEquals("confirmed flag must be copied verbatim",docs.first { it.id==h.doc.id }.confirmed,h.confirmed) }
            assertTrue(hits.filter { it.doc.id.startsWith("h") }.none { it.confirmed })
        }
        repeat(3) { queries.forEach { r.search(it.first,now,3) } }
        val lat=(0 until 5).flatMap { queries.map { (q,_) -> val t=System.nanoTime();r.search(q,now,3);(System.nanoTime()-t)/1e6 } }.sorted()
        val n=queries.size.toDouble()
        println("M2-RETRIEVAL-MISSES $name: "+misses.joinToString(" | "))
        return Score(name,r1/n,r3/n,mrr/n,lat[(lat.size*0.95).toInt()-1],r.approxIndexBytes(),indexMs)
    }

    @Test fun compareVariantsOnGermanTestSet() {
        assertTrue(queries.size>=40); assertEquals(1000,corpus().size)
        val t=TemporalCue(zone)
        val scores=listOf(
            evaluate("A BM25-de",Bm25GermanRetriever()),
            evaluate("A BM25-de + Zeit",Bm25GermanRetriever(t)),
            evaluate("B Char-3-5-Gramm-Hash",CharNgramHashRetriever()),
            evaluate("B Char-3-5-Gramm-Hash + Zeit",CharNgramHashRetriever(t)))
        println("M2-RETRIEVAL | Variante | recall@1 | recall@3 | MRR | p95 ms | Index-Bytes | Indexaufbau ms |")
        scores.forEach { println("M2-RETRIEVAL | %s | %.3f | %.3f | %.3f | %.2f | %d | %.1f |".format(it.name,it.r1,it.r3,it.mrr,it.p95,it.bytes,it.indexMs)) }
        val default=evaluate("default",ContinuumMemoryRetriever.default(zone))
        assertTrue("default retriever must reach recall@3 >= 0.80",default.r3>=0.80)
    }

    @Test fun hypothesesNeverComeBackConfirmedAndEmptyIndexIsEmpty() {
        val r=ContinuumMemoryRetriever.default(zone)
        assertTrue(r.search("Zahnarzt",now).isEmpty())
        r.index(listOf(MemoryDoc("h1","Hypothese: Wenn ich im Lotto gewinne, kaufe ich ein Haus.",now,false),
            MemoryDoc("f1","Ich wohne in Steyr.",now,true)))
        val hits=r.search("Was kaufe ich, wenn ich im Lotto gewinne?",now)
        assertEquals("h1",hits.first().doc.id); assertFalse(hits.first().confirmed)
    }
}
