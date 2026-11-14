package com.pacecraft.engine

import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.*

enum class GoalVerdict { COMFORTABLE, REALISTIC, AMBITIOUS, UNREALISTIC }
data class Feasibility(val verdict:GoalVerdict,val requiredImprovement:Double,val achievableImprovement:Double,
                       val achievableSeconds:Double,val achievableDate:LocalDate?)
enum class Track { AHEAD, ON_TRACK, DRIFTING, OFF_TRACK }
data class Adherence(val completed:Int,val planned:Int,val volumeRatio:Double?,val qualityRatio:Double?)
data class Progress(val status:Track,val requiredNow:Double,val predictedNow:Double,val gap:Double,
                    val causes:List<String>,val options:List<String>,val adherence:Adherence)
object ProgressAnalysis {
    fun feasibility(goal:Goal,prediction:Prediction,meanWeekly:Double,historyWeeks:Double,today:LocalDate):Feasibility {
        val target=goal.targetSeconds?:return Feasibility(GoalVerdict.COMFORTABLE,0.0,0.0,prediction.seconds,null)
        val rate=if(meanWeekly>=55000 && historyWeeks>=20) .0018 else if(meanWeekly>=25000 && historyWeeks>=10) .0032 else .005
        val ceiling=if(rate==.0018) .05 else if(rate==.0032) .10 else .18
        val weeks=goal.raceDate?.let { max(0.0,ChronoUnit.DAYS.between(today,it)/7.0) }?:12.0
        val headroom=if(meanWeekly>0) ((Demands.weekly(goal.distance)-meanWeekly)/meanWeekly).coerceIn(0.0,.5) else .5
        val possible=min(rate*weeks*(1+headroom),ceiling)
        val needed=(prediction.seconds-target)/prediction.seconds
        val credit=((prediction.seconds-prediction.low)/prediction.seconds).coerceIn(0.0,.05)
        val ratio=if(possible+credit>0) needed/(possible+credit) else Double.POSITIVE_INFINITY
        val verdict=when { needed<=0 || ratio<.4 -> GoalVerdict.COMFORTABLE;ratio<.8->GoalVerdict.REALISTIC;ratio<1.1->GoalVerdict.AMBITIOUS;else->GoalVerdict.UNREALISTIC }
        val training=max(0.0,needed-credit)
        val date=if(training>0 && training<=ceiling) today.plusDays(ceil(training/(rate*(1+headroom))*7).toLong()) else null
        return Feasibility(verdict,needed,possible,prediction.seconds*(1-possible),date)
    }
    fun evaluate(goal:Goal,prediction:Prediction,load:Load,records:List<SessionRecord>,runs:List<Run>,fitness:Fitness,
                 now:Instant,zone:ZoneId,driftingChecks:Int=0):Progress {
        val cutoff=now.minusSeconds(28*86400L)
        val due=records.filter { it.session.kind!=Kind.REST && it.session.day>=cutoff.atZone(zone).toLocalDate() && it.session.day<now.atZone(zone).toLocalDate() }
        val done=due.count{it.runId!=null};val quality=due.filter{it.session.kind.quality}
        val planned=due.sumOf{it.session.distance}
        // Imported history from before the first prescription is evidence, not adherence.
        val actual=due.minOfOrNull{it.session.day}?.let{first->runs.filter{it.day(zone)>=first&&it.day(zone)<now.atZone(zone).toLocalDate()}.sumOf{it.distance}}?:0.0
        val adherence=Adherence(done,due.size,if(planned>0) actual/planned else null,if(quality.isNotEmpty()) quality.count{it.runId!=null}.toDouble()/quality.size else null)
        val target=goal.targetSeconds;val baseline=goal.baselineSeconds;val race=goal.raceDate
        if(target==null || baseline==null || race==null) return Progress(Track.ON_TRACK,prediction.seconds,prediction.seconds,0.0,emptyList(),emptyList(),adherence)
        val total=max(1,ChronoUnit.DAYS.between(goal.created,race)); val elapsed=max(0,ChronoUnit.DAYS.between(goal.created,now.atZone(zone).toLocalDate()))
        val required=baseline+(target-baseline)*(elapsed.toDouble()/total).coerceIn(0.0,1.0)
        val gap=prediction.seconds-required
        val status=when { gap<-.015*required->Track.AHEAD;gap<=.015*required->Track.ON_TRACK;driftingChecks>=2->Track.OFF_TRACK;else->Track.DRIFTING }
        val causes=buildList {
            if(status in setOf(Track.DRIFTING,Track.OFF_TRACK)) {
                if((adherence.volumeRatio?:1.0)<.85) add("Completed volume is below the recent prescriptions.")
                if((adherence.qualityRatio?:1.0)<.60) add("Some quality sessions have been missed.")
                if(load.ratio>1.4 || load.balance < -30) add("Accumulated training load may be limiting recovery.")
                val recent=runs.filter{it.start>=cutoff}
                if(recent.size>=5 && recent.count{it.speed>fitness.cs*Kind.EASY.high}.toDouble()/recent.size>=.4) add("Many recent runs are above the easy-pace ceiling.")
                if(ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(),race)<28) add("There is limited time left before race day.")
                if(isEmpty()) add("Current fitness is behind the target trajectory.")
            }
        }
        val options=if(causes.isEmpty()) emptyList() else if(load.flag==Flag.RED) listOf("Back off and recover","Revise the target time","Move the race date") else listOf("Revise the target time","Allow more time","Review consistency and available training time")
        return Progress(status,required,prediction.seconds,gap,causes,options,adherence)
    }
}
