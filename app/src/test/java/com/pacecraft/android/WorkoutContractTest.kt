package com.pacecraft.android
import com.pacecraft.engine.*
import com.pacecraft.shared.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutContractTest {
    private val f=Fitness(3.5,180.0,1.06,3,.99,.01,0.0,Confidence.HIGH)
    @Test fun phoneExportPreservesEveryTimedIntervalAndPaceBand(){
        val s=Session("workout",LocalDate.of(2026,11,16),Kind.INTERVALS,Phase.PEAK,Planner.structure(Kind.INTERVALS,14000.0,f,Phase.PEAK),35000.0,emptyList())
        val w=Workout.decode(WorkoutWire.encode(s,f))
        assertEquals(s.steps.size,w.steps.size);assertEquals(s.seconds,w.seconds,.001)
        s.steps.zip(w.steps).forEach{(step,wire)->assertEquals(step.distance,wire.metres,.001);assertEquals(f.cs*step.kind.high,wire.highSpeed,.001)}
        assertEquals(w,Workout.decode(w.encode()))
    }
    @Test fun invalidPayloadsCannotCreateAnExerciseOrInventDistance(){
        val valid=CompletedWorkout("test-1",Instant.parse("2026-11-16T09:00:00Z"),5000.0,1800.0,null,"workout")
        assertEquals(valid,CompletedWorkout.decode(valid.encode()))
        for(bad in listOf(valid.copy(metres=0.0),valid.copy(seconds=-1.0),valid.copy(id="../escape"),valid.copy(heartRate=999.0))){
            assertTrue(runCatching{CompletedWorkout.decode(bad.encode())}.isFailure)
        }
        assertTrue(runCatching{Workout.decode("{\"schema\":2}")}.isFailure)
    }
}
