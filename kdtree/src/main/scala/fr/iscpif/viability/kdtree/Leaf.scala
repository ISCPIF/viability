/*
 * Copyright (C) 2013 de Aldama / Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */




package fr.iscpif.viability.kdtree



// TODO: the control is specific to the language Model problem, and the [def] for label too
trait Leaf extends Node {

  val testPoint: Point
  val control: Option[Double]

  def label: Boolean = if (control == None) false else true

  def containingLeaf(point: Point) = if (label && zone.contains(point)) Some(this) else None

  // This function is specific to the bounded case. The output
  // is an Option[Int] that gives the coordinate corresponding to the direction
  def touchesBoundary(rootZone: Zone): Option[Int] = {
    val indexedIntervals = this.zone.region.zipWithIndex
    val borderIndexedInterval =
      indexedIntervals.find(interval =>
        interval._1.min == rootZone.region(interval._2).min || interval._1.max == rootZone.region(interval._2).max)

    borderIndexedInterval match{
      case None => None
      case Some(x) => Some(x._2)
    }
  }

  def borderLeaves(direction: Direction, _label: Boolean): List[Leaf] =
     if (label == _label) List(this) else Nil

  //TODO: Define refinable by coordinate: the maxDepth could be different for each coordinate. But think before about convergence
  def refinable(maxDepth: Int) = {
    //zoneVolume(leaf.zone) > 1 / pow(2, maxDepth)
    this.path.length <= maxDepth
  }

  def leafExtractor: List[Leaf] = List(this)

  def volumeKdTree: Double =  if (label) this.zone.volume else 0

  def leaves: List[Leaf] = List(this)



  ///////DEBUG HELPERS
  def consistency = true



}