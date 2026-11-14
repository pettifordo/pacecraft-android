package com.pacecraft.wear

import kotlin.math.max

/** A GPS gap or jump is omitted; no target pace/distance is ever used to fill it. */
object DistanceFilter {
    fun accept(metres:Double,seconds:Double,accuracy:Double,speed:Double?):Boolean {
        if(!metres.isFinite()||!seconds.isFinite()||!accuracy.isFinite()||seconds<=0||seconds>30||accuracy !in 0.0..30.0)return false
        if(metres>seconds*12)return false
        return if(speed!=null) speed.isFinite()&&speed in .4..12.0&&metres>=1.5 else metres>=max(3.0,accuracy)
    }
}
