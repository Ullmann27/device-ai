package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumDashboardState.Companion.UNKNOWN
import at.heinz.deviceai.ui.ContinuumIcons.Icon

/** Portrait-Fold7 cards all occupy identical measured rectangles. Values are real, never mock KPIs. */
abstract class PortraitDashboardCard(textScale: Float, private val title: String, private val glyph: Icon) :
    ContinuumElement(textScale) {
    protected fun frame(p: ContinuumPainter, w: Float, h: Float) {
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h, 15f)
        ContinuumSurfaces.iconDisc(p, glyph, 30f, 30f, 44f, 22f)
        val style = fittedTitle(p, title, 650, 13.3f, w - 90f)
        p.drawText(ContinuumSurfaces.fit(p, title, style, w - 90f), 59f, 34f,
            style, Fill.Solid(ContinuumTheme.TEXT))
        ContinuumSurfaces.chevron(p, w - 15f, 30f, 11f)
        p.drawLine(13f, 57f, w - 13f, 57f, .7f, Fill.Solid(ContinuumTheme.CARD_DIVIDER))
    }
    protected fun detail(p: ContinuumPainter, w: Float, y: Float, glyph: Icon, label: String, value: String) {
        ContinuumIcons.draw(p, glyph, 22f, y-3f, 13f, Fill.Solid(ContinuumTheme.CYAN_SOFT), 1.1f)
        p.drawText(label, 35f, y, t(400, 9f), Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
        val style = fittedTitle(p, value, 500, 9f, w * .43f)
        p.drawText(ContinuumSurfaces.fit(p, value, style, w * .43f), w-13f, y,
            style, Fill.Solid(ContinuumTheme.CYAN_SOFT), TextAlign.RIGHT)
        p.drawLine(13f,y+8f,w-13f,y+8f,.5f,Fill.Solid(ContinuumTheme.CARD_DIVIDER))
    }
}

