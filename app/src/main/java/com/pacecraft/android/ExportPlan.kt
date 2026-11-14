package com.pacecraft.android

import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.pacecraft.engine.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

object PlanExport {
    private fun share(context:Context,file:File,mime:String) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share CoachMeSimple"))
    }
    private fun file(context:Context,name:String)=File(context.cacheDir,"exports").apply{mkdirs()}.let{File(it,name)}
    fun calendar(context:Context,sessions:List<Session>,fitness:Fitness,imperial:Boolean) {
        fun escape(s:String)=s.replace("\\","\\\\").replace(";","\\;").replace(",","\\,").replace("\n","\\n")
        val start=LocalDate.now(); val cutoff=start.plusDays(7)
        val text=buildString {
            append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//CoachMeSimple//Android//EN\r\nCALSCALE:GREGORIAN\r\n")
            sessions.filter{it.kind!=Kind.REST && it.day>=start && it.day<cutoff}.forEach { s ->
                val date=s.day.format(DateTimeFormatter.BASIC_ISO_DATE);val end=s.day.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
                val notes=(s.reasons+SessionDescription.paces(s,fitness,imperial).map{"${it.label}: ${it.text}"}+
                    listOfNotNull(SessionDescription.repetitions(s))+"Forecast: check the app before running; future sessions change with your history.").joinToString("\n")
                append("BEGIN:VEVENT\r\nUID:coachmesimple-$date@local\r\nDTSTAMP:${Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))}\r\n")
                append("DTSTART;VALUE=DATE:$date\r\nDTEND;VALUE=DATE:$end\r\nSUMMARY:${escape(s.kind.label)} · ${Display.distance(s.distance,imperial)} ${if(imperial) "mi" else "km"}\r\nDESCRIPTION:${escape(notes)}\r\nEND:VEVENT\r\n")
            };append("END:VCALENDAR\r\n")
        }
        val f=file(context,"CoachMeSimple-next-week.ics");f.writeText(text.lineSequence().filter{it.isNotEmpty()}.joinToString("\r\n",postfix="\r\n"){line->
            val parts=mutableListOf<String>();var part="";var bytes=0
            for(c in line){val n=c.toString().toByteArray(Charsets.UTF_8).size;if(bytes+n>74){parts+=part;part=" ";bytes=1};part+=c;bytes+=n};parts+=part;parts.joinToString("\r\n")
        });share(context,f,"text/calendar")
    }
    fun pdf(context:Context,plan:FullPlan,fitness:Fitness,imperial:Boolean) {
        val document=PdfDocument();val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=11f}
        var page:PdfDocument.Page?=null;var y=0f;var index=0
        fun newPage() { page?.let{document.finishPage(it)};page=document.startPage(PdfDocument.PageInfo.Builder(595,842,++index).create());y=42f }
        fun line(s:String,bold:Boolean=false) {
            if(page==null || y>800) newPage()
            paint.typeface=if(bold) Typeface.create(Typeface.DEFAULT,Typeface.BOLD) else Typeface.DEFAULT
            // Wrap by measured width rather than clipping sessions on the page edge.
            var remaining=s
            while(remaining.isNotEmpty()) {
                if(y>800) newPage()
                val n=paint.breakText(remaining,true,510f,null).coerceAtLeast(1)
                val breakAt=if(n<remaining.length) remaining.lastIndexOf(' ',n).takeIf{it>0}?:n else n
                page!!.canvas.drawText(remaining.take(breakAt),42f,y,paint);y+=16
                remaining=remaining.drop(breakAt).trimStart()
            }
        }
        line("CoachMeSimple · projected training plan",true)
        line("${plan.from} to ${plan.through}")
        line("Only the next run is a prescription. Check the app after each real run or check-in.")
        plan.weeks.forEachIndexed { i,w ->
            y+=10;line("Week ${i+1} · ${w.start} · ${w.phase.name.lowercase().replace('_',' ')} · ${Display.distance(w.totalDistance,imperial)} ${if(imperial) "mi" else "km"}",true)
            w.sessions.forEach { s ->
                line("${s.day} · ${s.kind.label}${if(s.kind!=Kind.REST) " · ${Display.distance(s.distance,imperial)} ${if(imperial) "mi" else "km"} · ${Display.time(s.seconds)}" else ""}",true)
                SessionDescription.repetitions(s)?.let{line(it)}
                SessionDescription.paces(s,fitness,imperial).forEach { line("${it.label}: ${it.text}") }
                s.steps.forEach { step -> line("  ${step.label}: ${Display.time(step.seconds)} · ${SessionDescription.pace(step.kind,fitness,imperial,step.paceScale)}") }
            }
        }
        page?.let{document.finishPage(it)}
        val f=file(context,"CoachMeSimple-plan.pdf");f.outputStream().use{document.writeTo(it)};document.close();share(context,f,"application/pdf")
    }
    fun workout(context:Context,s:Session,fitness:Fitness) {
        // A documented, portable structured-workout JSON; paired Wear OS uses the same payload.
        val f=file(context,"CoachMeSimple-workout.json");f.writeText(WorkoutWire.encode(s,fitness));share(context,f,"application/json")
    }
}
