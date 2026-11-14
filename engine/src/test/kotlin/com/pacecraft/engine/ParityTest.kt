package com.pacecraft.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ParityTest {
    private val now=Instant.parse("2026-11-16T09:00:00Z")
    private val zone=ZoneId.of("Europe/London")
    private val today=now.atZone(zone).toLocalDate()
    private val f=Fitness(3.4,180.0,1.06,4,.99,.02,0.0,Confidence.HIGH)
    private val goal=Goal(created=today,raceDate=today.plusWeeks(12))
    private val history=(1..36).map{n->val d=if(n%4==0)12000.0 else 7000.0;Run("run$n",now.minusSeconds(n*2L*86400),d,d/2.8)}
    private fun starting(ability:Ability=Ability.WALK30,minutes:Int=20)=StarterProfile(ability,minutes=minutes,created=today)
    private fun recommend(runs:List<Run> = emptyList(),starter:StarterProfile=starting(),c:Constraints=Constraints(),p:Profile=Profile(),feedback:List<Feedback> = emptyList())=
        CoachingEngine.replan(runs,goal,c,p,feedback=feedback,now=now,zone=zone,starter=starter)

    @Test fun beginnerCanStartWithoutInventedHistoryOrRacePrediction(){
        val r=recommend();assertTrue(r.bootstrapMode);assertNull(r.prediction);assertTrue(r.runs.isEmpty());assertEquals(0,r.sufficiency.count)
        assertEquals(1200.0,r.next!!.seconds,.001);assertTrue(r.next!!.steps.any{it.kind==Kind.WALK});assertEquals(Confidence.LOW,r.fitness!!.confidence)
    }
    @Test fun slowRunWalkCountsForLoadAndSchedulingButNotRaceFitness(){
        val walk=Run("walk",now.minusSeconds(86400),1200.0,1200.0)
        val r=recommend(listOf(walk));assertEquals(1,r.runs.size);assertEquals(0,r.sufficiency.count);assertTrue(r.load!!.acute>0)
        assertTrue(r.next!!.day>walk.day(zone));assertNotEquals(Flag.RED,r.load!!.flag)
    }
    @Test fun startingPriorStillRefusesOverloadAndPhysiologicalRedFlags(){
        val tooMuch=(1..5).map{Run("$it",now.minusSeconds(it*86400L),15000.0,5400.0)}
        assertEquals(Kind.REST,recommend(tooMuch).next!!.kind)
        assertEquals(Kind.REST,recommend(p=Profile(restingHRDelta=7.0)).next!!.kind)
        assertEquals(Kind.REST,recommend(feedback=listOf(Feedback(now.minusSeconds(3600),niggle=true))).next!!.kind)
    }
    @Test fun beginnerFullPlanKeepsWeeklySessionsAndTimeCaps(){
        val c=Constraints(sessions=3,maxMinutes=15,blackoutDates=setOf(today.plusDays(1)))
        val starter=starting();val r=recommend(c=c,starter=starter)
        val plan=FullPlanProjector.project(r.runs,goal,c,Profile(),r.fitness!!,r.load!!,emptyList(),now=now,zone=zone,starter=starter)
        assertTrue(plan.sessions.any{it.kind!=Kind.REST});assertTrue(plan.sessions.all{it.seconds<=900.001})
        assertTrue(plan.sessions.none{it.day in c.blackoutDates});assertTrue(plan.weeks.all{it.runCount<=3})
        assertTrue("A starter should continue after its first week",plan.weeks.drop(1).any{it.runCount>=2})
    }
    @Test fun historicalOutdoorEvidenceGraduatesButTreadmillOnlyKeepsProvisionalPlan(){
        val r=recommend(history);assertFalse(r.bootstrapMode);assertNotNull(r.prediction)
        val treadmill=recommend(history.map{it.copy(indoor=true)});assertTrue(treadmill.bootstrapMode);assertNull(treadmill.prediction)
    }
    @Test fun everyTemplateClosesItsDistanceBudgetAndHasValidSteps(){
        for(kind in Kind.entries)for(distance in listOf(3000.0,8000.0,16000.0))for(phase in Phase.entries){
            val steps=Planner.structure(kind,distance,f,phase)
            assertEquals("$kind $distance $phase",if(kind==Kind.REST)0.0 else distance,steps.sumOf{it.distance},.001)
            assertTrue(steps.all{it.seconds>0&&it.seconds.isFinite()&&it.distance>0})
            assertTrue(steps.filter{it.kind==Kind.INTERVALS}.sumOf{it.seconds}<=1440)
            assertTrue(steps.filter{it.kind==Kind.THRESHOLD}.sumOf{it.seconds}<=2700)
        }
    }
    @Test fun peakIntervalsUseLadderWhenAllWorkAndRecoveriesFit(){
        val steps=Planner.structure(Kind.INTERVALS,15000.0,f,Phase.PEAK)
        assertEquals(listOf(120.0,180.0,240.0,180.0,120.0),steps.filter{it.kind==Kind.INTERVALS}.map{it.seconds})
        assertEquals(5,steps.count{it.kind==Kind.RECOVERY})
        assertEquals(15000.0,steps.sumOf{it.distance},.001)
    }
    @Test fun hillsAreNotScheduledForTreadmillOnlyAndRecoveryHasNoStrides(){
        val r=CoachingEngine.replan(history,goal,Constraints(intensity=5,treadmillOnly=true),now=now,zone=zone)
        assertNotEquals(Kind.HILLS,r.next!!.kind)
        val tired=CoachingEngine.replan(history,goal,feedback=listOf(Feedback(now,niggle=true)),now=now,zone=zone)
        assertFalse(tired.next!!.kind.quality);assertNotEquals(Kind.STRIDES,tired.next!!.kind)
    }
    @Test fun adjustmentPreviewRespectsLimitsAndDoesNotMutateBase(){
        val base=Session("base",today,Kind.EASY,Phase.BASE,Planner.structure(Kind.EASY,5000.0,f,Phase.BASE),30000.0,emptyList())
        val load=LoadModel.evaluate(history,emptyList(),f,Profile(),now,zone)
        val shorter=SessionAdjuster.preview(base,SessionAdjustment(length=-1,speed=-1),history,f,load,Profile(),Constraints(),emptyList(),now,zone)
        assertTrue(shorter.allowed);assertTrue(shorter.session.distance<base.distance);assertTrue(shorter.session.steps.first().paceScale<1);assertEquals(5000.0,base.distance,.001)
        val blocked=SessionAdjuster.preview(base,SessionAdjustment(days=1),history,f,load,Profile(),Constraints(blackoutDates=setOf(today.plusDays(1))),emptyList(),now,zone)
        assertFalse(blocked.allowed)
        val red=SessionAdjuster.preview(base,SessionAdjustment(length=1),history,f,load.copy(flag=Flag.RED),Profile(),Constraints(),emptyList(),now,zone)
        assertFalse(red.allowed)
    }
    @Test fun goalFeasibilityBoundsUnrealisticPromises(){
        val p=Predictor.predict(10000.0,f,12000.0,30000.0)
        val g=goal.copy(targetSeconds=p.seconds*.5,raceDate=today.plusWeeks(2))
        val result=ProgressAnalysis.feasibility(g,p,30000.0,12.0,today)
        assertEquals(GoalVerdict.UNREALISTIC,result.verdict);assertTrue(result.achievableSeconds>g.targetSeconds!!);assertNull(result.achievableDate)
    }
    @Test fun progressUsesDueSessionsOnlyAndGivesActionableCauses(){
        val p=Predictor.predict(10000.0,f,12000.0,30000.0);val load=LoadModel.evaluate(history,emptyList(),f,Profile(),now,zone)
        val session=Session("old",today.minusDays(3),Kind.THRESHOLD,Phase.BUILD,Planner.structure(Kind.THRESHOLD,8000.0,f,Phase.BUILD),30000.0,emptyList())
        val records=listOf(SessionRecord(session,skipped=true),SessionRecord(session.copy(id="future",day=today.plusDays(1))))
        val g=goal.copy(created=today.minusWeeks(10),baselineSeconds=p.seconds*.8,targetSeconds=p.seconds*.7)
        val progress=ProgressAnalysis.evaluate(g,p,load,records,emptyList(),f,now,zone)
        assertEquals(1,progress.adherence.planned);assertEquals(0,progress.adherence.completed)
        assertEquals(Track.DRIFTING,progress.status);assertTrue(progress.causes.isNotEmpty());assertTrue(progress.options.isNotEmpty())
    }
    @Test fun optionalCrossTrainingAndBaselineSignalsAffectFatigueNotRunMileage(){
        val cross=listOf(CrossTraining("cycle",now.minusSeconds(3600),7200.0))
        val yes=CoachingEngine.replan(history,goal,crossTraining=cross,now=now,zone=zone)
        val no=CoachingEngine.replan(history,goal,profile=Profile(countsCrossTraining=false),crossTraining=cross,now=now,zone=zone)
        assertTrue(yes.load!!.acute>no.load!!.acute);assertEquals(yes.load!!.weekly,no.load!!.weekly)
        assertEquals(Flag.RED,CoachingEngine.replan(history,goal,profile=Profile(hrvDelta=-.3),now=now,zone=zone).load!!.flag)
    }
}
