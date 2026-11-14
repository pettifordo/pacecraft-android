package com.pacecraft.shared

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Versioned phone/watch contract. Contains only the explicitly selected workout. */
data class WorkoutStep(val label:String,val seconds:Double,val metres:Double,val lowSpeed:Double,val highSpeed:Double)
data class Workout(val id:String,val day:String,val name:String,val steps:List<WorkoutStep>,val imperial:Boolean=false) {
    val seconds get()=steps.sumOf{it.seconds}
    fun encode()=JSONObject().put("schema",1).put("id",id).put("day",day).put("name",name).put("imperial",imperial).put("steps",JSONArray(steps.map {
        JSONObject().put("label",it.label).put("seconds",it.seconds).put("metres",it.metres).put("lowSpeed",it.lowSpeed).put("highSpeed",it.highSpeed)
    })).toString()
    companion object {
        fun decode(text:String):Workout {
            require(text.length<200000)
            val j=JSONObject(text);require(j.getInt("schema")==1)
            val a=j.getJSONArray("steps");require(a.length() in 1..200)
            val steps=(0 until a.length()).map { i -> val s=a.getJSONObject(i)
                WorkoutStep(s.getString("label").take(150),s.getDouble("seconds"),s.getDouble("metres"),s.getDouble("lowSpeed"),s.getDouble("highSpeed")).also {
                    require(it.seconds.isFinite() && it.seconds in 1.0..21600.0 && it.metres.isFinite() && it.metres in 0.0..200000.0)
                    require(it.lowSpeed.isFinite() && it.highSpeed.isFinite() && it.lowSpeed in 0.0..15.0 && it.highSpeed in it.lowSpeed..15.0)
                }
            };require(steps.sumOf{it.seconds}<=86400)
            return Workout(j.getString("id").take(100),j.getString("day").also{java.time.LocalDate.parse(it)},j.getString("name").take(100),steps,j.optBoolean("imperial"))
        }
    }
}
data class CompletedWorkout(val id:String,val start:Instant,val metres:Double,val seconds:Double,val heartRate:Double?,val workoutId:String) {
    fun encode()=JSONObject().put("schema",1).put("id",id).put("start",start.toString()).put("metres",metres).put("seconds",seconds).put("hr",heartRate).put("workoutId",workoutId).toString()
    companion object {
        fun decode(text:String):CompletedWorkout {
            require(text.length<10000);val j=JSONObject(text);require(j.getInt("schema")==1)
            val r=CompletedWorkout(j.getString("id"),Instant.parse(j.getString("start")),j.getDouble("metres"),j.getDouble("seconds"),
                if(j.isNull("hr")) null else j.getDouble("hr"),j.getString("workoutId"))
            require(r.id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
            require(r.metres.isFinite() && r.metres in 100.0..200000.0 && r.seconds.isFinite() && r.seconds in 60.0..172800.0)
            require(r.heartRate==null || r.heartRate.isFinite() && r.heartRate in 25.0..240.0)
            return r
        }
    }
}
