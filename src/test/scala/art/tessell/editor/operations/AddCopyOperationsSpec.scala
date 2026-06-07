package art.tessell.editor.operations

import art.tessell.editor.utils.geo.Point
import art.tessell.editor.utils.geo.Geometry.regularPolygonPoints
import munit.FunSuite

class AddCopyOperationsSpec extends FunSuite:

  test("faceSymmetryOrder of a regular polygon equals its number of sides") {
    assertEquals(AddCopyOperations.faceSymmetryOrder(regularPolygonPoints(3, 1.0).toList), 3)
    assertEquals(AddCopyOperations.faceSymmetryOrder(regularPolygonPoints(4, 1.0).toList), 4)
    assertEquals(AddCopyOperations.faceSymmetryOrder(regularPolygonPoints(6, 1.0).toList), 6)
  }

  test("faceSymmetryOrder of a (non-square) rectangle is 2, not 4") {
    val rectangle = List(Point(0, 0), Point(2, 0), Point(2, 1), Point(0, 1))
    assertEquals(AddCopyOperations.faceSymmetryOrder(rectangle), 2)
  }

  test("faceSymmetryOrder of a scalene triangle is 1 (no rotational symmetry)") {
    val scalene = List(Point(0, 0), Point(3, 0), Point(1, 2))
    assertEquals(AddCopyOperations.faceSymmetryOrder(scalene), 1)
  }

  test("faceSymmetryOrder of a rhombus (non-square) is 2") {
    val rhombus = List(Point(0, 0), Point(2, 1), Point(0, 2), Point(-2, 1))
    assertEquals(AddCopyOperations.faceSymmetryOrder(rhombus), 2)
  }
