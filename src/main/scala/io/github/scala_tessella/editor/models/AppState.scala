package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.TilingGrowth.OtherNodeStrategy.AFTER_PERIMETER
import io.github.scala_tessella.tessella.Topology.{Edge, NodeOrdering}
import org.scalajs.dom
import io.github.scala_tessella.editor.utils.TilingGenerator
import scala.scalajs.js
import scala.util.Try

// Case class to represent a failed polygon placement
case class FailedPolygonPlacement(
                                   edgeIndex: Int,
                                   polygonSides: Int,
                                   edge: Edge,
                                   tiling: Tiling
                                 )

// Editor mode enumeration
enum EditorMode:
  case Select, Delete

object AppState:
  // Polygon palette state
  val polygonSides: List[Int] = List(3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 24, 42)
  val selectedPolygon: Var[Option[Int]] = Var[Option[Int]](None)

  // Canvas state - simplified to only include view transform
  val viewTransform: Var[ViewTransform] = Var(ViewTransform())

  // Editor mode state
  val editorMode: Var[EditorMode] = Var(EditorMode.Select)

  // Tessellation state - start with empty tiling
  val currentTiling: Var[Option[Tiling]] = Var(None)
  val selectedPerimeterEdges: Var[Set[String]] = Var(Set.empty)
  val selectedTilingPolygons: Var[Set[String]] = Var(Set.empty)

  val fillColor: Var[(Int, Int, Int)] = Var((76, 175, 80)) // Default: Material green (R,G,B)
  val polygonColors: Var[Map[String, (Int, Int, Int)]] = Var(Map.empty)

  // Visualization state
  val showNodeLabels: Var[Boolean] = Var(false)

  // Error message state
  val errorMessage: Var[Option[String]] = Var(None)

  // Failed polygon placement state - for showing wireframe feedback
  val failedPlacement: Var[Option[FailedPolygonPlacement]] = Var(None)

  // Canvas interaction state
  val isDragging: Var[Boolean] = Var(false)
  val dragStart: Var[Option[Point]] = Var(None)
  val canvasElementRef: Var[Option[dom.Element]] = Var(None)

  // Toggle editor mode between Select and Delete
  def toggleEditorMode(): Unit =
    editorMode.update {
      case EditorMode.Select => EditorMode.Delete
      case EditorMode.Delete => EditorMode.Select
    }

  // Apply color to selected polygons
  def applyColorToSelectedPolygons(color: (Int, Int, Int)): Unit =
    val selectedIds = selectedTilingPolygons.now()
    if selectedIds.nonEmpty then
      // Extract polygon tags from the selected polygon IDs
      val selectedTags = selectedIds.map { id =>
        // Remove "tiling-poly-" prefix to get the polygon tag
        if id.startsWith("tiling-poly-") then id.substring("tiling-poly-".length)
        else id
      }

      // Update colors for selected polygon tags
      polygonColors.update { currentColors =>
        selectedTags.foldLeft(currentColors) { (colors, tag) =>
          colors + (tag -> color)
        }
      }

  // Toggle node labels visibility
  def toggleNodeLabels(): Unit =
    showNodeLabels.update(!_)

  // Show error message temporarily with optional failed placement info
  def showError(message: String, placement: Option[FailedPolygonPlacement] = None): Unit =
    errorMessage.set(Some(message))
    failedPlacement.set(placement)

    // Clear error and failed placement after 3 seconds
    Try {
      if (js.typeOf(js.Dynamic.global.window) != "undefined") {
        dom.window.setTimeout(() => {
          errorMessage.set(None)
          failedPlacement.set(None)
        }, 3000)
      }
    }.recover {
      case _ => // Ignore errors in test environment
    }

  // Clear error message and failed placement
  def clearError(): Unit =
    errorMessage.set(None)
    failedPlacement.set(None)

  // Polygon selection with tiling creation logic
  def selectPolygon(sides: Int): Unit =
    selectedPolygon.set(Some(sides))

    // If tiling is empty, create a new tiling from the selected polygon
    if currentTiling.now().isEmpty then
      TilingGenerator.createTilingFromPolygon(sides) match
        case Some(tiling) =>
          currentTiling.set(Some(tiling))
          clearAllSelections() // Clear selections when new tiling is created
        case None =>
          showError(s"Failed to create tiling from $sides-sided polygon")

  // Check if tiling is empty
  def isTilingEmpty: Boolean = currentTiling.now().isEmpty

  // Clear tiling and reset to empty state
  def clearTiling(): Unit =
    currentTiling.set(None)
    selectedTilingPolygons.set(Set.empty)
    selectedPerimeterEdges.set(Set.empty)

  // Clear all selections
  def clearAllSelections(): Unit =
    selectedTilingPolygons.set(Set.empty)
    selectedPerimeterEdges.set(Set.empty)

  // Handle tiling polygon click based on current editor mode
  def handleTilingPolygonClick(polygonId: String): Unit =
    editorMode.now() match
      case EditorMode.Select =>
        toggleTilingPolygonSelection(polygonId)
      case EditorMode.Delete =>
        attemptPolygonDeletion(polygonId)

  // Toggle tiling polygon selection
  def toggleTilingPolygonSelection(polygonId: String): Unit =
    selectedTilingPolygons.update { selected =>
      if selected.contains(polygonId) then selected - polygonId
      else selected + polygonId
    }

  // Attempt to delete a polygon from the tessellation
  private def attemptPolygonDeletion(polygonId: String): Unit =
    currentTiling.now() match
      case Some(tiling) =>
        // Extract polygon tag from the ID
        val polyTag = if polygonId.startsWith("tiling-poly-") then
          polygonId.substring("tiling-poly-".length)
        else
          polygonId

        // Find the specific polygon in the tiling
        val targetPolygon = tiling.orientedPolygons.find { poly =>
          val nodes = poly.toPolygonPathNodes
          val tag = nodes.sorted(NodeOrdering).map(_.toString).mkString("-")
          tag == polyTag
        }

        targetPolygon match
          case Some(polygon) =>
            // Get the polygon's nodes and edges
            val polygonNodes = polygon.toPolygonPathNodes
            val polygonEdges = polygonNodes.zipWithIndex.map { case (node, i) =>
              val nextNode = polygonNodes((i + 1) % polygonNodes.length)
              Edge(node, nextNode)
            }.toSet

            // Get perimeter edges
            val perimeterEdges = tiling.perimeter.toRingEdges.toSet

            // Find which polygon edges are on the perimeter
            val edgesOnPerimeter = polygonEdges.intersect(perimeterEdges)

            if edgesOnPerimeter.nonEmpty then
              val edgeCount = edgesOnPerimeter.size
              val edgeList = edgesOnPerimeter.map(edge => s"${edge.lesserNode}-${edge.greaterNode}").mkString(", ")
              showError(s"Potentially deletable polygon $polyTag: would remove $edgeCount perimeter edge${if edgeCount > 1 then "s" else ""} ($edgeList). Deletion of polygons with perimeter edges not yet supported.")
            else
              // This polygon has no perimeter edges, so it could not be deleted
              showError(s"Polygon $polyTag has no perimeter edges, it cannot be removed safely.")

          case None =>
            showError(s"Could not find polygon with tag: $polyTag")

      case None =>
        showError("No tessellation available to modify")

  // Toggle perimeter edge selection
  def togglePerimeterEdgeSelection(edgeId: String): Unit =
    selectedPerimeterEdges.update { selected =>
      if selected.contains(edgeId) then selected - edgeId
      else selected + edgeId
    }

  // Handle perimeter edge click with polygon growth
  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    (currentTiling.now(), selectedPolygon.now()) match
      case (Some(tiling), Some(polygonSides)) =>
        // Try to grow the edge with the selected polygon
        try
          import io.github.scala_tessella.tessella.RegularPolygon.Polygon
          val polygon = Polygon(polygonSides)
          val perimeterEdges = tiling.perimeter.toRingEdges.toVector

          if edgeIndex < perimeterEdges.length then
            val selectedEdge = perimeterEdges(edgeIndex)

            val result = tiling.maybeGrowEdge(selectedEdge, polygon, AFTER_PERIMETER)

            result match
              case Right(newTiling) =>
                // Success: update tiling and clear selections
                currentTiling.set(Some(newTiling))
                selectedPerimeterEdges.set(Set.empty)
                clearError()
              case Left(errMsg) =>
                // Failure: show error message with wireframe
                val placement = FailedPolygonPlacement(edgeIndex, polygonSides, selectedEdge, tiling)
                val truncated =
                  val idx = errMsg.indexOf("See SVG")
                  if idx >= 0 then errMsg.substring(0, idx)
                  else errMsg
                showError(s"Cannot grow edge with $polygonSides-sided polygon: $truncated", Some(placement))
          else
            showError("Invalid edge index")
        catch
          case e: Exception =>
            showError(s"Error growing edge: ${e.getMessage}")
      case (None, _) =>
        showError("No tiling available to grow")
      case (_, None) =>
        // No polygon selected, just toggle selection
        togglePerimeterEdgeSelection(edgeId)

  // Delete selected elements (only applies to tiling polygons now)
  def deleteSelectedElements(): Unit =
    // For now, we don't support deleting parts of tessellations
    // This could be enhanced later to support tiling modifications
    if (selectedTilingPolygons.now().nonEmpty) then
      showError("Tessellation polygon deletion not supported yet")

  // Helper to get or generate a unique RGB color for a given polygon id
  def getOrAssignPolygonColor(polyTag: String): (Int, Int, Int) =
    polygonColors.now().get(polyTag) match
      case Some(rgb) => rgb
      case None =>
        val rgb = fillColor.now()
        polygonColors.update(_ + (polyTag -> rgb))
        rgb