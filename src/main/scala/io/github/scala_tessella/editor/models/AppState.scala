package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import org.scalajs.dom
import io.github.scala_tessella.editor.utils.TilingGenerator

object AppState:
  // Polygon palette state
  val polygonSides: List[Int] = List(3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 24, 42)
  val selectedPolygon: Var[Option[Int]] = Var[Option[Int]](None)

  // Canvas state
  val canvasPolygons: Var[List[CanvasPolygon]] = Var(TilingGenerator.generateSamplePolygons())
  val canvasTexts: Var[List[CanvasText]] = Var(TilingGenerator.generateSampleTexts())
  val viewTransform: Var[ViewTransform] = Var(ViewTransform())
  val selectedElements: Var[Set[String]] = Var(Set.empty)

  // Tessellation state
  val currentTiling: Var[Option[Tiling]] = Var(TilingGenerator.generateSampleTiling())
  val selectedPerimeterEdges: Var[Set[String]] = Var(Set.empty)
  val selectedTilingPolygons: Var[Set[String]] = Var(Set.empty)

  // Canvas interaction state
  val isDragging: Var[Boolean] = Var(false)
  val dragStart: Var[Option[Point]] = Var(None)
  val canvasElementRef: Var[Option[dom.Element]] = Var(None)

  // Selection management
  def toggleSelection(elementId: String): Unit =
    selectedElements.update(current =>
      if (current.contains(elementId)) current - elementId
      else current + elementId
    )

  def toggleTilingPolygonSelection(id: String): Unit =
    selectedTilingPolygons.update(current =>
      if (current.contains(id)) current - id
      else current + id
    )

  def togglePerimeterEdgeSelection(id: String): Unit =
    selectedPerimeterEdges.update(current =>
      if (current.contains(id)) current - id
      else current + id
    )

  def clearAllSelections(): Unit =
    selectedElements.set(Set.empty)
    selectedTilingPolygons.set(Set.empty)
    selectedPerimeterEdges.set(Set.empty)

  def deleteSelectedElements(): Unit =
    val selected = selectedElements.now()
    canvasPolygons.update(_.filterNot(p => selected.contains(p.id)))
    canvasTexts.update(_.filterNot(t => selected.contains(t.id)))
    selectedElements.set(Set.empty)