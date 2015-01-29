package fr.iscpif.kdtree

import fr.iscpif.kdtree.algorithm.OracleApproximation
import fr.iscpif.kdtree.content.Label
import fr.iscpif.kdtree.structure._
import HelperFunctions._
import Path._
import scala.util.Random

package object content {

  //type Relabeliser[T] = (T, T => Boolean) => T

  // The critical pairs together with the coordinate of adjacency (no need to include the sign)
  def pairsBetweenNodes[T <: Label](node1: Node[T], node2: Node[T]): Iterable[(Leaf[T], Leaf[T], Int)] = {
    lazy val direction =
      adjacency(node1.path, node2.path) match {
        case None => throw new RuntimeException("Zones must be adjacent.")
        case Some(x) => x.toDirection
      }

    def adjacentOppositeLeaves(leaf: Leaf[T], fork: Fork[T], direction: Direction) =
      fork.borderLeaves(direction).filter(leaf2 => leaf2.content.label == !leaf.content.label).filter(leaf2 => adjacent(leaf.path, leaf2.path))

    (node1, node2) match {
      case (leaf1: Leaf[T], leaf2: Leaf[T]) =>
        assert(Zone.adjacentZones(leaf1.zone, leaf2.zone))
        if (xor(leaf1.content.label, leaf2.content.label))
          List((leaf1, leaf2, direction.coordinate))
        else
          Nil

      case (fork1: Fork[T], fork2: Fork[T]) =>
        List(
          (fork1.lowChild, fork2.lowChild),
          (fork1.lowChild, fork2.highChild),
          (fork1.highChild, fork2.lowChild),
          (fork1.highChild, fork2.highChild)
        ).flatMap {
            case (n1, n2) =>
              if (adjacent(n1.path, n2.path)) pairsBetweenNodes(n1, n2)
              else Nil
          }

      case (fork: Fork[T], leaf: Leaf[T]) =>
        adjacentOppositeLeaves(leaf, fork, direction).map(borderLeaf => (leaf, borderLeaf, direction.coordinate))
      case (leaf: Leaf[T], fork: Fork[T]) =>
        adjacentOppositeLeaves(leaf, fork, direction.opposite).map(borderLeaf => (leaf, borderLeaf, direction.coordinate))
    }
  }

  implicit class LabelNodeDecorator[T <: Label](val n: Node[T]) {

    import n._

    def leavesLabeled(label: Boolean): Iterable[Leaf[T]] = leaves.filter(_.content.label == label)

    def borderLeavesLabeled(direction: Direction, label: Boolean) =
      borderLeaves(direction).filter(_.content.label == label)

    // It is supposed to be applied on the root
    def preferredDirections: List[Direction] = {
      assert(parent == None)
      def preferableDirection(direction: Direction): Boolean =
        if (borderLeaves(direction).filter(x => x.content.label) != Nil) true else false
      val positiveDirections: List[Direction] = zone.region.indices.toList.map(c => new Direction(c, Positive))
      val negativeDirections: List[Direction] = zone.region.indices.toList.map(c => new Direction(c, Negative))
      val preferredPDirections = positiveDirections.filter(d => preferableDirection(d))
      val preferredNDirections = negativeDirections.filter(d => preferableDirection(d))
      preferredPDirections ::: preferredNDirections
    }

    def volume: Double = leavesLabeled(label = true).map(_.zone.volume).sum

    def normalizedVolume: Double = leavesLabeled(label = true).map(_.zone.normalizedVolume(zone)).sum

    def containingColoredLeaf(point: Point, label: Boolean): Option[Leaf[T]] =
      containingLeaf(point).filter(_.content.label == label)

  }

  implicit class LabelTreeDecorator[T <: Label](val t: Tree[T]) {

    def volume = t.root.volume

    def reassign(update: T => T)(implicit m: Manifest[T]) = {
      val newT = t.clone
      newT.leaves.foreach {
        l => newT.replace(l.path, update(l.content))
      }
      newT
    }

    def criticalLeaves(n: Node[T] = t.root, includeNonAtomic: Boolean = false): Iterable[Leaf[T]] =
      (n match {
        case leaf: Leaf[T] => List.empty
        case fork: Fork[T] =>
          criticalLeaves(fork.lowChild, includeNonAtomic) ++ criticalLeaves(fork.highChild, includeNonAtomic) ++
            criticalLeavesBetweenNodes(fork.lowChild, fork.highChild, includeNonAtomic)
      }).toSeq.distinct

    def criticalLeavesBetweenNodes(node1: Node[T], node2: Node[T], includeNonAtomic: Boolean = false): Iterable[Leaf[T]] = {
      def borderLeaves(leaf: Leaf[T], fork: Fork[T]) = {
        if (t.isAtomic(leaf) || includeNonAtomic) {
          val direction =
            adjacency(fork.path, leaf.path) match {
              case None => throw new RuntimeException("Zones must be adjacent.")
              case Some(x) => x.toDirection
            }

          val test = fork.borderLeaves(direction).filter(
            x => (x.content.label != leaf.content.label) && adjacent(leaf.path, x.path)
          )

          if (test.size != 0)
            test.flatMap {
              borderLeaf => List(leaf, borderLeaf)
            }
          else List.empty
        } else List.empty
      }

      (node1, node2) match {
        case (leaf1: Leaf[T], leaf2: Leaf[T]) =>
          assert(adjacent(leaf1.path, leaf2.path))
          //TODO test adjacency  if true assert(Zone.adjacentZones(leaf1.zone, leaf2.zone),{println(leaf1.zone.region.toList); println(leaf2.zone.region.toList)})
          if ((includeNonAtomic || (t.isAtomic(leaf1) && t.isAtomic(leaf2))) &&
            xor(leaf1.content.label, leaf2.content.label)) List(leaf1, leaf2)
          else Nil

        case (fork1: Fork[T], fork2: Fork[T]) =>
          val listAux = List(
            (fork1.lowChild, fork2.lowChild),
            (fork1.lowChild, fork2.highChild),
            (fork1.highChild, fork2.lowChild),
            (fork1.highChild, fork2.highChild))

          listAux.flatMap {
            case (n1, n2) =>
              if (adjacent(n1.path, n2.path)) criticalLeavesBetweenNodes(n1, n2, includeNonAtomic)
              else Nil
          }

        case (fork: Fork[T], leaf: Leaf[T]) => borderLeaves(leaf, fork)
        case (leaf: Leaf[T], fork: Fork[T]) => borderLeaves(leaf, fork)
      }
    }

    def leavesToRefine: Iterable[(Leaf[T], Int)] = leavesToRefine(t.root)

    // The node together with the preferred coordinate (if another coordinate is bigger it will have no impact)
    // Former node to refine
    def leavesToRefine(n: Node[T]): Iterable[(Leaf[T], Int)] = {
      val leaves =
        (n match {
          case leaf: Leaf[T] =>
            leaf.content.label match {
              case true =>
                leaf.touchesBoundary match {
                  case Some(coordinate) => List((leaf, coordinate))
                  case None => List.empty
                }
              case false => List.empty
            }
          case fork: Fork[T] =>
            leavesToRefine(fork.lowChild) ++ leavesToRefine(fork.highChild) ++
              pairsToSet(pairsBetweenNodes(fork.lowChild, fork.highChild))
        }).filterNot {
          case (leaf, _) => t.isAtomic(leaf)
        }

      //TODO: Consider the lines below
      var distinctLeaves: List[(Leaf[T], Int)] = Nil
      leaves.foreach(
        x => {
          if (distinctLeaves.forall(y => y._1 != x._1)) distinctLeaves = x :: distinctLeaves
        }
      )
      distinctLeaves
      /*  Source of non-deterministic behaviour (thanks Romain, 2 wasted journeys)
      leaves.groupBy {
        case (l, _) => l
      }.toList.map {
        case (_, l) => l.head
      }
      */
    }

    // TODO Choose whether if p has not been found return false
    def label(p: Point) = t.containingLeaf(p).map(_.content.label).getOrElse(false)

    // For TreeHandling : return atomic leaves that are extreme (i.e. on the root border)
    // Note : return only positive leaves
    // Note : they are supposed to be atomic leaves (since the tree was refined earlier)
    //TODO verify this point : leaf.touchesBoundary gives only atomic leaves if leaves are handled by a KdTreeHandlingComputation
    def leavesOnRootZone: Iterable[(Leaf[T], Int)] = leavesOnRootZone(t.root)
    def leavesOnRootZone(n: Node[T]): Iterable[(Leaf[T], Int)] = {
      val leaves =
        (n match {
          case leaf: Leaf[T] =>
            leaf.content.label match {
              case true =>
                leaf.touchesBoundary match {
                  case Some(coordinate) => List((leaf, coordinate))
                  case None => List.empty
                }
              case false => List.empty
            }
          case fork: Fork[T] =>
            leavesOnRootZone(fork.lowChild) ++ leavesOnRootZone(fork.highChild)
        }).filter {
          case (leaf, _) => t.isAtomic(leaf)
        }

      //TODO: Consider the lines below
      var distinctLeaves: List[(Leaf[T], Int)] = Nil
      leaves.foreach(
        x => {
          if (distinctLeaves.forall(y => y._1 != x._1)) distinctLeaves = x :: distinctLeaves
        }
      )
      distinctLeaves
      /*  Source of non-deterministic behaviour (thanks Romain, 2 wasted journeys)
      leaves.groupBy {
        case (l, _) => l
      }.toList.map {
        case (_, l) => l.head
      }
      */
    }

  }

 /* implicit class LabelTreeWithDomainDecorator extends LabelTreeDecorator[T : Content](t: TreeWithDomain[T])  {

    def leavesToReassignWithDomain(n: Node[T], label: Boolean): Iterable[Leaf[T]] =
      criticalLeavesWithDomain(n).filter(_.content.label == label).toSeq.distinct

    def criticalLeavesWithDomain (n: Node[T] = t.root, includeNonAtomic: Boolean = false, label: Boolean): Iterable[Leaf[T]] =
      (n match {
        case leaf: Leaf[T] => List.empty
        case fork: Fork[T] =>
          criticalLeavesWithDomain(fork.lowChild, includeNonAtomic, label) ++ criticalLeavesWithDomain(fork.highChild, includeNonAtomic, label) ++
            criticalLeavesBetweenNodes(fork.lowChild, fork.highChild, includeNonAtomic)
      }).toSeq.distinct

    def listOfLeafExtremeWithDomain(leaf:Leaf, label:Label): Iterable[Leaf[T]] = {
      if (t.touchesDomain(leaf))
  }

}
*/

    implicit class TestPointLeafDecorator[T <: TestPoint](l: Leaf[T]) {
    def emptyExtendedZoneAndPath(coordinate: Int) =
      if (l.zone.divideLow(coordinate).contains(l.content.testPoint)) (l.zone.divideHigh(coordinate), l.extendedHighPath(coordinate))
      else (l.zone.divideLow(coordinate), l.extendedLowPath(coordinate))
  }

  implicit class LabelTestPointNodeDecorator[T <: Label with TestPoint](n: Node[T]) {

    def zonesAndPathsToTest(leavesAndPrefCoord: Iterable[(Leaf[T], Int)]): List[(Zone, Path)] =
      leavesAndPrefCoord.toList.map {
        case (leaf, prefCoord) =>
          assert(leaf.contains(leaf.content.testPoint), "TestPoint: " + leaf.content.testPoint + "  Leaf: " + leaf.zone.region.map(x => println(x.min + " " + x.max)))

          val minCoord = leaf.minimalCoordinates

          val coordinate =
            minCoord.exists(_ == prefCoord) match {
              case true => prefCoord
              case false => minCoord.head
            }

          //The new zone to test will be the one that does not contain the point of the current leaf
          leaf.emptyExtendedZoneAndPath(coordinate)
      }

  }

  object mutable {

    implicit class MutableTreeDecorator[T](t: Tree[T]) {
      def evaluateAndInsert(
        zonesAndPaths: Iterable[(Zone, Path)],
        evaluator: (Seq[Zone], Random) => Seq[T])(implicit rng: Random) = {
        val (zones, paths) = zonesAndPaths.unzip
        val evaluated = paths zip evaluator(zones.toSeq, rng)
        var currentRoot = t.root
        evaluated.foreach {
          case (path, content) =>
            currentRoot = currentRoot.insert(path, content).rootCalling
        }
        Tree(currentRoot, t.depth)
      }
    }

  }

}

