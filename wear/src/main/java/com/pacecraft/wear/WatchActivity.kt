package com.pacecraft.wear

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacecraft.shared.Workout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WatchActivity:ComponentActivity(){
    private fun command(action:String){val i=Intent(this,RunService::class.java).setAction(action);if(action in setOf("START","RESUME","END"))startForegroundService(i)else startService(i)}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{
        MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFD4F76A),background=Color(0xFF101713),surface=Color(0xFF1B241E),onPrimary=Color.Black)){
            var workout by remember{mutableStateOf(WatchStore.workout(this))};var restored by remember{mutableStateOf(WatchStore.savedRun(this)?.let{runCatching{RunSnapshot.decode(it).copy(paused=true)}.getOrNull()})}
            val live by RunService.live.collectAsState();var summary by remember{mutableStateOf(WatchStore.summary(this))};var status by remember{mutableStateOf<String?>(null)}
            val scope=rememberCoroutineScope();var end by remember{mutableStateOf(false)};var requested by rememberSaveable{mutableStateOf("START")}
            val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
                runCatching{command(requested)}.onFailure{status="Could not start: ${it.message}"}
            }
            LaunchedEffect(Unit){runCatching{WatchStore.sync(this@WatchActivity)};while(true){workout=WatchStore.workout(this@WatchActivity);summary=WatchStore.summary(this@WatchActivity);if(!RunService.running)restored=WatchStore.savedRun(this@WatchActivity)?.let{runCatching{RunSnapshot.decode(it).copy(paused=true)}.getOrNull()}else restored=null;delay(1000)}}
            val active=live?:restored
            val scroll=rememberScrollState();val focus=remember{FocusRequester()}
            LaunchedEffect(Unit){focus.requestFocus()}
            Surface(color=MaterialTheme.colorScheme.background,contentColor=Color(0xFFF1F5EC)){
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onRotaryScrollEvent{event->scope.launch{scroll.scrollTo((scroll.value+event.verticalScrollPixels).toInt())};true}.focusRequester(focus).focusable().verticalScroll(scroll).padding(horizontal=24.dp,vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(active==null)Text("CoachMeSimple",color=MaterialTheme.colorScheme.primary,fontSize=15.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                if(active!=null){
                    Text(if(active.paused)"Paused" else "${active.stepIndex+1} / ${active.workout.steps.size}",color=MaterialTheme.colorScheme.primary)
                    Text(active.step.label,fontSize=18.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                    Text(clock(active.remaining),fontSize=36.sp,fontWeight=FontWeight.Bold)
                    Text("Target ${pace(active.step.highSpeed,active.workout.imperial)}–${pace(active.step.lowSpeed,active.workout.imperial)} /${if(active.workout.imperial)"mi"else"km"}",textAlign=TextAlign.Center,fontSize=12.sp)
                    Text("${"%.2f".format(active.metres/if(active.workout.imperial)1609.344 else 1000.0)} ${if(active.workout.imperial)"mi"else"km"} · ${clock(active.elapsed)}",fontSize=12.sp)
                    Text(active.speed?.let{"Now ${pace(it,active.workout.imperial)} /${if(active.workout.imperial)"mi"else"km"}"}?:if(active.gps)"Pace unavailable"else "Waiting for GPS",color=MaterialTheme.colorScheme.primary,fontSize=12.sp)
                    active.speed?.let{speed->Text(when{speed>active.step.highSpeed->"Ease the pace";speed<active.step.lowSpeed->"Keep it comfortable";else->"Within target pace"},fontSize=12.sp,textAlign=TextAlign.Center)}
                    active.averageHr?.let{Text("${it.toInt()} bpm average")}
                    Button(onClick={if(active.paused){requested="RESUME";permissions.launch(requiredPermissions())}else command("PAUSE")}){Text(if(active.paused)"Resume" else "Pause")}
                    OutlinedButton(onClick={end=true}){Text(if(end)"Tap again to finish"else "Finish run")}
                    if(end){Button(onClick={command("END");end=false;restored=null}){Text("Save & finish")};TextButton(onClick={end=false}){Text("Keep running")}}
                }else{
                    summary?.let{Text(it,textAlign=TextAlign.Center,fontSize=13.sp)}
                    val w=workout
                    if(w==null){Text("Your next run, on your wrist",fontSize=21.sp,textAlign=TextAlign.Center,fontWeight=FontWeight.Bold);Text("Open CoachMeSimple on your paired phone, then Send workout to Wear OS.",textAlign=TextAlign.Center)}
                    else{Text(w.name,fontSize=23.sp,textAlign=TextAlign.Center,fontWeight=FontWeight.Bold);Text("${w.day} · ${(w.seconds/60).toInt()} min",textAlign=TextAlign.Center)
                        Text("Go outdoors and allow GPS for a measured distance. Heart rate is optional.",textAlign=TextAlign.Center,fontSize=12.sp)
                        Button(onClick={requested="START";permissions.launch(requiredPermissions())}){Text("Start workout")}
                        w.steps.forEachIndexed{i,s->Text("${i+1}. ${s.label} · ${clock(s.seconds)}\n${pace(s.highSpeed,w.imperial)}–${pace(s.lowSpeed,w.imperial)} /${if(w.imperial)"mi"else"km"}",textAlign=TextAlign.Center,fontSize=12.sp)}
                    }
                    OutlinedButton(onClick={scope.launch{status=runCatching{WatchStore.sync(this@WatchActivity);"Synced with paired phone"}.getOrElse{"Sync queued until your phone reconnects."}}}){Text("Sync with phone")}
                }
                status?.let{Text(it,textAlign=TextAlign.Center,fontSize=12.sp)}
            }
            }
        }
    }}
    private fun requiredPermissions()=buildList{add(Manifest.permission.ACTIVITY_RECOGNITION);add(Manifest.permission.ACCESS_FINE_LOCATION);add(Manifest.permission.ACCESS_COARSE_LOCATION);add(Manifest.permission.BODY_SENSORS);if(android.os.Build.VERSION.SDK_INT>=33)add(Manifest.permission.POST_NOTIFICATIONS)}.toTypedArray()
}
private fun clock(seconds:Double):String{val t=seconds.toInt().coerceAtLeast(0);return "%d:%02d".format(t/60,t%60)}
private fun pace(speed:Double,imperial:Boolean=false)=if(speed>.0)clock((if(imperial)1609.344 else 1000.0)/speed)else "—"
