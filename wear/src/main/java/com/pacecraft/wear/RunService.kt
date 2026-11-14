package com.pacecraft.wear

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.location.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pacecraft.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Active exercise runs only after a visible start/resume action. Sensors never supply target distance. */
class RunService:Service(),LocationListener,SensorEventListener {
    companion object {
        val live=MutableStateFlow<RunSnapshot?>(null)
        var running=false;private set
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val handler=Handler(Looper.getMainLooper())
    private val locations by lazy{getSystemService(LocationManager::class.java)}
    private val sensors by lazy{getSystemService(SensorManager::class.java)}
    private var state:RunSnapshot?=null;private var anchor=0L;private var anchorSeconds=0.0
    private var lastLocation:Location?=null;private var lastStep=-1
    private var wake:PowerManager.WakeLock?=null
    override fun onBind(intent:Intent?)=null
    private val tick=object:Runnable {override fun run(){
        state?.let{s->if(!s.paused){state=s.copy(elapsed=anchorSeconds+(SystemClock.elapsedRealtime()-anchor)/1000.0)
            val current=state!!;if(current.elapsed>=current.workout.seconds){finishRun();return}
            if(current.stepIndex!=lastStep){if(lastStep>=0)buzz();lastStep=current.stepIndex}
        };publish()};handler.postDelayed(this,1000)
    }}
    override fun onCreate(){super.onCreate();running=true;state=WatchStore.savedRun(this)?.let{runCatching{RunSnapshot.decode(it).copy(paused=true)}.getOrNull()}}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // Never resume tracking after a process restart without a visible user action.
        state?.let{if(!it.paused)state=it.copy(elapsed=anchorSeconds+(SystemClock.elapsedRealtime()-anchor)/1000.0)}
        when(intent?.action) {
            "START" -> if(state==null){val w=WatchStore.workout(this)?:return stopEmpty();state=RunSnapshot(UUID.randomUUID().toString(),w,Instant.now().toString());lastStep=-1}
            "PAUSE" -> {state=state?.copy(paused=true,speed=null);lastLocation=null}
            "RESUME" -> state=state?.copy(paused=false)
            "END" -> {if(state!=null){foreground();finishRun()}else stopSelf();return START_NOT_STICKY}
            else -> {stopSelf();return START_NOT_STICKY}
        }
        val current=state?:return stopEmpty()
        if(!foreground())return START_NOT_STICKY
        if(intent?.action=="START")state=current.copy(paused=false)
        anchor=SystemClock.elapsedRealtime();anchorSeconds=state!!.elapsed
        handler.removeCallbacks(tick);handler.post(tick)
        if(state?.paused==false)startSensors()else stopSensors()
        publish();return START_NOT_STICKY
    }
    private fun stopEmpty():Int{stopSelf();return START_NOT_STICKY}
    private fun foreground():Boolean {
        val manager=getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("active-run","Active workout",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,WatchActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(this,"active-run").setSmallIcon(com.pacecraft.wear.R.drawable.ic_coach).setContentTitle("CoachMeSimple workout")
            .setContentText("Tap to view steps, pause or finish").setOngoing(true).setContentIntent(open).build()
        val health=checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.BODY_SENSORS)==PackageManager.PERMISSION_GRANTED
        if(!health){WatchStore.summary(this,"Allow physical activity access to start a workout.");stopSelf();return false}
        val location=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val types=(if(Build.VERSION.SDK_INT>=34)ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0) or (if(location)ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        return try{ServiceCompat.startForeground(this,11,n,types);true}
        catch(e:Exception){WatchStore.summary(this,"Could not start tracking: ${e.message}");stopSelf();false}
    }
    private fun startSensors(){
        if(wake==null)wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"CoachMeSimple:workout").apply{acquire(12*60*60*1000L)}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)runCatching{locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,0f,this)}
        if(checkSelfPermission(Manifest.permission.BODY_SENSORS)==PackageManager.PERMISSION_GRANTED)sensors.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let{sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
    }
    private fun stopSensors(){locations.removeUpdates(this);sensors.unregisterListener(this);lastLocation=null;wake?.let{if(it.isHeld)it.release()};wake=null}
    override fun onLocationChanged(location:Location){
        val s=state?:return;if(s.paused||!location.hasAccuracy()||location.accuracy>30||SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos>15_000_000_000)return
        val speed=location.speed.toDouble().takeIf{location.hasSpeed()&&it in .0..12.0}
        val previous=lastLocation;var distance=s.metres;var accepted=false
        if(previous!=null){val dt=(location.elapsedRealtimeNanos-previous.elapsedRealtimeNanos)/1e9;val d=previous.distanceTo(location).toDouble()
            accepted=DistanceFilter.accept(d,dt,maxOf(previous.accuracy,location.accuracy).toDouble(),speed)
            if(accepted)distance+=d
            if(dt>30||speed!=null&&speed<.4)lastLocation=location
        }
        if(previous==null||accepted)lastLocation=location
        state=s.copy(metres=distance,speed=speed?.takeIf{it>=.3},gps=true);publish()
    }
    override fun onSensorChanged(event:SensorEvent){val s=state?:return;if(s.paused)return;val hr=event.values.firstOrNull()?.toDouble()?:return
        if(hr in 25.0..240.0)state=s.copy(hrTotal=s.hrTotal+hr,hrCount=s.hrCount+1)
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
    private fun publish(){state?.let{live.value=it;WatchStore.saveRun(this,it.encode())}}
    private fun buzz(){getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(400,VibrationEffect.DEFAULT_AMPLITUDE))}
    private fun finishRun(){
        val s=state?:return;handler.removeCallbacks(tick);stopSensors();buzz()
        if(s.metres>=100&&s.elapsed>=60){WatchStore.enqueue(this,CompletedWorkout(s.id,Instant.parse(s.start),s.metres,s.elapsed,s.averageHr,s.workout.id));WatchStore.summary(this,"Run saved: %.2f km · %d min. Queued for your phone.".format(s.metres/1000,(s.elapsed/60).toInt()))}
        else WatchStore.summary(this,"Workout finished: %d min. No complete GPS distance was measured; add the run manually on your phone.".format((s.elapsed/60).toInt()))
        WatchStore.saveRun(this,null);state=null;live.value=null
        scope.launch{runCatching{WatchStore.sync(this@RunService)};stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    }
    override fun onDestroy(){handler.removeCallbacks(tick);stopSensors();state?.let{WatchStore.saveRun(this,it.copy(paused=true,speed=null).encode())};live.value=null;running=false;scope.cancel();super.onDestroy()}
}
data class RunSnapshot(val id:String,val workout:Workout,val start:String,val elapsed:Double=0.0,val metres:Double=0.0,
    val paused:Boolean=false,val speed:Double?=null,val gps:Boolean=false,val hrTotal:Double=0.0,val hrCount:Int=0){
    val stepIndex get():Int{var t=elapsed;workout.steps.forEachIndexed{i,s->if(t<s.seconds)return i;t-=s.seconds};return workout.steps.lastIndex}
    val step get()=workout.steps[stepIndex]
    val remaining get()=(workout.steps.take(stepIndex+1).sumOf{it.seconds}-elapsed).coerceAtLeast(0.0)
    val averageHr get()=if(hrCount>0)hrTotal/hrCount else null
    fun encode()=JSONObject().put("id",id).put("workout",workout.encode()).put("start",start).put("elapsed",elapsed).put("metres",metres).put("paused",paused).put("hrTotal",hrTotal).put("hrCount",hrCount).toString()
    companion object{fun decode(text:String):RunSnapshot{val j=JSONObject(text);return RunSnapshot(j.getString("id"),Workout.decode(j.getString("workout")),j.getString("start"),j.getDouble("elapsed"),j.getDouble("metres"),j.getBoolean("paused"),hrTotal=j.optDouble("hrTotal",0.0),hrCount=j.optInt("hrCount"))}}
}