class PortraitModelCard(textScale: Float = 1f) : PortraitDashboardCard(textScale,"Modellstatus",Icon.BRAIN) {
    override fun draw(p: ContinuumPainter,w: Float,h: Float,s: ContinuumDashboardState) {
        frame(p,w,h)
        val m=s.model
        ContinuumSurfaces.pill(p,w-12f,7f,17f,m.stateLabel,t(500,8f),
            if(m.online) ContinuumTheme.GREEN else ContinuumTheme.AMBER,alignRight=true)
        p.drawText(ContinuumSurfaces.fit(p,m.name,t(400,9f),w-26f),14f,75f,
            t(400,9f),Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
        p.drawText("Prüffragen",14f,100f,t(400,9f),Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
        ModelStatusCardElement.drawSegmentBar(p,84f,90f,w-65f,99f,m.qualityPercent,phase=motionPhase)
        p.drawText(ContinuumDashboardState.percent(m.qualityPercent),w-13f,101f,
            t(600,15f),Fill.Solid(ContinuumTheme.CYAN_SOFT),TextAlign.RIGHT)
        p.drawText(ContinuumDashboardState.delta24h(m.qualityDelta24h),82f,115f,
            t(400,8f),Fill.Solid(ContinuumTheme.CYAN_SOFT))
        val y=135f
        val step=((h-y-17f)/4f).coerceIn(15f,26f)
        detail(p,w,y,Icon.MODE,"Modus",m.mode)
        detail(p,w,y+step,Icon.DOCUMENT,"Kontextfenster",m.context ?: UNKNOWN)
        detail(p,w,y+step*2f,Icon.CLOCK,"Reaktionszeit",ContinuumDashboardState.millis(m.latencyMs))
        detail(p,w,y+step*3f,Icon.REFRESH,"Letztes Update",m.lastUpdate ?: UNKNOWN)
    }
    override fun describe(s: ContinuumDashboardState)=WideModelCardElement().describe(s)
}

class PortraitLearningCard(textScale: Float = 1f) : PortraitDashboardCard(textScale,"Lernen & Forschung",Icon.BOOK) {
    override fun draw(p: ContinuumPainter,w: Float,h: Float,s: ContinuumDashboardState) {
        frame(p,w,h)
        val l=s.learning
        ContinuumSurfaces.pill(p,w-12f,8f,17f,if(l.live)"Live" else "Ruhe",t(500,8f),
            if(l.live) ContinuumTheme.GREEN else ContinuumTheme.CYAN,alignRight=true)
        p.drawText(ContinuumDashboardState.count(l.today),14f,97f,t(600,24f),
            ContinuumSurfaces.figureFill(70f,97f))
        p.drawText("Lernereignisse heute",77f,84f,t(400,9f),
            Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
        l.changePercent?.let { d ->
            p.drawText((if(d>0)"+" else "")+d.toString()+"%",w-15f,96f,
                t(600,9f),Fill.Solid(if(d>=0)ContinuumTheme.GREEN else ContinuumTheme.RED),TextAlign.RIGHT)
        }
        val end=(h*.62f).coerceAtLeast(124f)
        WaveChart.draw(p,13f,101f,w-13f,end,l.series,phase=motionPhase)
        val start=end+17f
        val spacing=((h-start-9f)/4f).coerceAtLeast(12f)
        if(l.recent.isEmpty())p.drawText("Noch keine überprüften Lernereignisse.",14f,start,
            t(400,8.4f),Fill.Solid(ContinuumTheme.TEXT_MUTED))
        for((i,e) in l.recent.take(4).withIndex()) {
            val y=start+i*spacing
            EventDot.draw(p,17f,y-3f,e.kind)
            p.drawText(e.ago,w-12f,y,t(400,7.7f),
                Fill.Solid(ContinuumTheme.TEXT_MUTED),TextAlign.RIGHT)
            p.drawText(ContinuumSurfaces.fit(p,e.title,t(400,8f),w-105f),29f,y,
                t(400,8f),Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
        }
    }
    override fun describe(s: ContinuumDashboardState)=WideLearningCardElement().describe(s)
}

class PortraitStorageCard(textScale: Float = 1f) : PortraitDashboardCard(textScale,"Speicher & Quellen",Icon.DATABASE) {
    override fun draw(p: ContinuumPainter,w: Float,h: Float,s: ContinuumDashboardState) {
        frame(p,w,h)
        val st=s.storage
        val cy=111f+(h-220f).coerceAtLeast(0f)*.25f
        Donut.draw(p,70f,cy,54f,12f,st.usedPercent,st.segments)
        p.drawText(ContinuumDashboardState.percent(st.usedPercent),70f,cy+4f,
            t(650,19f),Fill.Solid(ContinuumTheme.TEXT),TextAlign.CENTER)
        p.drawText("belegt",70f,cy+18f,t(400,8f),
            Fill.Solid(ContinuumTheme.TEXT_SECONDARY),TextAlign.CENTER)
        for((i,seg) in st.segments.take(4).withIndex()){
            val y=78f+i*28f
            p.fillCircle(139f,y-3f,3.8f,Fill.Solid(seg.color))
            val label=ContinuumDashboardState.compactStorageLabel(seg.label)
            p.drawText(ContinuumSurfaces.fit(p,label,t(400,8.7f),w-155f),150f,y,
                t(400,8.7f),Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
            p.drawText(ContinuumDashboardState.bytes(seg.bytes),150f,y+12f,
                t(500,8.3f),Fill.Solid(ContinuumTheme.CYAN_SOFT))
        }
        val foot=h-27f
        p.drawLine(13f,foot-13f,w-13f,foot-13f,.7f,Fill.Solid(ContinuumTheme.CARD_DIVIDER))
        ContinuumIcons.draw(p,Icon.GLOBE,25f,foot,21f,Fill.Solid(ContinuumTheme.CYAN_SOFT))
        val sources=st.activeSources?.let{it.toString()+" gespeicherte Quellen"} ?: "Quellen: "+UNKNOWN
        p.drawText(ContinuumSurfaces.fit(p,sources,t(500,9.4f),w-62f),45f,foot-3f,
            t(500,9.4f),Fill.Solid(ContinuumTheme.TEXT))
        p.drawText(ContinuumSurfaces.fit(p,st.syncLabel,t(400,7.7f),w-62f),45f,foot+10f,
            t(400,7.7f),Fill.Solid(ContinuumTheme.TEXT_MUTED))
    }
    override fun describe(s: ContinuumDashboardState)=WideStorageCardElement().describe(s)
}

class PortraitDevelopmentCard(textScale: Float = 1f) : PortraitDashboardCard(textScale,"Entwicklung & Code",Icon.CODE) {
    override fun draw(p: ContinuumPainter,w: Float,h: Float,s: ContinuumDashboardState) {
        frame(p,w,h)
        val d=s.development
        val data=listOf(ContinuumDashboardState.count(d.activeProjects) to "offen",
            ContinuumDashboardState.count(d.runningTasks) to "laufend",
            ContinuumDashboardState.count(d.errors) to "fehlgeschlagen")
        for((i,item) in data.withIndex()){
            val x=w*(i+.5f)/3f
            p.drawText(item.first,x,91f,t(600,21f),Fill.Solid(ContinuumTheme.CYAN_SOFT),TextAlign.CENTER)
            p.drawText(item.second,x,107f,t(400,8.3f),Fill.Solid(ContinuumTheme.TEXT_SECONDARY),TextAlign.CENTER)
        }
        BarChart.draw(p,13f,115f,w-13f,151f,d.series,grid=false)
        p.drawLine(12f,159f,w-12f,159f,.6f,Fill.Solid(ContinuumTheme.CARD_DIVIDER))
        val step=((h-170f)/4f).coerceAtLeast(11f)
        if(d.projects.isEmpty())p.drawText("Keine bestätigten Entwicklungsaufträge.",14f,179f,
            t(400,8f),Fill.Solid(ContinuumTheme.TEXT_MUTED))
        for((i,item) in d.projects.take(4).withIndex()){
            val y=175f+i*step
            ContinuumIcons.draw(p,Icon.CUBE,23f,y-3f,12f,Fill.Solid(ContinuumTheme.CYAN_SOFT))
            p.drawText(ContinuumSurfaces.fit(p,item.name,t(500,8.8f),w*.45f),
                35f,y,t(500,8.8f),Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
            p.drawText(ContinuumSurfaces.fit(p,item.status,t(400,8f),w*.39f),
                w-12f,y,t(400,8f),Fill.Solid(ContinuumTheme.CYAN_SOFT),TextAlign.RIGHT)
        }
    }
    override fun describe(s: ContinuumDashboardState)=WideDevelopmentCardElement().describe(s)
}

class PortraitActivityCard(textScale: Float = 1f) : PortraitDashboardCard(textScale,"Systemaktivität",Icon.PULSE) {
    override fun draw(p: ContinuumPainter,w: Float,h: Float,s: ContinuumDashboardState) {
        frame(p,w,h)
        p.drawText("alle 5 s",w-15f,69f,t(400,8f),Fill.Solid(ContinuumTheme.TEXT_MUTED),TextAlign.RIGHT)
        WaveChart.drawRidge(p,12f,76f,w-12f,h*.64f,s.system.activity)
        val gauges=listOf("CPU" to s.system.appCpuPercent,"GPU" to null,
            "RAM" to s.system.memoryPercent,"Speicher" to s.system.storagePercent)
        val cy=h-48f
        for((i,g) in gauges.withIndex()){
            val x=w*(i+.5f)/4f
            val color=when(i){0->ContinuumTheme.CYAN;1->Argb.rgb(152,100,255);2->Argb.rgb(255,100,190);else->ContinuumTheme.CYAN_SOFT}
            p.strokeCircle(x,cy,22f,6f,Fill.Solid(Argb.rgb(28,45,83)))
            g.second?.let { level ->
                p.strokeArc(x-22f,cy-22f,x+22f,cy+22f,-90f,level.coerceIn(0,100)*3.6f,
                    6f,Fill.Solid(color),roundCap=false)
            }
            p.drawText(ContinuumDashboardState.percent(g.second),x,cy+4f,
                t(550,9f),Fill.Solid(ContinuumTheme.TEXT),TextAlign.CENTER)
            p.drawText(g.first,x,h-13f,t(400,8f),
                Fill.Solid(ContinuumTheme.TEXT_SECONDARY),TextAlign.CENTER)
        }
    }
    override fun describe(s: ContinuumDashboardState)=SystemActivityElement().describe(s)
}

class PortraitWorkersCard(textScale: Float = 1f) : PortraitDashboardCard(textScale,"Aufgaben & Worker",Icon.CODE) {
    override fun draw(p: ContinuumPainter,w: Float,h: Float,s: ContinuumDashboardState) {
        frame(p,w,h)
        val d=s.development
        val figures=listOf(ContinuumDashboardState.count(d.runningTasks) to "laufend",
            ContinuumDashboardState.count(d.activeProjects) to "Aufträge",
            ContinuumDashboardState.count(d.errors) to "Fehler")
        for((i,f) in figures.withIndex()){
            val x=w*(i+.5f)/3f
            p.drawText(f.first,x,91f,t(600,23f),Fill.Solid(ContinuumTheme.CYAN_SOFT),TextAlign.CENTER)
            p.drawText(f.second,x,107f,t(400,8.8f),Fill.Solid(ContinuumTheme.TEXT_SECONDARY),TextAlign.CENTER)
        }
        p.drawLine(13f,125f,w-13f,125f,.8f,Fill.Solid(ContinuumTheme.CARD_DIVIDER))
        val spacing=((h-146f)/4f).coerceAtLeast(12f)
        if(d.projects.isEmpty())p.drawText("Keine belegten aktiven Worker.",14f,154f,
            t(400,9f),Fill.Solid(ContinuumTheme.TEXT_MUTED))
        for((i,job) in d.projects.take(4).withIndex()){
            val y=149f+i*spacing
            ContinuumIcons.draw(p,Icon.CODE,22f,y-3f,13f,Fill.Solid(ContinuumTheme.CYAN_SOFT))
            p.drawText(ContinuumSurfaces.fit(p,job.name,t(500,9f),w*.53f),35f,y,
                t(500,9f),Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
            p.drawText(ContinuumSurfaces.fit(p,job.status,t(400,8f),w*.34f),w-12f,y,
                t(400,8f),Fill.Solid(ContinuumTheme.CYAN_SOFT),TextAlign.RIGHT)
        }
    }
    override fun describe(s: ContinuumDashboardState)="Aufgaben und Worker: "+
        ContinuumDashboardState.count(s.development.runningTasks)+" laufend, "+
        ContinuumDashboardState.count(s.development.activeProjects)+" Aufgaben."
}

/** Right-side tower: a large TRUE app-CPU donut, not mock device-wide CPU data. */
class PortraitSystemStatus(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter,w: Float,h: Float,s: ContinuumDashboardState) {
        ContinuumSurfaces.glassCard(p,0f,0f,w,h,14f)
        p.drawText("Systemstatus",12f,25f,t(650,12.1f),Fill.Solid(ContinuumTheme.TEXT))
        ContinuumSurfaces.pill(p,w-10f,34f,17f,s.system.overall,t(500,7f),
            if(s.system.openIssues==0)ContinuumTheme.GREEN else ContinuumTheme.AMBER,alignRight=true)
        Donut.draw(p,w/2f,111f,55f,12f,s.system.appCpuPercent,emptyList())
        p.drawText(ContinuumDashboardState.percent(s.system.appCpuPercent),w/2f,115f,
            t(600,22f),Fill.Solid(ContinuumTheme.TEXT),TextAlign.CENTER)
        p.drawText("App-CPU",w/2f,134f,t(400,9f),
            Fill.Solid(ContinuumTheme.TEXT_SECONDARY),TextAlign.CENTER)
        WaveChart.draw(p,9f,166f,w-9f,185f,s.system.activity)
        val rows=listOf(
            Triple(Icon.WIFI,"Netzwerk",s.system.networkLabel),
            Triple(Icon.DATABASE,"Quellen",s.storage.activeSources?.toString() ?: UNKNOWN),
            Triple(Icon.DOCUMENT,"Kontext",s.model.context ?: UNKNOWN),
            Triple(Icon.BOLT,"Antwort",ContinuumDashboardState.millis(s.model.latencyMs)),
            Triple(Icon.CPU,"RAM",ContinuumDashboardState.percent(s.system.memoryPercent)))
        val step=(h-194f)/5f
        for((i,item) in rows.withIndex()){
            val y=201f+i*step
            ContinuumIcons.draw(p,item.first,17f,y,12f,Fill.Solid(ContinuumTheme.CYAN_SOFT))
            p.drawText(item.second,28f,y+3f,t(400,7.5f),
                Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
            val value=fittedTitle(p,item.third,500,7.9f,w*.42f)
            p.drawText(ContinuumSurfaces.fit(p,item.third,value,w*.42f),w-8f,y+3f,
                value,Fill.Solid(ContinuumTheme.CYAN_SOFT),TextAlign.RIGHT)
            if(i<4)p.drawLine(9f,y+step*.6f,w-9f,y+step*.6f,
                .5f,Fill.Solid(ContinuumTheme.CARD_DIVIDER))
        }
    }
    override fun describe(s: ContinuumDashboardState)="Systemstatus: "+s.system.overallSpoken+
        ", CPU "+ContinuumDashboardState.percent(s.system.appCpuPercent)+
        ", Netzwerk "+s.system.networkLabel
}
