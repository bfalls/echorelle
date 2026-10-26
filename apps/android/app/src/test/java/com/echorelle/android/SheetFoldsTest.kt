package com.echorelle.android

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SheetFoldsTest {
    private fun area(v: List<PaperVertex>)=abs(v.indices.sumOf{i->val a=v[i];val b=v[(i+1)%v.size];a.u*b.v-b.u*a.v})/2
    private fun distance(a: PaperVertex,b: PaperVertex)=sqrt((a.x-b.x).pow(2)+(a.y-b.y).pow(2)+(a.z-b.z).pow(2))
    @Test fun realProjectsHaveDistinctSequentialStepCountsAndKeepTheirWholeSheet() {
        val expected=mapOf(PaperProject.DART to 9,PaperProject.HELMET to 14,PaperProject.SHELL to 189)
        for(project in PaperProject.entries) {
            val model=OrigamiPrograms.create(project)
            assertEquals(expected[project],model.stepCount)
            val materialArea=if(project==PaperProject.HELMET).5 else 1.0
            for(stage in model.stages) {
                val panels=stage.fixed+stage.moving
                assertEquals("$project ${stage.move.label}: all original material survives",materialArea,panels.sumOf{area(it.vertices)},1e-7)
                for(panel in stage.moving) for(i in panel.vertices.indices) {
                    val a=panel.vertices[i];val b=panel.vertices[(i+1)%panel.vertices.size]
                    assertEquals("Rigid panels must not stretch",distance(a,b),
                        distance(stage.move.hinge.rotate(a,stage.move.angle*.5),stage.move.hinge.rotate(b,stage.move.angle*.5)),1e-8)
                }
            }
            assertEquals(materialArea,model.finalPanels.sumOf{area(it.vertices)},1e-7)
            assertTrue(model.maxTriangles<10000)
        }
    }
    @Test fun sharedMaterialBoundariesStayJoinedAtEveryIntermediateFold() {
        for(project in PaperProject.entries) {
            val model=OrigamiPrograms.create(project)
            for(stage in model.stages) for(t in listOf(.25,.5,.75)) {
                val fixed=stage.fixed.flatMap{it.vertices}
                val moving=stage.moving.flatMap{it.vertices}
                // For every fixed/moving edge in UV, test material vertices lying on it.
                // Also catches a T-junction where only one adjacent panel was subdivided.
                for(panel in stage.fixed) for(i in panel.vertices.indices) {
                    val a=panel.vertices[i];val b=panel.vertices[(i+1)%panel.vertices.size]
                    val du=b.u-a.u;val dv=b.v-a.v;val length=du*du+dv*dv
                    if(length<1e-12)continue
                    for(p in moving) {
                        val q=((p.u-a.u)*du+(p.v-a.v)*dv)/length
                        if(q< -1e-7 || q>1+1e-7 || abs((p.u-a.u)*dv-(p.v-a.v)*du)>1e-7)continue
                        assertEquals("$project ${stage.move.label}: attached paper tore",0.0,
                            distance(a.between(b,q),stage.move.hinge.rotate(p,stage.move.angle*t)),2e-6)
                    }
                }
                // Shared vertices themselves must agree even after earlier folds.
                val known=HashMap<Pair<Long,Long>,PaperVertex>()
                for(p in fixed+moving.map{stage.move.hinge.rotate(it,stage.move.angle*t)}) {
                    val key=(p.u*1e8).roundToLong() to (p.v*1e8).roundToLong()
                    known.putIfAbsent(key,p)?.let{assertEquals("$project ${stage.move.label}",0.0,distance(it,p),2e-6)}
                }
            }
        }
    }
    @Test fun oneActiveHingeAndDeterministicWholeIntervalSeek() {
        for(project in PaperProject.entries) {
            val model=OrigamiPrograms.create(project)
            assertEquals(0,model.step(0f));assertEquals(0.0,model.local(0f),0.0)
            assertEquals(model.stepCount-1,model.step(1f));assertEquals(1.0,model.local(1f),0.0)
            for(i in model.stages.indices) {
                val midpoint=(i+.5f)/model.stepCount
                assertEquals(i,model.step(midpoint))
                assertEquals(.5,model.local(midpoint),.0001)
            }
        }
    }
    @Test fun radialFanHasNoFoldedSurfaceOverlapsDuringItsFormingPass() {
        val model=OrigamiPrograms.create(PaperProject.SHELL)
        // All projected triangles retain positive orientation in the same quadrant
        // around the shared boundary vertex: adjacent fan sectors cannot cross.
        for(stage in model.stages.drop(126))for(t in listOf(0.0,.25,.5,.75,1.0)) {
            for(triangle in stage.triangles) {
                fun pose(p: PaperVertex)=if(triangle.moving)stage.move.hinge.rotate(p,stage.move.angle*t) else p
                val a=pose(triangle.a);val b=pose(triangle.b);val c=pose(triangle.c)
                assertTrue("Fan facets must not turn through each other",(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)>-1e-8)
                for(p in listOf(a,b,c)){assertTrue(p.x>=-1.000001);assertTrue(p.y>=-1.000001)}
            }
        }
    }
    @Test fun movingToTheNextOperationNeverMorphsOrResetsTheMaterial() {
        fun locate(stage: PaperStage,u: Double,v: Double,end: Boolean): PaperVertex? {
            for(tri in stage.triangles) {
                val a=tri.a;val b=tri.b;val c=tri.c
                val d=(b.v-c.v)*(a.u-c.u)+(c.u-b.u)*(a.v-c.v)
                if(abs(d)<1e-12)continue
                val x=((b.v-c.v)*(u-c.u)+(c.u-b.u)*(v-c.v))/d
                val y=((c.v-a.v)*(u-c.u)+(a.u-c.u)*(v-c.v))/d
                val z=1-x-y
                if(min(x,min(y,z))< -1e-7)continue
                val p=PaperVertex(a.x*x+b.x*y+c.x*z,a.y*x+b.y*y+c.y*z,a.z*x+b.z*y+c.z*z,u,v)
                return if(end && tri.moving)stage.move.hinge.rotate(p,stage.move.angle) else p
            }
            return null
        }
        for(project in PaperProject.entries) {
            val model=OrigamiPrograms.create(project)
            for((before,after) in model.stages.zipWithNext())for((u,v) in listOf(.13 to .37,.31 to .61,.55 to .15,.77 to .29,.45 to .73)) {
                val a=locate(before,u,v,true)?:continue
                val b=locate(after,u,v,false)?:error("Material disappeared between folds")
                assertEquals("$project ${before.move.label} -> ${after.move.label}",0.0,distance(a,b),2e-6)
            }
        }
    }
}
