package com.echorelle.android

import kotlin.math.*

/** Material coordinates never change. Subdivision is meshing, not cutting the paper. */
internal data class PaperVertex(val x: Double,val y: Double,val z: Double,val u: Double,val v: Double) {
    fun between(b: PaperVertex,t: Double)=PaperVertex(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,u+(b.u-u)*t,v+(b.v-v)*t)
}
internal data class PaperPanel(val vertices: List<PaperVertex>,val marks: Int=0,val layer: Int=0)
internal data class PaperHinge(val ax: Double,val ay: Double,val bx: Double,val by: Double,val az: Double=0.0,val bz: Double=0.0) {
    private val length=sqrt((bx-ax).pow(2)+(by-ay).pow(2)+(bz-az).pow(2))
    val ux=(bx-ax)/length;val uy=(by-ay)/length;val uz=(bz-az)/length
    init { require(length>1e-8) }
    fun side(p: PaperVertex)=ux*(p.y-ay)-uy*(p.x-ax)
    fun rotate(p: PaperVertex,angle: Double): PaperVertex {
        val x=p.x-ax;val y=p.y-ay;val z=p.z-az
        val c=cos(angle);val s=sin(angle);val d=(ux*x+uy*y+uz*z)*(1-c)
        return p.copy(x=ax+x*c+(uy*z-uz*y)*s+ux*d,y=ay+y*c+(uz*x-ux*z)*s+uy*d,z=az+z*c+(ux*y-uy*x)*s+uz*d)
    }
}
internal data class PaperMove(val label: String,val hinge: PaperHinge,val side: Int,val angle: Double,
    val requireMarks: Int=0,val rejectMarks: Int=0,val mark: Int=0,val behind: Boolean=false)
internal data class PaperTriangle(val a: PaperVertex,val b: PaperVertex,val c: PaperVertex,
    val moving: Boolean,val edges: Int,val layer: Int,val startLayer: Int)
internal class PaperStage(val move: PaperMove,val fixed: List<PaperPanel>,val moving: List<PaperPanel>,val inkCount: Int) {
    val triangles: Array<PaperTriangle> = buildList {
        val ordered=if(move.behind) moving.asReversed().map{it to true}+fixed.map{it to false}
            else fixed.map{it to false}+moving.asReversed().map{it to true}
        ordered.forEachIndexed { layer,(panel,moves) ->
            val v=panel.vertices
            for(i in 1 until v.lastIndex) add(PaperTriangle(v[0],v[i],v[i+1],moves,
                (if(i==1)1 else 0) or 2 or (if(i==v.lastIndex-1)4 else 0),layer,panel.layer))
        }
    }.toTypedArray()
}
internal data class PaperInk(val ax: Double,val ay: Double,val bx: Double,val by: Double)
internal enum class PaperProject(val title: String) { DART("Paper dart"),HELMET("Samurai helmet"),SHELL("Radial fan");
    companion object { fun forPeriod(period: Long)=when {
        period<30L*SAMPLE_RATE -> DART
        period<300L*SAMPLE_RATE -> HELMET
        else -> SHELL
    } }
}

