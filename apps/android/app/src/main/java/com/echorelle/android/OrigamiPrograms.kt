package com.echorelle.android

import kotlin.math.PI

/** Functional fold coordinates, independently encoded from traditional/reference projects.
 * Every left/right pair is separate. No cuts, floating decorative faces or shape morphs. */
internal object OrigamiPrograms {
    fun create(project: PaperProject,checkCancelled: ()->Unit={}): SheetFolds {
        val b=SheetFolds.Builder(project,project==PaperProject.HELMET,checkCancelled)
        when(project) {
            PaperProject.DART -> with(b) {
                crease("centre",0.0,-1.0,0.0,1.0,1)
                fold("Left nose corner",-1.0,0.0,0.0,1.0,1,mark=1)
                fold("Right nose corner",0.0,1.0,1.0,0.0,1,mark=2)
                // Bisect the new sloping edges toward the centre line.
                val k=2.0/(1.0+kotlin.math.sqrt(2.0))
                fold("Left narrow nose",-k,-1.0,0.0,1.0,1)
                fold("Right narrow nose",0.0,1.0,k,-1.0,1)
                fold("Close centre",0.0,-1.0,0.0,1.0,1,mark=4)
                fold("First wing",.10,-1.0,.10,1.0,-1,-PI/2,requireMarks=4,mark=8)
                fold("Second wing",.10,-1.0,.10,1.0,-1,PI/2,rejectMarks=4,mark=16,behind=true)
            }
            PaperProject.HELMET -> with(b) {
                crease("helmet centre",0.0,-1.0,0.0,1.0,1)
                // Diamond sheet: separate front/back and left/right flap material identities.
                fold("Triangle",-1.0,0.0,1.0,0.0,1,PI,mark=1)
                fold("Right corner down",0.0,0.0,1.0,-1.0,1,PI,mark=2)
                fold("Left corner down",-1.0,-1.0,0.0,0.0,1,PI,mark=4)
                fold("Right flap up",-1.0,-.5,1.0,-.5,-1,-PI,requireMarks=2,mark=8)
                fold("Left flap up",-1.0,-.5,1.0,-.5,-1,-PI,requireMarks=4,mark=16)
                fold("Right horn out",0.0,-.5,.25,-.10,1,PI,requireMarks=8)
                fold("Left horn out",-.25,-.10,0.0,-.5,1,PI,requireMarks=16)
                fold("Front brim",-1.0,-.64,1.0,-.64,-1,-PI,requireMarks=1,rejectMarks=6,mark=32)
                fold("Roll brim edge",-1.0,-.5,1.0,-.5,-1,-PI,requireMarks=1,rejectMarks=6,mark=64)
                fold("Back flap crease",-1.0,-.5,1.0,-.5,-1,PI,rejectMarks=7,mark=128,behind=true)
                fold("Open back flap",-1.0,-.5,1.0,-.5,1,-PI,requireMarks=128,behind=true)
                fold("Tuck back flap",-1.0,-.5,1.0,-.5,-1,PI,requireMarks=128,behind=true)
            }
            PaperProject.SHELL -> with(b) {
                // A boundary-vertex fan is an open hinge chain, with no closed-loop collapse.
                // Original 64-panel square-sheet shell: prepare, then form each actual crease.
                fun endpoint(i: Int): Pair<Double,Double> {
                    val angle=i*kotlin.math.PI/128
                    val x=kotlin.math.cos(angle);val y=kotlin.math.sin(angle)
                    val scale=1/kotlin.math.max(x,y)
                    return x*scale to y*scale
                }
                for(i in 1..63) {
                    val (u,v)=endpoint(i)
                    radialCrease("shell rib $i",u,v)
                }
                for(i in 1..63) {
                    val (u,v)=endpoint(i)
                    materialFold("Form shell rib $i",u,v,(if(i%2==0)-1 else 1)*kotlin.math.PI*.34)
                }
            }
        }
        return b.build()
    }
}