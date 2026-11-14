package com.pacecraft.android

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pacecraft.engine.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

val Ink = Color(0xFF101713)
val Surface = Color(0xFF1B241E)
val Lime = Color(0xFFD4F76A)
val Muted = Color(0xFFA4B2A6)
val Cream = Color(0xFFF1F5EC)
@Composable fun PacecraftTheme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Lime,onPrimary=Ink,background=Ink,surface=Surface,onSurface=Cream,
        onBackground=Cream,secondary=Color(0xFF9FCEC1),outline=Color(0xFF4A584C)),content=content)
}
class MainActivity: ComponentActivity() {
    private val model: CoachViewModel by viewModels()
    private val permissionLauncher = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { model.refresh() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { PacecraftTheme { PacecraftApp(model,::connect) } }
    }
    private fun connect() {
        when(model.health.status()) {
            HealthConnectClient.SDK_AVAILABLE -> permissionLauncher.launch(model.health.permissions())
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                val uri=Uri.parse("market://details?id=com.google.android.apps.healthdata")
                try { startActivity(Intent(Intent.ACTION_VIEW,uri)) }
                catch(_: Exception) { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))) }
            }
            else -> startActivity(Intent(this,PrivacyActivity::class.java).putExtra("unavailable",true))
        }
    }
}
class PrivacyActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PacecraftTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Text("Your runs. Your device.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                if(intent.getBooleanExtra("unavailable",false)) Text("Health Connect is unavailable on this device. You can still add runs manually or explore the demo.")
                Text("Pacecraft reads exercise sessions and distance to understand your running history. Optional heart rate and elevation help assess effort. History access allows up to 26 weeks of data, instead of the default limited window.")
                Text("Your data is stored only on this phone. There is no account, advertising, analytics, AI service or app network permission. Android cloud backup is disabled. Health Connect controls the data supplied by your recording apps.")
                Text("You can revoke access in Health Connect. When the app checks permissions again, it removes its imported cache if exercise or distance permission is missing. Clear local data in Settings to delete all cached runs, feedback and preferences. This does not delete the original health records.")
                Text("The coach uses deterministic rules. Training estimates are uncertain and are not medical advice. Stop and seek appropriate advice if you have pain or feel unwell.")
                Text("Testing build · ${BuildConfig.VERSION_NAME}",color=Muted)
                Button(onClick={finish()}) { Text("Back to Pacecraft") }
            }
        } } }
    }
}
@Composable fun PacecraftApp(model: CoachViewModel, connect: ()->Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val pageScroll = rememberScrollState()
    LaunchedEffect(tab, model.demo) { pageScroll.scrollTo(0) }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) model.refresh() }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(model.message) { model.message?.let { snackbar.showSnackbar(it,duration=SnackbarDuration.Long); model.clearMessage() } }
    Scaffold(containerColor=Ink,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        NavigationBar(containerColor=Ink,tonalElevation=0.dp) {
            val labels=listOf("Next run","Plan","Progress","History","Coach")
            val icons=listOf(Icons.Default.PlayArrow,Icons.Default.DateRange,Icons.Default.Star,Icons.Default.List,Icons.Default.Email)
            labels.forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],label)},label={Text(label)},
                colors=NavigationBarItemDefaults.colors(selectedIconColor=Ink,indicatorColor=Lime,selectedTextColor=Lime,unselectedIconColor=Muted,unselectedTextColor=Muted)) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=22.dp).verticalScroll(pageScroll),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow,null,tint=Lime,modifier=Modifier.size(27.dp)); Spacer(Modifier.width(8.dp))
                Text("pacecraft",fontSize=25.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp,modifier=Modifier.weight(1f))
                IconButton(onClick={dialog="settings"}) { Icon(Icons.Default.Settings,"Settings",tint=Muted) }
            }
            if(model.demo) Row(Modifier.fillMaxWidth().background(Lime.copy(alpha=.13f),RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("DEMO · Sample history",color=Lime,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                TextButton(onClick={model.showDemo(false)}) { Text("Exit demo") }
            }
            if(model.busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Lime)
            when(tab) {
                0 -> NextScreen(model,{dialog=it},connect,{tab=1})
                1 -> FullPlanScreen(model,{dialog="goal"})
                2 -> ProgressScreen(model)
                3 -> HistoryScreen(model,{dialog="run"},connect)
                4 -> CoachScreen(model,{dialog="feedback"})
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    when(dialog) {
        "goal" -> GoalDialog(model,{dialog=null})
        "settings" -> SettingsDialog(model,connect,{dialog=null})
        "run" -> RunDialog(model,{dialog=null})
        "feedback" -> FeedbackDialog(model,{dialog=null})
        "skip" -> AlertDialog(onDismissRequest={dialog=null},title={Text("Skip this day?")},text={Text("The next run will move to an available day. Your run history stays the same.")},
            confirmButton={TextButton(onClick={model.skip();dialog=null}) { Text("Skip day") }},dismissButton={TextButton(onClick={dialog=null}) { Text("Cancel") }})
    }
}
@Composable fun Eyebrow(text: String) { Text(text.uppercase().replace('_',' '),color=Lime,fontSize=11.sp,letterSpacing=1.8.sp,fontWeight=FontWeight.Bold) }
@Composable fun Heading(title: String, subtitle: String) { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,fontSize=34.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp); Text(subtitle,color=Muted,fontSize=14.sp,lineHeight=21.sp) } }
@Composable fun Panel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().background(Surface,RoundedCornerShape(22.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
}
@Composable fun Stat(label: String, value: String, modifier: Modifier=Modifier) { Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) { Text(value,fontSize=25.sp,fontWeight=FontWeight.SemiBold); Text(label,color=Muted,fontSize=12.sp) } }
@Composable fun NextScreen(m: CoachViewModel, open: (String)->Unit, connect: ()->Unit, viewPlan: ()->Unit) {
    val r=m.result; val next=r?.next; val imperial=m.saved.imperial; val unit=if(imperial) "mi" else "km"
    Eyebrow("One run at a time")
    if(next==null) {
        Heading("Your next chapter\nstarts here.","A running plan that changes with you. Connect your history and let the numbers guide your next run.")
        Panel {
            Icon(Icons.Default.FavoriteBorder,null,tint=Lime,modifier=Modifier.size(32.dp))
            Text("Start with your history",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Read up to 26 weeks of runs from Health Connect. Your watch or running app needs to share exercise sessions and distance there.",color=Muted,lineHeight=22.sp)
            Button(onClick=connect,modifier=Modifier.fillMaxWidth(),enabled=!m.demo) { Text("Connect Health Connect") }
            OutlinedButton(onClick={open("run")},modifier=Modifier.fillMaxWidth(),enabled=!m.demo) { Text("Add a run manually") }
        }
        Panel {
            Eyebrow("Establishing your base")
            val s=r?.sufficiency
            Text("${s?.count ?: 0} / 8 runs  ·  ${"%.1f".format(s?.weeks ?: 0.0)} / 6 weeks",fontWeight=FontWeight.SemiBold)
            Text("${Display.distance(s?.distance ?: 0.0,imperial)} / ${Display.distance(40000.0,imperial)} $unit of usable running",color=Muted)
            Text(r?.comments?.firstOrNull() ?: "Enough evidence makes for a more honest estimate.",color=Muted,lineHeight=21.sp)
        }
        TextButton(onClick={m.showDemo(!m.demo)},modifier=Modifier.fillMaxWidth()) { Text(if(m.demo) "Leave demo" else "Explore with sample history →") }
        TextButton(onClick={open("goal")},enabled=!m.demo) { Text("Set your race goal") }
        return
    }
    val f=r.fitness!!
    Heading(if(next.kind==Kind.REST) "Let the work\nsink in." else "Make this\none count.","${next.day.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))} · ${next.phase.name.lowercase().replace('_',' ')} phase")
    Column(Modifier.fillMaxWidth().background(Lime,RoundedCornerShape(26.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("YOUR NEXT RUN",color=Ink,fontSize=11.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            Icon(if(next.kind==Kind.REST) Icons.Default.FavoriteBorder else Icons.Default.ArrowForward,null,tint=Ink)
        }
        Text(next.kind.label,color=Ink,fontSize=28.sp,fontWeight=FontWeight.Bold)
        Text(if(next.kind==Kind.REST) "Rest day" else "${Display.distance(next.distance,imperial)} $unit",color=Ink,fontSize=51.sp,fontWeight=FontWeight.Bold,letterSpacing=(-2).sp)
        if(next.kind!=Kind.REST) {
            HorizontalDivider(color=Ink.copy(alpha=.20f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column { Text("EST. TIME",color=Ink.copy(alpha=.65f),fontSize=10.sp); Text("${(next.seconds/60).roundToInt()} min",color=Ink,fontWeight=FontWeight.Bold,fontSize=20.sp) }
                Column { Text("PACE / $unit".uppercase(),color=Ink.copy(alpha=.65f),fontSize=10.sp)
                    Text("${Display.pace(f.cs*next.kind.high,imperial)}–${Display.pace(f.cs*next.kind.low,imperial)}",color=Ink,fontWeight=FontWeight.Bold,fontSize=20.sp) }
            }
        }
    }
    OutlinedButton(onClick=viewPlan,modifier=Modifier.fillMaxWidth()) { Text("View whole plan →") }
    Panel { Eyebrow("Why this run"); next.reasons.forEach { Text(it,lineHeight=23.sp) } }
    if(next.steps.isNotEmpty()) Panel {
        Eyebrow("The session")
        next.steps.forEachIndexed { i,s -> Row(verticalAlignment=Alignment.CenterVertically) {
            Text("%02d".format(i+1),color=Lime,fontWeight=FontWeight.Bold,modifier=Modifier.width(35.dp))
            Column(Modifier.weight(1f)) { Text(s.label,fontWeight=FontWeight.Medium); Text("${Display.distance(s.distance,imperial)} $unit · ${Display.time(s.seconds)}",color=Muted,fontSize=12.sp) }
            Text("${Display.pace(f.cs*s.kind.high,imperial)}–${Display.pace(f.cs*s.kind.low,imperial)}",fontSize=12.sp)
        } }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Button(onClick={open("feedback")},modifier=Modifier.weight(1f),enabled=!m.demo) { Text("Check in") }
        OutlinedButton(onClick={open("skip")},modifier=Modifier.weight(1f),enabled=!m.demo) { Text("Skip this day") }
    }
    Panel {
        Eyebrow("Your goal")
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("${Display.distance(m.effectiveGoal.distance,imperial)} $unit",fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text(m.effectiveGoal.raceDate?.toString() ?: "Get faster · no race date",color=Muted,fontSize=13.sp) }
            TextButton(onClick={open("goal")},enabled=!m.demo) { Text("Edit") }
        }
    }
}
@Composable fun ProgressScreen(m: CoachViewModel) {
    val r=m.result; val p=r?.prediction; val f=r?.fitness; val load=r?.load; val imperial=m.saved.imperial
    Eyebrow("The bigger picture")
    Heading("Built on\nyour running.","Fitness, fatigue and race estimates from your own history.")
    if(p==null || f==null || load==null) { Panel { Text("Your progress will appear once there is enough usable outdoor running history."); Text("At least 8 runs, 6 weeks and 40 km.",color=Muted) }; return }
    Panel {
        Eyebrow(if(p.beyondEvidence) "Race hypothesis · beyond evidence" else "Current race estimate")
        Text(Display.time(p.seconds),fontSize=52.sp,fontWeight=FontWeight.Bold,letterSpacing=(-2).sp)
        Text("80% band · ${Display.time(p.low)} – ${Display.time(p.high)}",color=Muted)
        Text("${f.confidence.name.lowercase().replaceFirstChar { it.uppercase() }} confidence · ${f.effortCount} frontier ${if(f.effortCount == 1) "effort" else "efforts"}",color=Lime,fontSize=13.sp)
        m.saved.goal.targetSeconds?.let { Text("Target ${Display.time(it)} · ${if(p.seconds<=it) "currently supported" else "ahead of current estimate"}",color=Muted) }
    }
    Panel {
        Eyebrow("Weekly distance · rolling 7-day windows")
        val values=load.weekly
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val maxVal=max(1.0,values.maxOrNull() ?: 1.0); val width=size.width/values.size
            values.forEachIndexed { i,v -> drawRoundRect(if(i==values.lastIndex) Lime else Color(0xFF47604B),
                topLeft=androidx.compose.ui.geometry.Offset(i*width+6,(size.height*(1-v/maxVal)).toFloat()),
                size=androidx.compose.ui.geometry.Size(width-12,(size.height*v/maxVal).toFloat()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(8f)) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { values.forEach { Text(Display.distance(it,imperial),color=Muted,fontSize=12.sp) } }
        Text("${if(imperial) "Miles" else "Kilometres"} · oldest to newest",color=Muted,fontSize=11.sp)
    }
    Panel {
        Eyebrow("Training load")
        Row(Modifier.fillMaxWidth()) { Stat("Acute / chronic", "%.2f".format(load.ratio),Modifier.weight(1f)); Stat("Load status",when(load.flag) { Flag.GREEN->"Balanced"; Flag.AMBER->"Ease back"; Flag.RED->"Recovery" },Modifier.weight(1f)) }
        Row { Stat("Acute load","%.1f".format(load.acute),Modifier.weight(1f)); Stat("Chronic load","%.1f".format(load.chronic),Modifier.weight(1f)) }
        load.reasons.forEach { Text(it,color=Color(0xFFFFC795),fontSize=13.sp) }
        if(load.ratio<.8) Text("Less recent training is shown separately from overload.",color=Muted,fontSize=13.sp)
    }
    Panel {
        Eyebrow("Your engine")
        Row { Stat("Critical pace / ${if(imperial) "mi" else "km"}",Display.pace(f.cs,imperial),Modifier.weight(1f)); Stat("Endurance exponent","%.2f".format(f.exponent),Modifier.weight(1f)) }
        Text("Paces and estimates come from a Critical Speed / D′ model. A lower endurance exponent means less predicted fade as distance increases.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
    }
    if(p.caveats.isNotEmpty()) Panel { Eyebrow("Read with the estimate"); p.caveats.forEach { Text(it,color=Muted,fontSize=13.sp,lineHeight=20.sp) } }
}
@Composable fun HistoryScreen(m: CoachViewModel, add: ()->Unit, connect: ()->Unit) {
    val runs=if(m.demo) m.result?.runs.orEmpty() else m.saved.runs.sortedByDescending { it.start }
    var selected by remember { mutableStateOf<Run?>(null) }
    val imperial=m.saved.imperial; val unit=if(imperial) "mi" else "km"
    Eyebrow("Every run counts")
    Heading("Your running\nstory.","Run history powers every pace, prediction and training decision.")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Button(onClick={m.refresh()},enabled=!m.busy&&!m.demo,modifier=Modifier.weight(1f)) { Icon(Icons.Default.Refresh,null,Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Refresh") }
        OutlinedButton(onClick=add,enabled=!m.demo,modifier=Modifier.weight(1f)) { Text("Add run") }
    }
    Text(m.saved.lastSync?.let { "Last import · ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}" } ?: "No Health Connect import yet",color=Muted,fontSize=12.sp)
    if(runs.isEmpty()) Panel { Text("No runs yet",fontWeight=FontWeight.Bold); Text("Connect your recording app to Health Connect, then allow Pacecraft to read exercise and distance.",color=Muted); TextButton(onClick=connect) { Text("Set up Health Connect") } }
    runs.sortedByDescending { it.start }.forEach { run ->
        Row(Modifier.fillMaxWidth().clickable { selected=run }.background(Surface,RoundedCornerShape(16.dp)).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow,null,tint=Lime,modifier=Modifier.padding(end=14.dp))
            Column(Modifier.weight(1f)) { Text(run.day(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM")),fontWeight=FontWeight.Medium)
                Text("${Display.time(run.duration)} · ${Display.pace(run.speed,imperial)} / $unit",color=Muted,fontSize=12.sp)
                if(!Cleaner.usable(run)) Text("Excluded from engine · outside cleaning limits",color=Color(0xFFFFC795),fontSize=11.sp)
            }
            Text("${Display.distance(run.distance,imperial)} $unit",fontWeight=FontWeight.Bold)
        }
    }
    selected?.let { run -> AlertDialog(onDismissRequest={selected=null},title={Text("Run details")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("${run.day(ZoneId.systemDefault())} · ${Display.distance(run.distance,imperial)} $unit · ${Display.time(run.duration)}")
        Text("Source: ${run.source}"); run.heartRate?.let { Text("Average heart rate: ${it.roundToInt()} bpm") }
        run.ascent?.let { Text("Ascent: ${it.roundToInt()} m") }; Text(if(run.indoor) "Indoor · load only" else "Outdoor")
        val rec=m.saved.records.firstOrNull { it.runId==run.id }; if(rec!=null) Text("Matched to ${rec.session.kind.label} on ${rec.session.day}")
        if(run.id.startsWith("hc:")) Text("Imported records are managed by Health Connect. Refresh replaces the cache.",color=Muted)
    }},confirmButton={TextButton(onClick={selected=null}) { Text("Done") }},dismissButton={if(run.id.startsWith("manual:")&&!m.demo) TextButton(onClick={m.deleteRun(run.id);selected=null}) { Text("Delete run") }}) }
}
@Composable fun CoachScreen(m: CoachViewModel, checkIn: ()->Unit) {
    Eyebrow("Clear advice. No AI required.")
    Heading("A little context.\nA better run.","Comments follow the engine’s facts. No generated prescriptions, no cloud service.")
    m.result?.comments.orEmpty().forEachIndexed { i,comment -> Panel { Text("%02d".format(i+1),color=Lime,fontSize=12.sp); Text(comment,lineHeight=24.sp) } }
    if(m.result?.comments.isNullOrEmpty()) Panel { Text("Connect or add your run history to get training comments.") }
    Button(onClick=checkIn,enabled=!m.demo,modifier=Modifier.fillMaxWidth()) { Text("How are you feeling?") }
    if(m.saved.feedback.isNotEmpty()&&!m.demo) Panel { Eyebrow("Latest check-in"); val f=m.saved.feedback.last(); Text("Energy ${f.energy}/5 · soreness ${f.soreness}/5${if(f.niggle) " · niggle reported" else ""}"); if(f.note.isNotBlank()) Text(f.note,color=Muted) }
    Panel { Icon(Icons.Default.Lock,null,tint=Lime); Text("Private by design",fontWeight=FontWeight.Bold); Text("Your history and feedback stay on your phone. Every training number is calculated locally.",color=Muted,lineHeight=21.sp) }
}
