package com.pacecraft.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.*

class EngineTest {
    private val now=Instant.parse("2026-06-15T10:00:00Z")
    private val zone=ZoneId.of("Europe/London")
    private val today=now.atZone(zone).toLocalDate()
    private val goal=Goal(distance=10000.0,created=today.minusWeeks(5),raceDate=today.plusWeeks(7))
    private val fitness=Fitness(3.5,180.0,1.06,4,.99,.01,1.0,Confidence.HIGH)
    private val balanced=Load(20.0,20.0,1.0,0.0,Flag.GREEN,emptyList(),listOf(30000.0,30000.0,30000.0,30000.0,30000.0),0.0,0,10)
    private fun run(id: String, days: Long, d: Double=7000.0, speed: Double=2.8) = Run(id,now.minusSeconds(days*86400),d,d/speed)
    private fun history()=(1..40).map { run("run$it",it*2L,if(it%4==0) 12000.0 else 7000.0) }

    @Test fun exactCriticalSpeedFitFromKnownLine() {
        // Same d = 3.5t + 180 recovery case as CoachKit AnalysisTests.
        val efforts=listOf(180.0,360.0,720.0,1200.0).map { Effort(now.minusSeconds(86400),3.5*it+180,it,true) }
        val f=FitnessModel.estimate(efforts,now)!!
        assertEquals(3.5,f.cs,1e-9); assertEquals(180.0,f.dPrime,1e-9)
        assertEquals(Confidence.HIGH,f.confidence)
    }
    @Test fun priorFallbackHasWideUncertainty() {
        val f=FitnessModel.estimate(listOf(Effort(now,5000.0,1500.0,true)),now)!!
        assertEquals((5000-180)/1500.0,f.cs,1e-9); assertEquals(Confidence.LOW,f.confidence); assertEquals(.06,f.residual,1e-9)
    }
    @Test fun invalidFitFallsBack() {
        val f=FitnessModel.estimate(listOf(Effort(now,3000.0,600.0,true),Effort(now,5000.0,1200.0,true)),now)!!
        assertEquals(180.0,f.dPrime,0.0); assertEquals(Confidence.LOW,f.confidence)
    }
    @Test fun newestEvidenceControlsStaleness() {
        val efforts=listOf(Effort(now.minusSeconds(100L*86400),1000.0,200.0,true),Effort(now.minusSeconds(7L*86400),5000.0,1200.0,true))
        assertEquals(1.0,FitnessModel.estimate(efforts,now)!!.staleWeeks,1e-9)
    }
    @Test fun submaximalLongRunsDoNotFitEndurance() {
        val efforts=listOf(Effort(now,1000.0,200.0,true),Effort(now,5000.0,1500.0,true),Effort(now,15000.0,6000.0,false))
        assertEquals(1.06,FitnessModel.estimate(efforts,now)!!.exponent,1e-9)
    }
    @Test fun personalEnduranceExponentRecovered() {
        val e=listOf(1000.0,3000.0,5000.0).map { Effort(now,it,.15*it.pow(1.08),true) }
        assertEquals(1.08,FitnessModel.estimate(e,now)!!.exponent,1e-9)
    }
    @Test fun dominatedEffortsRemoved() {
        val e=listOf(Effort(now,5000.0,1200.0,true),Effort(now,3000.0,1000.0,false),Effort(now,10000.0,2700.0,true))
        assertEquals(2,FitnessModel.frontier(e).size)
    }
    @Test fun sixWeekWeightHalfLife() {
        assertEquals(.5,Effort(now.minusSeconds(42L*86400),1000.0,200.0,true).weight(now),1e-9)
    }
    @Test fun duplicatesKeepMagnitudeBeforeRichness() {
        val full=run("full",1,10000.0); val warm=full.copy(id="warm",start=full.start.plusSeconds(200),distance=2000.0,duration=600.0,heartRate=140.0,ascent=20.0)
        val cleaned=Cleaner.clean(listOf(full,warm),zone)
        assertEquals(listOf(full),cleaned)
    }
    @Test fun duplicatesOffsetByMinutesMerge() {
        val a=run("a",1); val b=a.copy(id="b",start=a.start.plusSeconds(150),heartRate=150.0)
        assertEquals(listOf(b),Cleaner.clean(listOf(a,b),zone))
        assertEquals(2,Cleaner.clean(listOf(a,b),zone,false).size)
    }
    @Test fun separateRunsSameDaySurvive() {
        val a=run("a",1); assertEquals(2,Cleaner.clean(listOf(a,a.copy(id="b",start=a.start.plusSeconds(20000))),zone).size)
    }
    @Test fun contaminationAndNonFiniteRejected() {
        assertFalse(Cleaner.usable(run("a",1,d=500.0)))
        assertFalse(Cleaner.usable(run("b",1,speed=8.0)))
        assertFalse(Cleaner.usable(run("c",1).copy(distance=Double.NaN)))
        assertFalse(Cleaner.effort(run("d",1).copy(indoor=true)))
        assertFalse(Cleaner.effort(run("e",1).copy(ascent=300.0)))
    }
    @Test fun noPredictionsBeforeSufficiency() {
        val r=CoachingEngine.replan(listOf(run("a",1)),goal,now=now,zone=zone)
        assertNull(r.fitness); assertNull(r.prediction); assertNull(r.next)
    }
    @Test fun futureAndStaleRunsExcluded() {
        val r=CoachingEngine.replan(listOf(run("a",-1),run("b",183)),goal,now=now,zone=zone)
        assertEquals(0,r.sufficiency.count)
    }
    @Test fun inversionAndBandMatchReferenceFormula() {
        val p=Predictor.predict(10000.0,fitness,Demands.long(10000.0),Demands.weekly(10000.0))
        val expected=(10000-180)/3.5
        assertEquals(expected,p.seconds,1e-9)
        assertEquals(expected*(1-1.28*.02),p.low,1e-9)
        assertEquals(expected*(1+1.28*.02),p.high,1e-9)
    }
    @Test fun marathonUsesRiegelAndDurabilityCap() {
        val p=Predictor.predict(MARATHON,fitness,0.0,0.0)
        assertEquals((HALF-180)/3.5*2.0.pow(1.06)*1.20,p.seconds,1e-6)
        assertTrue(p.beyondEvidence); assertEquals(Confidence.LOW,p.confidence)
    }
    @Test fun raceDemandAndLongRunCapAgree() {
        listOf(5000.0,10000.0,HALF,MARATHON,50000.0).forEach { d ->
            assertTrue(Demands.long(d)<=Demands.weekly(d)*Demands.longShare(d,5)+1e-6)
        }
        assertTrue(Demands.weekly(MARATHON)>Demands.weekly(5000.0))
    }
    @Test fun paceLoadReference() { assertEquals(100.0,LoadModel.pace(12600.0,3600.0,3.5),1e-9) }
    @Test fun stableWeeklyPatternIsNotOverload() {
        val runs=(0..12).flatMap { w -> listOf(1,3,5,7).map { day -> run("$w-$day",w*7L+day) } }
        val l=LoadModel.evaluate(runs,emptyList(),fitness,Profile(),now,zone)
        assertEquals(Flag.GREEN,l.flag); assertTrue(l.ratio in .85..1.15)
    }
    @Test fun underloadIsNotSafetyRed() {
        val runs=(2..12).flatMap { w -> listOf(1,3,5,7).map { day -> run("$w-$day",w*7L+day) } }
        val l=LoadModel.evaluate(runs,emptyList(),fitness,Profile(),now,zone)
        assertTrue(l.ratio<.8); assertEquals(Flag.GREEN,l.flag)
    }
    @Test fun crossTrainingAddsFatigueNotRunningVolume() {
        val runs=history(); val cross=listOf(CrossTraining("bike",now.minusSeconds(86400),7200.0))
        val a=LoadModel.evaluate(runs,emptyList(),fitness,Profile(),now,zone)
        val b=LoadModel.evaluate(runs,cross,fitness,Profile(),now,zone)
        assertTrue(b.acute>a.acute); assertEquals(a.weekly,b.weekly); assertTrue(b.crossShare>0)
    }
    @Test fun calendarWeeksRespectDaylightSaving() {
        val z=ZoneId.of("Europe/London"); val monday=LocalDate.parse("2026-03-30")
        val r=Run("dst",monday.atTime(0,30).atZone(z).toInstant(),5000.0,1800.0)
        assertEquals(listOf(0.0,5000.0),LoadModel.calendar(listOf(r),monday,z,2))
    }
    @Test fun redAfterRunYesterdayPrescribesRest() {
        val red=balanced.copy(flag=Flag.RED,reasons=listOf("Overloaded"))
        val s=Planner.next(listOf(run("yesterday",1)),goal,Constraints(),fitness,red,emptyList(),emptyList(),now,zone)
        assertEquals(Kind.REST,s.kind)
    }
    @Test fun feedbackSuppressesAllQuality() {
        val s=Planner.next(history(),goal,Constraints(intensity=5),fitness,balanced,emptyList(),listOf(Feedback(now,niggle=true)),now,zone)
        assertFalse(s.kind.quality)
        assertTrue(s.steps.none { it.kind.quality })
    }
    @Test fun raceDistanceIsNotRoundedDown() {
        val g=goal.copy(distance=HALF,raceDate=today)
        val s=Planner.next(history(),g,Constraints(),fitness,balanced,emptyList(),emptyList(),now,zone)
        assertEquals(Kind.RACE,s.kind); assertEquals(HALF,s.distance,1e-6)
    }
    @Test fun constraintsNeverExceeded() {
        val c=Constraints(maxWeekly=15000.0,maxMinutes=20,blackout=setOf(DayOfWeek.MONDAY))
        val s=Planner.next(history(),goal,c,fitness,balanced,emptyList(),emptyList(),now,zone)
        assertTrue(s.day.dayOfWeek!=DayOfWeek.MONDAY); assertTrue(s.seconds<=1200.001)
        assertTrue(s.weeklyTarget<=15000); assertTrue(s.distance>=0)
    }
    @Test fun skippedDayIsNotOfferedAgain() {
        val s=Planner.next(history(),goal,Constraints(),fitness,balanced,emptyList(),emptyList(),now,zone)
        val again=Planner.next(history(),goal,Constraints(),fitness,balanced,listOf(SessionRecord(s,skipped=true)),emptyList(),now,zone)
        assertTrue(again.day>s.day)
    }
    @Test fun historyReconciliationIsOneToOne() {
        val session=Session("a",today.minusDays(1),Kind.EASY,Phase.BASE,listOf(Step("Easy",2500.0,7000.0,Kind.EASY)),30000.0,emptyList())
        val records=listOf(SessionRecord(session),SessionRecord(session.copy(id="b")))
        assertEquals(1,CoachingEngine.reconcile(records,listOf(run("run",1)),zone).count { it.runId!=null })
    }
    @Test fun repeatedEqualPerformancesKeepNewestEvidence() {
        val old=Effort(now.minusSeconds(86400),5000.0,1500.0,false)
        val fresh=old.copy(date=now)
        assertEquals(listOf(fresh),FitnessModel.frontier(listOf(old,fresh,fresh)))
    }
    @Test fun exactRepeatInputsGiveIdenticalResult() {
        val a=CoachingEngine.replan(history(),goal,now=now,zone=zone)
        val b=CoachingEngine.replan(history().reversed(),goal,now=now,zone=zone)
        assertEquals(a,b); assertNotNull(a.next)
    }
    @Test fun taperVolumeOnlyFallsTowardsRace() {
        val g=goal.copy(distance=MARATHON,raceDate=today.plusDays(21))
        val factors=(0..21).map { day -> .55+.45*(1-exp(-(21-day)/(Phases.taper(g.distance)/2.5))) }
        assertTrue(factors.zipWithNext().all { (a,b) -> b<=a })
    }
    @Test fun afterRaceEventuallyBuildsAgain() {
        val g=goal.copy(raceDate=today.minusDays(100))
        assertEquals(Phase.BUILD,Phases.phase(g,today))
    }
    @Test fun sessionCeilingsAcrossGoalsAndConstraints() {
        for(d in listOf(5000.0,10000.0,HALF,MARATHON)) for(sessions in 2..7) for(intensity in 1..5) for(minutes in listOf(20,40,90)) {
            val c=Constraints(sessions=sessions,intensity=intensity,maxMinutes=minutes,maxWeekly=40000.0)
            val g=goal.copy(distance=d,raceDate=today.plusWeeks(10))
            val s=Planner.next(history(),g,c,fitness,balanced,emptyList(),emptyList(),now,zone)
            assertTrue("$d $sessions $intensity $minutes",s.seconds<=minutes*60+.01)
            assertTrue(s.distance.isFinite() && s.distance>=0)
            assertTrue(s.weeklyTarget<=40000.0)
            val used=LoadModel.calendar(history(),s.day,zone,1).single()
            assertTrue(s.distance<=max(0.0,s.weeklyTarget-used)+.01)
        }
    }
    @Test fun unavailableFortnightReturnsExplainedRest() {
        val c=Constraints(blackout=DayOfWeek.entries.toSet())
        val s=Planner.next(history(),goal,c,fitness,balanced,emptyList(),emptyList(),now,zone)
        assertEquals(Kind.REST,s.kind); assertTrue(s.reasons.first().contains("No available"))
    }
}
