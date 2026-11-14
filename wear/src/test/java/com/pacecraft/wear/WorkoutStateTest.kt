package com.pacecraft.wear
import com.pacecraft.shared.*
import org.junit.Assert.*
import org.junit.Test
class WorkoutStateTest {
    private val w=Workout("test","2026-11-16","Intervals",listOf(WorkoutStep("Warm up",600.0,1500.0,2.0,3.0),WorkoutStep("Work",180.0,700.0,3.5,4.0),WorkoutStep("Recovery",120.0,250.0,1.8,2.3)))
    @Test fun stepBoundariesAndTimerUseActiveTime(){
        val s=RunSnapshot("test",w,"2026-11-16T09:00:00Z")
        assertEquals(0,s.stepIndex);assertEquals(600.0,s.remaining,.001)
        assertEquals(1,s.copy(elapsed=600.0).stepIndex);assertEquals(180.0,s.copy(elapsed=600.0).remaining,.001)
        assertEquals(2,s.copy(elapsed=780.0).stepIndex);assertEquals(0.0,s.copy(elapsed=901.0).remaining,.001)
    }
    @Test fun gpsRejectsStationaryJitterJumpsAndGaps(){
        assertFalse(DistanceFilter.accept(4.0,2.0,10.0,0.0))
        assertFalse(DistanceFilter.accept(250.0,2.0,10.0,3.0))
        assertFalse(DistanceFilter.accept(50.0,40.0,10.0,3.0))
        assertFalse(DistanceFilter.accept(4.0,2.0,10.0,null))
        assertTrue(DistanceFilter.accept(6.0,2.0,10.0,3.0))
        assertTrue(DistanceFilter.accept(15.0,6.0,10.0,null))
    }
    @Test fun durableSnapshotKeepsFrozenWorkoutAndMeasuredDistance(){
        val s=RunSnapshot("test",w,"2026-11-16T09:00:00Z",elapsed=123.0,metres=456.0,paused=true,hrTotal=300.0,hrCount=2)
        val restored=RunSnapshot.decode(s.encode());assertEquals(s,restored);assertEquals(150.0,restored.averageHr!!,.001)
        assertEquals(456.0,restored.metres,.001);assertNotEquals(w.steps.sumOf{it.metres},restored.metres)
    }
}
