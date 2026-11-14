package com.pacecraft.android

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.work.*
import com.pacecraft.engine.*
import java.time.*
import java.util.concurrent.TimeUnit

object DailyCoach {
    fun schedule(context:Context) {
        val now=ZonedDateTime.now();var next=now.toLocalDate().atTime(7,0).atZone(now.zone)
        if(next<=now)next=next.plusDays(1)
        val request=PeriodicWorkRequestBuilder<CoachWorker>(24,TimeUnit.HOURS).setInitialDelay(Duration.between(now,next)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("coach-daily",ExistingPeriodicWorkPolicy.KEEP,request)
    }
    fun notify(context:Context,id:Int,title:String,body:String) {
        if(android.os.Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("daily-coach","Training reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(id,NotificationCompat.Builder(context,"daily-coach").setSmallIcon(com.pacecraft.android.R.drawable.ic_pacecraft).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(intent).setAutoCancel(true).build())
    }
}
class CoachWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val store=LocalStore(applicationContext)
        return try {
            kotlinx.coroutines.withTimeoutOrNull(3000){runCatching{WearInbox.pull(applicationContext)}}
            WearInbox.import(applicationContext)
            var saved=store.read();val health=HealthHistory(applicationContext);val granted=health.granted()
            val canRead=HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted && HealthPermission.getReadPermission(DistanceRecord::class) in granted
            val oldDone=saved.records.mapNotNull{it.runId}.toSet()
            if(!canRead) saved=store.update{it.copy(runs=it.runs.filterNot{r->r.id.startsWith("hc:")},cross=emptyList(),lastSync=null,importedRHR=null,importedHRV=null)}
            else if(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted) {
                val now=Instant.now();val imported=health.read(now)
                saved=store.update{it.copy(runs=it.runs.filterNot{r->r.id.startsWith("hc:")}+imported.runs,cross=imported.cross,lastSync=now,importedRHR=imported.rhrChange,importedHRV=imported.hrvChange)}
            }
            val now=Instant.now();val zone=ZoneId.systemDefault();val today=now.atZone(zone).toLocalDate()
            val reconciled=CoachingEngine.reconcile(saved.records,saved.runs,zone)
            val result=CoachingEngine.replan(saved.runs,saved.goal,saved.constraints,saved.trainingProfile(now),reconciled,saved.feedback,saved.cross,now,zone,saved.starter)
            // Notification work does not replace the next-run record; opening the app makes that prescription.
            store.update{it.copy(records=reconciled)}
            if(saved.reminders) {
                val completed=reconciled.filter{it.runId!=null&&it.runId !in oldDone}
                if(completed.isNotEmpty())DailyCoach.notify(applicationContext,2,"Run recorded","${completed.size} session${if(completed.size==1)"" else "s"} matched. Open CoachMeSimple to see your updated plan.")
                val next=saved.override?.takeIf{saved.overrideBase==result.next?.id}?:result.next
                if(saved.lastReminder!=today&&next!=null&&next.day==today) {
                    DailyCoach.notify(applicationContext,1,"Today's ${next.kind.label.lowercase()}",if(next.kind==Kind.REST)"Let your training settle. Check in before the next run." else "${Display.distance(next.distance,saved.imperial)} ${if(saved.imperial)"mi" else "km"} · ${Display.time(next.seconds)}. Check the latest prescription before heading out.")
                    store.update{it.copy(lastReminder=today)}
                }
            }
            Result.success()
        }catch(_:SecurityException){Result.success()}catch(_:Exception){Result.retry()}
    }
}