/** An immutable, seekable sequence of one-axis rigid folds of one material sheet. */
internal class SheetFolds(val project: PaperProject,val stages: List<PaperStage>,val finalPanels: List<PaperPanel>,
    val inks: List<PaperInk>,val materialArea: Double,val initial: List<PaperVertex>) {
    val stepCount get()=stages.size
    val maxTriangles=stages.maxOf{it.triangles.size}
    fun step(fraction: Float)=floor(fraction.coerceIn(0f,1f)*stepCount).toInt().coerceAtMost(stepCount-1)
    fun local(fraction: Float): Double {
        if(fraction>=1f)return 1.0
        val x=fraction.coerceAtLeast(0f)*stepCount
        val t=(x-floor(x)).toDouble()
        return t*t*t*(t*(t*6-15)+10)
    }

    internal class Builder(private val project: PaperProject,diamond: Boolean=false,
                           private val checkCancelled: () -> Unit={}) {
        private val original=if(diamond) listOf(PaperVertex(0.0,1.0,0.0,.5,1.0),PaperVertex(-1.0,0.0,0.0,0.0,.5),
            PaperVertex(0.0,-1.0,0.0,.5,0.0),PaperVertex(1.0,0.0,0.0,1.0,.5))
            else listOf(PaperVertex(-1.0,-1.0,0.0,0.0,0.0),PaperVertex(1.0,-1.0,0.0,1.0,0.0),
                PaperVertex(1.0,1.0,0.0,1.0,1.0),PaperVertex(-1.0,1.0,0.0,0.0,1.0))
        private var panels=listOf(PaperPanel(original))
        private val stages=ArrayList<PaperStage>()
        private val inks=ArrayList<PaperInk>()
        /** Fold in material coordinates, finding its current 3D hinge on the remaining flap. */
        fun materialFold(label: String,bu: Double,bv: Double,angle: Double) {
            checkCancelled()
            panels=panels.mapIndexed{i,p->p.copy(layer=i)}
            fun point(u: Double,v: Double): PaperVertex {
                for(panel in panels)for(i in 1 until panel.vertices.lastIndex) {
                    val a=panel.vertices[0];val b=panel.vertices[i];val c=panel.vertices[i+1]
                    val denominator=(b.v-c.v)*(a.u-c.u)+(c.u-b.u)*(a.v-c.v)
                    if(abs(denominator)<1e-12)continue
                    val x=((b.v-c.v)*(u-c.u)+(c.u-b.u)*(v-c.v))/denominator
                    val y=((c.v-a.v)*(u-c.u)+(a.u-c.u)*(v-c.v))/denominator
                    val z=1-x-y
                    if(x>=-1e-7 && y>=-1e-7 && z>=-1e-7)
                        return PaperVertex(a.x*x+b.x*y+c.x*z,a.y*x+b.y*y+c.y*z,a.z*x+b.z*y+c.z*z,u,v)
                }
                error("Material hinge is outside the sheet")
            }
            val a=point(0.0,0.0);val b=point(bu,bv)
            val hinge=PaperHinge(a.x,a.y,b.x,b.y,a.z,b.z)
            val fixed=ArrayList<PaperPanel>();val moving=ArrayList<PaperPanel>()
            for(panel in panels) {
                clip(panel,bu,bv,1)?.let{moving+=it}
                clip(panel,bu,bv,-1)?.let{fixed+=it}
            }
            require(moving.isNotEmpty())
            require(fixed.size+moving.size<=12000) { "Paper mesh limit exceeded" }
            val move=PaperMove(label,hinge,1,angle,behind=angle<0)
            stages+=PaperStage(move,fixed,moving,inks.size)
            val folded=moving.asReversed().map{p->p.copy(vertices=p.vertices.map{hinge.rotate(it,angle)})}
            panels=if(move.behind)folded+fixed else fixed+folded
        }
        fun radialCrease(label: String,bu: Double,bv: Double) {
            val before=panels
            materialFold("Crease $label",bu,bv,PI)
            materialFold("Open $label",bu,bv,-PI)
            inks+=PaperInk(0.0,0.0,bu,bv)
            panels=before
        }
        fun fold(label: String,ax: Double,ay: Double,bx: Double,by: Double,side: Int,angle: Double=PI,
                 requireMarks: Int=0,rejectMarks: Int=0,mark: Int=0,behind: Boolean=false) {
            checkCancelled()
            val move=PaperMove(label,PaperHinge(ax,ay,bx,by),side,angle,requireMarks,rejectMarks,mark,behind)
            val fixed=ArrayList<PaperPanel>();val moving=ArrayList<PaperPanel>()
            panels=panels.mapIndexed{i,p->p.copy(layer=i)}
            for(panel in panels) {
                if(panel.marks and requireMarks != requireMarks || panel.marks and rejectMarks !=0) { fixed+=panel;continue }
                val a=if(side==0)panel else clip(panel,move.hinge,side)
                val b=if(side==0)null else clip(panel,move.hinge,-side)
                if(a!=null)moving+=a.copy(marks=a.marks or mark)
                if(b!=null)fixed+=b
            }
            require(moving.isNotEmpty()) { "$project: empty move $label" }
            require(fixed.size+moving.size<=12000) { "Paper mesh limit exceeded" }
            stages+=PaperStage(move,fixed,moving,inks.size)
            val folded=moving.asReversed().map{p->p.copy(vertices=p.vertices.map{move.hinge.rotate(it,angle)})}
            panels=if(behind)folded+fixed else fixed+folded
        }
        /** A genuine crease-and-open pair, followed by merging coplanar render facets. */
        fun crease(label: String,ax: Double,ay: Double,bx: Double,by: Double,side: Int) {
            fold("Crease $label",ax,ay,bx,by,side,PI,mark=1024)
            // The reflected flap is on the other side of its crease; keep its material identity.
            fold("Open $label",ax,ay,bx,by,-side,-PI,requireMarks=1024)
            inks+=PaperInk((ax+1)/2,(ay+1)/2,(bx+1)/2,(by+1)/2)
            panels=listOf(PaperPanel(original))
        }
        fun build()=SheetFolds(project,stages,panels,inks,if(original.size==4 && original[0].x==0.0)2.0 else 4.0,original)
        private fun clip(panel: PaperPanel,hinge: PaperHinge,side: Int): PaperPanel? {
            return clipBy(panel) { hinge.side(it)*side }
        }
        private fun clip(panel: PaperPanel,bu: Double,bv: Double,side: Int): PaperPanel? =
            clipBy(panel) { (bu*it.v-bv*it.u)*side }
        private fun clipBy(panel: PaperPanel,distance: (PaperVertex)->Double): PaperPanel? {
            val result=ArrayList<PaperVertex>()
            val points=panel.vertices
            for(i in points.indices) {
                val a=points[i];val b=points[(i+1)%points.size]
                val da=distance(a);val db=distance(b)
                if(da>=-1e-9)result+=a
                if((da>1e-9 && db< -1e-9)||(da< -1e-9 && db>1e-9))result+=a.between(b,da/(da-db))
            }
            val clean=result.filterIndexed { i,p ->
                val q=result[(i+result.size-1)%result.size]
                abs(p.u-q.u)+abs(p.v-q.v)>1e-9
            }
            if(clean.size<3)return null
            var area=0.0
            for(i in clean.indices){val a=clean[i];val b=clean[(i+1)%clean.size];area+=a.u*b.v-b.u*a.v}
            return if(abs(area)<1e-12)null else panel.copy(vertices=clean)
        }
    }
}
