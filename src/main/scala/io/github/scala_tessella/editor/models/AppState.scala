package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.TilingGrowth.OtherNodeStrategy.AFTER_PERIMETER
import org.scalajs.dom
import io.github.scala_tessella.editor.utils.TilingGenerator
import scala.scalajs.js
import scala.util.Try

object AppState:
  // Polygon palette state
  val polygonSides: List[Int] = List(3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 24, 42)
  val selectedPolygon: Var[Option[Int]] = Var[Option[Int]](None)

  // Canvas state - start with empty canvas and sample data
  val canvasPolygons: Var[List[CanvasPolygon]] = Var(TilingGenerator.generateSamplePolygons())
  val canvasTexts: Var[List[CanvasText]] = Var(TilingGenerator.generateSampleTexts())
  val viewTransform: Var[ViewTransform] = Var(ViewTransform())
  val selectedElements: Var[Set[String]] = Var(Set.empty)

  // Tessellation state - start with empty tiling
  val currentTiling: Var[Option[Tiling]] = Var(None)
  val selectedPerimeterEdges: Var[Set[String]] = Var(Set.empty)
  val selectedTilingPolygons: Var[Set[String]] = Var(Set.empty)

  // Visualization state
  val showNodeLabels: Var[Boolean] = Var(false)

  // Error message state
  val errorMessage: Var[Option[String]] = Var(None)

  // Canvas interaction state
  val isDragging: Var[Boolean] = Var(false)
  val dragStart: Var[Option[Point]] = Var(None)
  val canvasElementRef: Var[Option[dom.Element]] = Var(None)

  // Toggle node labels visibility
  def toggleNodeLabels(): Unit =
    showNodeLabels.update(!_)

  // Show error message temporarily
  def showError(message: String): Unit =
    errorMessage.set(Some(message))

    // Clear error after 3 seconds, but only if window is available (browser environment)
    Try {
      if (js.typeOf(js.Dynamic.global.window) != "undefined") {
        dom.window.setTimeout(() => errorMessage.set(None), 3000)
      }
    }.recover {
      case _ => // Ignore errors in test environment
    }

  // Clear error message
  def clearError(): Unit =
    errorMessage.set(None)

  // Polygon selection with tiling creation logic
  def selectPolygon(sides: Int): Unit =
    selectedPolygon.set(Some(sides))

    // If tiling is empty, create a new tiling from the selected polygon
    if (currentTiling.now().isEmpty) {
      TilingGenerator.createTilingFromPolygon(sides) match {
        case Some(tiling) =>
          currentTiling.set(Some(tiling))
        case None =>
          showError(s"Failed to create tiling from $sides-sided polygon")
      }
    }

  // Check if tiling is empty
  def isTilingEmpty: Boolean = currentTiling.now().isEmpty

  // Clear tiling and reset to empty state
  def clearTiling(): Unit =
    currentTiling.set(None)
    selectedTilingPolygons.set(Set.empty)
    selectedPerimeterEdges.set(Set.empty)

  // Handle perimeter edge click with polygon growth
  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    (currentTiling.now(), selectedPolygon.now()) match {
      case (Some(tiling), Some(polygonSides)) =>
        // Try to grow the edge with the selected polygon
        try {
          import io.github.scala_tessella.tessella.RegularPolygon.Polygon
          val polygon = Polygon(polygonSides)
          val perimeterEdges = tiling.perimeter.toRingEdges.toVector

          if (edgeIndex < perimeterEdges.length) {
            val selectedEdge = perimeterEdges(edgeIndex)

            val result = tiling.maybeGrowEdge(selectedEdge, polygon, AFTER_PERIMETER)

            result match {
              case Right(newTiling) =>
                // Success: update tiling and clear selections
                currentTiling.set(Some(newTiling))
                selectedPerimeterEdges.set(Set.empty)
                clearError()
              case Left(errMsg) =>
                // Failure: show error message
                showError(s"Cannot grow edge with ${polygonSides}-sided polygon: ${errMsg}")
            }
          } else {
            showError("Invalid edge index")
          }
        } catch {
          case e: Exception =>
            showError(s"Error growing edge: ${e.getMessage}")
        }
      case (None, _) =>
        showError("No tiling available to grow")
      case (_, None) =>
        // No polygon selected, just toggle selection
        togglePerimeterEdgeSelection(edgeId)
    }

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