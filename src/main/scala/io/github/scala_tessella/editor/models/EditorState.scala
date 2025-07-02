package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.IncrementalTiling
import io.github.scala_tessella.tessella.IncrementalTiling.Strictness
import io.github.scala_tessella.tessella.Topology.{Node => TilingNode}
import org.scalajs.dom

object EditorState:
  // File state
  val currentFileName: Var[Option[String]] = Var(None)

  // Polygon palette state
  val selectedPolygon: Var[Option[Int]] = Var[Option[Int]](None)

  // Canvas state
  val viewTransform: Var[ViewTransform] = Var(ViewTransform())

  // Editor mode state
  val editorMode: Var[EditorMode] = Var(EditorMode.Select)
  val activeTool: Var[Option[Tool]] = Var(None)

  // Loading state
  val isProcessing: Var[Boolean] = Var(false)

  // Tessellation state
  val currentTiling: Var[IncrementalTiling] = Var(IncrementalTiling.empty)
  val selectedPerimeterEdges: Var[Set[String]] = Var(Set.empty)
  val selectedTilingPolygons: Var[Set[String]] = Var(Set.empty)
  val strictness: Var[Strictness] = Var(Strictness.STRICT)

  // Color state
  val fillColor: Var[(Int, Int, Int)] = Var((76, 175, 80))
  val polygonColors: Var[Map[String, (Int, Int, Int)]] = Var(Map.empty)

  // Color picker
  val showColorPicker: Var[Boolean] = Var(false)
  val tempColor: Var[(Int, Int, Int)] = Var(fillColor.now())

  // Visualization state
  val showNodeLabels: Var[Boolean] = Var(false)

  // Error state
  val errorMessage: Var[Option[String]] = Var(None)
  val failedPlacement: Var[Option[FailedPolygonPlacement]] = Var(None)
  val failedDeletion: Var[Option[FailedPolygonDeletion]] = Var(None)

  // Canvas interaction state
  val isDragging: Var[Boolean] = Var(false)
  val dragStart: Var[Option[Point]] = Var(None)
  val canvasElementRef: Var[Option[dom.Element]] = Var(None)

  // About popup state
  val showAboutPopup: Var[Boolean] = Var(false)

  // Measurement tool state
  val measurementStartPoint: Var[Option[ClickablePoint]] = Var(None)
  val measurementEndPoint: Var[Option[ClickablePoint]] = Var(None)
  val highlightedPolygonId: Var[Option[String]] = Var(None)
  val clickablePoints: Var[List[ClickablePoint]] = Var(List.empty)
  val measurementResult: Var[Option[Double]] = Var(None)