package com.pacecraft.android

import android.content.Context
import com.google.android.gms.wearable.*
import com.pacecraft.engine.*
import com.pacecraft.shared.*
import kotlinx.coroutines.tasks.await
import java.io.File

object WorkoutWire {
    fun encode(s:Session,f:Fitness,imperial:Boolean=false):String=Workout(s.id,s.day.toString(),s.kind.label,s.steps.map {
        WorkoutStep(it.label,it.seconds,it.distance,f.cs*it.kind.low*it.paceScale,f.cs*it.kind.high*it.paceScale)
    },imperial).encode()
}
object WatchBridge {
    suspend fun send(context:Context,s:Session,f:Fitness,imperial:Boolean=false):String {
        require(s.kind!=Kind.REST && s.steps.isNotEmpty()) { "Rest days have no workout to send." }
        val request=PutDataMapRequest.create("/coach/workout").apply {
            dataMap.putString("workout",WorkoutWire.encode(s,f,imperial));dataMap.putLong("sentAt",System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
        val nodes=Wearable.getNodeClient(context).connectedNodes.await()
        return if(nodes.isEmpty()) "Workout queued. It will transfer when your paired Wear OS watch reconnects. Install the matching Wear APK on the watch."
            else "Workout sent to ${nodes.joinToString{it.displayName}}. Open CoachMeSimple on your watch."
    }
}
object WearInbox {
    suspend fun pull(c:Context) {
        Wearable.getDataClient(c).dataItems.await().use { items -> items.forEach { item ->
            if(item.uri.path?.startsWith("/coach/completed/")==true) {
                DataMapItem.fromDataItem(item).dataMap.getString("run")?.let { save(c,it);Wearable.getDataClient(c).deleteDataItems(item.uri).await() }
            }
        } }
    }
    private val lock=Any()
    private fun directory(c:Context)=File(c.filesDir,"wear-inbox").apply{mkdirs()}
    fun save(c:Context,text:String)=synchronized(lock) {
        val r=CompletedWorkout.decode(text);val target=File(directory(c),"${r.id}.json")
        val temp=File(directory(c),"${r.id}.tmp")
        temp.outputStream().use{it.write(text.toByteArray());it.fd.sync()};check(temp.renameTo(target))
    }
    /** Commit before removing the durable inbox so process death cannot lose a completed run. */
    fun import(c:Context):Int=synchronized(lock) {
        var added=0
        directory(c).listFiles()?.filter{it.extension=="json"}?.forEach{file ->
            val r=CompletedWorkout.decode(file.readText())
            val run=Run("wear:${r.id}",r.start,r.metres,r.seconds,r.heartRate,source="CoachMeSimple Wear OS")
            LocalStore(c).update { state -> if(state.runs.any{it.id==run.id}) state else state.copy(runs=state.runs+run,
                records=state.records.map{if(it.session.id==r.workoutId && it.runId==null && !it.skipped)it.copy(runId=run.id)else it}).also{added++} }
            file.delete()
        };added
    }
    fun clear(c:Context)=synchronized(lock){directory(c).listFiles()?.forEach{it.delete()}}
}
class WearReturnService:WearableListenerService() {
    override fun onDataChanged(events:DataEventBuffer) {
        events.forEach { event -> if(event.type==DataEvent.TYPE_CHANGED && event.dataItem.uri.path?.startsWith("/coach/completed/")==true) {
            val payload=DataMapItem.fromDataItem(event.dataItem).dataMap.getString("run") ?: return@forEach
            try { WearInbox.save(this,payload);Wearable.getDataClient(this).deleteDataItems(event.dataItem.uri) }
            catch(_:Exception) { /* Retain the data item for a later retry; never acknowledge failed persistence. */ }
        } }
    }
}
