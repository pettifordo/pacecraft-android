package com.pacecraft.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pacecraft.engine.*
import kotlinx.coroutines.launch
import java.time.*
import kotlin.math.roundToInt

@Composable fun StarterDialog(m:CoachViewModel,close:()->Unit) {
    val current=m.saved.starter
    var ability by remember{mutableStateOf(current?.ability?:Ability.JOG5)}
    var runs by remember{mutableFloatStateOf((current?.runsPerWeek?:3).toFloat())}
    var minutes by remember{mutableFloatStateOf((current?.minutes?:20).toFloat())}
    AlertDialog(onDismissRequest=close,title={Text("Start where you are")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("You can start without six weeks of history. Choose what you can comfortably do today.",color=Muted)
        Ability.entries.forEach { a -> Row(Modifier.fillMaxWidth().clickable{ability=a}) { RadioButton(ability==a,{ability=a});Text(a.label,Modifier.padding(top=12.dp)) } }
        Text("Runs per week · ${runs.roundToInt()}");Slider(runs,{runs=it},valueRange=2f..5f,steps=2)
        Text("Time per session · ${minutes.roundToInt()} min");Slider(minutes,{minutes=it},valueRange=10f..60f,steps=9)
        Text("Walking and jogging alternate for beginners. Paces are provisional; race estimates appear once enough real outdoor runs have accumulated. Your check-ins and availability still apply.",color=Muted)
    }},confirmButton={TextButton(onClick={m.startFromProfile(StarterProfile(ability,runsPerWeek=runs.roundToInt(),minutes=minutes.roundToInt(),created=current?.created?:LocalDate.now()));close()},enabled=!m.demo){Text("Build my starting plan")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}
@Composable fun AdjustmentDialog(m:CoachViewModel,close:()->Unit) {
    val r=m.result?:return;val base=r.next?:return;val f=r.fitness?:return;val load=r.load?:return
    var length by remember{mutableIntStateOf(0)};var speed by remember{mutableIntStateOf(0)};var days by remember{mutableIntStateOf(0)}
    val a=SessionAdjustment(length,speed,days)
    val preview=remember(a,base){SessionAdjuster.preview(base,a,r.runs,f,load,m.saved.trainingProfile(),m.saved.constraints,m.saved.feedback,Instant.now(),ZoneId.systemDefault(),if(m.saved.profile.countsCrossTraining)m.saved.cross else emptyList())}
    AlertDialog(onDismissRequest=close,title={Text("Adjust this session")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Preview the consequences before applying. Extra work is refused when it breaches your limits or recovery flags.",color=Muted)
        AdjustmentControl("Length · $length steps (10% each)",{length=(length-1).coerceAtLeast(-5)},{length=(length+1).coerceAtMost(5)})
        AdjustmentControl("Speed · $speed steps (2.5% each)",{speed=(speed-1).coerceAtLeast(-4)},{speed=(speed+1).coerceAtMost(4)})
        AdjustmentControl("Move · $days days",{days=(days-1).coerceAtLeast(-14)},{days=(days+1).coerceAtMost(14)})
        Text("${preview.session.day} · ${Display.distance(preview.session.distance,m.saved.imperial)} ${if(m.saved.imperial)"mi" else "km"} · ${Display.time(preview.session.seconds)}")
        PrescriptionSummary(preview.session,f,m.saved.imperial)
        preview.notes.forEach{Text(it,color=if(preview.allowed) Muted else MaterialTheme.colorScheme.error)}
        if(m.saved.override!=null) TextButton(onClick={m.resetAdjustment();close()}){Text("Reset to engine recommendation")}
    }},confirmButton={TextButton(onClick={m.adjust(preview);close()},enabled=preview.allowed&&!m.demo){Text("Apply adjustment")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}
@Composable private fun AdjustmentControl(label:String,minus:()->Unit,plus:()->Unit) {
    Text(label);Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(onClick=minus){Text("−")};OutlinedButton(onClick=plus){Text("+")}}
}
@Composable fun UnavailableDialog(m:CoachViewModel,close:()->Unit) {
    var dates by remember{mutableStateOf(m.saved.constraints.blackoutDates.filter{it>=LocalDate.now()}.toSet())}
    var input by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=close,title={Text("One-off unavailable dates")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Use this for travel, work or other days off. Weekly availability is in Settings.",color=Muted)
        OutlinedTextField(input,{input=it},label={Text("YYYY-MM-DD")},singleLine=true)
        TextButton(onClick={val d=runCatching{LocalDate.parse(input.trim())}.getOrNull();if(d==null||d<LocalDate.now())error="Enter today or a future date." else{dates=dates+d;input="";error=null}}){Text("Add date")}
        dates.sorted().forEach{d->Row{Text(d.toString(),Modifier.weight(1f));TextButton(onClick={dates=dates-d}){Text("Remove")}}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={m.unavailableDates(dates);close()},enabled=!m.demo){Text("Save dates")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}
@Composable fun WorkoutActions(m:CoachViewModel,s:Session,f:Fitness) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();var status by remember{mutableStateOf<String?>(null)};var sending by remember{mutableStateOf(false)}
    if(s.kind==Kind.REST) return
    OutlinedButton(onClick={sending=true;scope.launch{status=try{WatchBridge.send(context,s,f,m.saved.imperial)}catch(e:Exception){"Could not send: ${e.message}. Pair your watch through its Wear OS companion app."};sending=false}},enabled=!sending&&!m.demo,modifier=Modifier.fillMaxWidth()){Text(if(sending)"Sending…" else "Send workout to Wear OS")}
    TextButton(onClick={runCatching{PlanExport.workout(context,s,f)}.onFailure{status="Export failed: ${it.message}"}},enabled=!m.demo){Text("Share structured workout")}
    status?.let{Text(it,color=Muted)}
}
@Composable fun PlanExportActions(m:CoachViewModel,plan:FullPlan,f:Fitness) {
    val context=LocalContext.current;var error by remember{mutableStateOf<String?>(null)}
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick={runCatching{PlanExport.pdf(context,plan,f,m.saved.imperial)}.onFailure{error=it.message}},modifier=Modifier.weight(1f),enabled=!m.demo){Text("Share plan PDF")}
        OutlinedButton(onClick={runCatching{PlanExport.calendar(context,plan.sessions,f,m.saved.imperial)}.onFailure{error=it.message}},modifier=Modifier.weight(1f),enabled=!m.demo){Text("Next week → calendar")}
    };error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
}
@Composable fun GoalProgress(m:CoachViewModel,edit:()->Unit) {
    val r=m.result?:return;val p=r.prediction?:return;val f=r.fitness?:return;val load=r.load?:return
    val g=m.effectiveGoal
    val feasibility=ProgressAnalysis.feasibility(g,p,load.weekly.takeLast(4).average(),r.sufficiency.weeks,LocalDate.now())
    val progress=ProgressAnalysis.evaluate(g,p,load,m.saved.records,r.runs,f,Instant.now(),ZoneId.systemDefault(),m.saved.driftingChecks)
    Panel {
        Eyebrow("Goal & consistency")
        if(g.targetSeconds!=null) {
            Text("${feasibility.verdict.name.lowercase().replaceFirstChar{it.uppercase()}} target · ${Display.time(g.targetSeconds!!)}")
            Text("Indicative time with available training: ${Display.time(feasibility.achievableSeconds)}. This is a bounded training scenario, not a guarantee.",color=Muted)
            feasibility.achievableDate?.let{Text("Indicative earliest target date · $it",color=Muted)}
            if(g.baselineSeconds!=null&&g.raceDate!=null){Text("Trajectory · ${progress.status.name.lowercase().replace('_',' ')}");Text("Today's trajectory ${Display.time(progress.requiredNow)} · current estimate ${Display.time(p.seconds)}",color=Muted)}
        } else Text("Add a target time to compare your race goal with current fitness.",color=Muted)
        Text("Last 28 days · ${progress.adherence.completed}/${progress.adherence.planned} due sessions matched")
        progress.adherence.volumeRatio?.let{Text("Recorded / prescribed distance · ${(it*100).roundToInt()}%",color=Muted)}
        if(progress.adherence.planned==0)Text("Consistency starts accumulating as you complete prescribed sessions.",color=Muted)
        progress.causes.forEach{Text(it)}
        progress.options.forEach{Text("• $it",color=Muted)}
        TextButton(onClick=edit,enabled=!m.demo){Text("Review goal and race date")}
    }
}
@Composable fun ReminderSetting(m:CoachViewModel) {
    val context=LocalContext.current
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->m.reminders(granted)}
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("Morning workout reminders",Modifier.weight(1f));Switch(m.saved.reminders,{enabled->
        if(enabled&&android.os.Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else m.reminders(enabled)
    },enabled=!m.demo)}
    Text("A daily reminder around 7 am, plus completion recaps after an import. Android may delay background work. Background history access is optional in Health Connect.",color=Muted,style=MaterialTheme.typography.bodySmall)
}
@Composable fun HelpDialog(close:()->Unit) {
    val topics=listOf(
        "How your plan adapts" to "Real runs → clean duplicates → estimate fitness → assess training load → choose a safe next session. Completing a run, changing a goal or checking in rebuilds the forecast. Only the next run is your current prescription.",
        "Where run history comes from" to "On Pixel Watch, enable Fitbit → Health Connect sharing. On Galaxy Watch, enable Samsung Health → Health Connect. Enable exercise and distance sharing, then grant CoachMeSimple access. You can also add runs manually. Third-party writers control which records are available.",
        "Critical Speed / D′" to "Critical Speed estimates sustainable speed from your best recent efforts. D′ models the extra distance available above it. Easy paces use a fraction of Critical Speed. Estimated pace is not a requirement to force the effort.",
        "Intervals, hills and strides" to "Follow warm-up, each work/recovery block, and cool-down. Work pace and jog pace are separate. Hill pace is a flat-ground equivalent: use controlled effort uphill and recover safely downhill. Strides are relaxed, not all-out sprints.",
        "Training load" to "Acute load reflects roughly a week; chronic load reflects six weeks. High ratios and rapid distance increases reduce hard work. Low recent training is not labelled overload. Optional cross-training adds fatigue but never running mileage.",
        "Beginner plans" to "Your self-report supplies provisional paces and a conservative load prior. It never creates completed runs or race estimates. Run/walk steps and a four-week recovery cycle build consistency. With 8 usable runs, 6 weeks and 40 km, measured outdoor history takes over.",
        "The whole plan" to "Later weeks assume completion at today's fitness. Their weekly distance includes completed runs in a partial current week. A race is an event, not extra training mileage. Exported plans are snapshots; check the app before running.",
        "Wear OS setup" to "Install the phone APK on your Android phone and the matching Wear APK on your paired Pixel Watch or Galaxy Watch. Open both apps, then Send workout to Wear OS. Start on your watch while outdoors. GPS and optional heart rate are recorded locally. Completed runs return when the devices reconnect. A run without a measured distance must be added manually.",
        "Privacy & estimates" to "No AI, account or coaching backend. Your selected workout and completed watch run use Google's paired-device Data Layer. Sharing PDF, calendar or JSON exports sends only the files you choose. Predictions and training flags describe uncertain training evidence; they do not diagnose illness."
    )
    AlertDialog(onDismissRequest=close,title={Text("How CoachMeSimple works")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){topics.forEach{(title,body)->Text(title,color=Lime);Text(body)}}},confirmButton={TextButton(onClick=close){Text("Done")}})
}
