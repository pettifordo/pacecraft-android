package com.pacecraft.engine

import java.time.*
import kotlin.math.*

data class SessionAdjustment(val length:Int=0,val speed:Int=0,val days:Int=0)
data class AdjustmentPreview(val session:Session,val allowed:Boolean,val notes:List<String>,val projectedRatio:Double?)
object SessionAdjuster {
    fun preview(base:Session,adjustment:SessionAdjustment,runs:List<Run>,fitness:Fitness,load:Load,profile:Profile,
                constraints:Constraints,feedback:List<Feedback>,now:Instant,zone:ZoneId,cross:List<CrossTraining> = emptyList()):AdjustmentPreview {
        if(base.kind==Kind.REST) return AdjustmentPreview(base,false,listOf("A rest day cannot be turned into extra training."),null)
        val distanceFactor=1.10.pow(adjustment.length.coerceIn(-20,20));val speedFactor=1.025.pow(adjustment.speed.coerceIn(-20,20))
        val day=base.day.plusDays(adjustment.days.coerceIn(-14,14).toLong())
        val steps=base.steps.map { it.copy(distance=it.distance*distanceFactor,seconds=it.seconds*distanceFactor/speedFactor,paceScale=it.paceScale*speedFactor) }
        val proposed=base.copy(day=day,steps=steps,reasons=base.reasons+"Adjusted by you; review the load preview before heading out.")
        val problems=mutableListOf<String>();val adds=adjustment.length>0 || adjustment.speed>0
        val today=now.atZone(zone).toLocalDate()
        if(day<today) problems+="Choose today or a future day."
        if(!constraints.available(day)) problems+="That day is unavailable."
        if(runs.any{it.day(zone)==day}) problems+="A completed run already occupies that day."
        if(adds && load.flag==Flag.RED) problems+="Extra work is refused while the safety flag is red."
        if(adds && feedback.any{it.at.ageDays(now) in 0.0..2.0 && (it.niggle || it.soreness>=4 || it.energy<=1)}) problems+="Extra work is refused while your recent check-in asks for recovery."
        if(constraints.maxMinutes?.let{proposed.seconds>it*60}==true) problems+="This exceeds your session-time limit."
        val inWeek=runs.filter{it.day(zone)>=day.monday() && it.day(zone)<day.monday().plusWeeks(1)}
        if(inWeek.size>=constraints.sessions) problems+="This exceeds your weekly session limit."
        if(constraints.maxWeekly?.let{proposed.distance+inWeek.sumOf{r->r.distance}>it}==true) problems+="This exceeds your weekly distance limit."
        if(adds) runs.filter{it.start.ageDays(now) in 0.0..28.0}.maxOfOrNull{it.distance}?.let { if(proposed.distance>it*1.35) problems+="This is beyond the recent single-session distance ceiling." }
        val moved=day.atStartOfDay(zone).toInstant()
        if((base.kind.quality || speedFactor*base.kind.high>.85) && runs.any{it.start<moved && Duration.between(it.start,moved).toHours()<48 && it.speed>=fitness.cs*.85}) problems+="Keep 48 hours between hard efforts."
        val assumed=Run("adjustment",moved,proposed.distance,proposed.seconds,source="Adjustment preview")
        val after=LoadModel.evaluate(runs+assumed,cross,fitness,profile,day.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1),zone)
        if(adds && after.ratio>=1.5) problems+="The projected load ratio would reach the red zone."
        val notes=if(problems.isNotEmpty()) problems else buildList {
            add("${Display.distance(proposed.distance)} km · ${Display.time(proposed.seconds)} · projected load ratio %.2f".format(after.ratio))
            if(after.ratio>=1.3) add("Caution: this puts the projected load ratio in the amber band.")
            if(adjustment.days!=0) add("Future sessions will be rebuilt around the new day.")
        }
        return AdjustmentPreview(proposed,problems.isEmpty(),notes,after.ratio)
    }
}
