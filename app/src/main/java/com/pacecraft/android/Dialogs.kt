package com.pacecraft.android

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pacecraft.engine.*
import java.time.*

@Composable fun Field(value: String, change: (String)->Unit, label: String, numeric: Boolean = false) {
    OutlinedTextField(value,onValueChange=change,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth(),
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable fun Toggle(label: String, value: Boolean, change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text(label,Modifier.weight(1f)); Switch(value,onCheckedChange=change) }
}
private fun parseTime(text: String): Double? {
    val parts=text.trim().split(":")
    if(parts.size !in 2..3) return null
    val values=parts.map { it.toIntOrNull() ?: return null }
    if(values.any { it<0 } || values.drop(1).any { it>=60 }) return null
    return (if(values.size==3) values[0]*3600.0+values[1]*60+values[2] else values[0]*60.0+values[1]).takeIf { it>0 }
}
@Composable fun GoalDialog(m: CoachViewModel, close: ()->Unit) {
    val g=m.saved.goal
    var distance by remember { mutableStateOf((g.distance/1000).toString()) }
    var date by remember { mutableStateOf(g.raceDate?.toString() ?: "") }
    var target by remember { mutableStateOf(g.targetSeconds?.let(Display::time) ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=close,title={Text("Your race goal")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Distances are entered in kilometres. Leave the date empty for open-ended training.",color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf("5K" to "5","10K" to "10","Half" to "21.0975","Full" to "42.195").forEach { (label,d) -> TextButton(onClick={distance=d},contentPadding=PaddingValues(6.dp)) { Text(label) } } }
        Field(distance,{distance=it},"Distance (km)",true)
        Field(date,{date=it},"Race date · YYYY-MM-DD (optional)")
        Field(target,{target=it},"Target · mm:ss or h:mm:ss (optional)")
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }},confirmButton={TextButton(enabled=!m.demo,onClick={
        val d=distance.toDoubleOrNull(); val parsedDate=if(date.isBlank()) null else runCatching { LocalDate.parse(date.trim()) }.getOrNull()
        val seconds=if(target.isBlank()) null else parseTime(target)
        error=when { d==null || !d.isFinite() || d !in 1.0..100.0 -> "Enter a distance from 1 to 100 km."
            date.isNotBlank() && (parsedDate==null || parsedDate<LocalDate.now()) -> "Enter today or a future date as YYYY-MM-DD."
            target.isNotBlank() && seconds==null -> "Use mm:ss or h:mm:ss for the target."
            else -> null }
        if(error==null) { m.saveGoal(Goal(d!!*1000,parsedDate,seconds,LocalDate.now())); close() }
    }) { Text("Save goal") }},dismissButton={TextButton(onClick=close) { Text("Cancel") }})
}
@Composable fun RunDialog(m: CoachViewModel, close: ()->Unit) {
    val initial = ZonedDateTime.now().minusHours(1)
    var date by remember { mutableStateOf(initial.toLocalDate().toString()) }; var time by remember { mutableStateOf(initial.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))) }; var distance by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }; var indoor by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=close,title={Text("Add a completed run")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Use this for runs that are missing from Health Connect. The engine cleans duplicates and implausible speeds.",color=Muted)
        Field(date,{date=it},"Date · YYYY-MM-DD")
        Field(time,{time=it},"Start time · HH:mm (24-hour)")
        Field(distance,{distance=it},"Distance (${if(m.saved.imperial) "miles" else "km"})",true)
        Field(duration,{duration=it},"Elapsed time · mm:ss or h:mm:ss")
        Toggle("Treadmill run",indoor,{indoor=it})
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }},confirmButton={TextButton(onClick={
        val day=runCatching { LocalDate.parse(date.trim()) }.getOrNull(); val d=distance.toDoubleOrNull(); val seconds=parseTime(duration)
        val clock=runCatching { LocalTime.parse(time.trim()) }.getOrNull()
        val metres=(d ?: 0.0)*if(m.saved.imperial) 1609.344 else 1000.0
        error=when { day==null || day>LocalDate.now() || day<LocalDate.now().minusYears(2) -> "Enter a completed run date within the last two years."
            clock==null -> "Enter a start time as HH:mm."
            day.atTime(clock).atZone(ZoneId.systemDefault()).toInstant().plusSeconds((seconds ?: 0.0).toLong()) > Instant.now() -> "A completed run must finish in the past."
            d==null || !d.isFinite() || metres !in 100.0..200000.0 -> "Enter a valid distance."
            seconds==null || seconds>48*3600 -> "Enter elapsed time as mm:ss or h:mm:ss."
            else -> null }
        if(error==null) { m.addRun(day!!,clock!!,metres,seconds!!,indoor);close() }
    },enabled=!m.demo) { Text("Save run") }},dismissButton={TextButton(onClick=close) { Text("Cancel") }})
}
@Composable fun FeedbackDialog(m: CoachViewModel, close: ()->Unit) {
    var soreness by remember { mutableFloatStateOf(0f) }; var energy by remember { mutableFloatStateOf(3f) }
    var niggle by remember { mutableStateOf(false) }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=close,title={Text("How are you feeling?")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Soreness · ${soreness.toInt()}/5"); Slider(soreness,{soreness=it},valueRange=0f..5f,steps=4)
        Text("Energy · ${energy.toInt()}/5"); Slider(energy,{energy=it},valueRange=1f..5f,steps=3)
        Toggle("A niggle or pain",niggle,{niggle=it})
        OutlinedTextField(note,{note=it.take(500)},label={Text("A note for your diary")},modifier=Modifier.fillMaxWidth())
        Text("Soreness of 4+, very low energy, or a niggle suppresses hard sessions for 48 hours. Notes are stored as written; use the controls above to adjust training.",color=Muted,style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={m.checkIn(soreness.toInt(),energy.toInt(),niggle,note);close()},enabled=!m.demo) { Text("Save check-in") }},dismissButton={TextButton(onClick=close) { Text("Cancel") }})
}
@Composable fun SettingsDialog(m: CoachViewModel, connect: ()->Unit, close: ()->Unit) {
    val c=m.saved.constraints; val p=m.saved.profile; val context=LocalContext.current
    var sessions by remember { mutableFloatStateOf(c.sessions.toFloat()) }; var intensity by remember { mutableFloatStateOf(c.intensity.toFloat()) }
    var blocked by remember { mutableStateOf(c.blackout) }; var longDay by remember { mutableStateOf(c.longDay) }
    var weekly by remember { mutableStateOf(c.maxWeekly?.let { (it/1000).toString() } ?: "") }
    var minutes by remember { mutableStateOf(c.maxMinutes?.toString() ?: "") }; var imperial by remember { mutableStateOf(m.saved.imperial) }
    var treadmill by remember { mutableStateOf(c.treadmillOnly) }; var merge by remember { mutableStateOf(p.mergeDuplicates) }
    var birth by remember { mutableStateOf(p.birthYear?.toString() ?: "") }; var resting by remember { mutableStateOf(p.restingHeartRate?.toString() ?: "") }
    var cross by remember { mutableStateOf(p.countsCrossTraining) };var rhrDelta by remember{mutableStateOf(p.restingHRDelta?.toString()?:"")};var hrvDelta by remember{mutableStateOf(p.hrvDelta?.let{(it*100).toString()}?:"")}
    var extra by remember{mutableStateOf<String?>(null)}
    var maxHr by remember { mutableStateOf(p.observedMaxHeartRate?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }; var erase by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=close,title={Text("Make room for running")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Sessions per week · ${sessions.toInt()}"); Slider(sessions,{sessions=it},valueRange=2f..7f,steps=4)
        Text("Intensity · ${intensity.toInt()}/5"); Slider(intensity,{intensity=it},valueRange=1f..5f,steps=3)
        Text("Days you cannot run",color=Muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { DayOfWeek.entries.forEach { day ->
            TextButton(onClick={blocked=if(day in blocked) blocked-day else blocked+day},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(32.dp)) {
                Text(day.name.take(2),color=if(day in blocked) MaterialTheme.colorScheme.error else Lime,style=MaterialTheme.typography.labelSmall)
            }
        } }
        Text("Preferred long run · ${longDay.name.lowercase().replaceFirstChar { it.uppercase() }}")
        TextButton(onClick={longDay=longDay.plus(1)}) { Text("Change long-run day") }
        Field(weekly,{weekly=it},"Maximum weekly km (optional)",true)
        Field(minutes,{minutes=it},"Maximum session minutes (optional)",true)
        Toggle("Show miles and min/mile",imperial,{imperial=it})
        Toggle("Treadmill only",treadmill,{treadmill=it})
        Toggle("Merge duplicate recordings",merge,{merge=it})
        Toggle("Include cross-training fatigue",cross,{cross=it})
        TextButton(onClick={extra="dates"}){Text("One-off unavailable dates")}
        TextButton(onClick={extra="starter"}){Text("Starting ability / run-walk plan")}
        ReminderSetting(m)
        Text("Optional heart-rate profile",color=Lime)
        Field(birth,{birth=it},"Birth year",true); Field(resting,{resting=it},"Resting heart rate (bpm)",true); Field(maxHr,{maxHr=it},"Observed maximum heart rate (bpm)",true)
        Field(rhrDelta,{rhrDelta=it},"Resting HR change vs baseline (bpm)",true)
        Field(hrvDelta,{hrvDelta=it},"HRV change vs baseline (%)",true)
        Text("Optional baseline overrides. Otherwise, permitted Health Connect resting HR and HRV use 3 recent days against at least 7 baseline days. Clear overrides when they no longer apply.",color=Muted)
        TextButton(onClick={extra="help"}){Text("Help, glossary & watch setup")}
        TextButton(onClick=connect,enabled=!m.demo) { Text("Manage Health Connect access") }
        TextButton(onClick={context.startActivity(Intent(context,PrivacyActivity::class.java))}) { Text("Privacy & how data is used") }
        TextButton(onClick={m.showDemo(!m.demo);close()}) { Text(if(m.demo) "Exit sample history" else "Explore sample history") }
        TextButton(onClick={erase=true}) { Text("Clear all local data",color=MaterialTheme.colorScheme.error) }
        Text("CoachMeSimple ${BuildConfig.VERSION_NAME} · test build\n${ENGINE_VERSION}",color=Muted,style=MaterialTheme.typography.labelSmall)
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }},confirmButton={TextButton(onClick={
        val w=weekly.toDoubleOrNull(); val mins=minutes.toIntOrNull(); val year=birth.toIntOrNull(); val rest=resting.toDoubleOrNull(); val hr=maxHr.toDoubleOrNull()
        error=when { blocked.size>5 -> "Leave at least two days available."
            weekly.isNotBlank() && (w==null || !w.isFinite() || w !in 5.0..250.0) -> "Weekly distance must be 5–250 km."
            minutes.isNotBlank() && (mins==null || mins !in 15..360) -> "Session limit must be 15–360 minutes."
            birth.isNotBlank() && (year==null || year !in (LocalDate.now().year-100)..LocalDate.now().year-13) -> "Enter a valid birth year (age 13–100)."
            resting.isNotBlank() && (rest==null || rest !in 25.0..120.0) -> "Resting heart rate must be 25–120 bpm."
            maxHr.isNotBlank() && (hr==null || hr !in 100.0..230.0) -> "Maximum heart rate must be 100–230 bpm."
            rhrDelta.isNotBlank() && (rhrDelta.toDoubleOrNull()?.let{it.isFinite()&&it in -50.0..50.0}!=true) -> "Resting HR change must be −50 to 50 bpm."
            hrvDelta.isNotBlank() && (hrvDelta.toDoubleOrNull()?.let{it.isFinite()&&it in -100.0..200.0}!=true) -> "HRV change must be −100 to 200%."
            else -> null }
        if(error==null) { m.saveConstraints(Constraints(sessions.toInt(),intensity.toInt(),blocked,longDay=longDay,maxWeekly=w?.times(1000),maxMinutes=mins,treadmillOnly=treadmill),imperial,Profile(year,rest,hr,merge,cross,rhrDelta.toDoubleOrNull(),hrvDelta.toDoubleOrNull()?.div(100)));close() }
    },enabled=!m.demo) { Text("Save settings") }},dismissButton={TextButton(onClick=close) { Text("Close") }})
    when(extra){"dates"->UnavailableDialog(m,{extra=null});"starter"->StarterDialog(m,{extra=null});"help"->HelpDialog({extra=null})}
    if(erase) AlertDialog(onDismissRequest={erase=false},title={Text("Clear local CoachMeSimple data?")},text={Text("Deletes cached runs, manual runs, feedback, goals and preferences from this phone. Health Connect originals are untouched.")},
        confirmButton={TextButton(onClick={m.erase();erase=false;close()}) { Text("Clear data") }},dismissButton={TextButton(onClick={erase=false}) { Text("Cancel") }})
}
