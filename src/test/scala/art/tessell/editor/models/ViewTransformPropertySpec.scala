package art.tessell.editor.models

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class ViewTransformPropertySpec extends ScalaCheckSuite:

  private val intGen: Gen[Int] =
    Gen.chooseNum(-2000, 2000)

  property("normalizeRotation always returns [0, 360) for any input"):
    forAll(intGen): deg =>

      val t   = ViewTransform()
      val out = t.normalizeRotation(deg)
      out >= 0 && out < 360

  property("withRotation applies normalizeRotation"):
    forAll(intGen): deg =>

      val t   = ViewTransform()
      val out = t.withRotation(deg).rotationDegrees
      out == t.normalizeRotation(deg)
