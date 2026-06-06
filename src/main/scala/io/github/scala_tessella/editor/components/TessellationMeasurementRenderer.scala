package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{AddSubmode, ClickablePoint, EditorState, Tool}
import io.github.scala_tessella.editor.components.AnchorMarker.MarkerState
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.utils.SvgDsl.{lineCoords, midArrow}
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}

object TessellationMeasurementRenderer:

  private val clickablePointClickObserver: Observer[(ClickablePoint, Tool, AddSubmode)] =
    Observer:
      case (point, Tool.Measurement, _)                => AppState.handlePointClickForMeasurement(point)
      case (point, Tool.Fan, _)                        => AppState.handlePointClickForFan(point)
      case (point, Tool.AddPolygon, AddSubmode.Inside) => AppState.handlePointClickForInsertion(point)
      case (point, _, _)                               => AppState.handlePointClickForDeletion(point)

  def renderClickablePoint(
      p: ClickablePoint,
      toCanvasPoint: Point => Point,
      angleDeg: Option[Double]
  ): Element =
    val point = toCanvasPoint(p.point)

    AnchorMarker.renderInteractive(
      point,
      p.anchor,
      MarkerState.Idle,
      angleDeg,
      "clickable-point",
      onClick.preventDefault
        .mapTo(p)
        .compose(stream =>
          gate(stream)
            .withCurrentValueOf(EditorState.toolState.signal.map(_.activeTool).distinct)
            .withCurrentValueOf(EditorState.toolState.signal.map(_.addSubmode).distinct)
        ) --> clickablePointClickObserver,
      svg.style         := "cursor: crosshair;",
      svg.style <--
        EditorState.toolState.signal.map(_.activeTool).distinct
          .combineWith(EditorState.toolState.signal.map(_.addSubmode).distinct)
          .map((tool, sub) => TessellationCursorStyles.clickablePointCursorCss(tool, sub)),
      svg.pointerEvents := "visible"
    )

  private def renderMeasurementPoint(
      p: ClickablePoint,
      toCanvasPoint: Point => Point,
      angleDeg: Option[Double],
      isStartPoint: Boolean = true
  ): Element =
    val point = toCanvasPoint(p.point)

    AnchorMarker.renderInteractive(
      point,
      p.anchor,
      if isStartPoint then MarkerState.MeasureStart else MarkerState.MeasureEnd,
      angleDeg,
      s"measurement-${if isStartPoint then "start" else "end"}-point",
      onClick.preventDefault.mapTo(p) --> AppState.handlePointClickForMeasurement
    )

  def renderMeasurementStartPoint(
      p: ClickablePoint,
      toCanvasPoint: Point => Point,
      angleDeg: Option[Double]
  ): Element =
    renderMeasurementPoint(p, toCanvasPoint, angleDeg, isStartPoint = true)

  def renderMeasurementEndPoint(
      p: ClickablePoint,
      toCanvasPoint: Point => Point,
      angleDeg: Option[Double]
  ): Element =
    renderMeasurementPoint(p, toCanvasPoint, angleDeg, isStartPoint = false)

  def renderMeasurementAngleArc(
      start: ClickablePoint,
      previousEnd: Point,
      end: ClickablePoint,
      toCanvasPoint: Point => Point
  ): Element =
    val point  = toCanvasPoint(start.point)
    val radius = 25.0

    val p1 = previousEnd
    val p2 = end.point

    val angle1 = start.point.angleTo(p1)
    val angle2 = start.point.angleTo(p2)

    val startArc = point.offsetPolar(radius, angle1)
    val endArc   = point.offsetPolar(radius, angle2)

    val deltaAngle = angle2.normalizeDeltaAngle(angle1)

    val largeArcFlag = 0
    val sweepFlag    = if deltaAngle.toDouble > 0 then 1 else 0

    val dAttribute =
      s"M ${startArc.x} ${startArc.y} A $radius $radius 0 $largeArcFlag $sweepFlag ${endArc.x} ${endArc.y}"

    svg.path(
      svg.d             := dAttribute,
      svg.fill          := "none",
      svg.stroke        := "white",
      svg.strokeWidth   := "1",
      svg.className     := "measurement-angle-arc",
      svg.pointerEvents := "none"
    )

  def renderPreviousMeasurementLine(
      start: ClickablePoint,
      end: Point,
      toCanvasPoint: Point => Point
  ): Element =
    val point1 = toCanvasPoint(start.point)
    val point2 = toCanvasPoint(end)

    svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke        := "#ffffff",
      svg.strokeWidth   := "1",
      svg.className     := "previous-measurement-line",
      svg.pointerEvents := "none"
    )

  def renderMeasurementLine(
      start: ClickablePoint,
      end: ClickablePoint,
      toCanvasPoint: Point => Point
  ): Element =
    val point1 = toCanvasPoint(start.point)
    val point2 = toCanvasPoint(end.point)

    svg.g(
      svg.className     := "measurement-line",
      svg.pointerEvents := "none",
      svg.line(
        lineCoords(LineSegment(point1, point2)),
        svg.stroke          := "#ffffff",
        svg.strokeWidth     := "2",
        svg.strokeDashArray := "5, 5"
      ),
      // White arrowhead pointing start → end, so the order is legible without colour
      // (ADR-014 — colour-blind redundancy). Shared with Add Copy ▸ Translate.
      midArrow(point1, point2)
    )
