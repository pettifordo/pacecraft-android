package com.pacecraft.android

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacecraft.engine.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable fun FullPlanScreen(m: CoachViewModel, editGoal: ()->Unit) {
    LaunchedEffect(m.result) { m.ensurePlan() }
    val plan=m.fullPlan; val goal=m.effectiveGoal; val imperial=m.saved.imperial
    val unit=if(imperial) "mi" else "km"
    Eyebrow("The whole block")
    Heading("Your whole plan.",goal.raceDate?.let {
        "${Display.distance(goal.distance,imperial)} $unit · race ${it.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
    } ?: "A two-week look ahead. Add a race date to see the full block.")
    TextButton(onClick=editGoal,enabled=!m.demo) { Text("Edit race goal") }
    if(m.result?.fitness==null) {
        Panel {
            Text("Your plan starts with your running history.",fontWeight=FontWeight.Bold)
            Text("Connect Health Connect or add completed runs. The engine needs 8 usable runs, 6 weeks and 40 km, including suitable outdoor efforts.",color=Muted,lineHeight=22.sp)
            Text("You can also explore the sample history from Settings.",color=Muted)
        }
        return
    }
    if(m.projecting || plan==null) {
        Panel { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working through your training block…",color=Muted) }
        return
    }
    val fitness=m.result!!.fitness!!
    var expanded by remember(plan) { mutableStateOf(setOfNotNull(plan.weeks.firstOrNull()?.start)) }
    var selected by remember(plan) { mutableStateOf<Session?>(null) }
    PlanExportActions(m,plan,fitness)
    Panel {
        Eyebrow("A forecast that adapts")
        Text("Only the next run is a prescription. Later sessions assume you complete each run as written, using your current fitness and updated training load.",fontSize=13.sp,lineHeight=20.sp,color=Muted)
        Text("Rebuilt when runs, goals, constraints or check-ins change.",fontSize=12.sp,color=Lime)
        Text("Paces come from your current fitness. Interval sessions are chosen when your phase, workload and recovery allow them.",fontSize=12.sp,lineHeight=18.sp,color=Muted)
        if(plan.capped) Text("Showing the first 30 weeks; your race is beyond this window.",color=Color(0xFFFFC795),fontSize=12.sp)
    }
    Panel {
        Eyebrow("Weekly volume")
        val maximum=max(1.0,plan.weeks.maxOfOrNull { it.trainingDistance } ?: 1.0)
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            val width=size.width/max(1,plan.weeks.size)
            plan.weeks.forEachIndexed { i,week ->
                val height=(size.height*week.trainingDistance/maximum).toFloat()
                drawRoundRect(if(week.downWeek) Color(0xFF708957) else Lime,
                    topLeft=androidx.compose.ui.geometry.Offset(i*width+2,size.height-height),
                    size=androidx.compose.ui.geometry.Size(max(1f,width-4),height),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(4f))
            }
        }
        Text("${plan.weeks.size} calendar weeks · ${plan.from} to ${plan.through}",color=Muted,fontSize=12.sp)
        Text("Training distance, including runs already completed this week. Race distance is shown in its session below.",color=Muted,fontSize=11.sp,lineHeight=17.sp)
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Week by week",fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
        TextButton(onClick={expanded=if(expanded.size==plan.weeks.size) emptySet() else plan.weeks.map { it.start }.toSet()}) {
            Text(if(expanded.size==plan.weeks.size) "Collapse all" else "Expand all")
        }
    }
    plan.weeks.forEachIndexed { index,week ->
        val isOpen=week.start in expanded
        Panel {
            Row(Modifier.fillMaxWidth().clickable { expanded=if(isOpen) expanded-week.start else expanded+week.start },verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("Week ${index+1} · ${week.start.format(DateTimeFormatter.ofPattern("d MMM"))}",fontWeight=FontWeight.Bold)
                    Text("${week.phase.name.lowercase().replace('_',' ')}${if(week.downWeek) " · down week" else ""}${if(week.partial) " · partial week" else ""}",color=Lime,fontSize=12.sp)
                    Text("${Display.distance(week.totalDistance,imperial)} $unit total · ${week.runCount} projected runs",color=Muted,fontSize=12.sp)
                    if(week.sessions.any { it.kind==Kind.INTERVALS }) Text("Includes intervals",color=Lime,fontSize=12.sp)
                }
                Icon(if(isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,if(isOpen) "Collapse week" else "Expand week",tint=Lime)
            }
            if(isOpen) {
                HorizontalDivider(color=Muted.copy(alpha=.2f))
                for(offset in 0..6) {
                    val day=week.start.plusDays(offset.toLong())
                    if(day>plan.through) continue
                    val session=week.sessions.firstOrNull { it.day==day }
                    val completed=week.completed.filter { it.day(ZoneId.systemDefault())==day }
                    val raceDay=day==goal.raceDate
                    Row(Modifier.fillMaxWidth().then(if(session!=null) Modifier.clickable { selected=session } else Modifier).padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.width(56.dp)) {
                            Text(day.format(DateTimeFormatter.ofPattern("EEE")),color=Muted,fontSize=12.sp)
                            Text(day.dayOfMonth.toString(),fontWeight=FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(when {
                                completed.isNotEmpty() -> "Completed · ${completed.size} ${if(completed.size==1) "run" else "runs"}"
                                raceDay && session?.kind!=Kind.RACE -> "Race day · goal event"
                                session!=null -> session.kind.label
                                !m.saved.constraints.available(day) -> "Unavailable"
                                day<plan.from -> "Rest"
                                else -> "Rest / no session projected"
                            },fontSize=14.sp,color=if(raceDay) Lime else Cream)
                            when {
                                completed.isNotEmpty() -> Text("${Display.distance(completed.sumOf { it.distance },imperial)} $unit recorded",color=Muted,fontSize=11.sp)
                                raceDay && session?.kind!=Kind.RACE -> Text("${Display.distance(goal.distance,imperial)} $unit race · training advice: ${session?.kind?.label ?: "check next run"}",color=Muted,fontSize=11.sp)
                                session!=null && session.kind!=Kind.REST -> Text("${Display.distance(session.distance,imperial)} $unit · ${(session.seconds/60).roundToInt()} min${if(session.id==m.result?.next?.id) " · next run" else " · projected"}",color=Muted,fontSize=11.sp)
                                raceDay -> Text("${Display.distance(goal.distance,imperial)} $unit · see the next-run screen for current advice",color=Muted,fontSize=11.sp)
                            }
                            if(completed.isEmpty() && session!=null && session.kind!=Kind.REST) {
                                PrescriptionSummary(session,fitness,imperial)
                                Text("Tap for session steps",color=Muted,fontSize=10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { session ->
        AlertDialog(onDismissRequest={selected=null},title={Text("${session.kind.label} · ${session.day.format(DateTimeFormatter.ofPattern("d MMM"))}")},
            text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(session.kind!=Kind.REST) Text("${Display.distance(session.distance,imperial)} $unit · ${Display.time(session.seconds)}",fontWeight=FontWeight.Bold)
                Text(if(session.id==m.result?.next?.id) "This is your current next-run prescription." else "Projected: this session can change after your next run or check-in.",color=Lime,fontSize=13.sp)
                PrescriptionSummary(session,fitness,imperial)
                session.reasons.forEach { Text(it,color=Muted,fontSize=13.sp) }
                if(session.steps.isNotEmpty()) {
                    Eyebrow("Session steps")
                    SessionSteps(session,fitness,imperial)
                }
            }},confirmButton={TextButton(onClick={selected=null}) { Text("Done") }})
    }
}
