package com.pacecraft.wear

import android.content.Context
import com.google.android.gms.wearable.*
import com.pacecraft.shared.*
import kotlinx.coroutines.tasks.await

object WatchStore {
    private fun prefs(c:Context)=c.getSharedPreferences("coach-watch",Context.MODE_PRIVATE)
    fun workout(c:Context)=prefs(c).getString("workout",null)?.let{runCatching{Workout.decode(it)}.getOrNull()}
    fun receive(c:Context,text:String){Workout.decode(text);check(prefs(c).edit().putString("workout",text).commit())}
    fun savedRun(c:Context)=prefs(c).getString("active",null)
    fun saveRun(c:Context,text:String?) { check(prefs(c).edit().putString("active",text).commit()) }
    fun summary(c:Context)=prefs(c).getString("summary",null)
    fun summary(c:Context,text:String){prefs(c).edit().putString("summary",text).apply()}
    fun enqueue(c:Context,run:CompletedWorkout) { check(prefs(c).edit().putString("outbox-${run.id}",run.encode()).commit()) }
    suspend fun sync(c:Context) {
        Wearable.getDataClient(c).dataItems.await().use{buffer -> buffer.forEach{item ->
            if(item.uri.path=="/coach/workout")DataMapItem.fromDataItem(item).dataMap.getString("workout")?.let{receive(c,it)}
        }}
        prefs(c).all.filterKeys{it.startsWith("outbox-")}.forEach{(key,value)->
            val run=CompletedWorkout.decode(value as String)
            Wearable.getDataClient(c).putDataItem(PutDataMapRequest.create("/coach/completed/${run.id}").apply{dataMap.putString("run",run.encode())}.asPutDataRequest().setUrgent()).await()
            // The durable Data Layer retains the item until the phone commits and acknowledges it.
            prefs(c).edit().remove(key).commit()
        }
    }
}
class WatchTransferService:WearableListenerService() {
    override fun onDataChanged(events:DataEventBuffer) {
        events.forEach{event->if(event.type==DataEvent.TYPE_CHANGED&&event.dataItem.uri.path=="/coach/workout") {
            DataMapItem.fromDataItem(event.dataItem).dataMap.getString("workout")?.let{runCatching{WatchStore.receive(this,it)}}
        }}
    }
}
