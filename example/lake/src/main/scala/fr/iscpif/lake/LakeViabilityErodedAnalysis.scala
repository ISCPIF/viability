package fr.iscpif.lake

import fr.iscpif.viability._
import fr.iscpif.kdtree.algorithm._
import fr.iscpif.kdtree.structure._
import fr.iscpif.viability.kernel._
import fr.iscpif.kdtree.visualisation._
import scala.util.Random
import scalax.io.Resource

/**
 * Created by ia on 15/12/2014.
 */
object LakeViabilityErodedTest extends App {

  implicit val rng = new Random(42)

  val lake = new LakeViability with ZoneK {
    override def depth = 14
  }

  val viabilityKernel = lake().last
  println("erosion")
  val eroded = lake.erode(viabilityKernel, 2)


  val output = s"/tmp/lakeAnalysis${lake.depth}/"
  viabilityKernel.saveVTK2D(Resource.fromFile(s"${output}original${lake.dilations}.vtk"))
  eroded.saveVTK2D(Resource.fromFile(s"${output}/eroded${lake.dilations}.vtk"))


}

object LakeViabilityErodedAnalysis1 extends App {

  implicit val rng = new Random(42)

  val lake = new LakeViability with ZoneK {
    override def depth = 14
  }

  val viabilityKernel = lake().last
  val eroded = lake.erode(viabilityKernel, 10)

  val lakeViabilityAnalyse =
    new ViabilityKernel
      with LakeViability
      with LearnK {
      def k(p: Point) = eroded.label(p)
      def domain = lake.domain
  }

  val output = s"/tmp/lakeAnalysis${lake.depth}/"
  eroded.saveVTK2D(Resource.fromFile(s"${output}/eroded${lake.dilations}.vtk"))

  for {
    (k,s) <- lakeViabilityAnalyse().zipWithIndex
  } {
    println(s)
    k.saveVTK2D(Resource.fromFile(s"${output}/mu${lake.dilations}s$s.vtk"))
  }

}

object LakeViabilityErodedAnalysis2 extends App {

  implicit val rng = new Random(42)

  val lake = new LakeViability with ZoneK {
    override def depth = 18
  }

  val nbErosion = 10
  val viabilityKernel = lake().lastWithTrace
  val eroded = lake.erode(viabilityKernel, nbErosion)

  val lakeViabilityAnalyse =
    new ViabilityKernel
      with LakeViability {
      def tree0(implicit rng: Random) = Some(eroded)
      override def depth = 18
    }

  val output = s"/tmp/lakeAnalysis${lake.depth}/"

  viabilityKernel.saveVTK2D(Resource.fromFile(s"${output}/viab${lake.dilations}.vtk"))

  eroded.saveVTK2D(Resource.fromFile(s"${output}/eroded${lake.dilations}.vtk"))

  for {
    (k,s) <- lakeViabilityAnalyse().zipWithIndex
  } {
    println(s)
    k.saveVTK2D(Resource.fromFile(s"${output}/nb${nbErosion}s$s.vtk"))
  }

}
