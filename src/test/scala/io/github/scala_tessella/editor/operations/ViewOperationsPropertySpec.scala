package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class ViewOperationsPropertySpec extends ScalaCheckSuite:

  private val doubleGen: Gen[Double] =
    Gen.chooseNum(-1000.0, 1000.0)

  private val positiveScaleGen: Gen[Double] =
    Gen.chooseNum(0.1, 10.0)

  private val radGen: Gen[Radian] =
    Gen.chooseNum(-Math.PI * 4, Math.PI * 4).map(Radian(_))

  private val pointGen: Gen[Point] =
    for
      x <- doubleGen
      y <- doubleGen
    yield Point(x, y)

  private val eps = 1e-7

  property("inverseTransform returns a world point that maps back to viewCenter"):
    forAll(pointGen, pointGen, positiveScaleGen, radGen): (viewCenter, pan, scale, rotation) =>
      if scale <= 0.0 then true
      else
        val world  = ViewOperations.inverseTransform(viewCenter, pan, scale, rotation)
        val screen = pan + ViewOperations.forwardTransform(world, viewCenter, scale, rotation)
        math.abs(screen.x - viewCenter.x) <= eps && math.abs(screen.y - viewCenter.y) <= eps
