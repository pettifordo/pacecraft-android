package com.pacecraft.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacecraft.engine.*

@Composable fun PrescriptionSummary(session: Session, fitness: Fitness, imperial: Boolean, color: Color = Muted) {
    SessionDescription.repetitions(session)?.let {
        Text(it,color=Lime,fontSize=13.sp,lineHeight=19.sp,fontWeight=FontWeight.Medium)
    }
    SessionDescription.paces(session,fitness,imperial).forEach {
        Text("${it.label} · ${it.text}",color=color,fontSize=12.sp,lineHeight=18.sp)
    }
}

@Composable fun SessionSteps(session: Session, fitness: Fitness, imperial: Boolean) {
    val unit=if(imperial) "mi" else "km"
    session.steps.forEachIndexed { index,step ->
        Row(Modifier.fillMaxWidth()) {
            Text("%02d".format(index+1),color=Lime,fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.width(30.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(step.label,fontWeight=FontWeight.Medium,fontSize=14.sp)
                val timed=session.kind in setOf(Kind.INTERVALS,Kind.OPENERS) || step.label in setOf("Warm up","Cool down")
                Text(if(timed) "${Display.time(step.seconds)} · approx. ${Display.distance(step.distance,imperial)} $unit"
                    else "${Display.distance(step.distance,imperial)} $unit · about ${Display.time(step.seconds)}",
                    color=Muted,fontSize=12.sp)
                Text(SessionDescription.pace(step.kind,fitness,imperial),color=Muted,fontSize=12.sp)
            }
        }
    }
}
