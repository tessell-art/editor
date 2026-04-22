package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{ClickablePoint, EditorState, Tool}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.utils.SvgDsl.{circleCoordsRadius, lineCoords}
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}

object TessellationMeasurementRenderer:

  private val clickablePointClickObserver: Observer[(ClickablePoint, Option[Tool])] =
    Observer:
      case (point, Some(Tool.Measurement)) => AppState.handlePointClickForMeasurement(point)
      case (point, Some(Tool.Fan))         => AppState.handlePointClickForFan(point)
      case (point, Some(Tool.Inserter))    => AppState.handlePointClickForInsertion(point)
      case (point, _)                      => AppState.handlePointClickForDeletion(point)

  def renderClickablePoint(
      p: ClickablePoint,
      toCanvasPoint: Point => Point
  ): Element =
    val point = toCanvasPoint(p.point)

    svg.circle(
      circleCoordsRadius(point, 4),
      svg.fill          := "#ff9500",
      svg.stroke        := "black",
      svg.strokeWidth   := "1",
      svg.className     := "clickable-point",
      onClick.preventDefault
        .mapTo(p)
        .compose(stream =>
          gate(stream).withCurrentValueOf(EditorState.activeTool.signal)
        ) --> clickablePointClickObserver,
      svg.style         := "cursor: crosshair;",
      svg.style <-- EditorState.activeTool.signal.map: tool =>
        TessellationCursorStyles.clickablePointCursorCss(tool),
      svg.pointerEvents := "visible"
    )

  private def renderMeasurementPoint(
      p: ClickablePoint,
      toCanvasPoint: Point => Point,
      isStartPoint: Boolean = true
  ): Element =
    val point = toCanvasPoint(p.point)

    svg.circle(
      circleCoordsRadius(point, 5),
      svg.fill        := (if isStartPoint then "#00C853" else "#D50000"),
      svg.stroke      := "black",
      svg.strokeWidth := "1",
      svg.className   := s"measurement-${if isStartPoint then "start" else "end"}-point",
      onClick.preventDefault.mapTo(p) --> AppState.handlePointClickForMeasurement
    )

  def renderMeasurementStartPoint(
      p: ClickablePoint,
      toCanvasPoint: Point => Point
  ): Element =
    renderMeasurementPoint(p, toCanvasPoint, isStartPoint = true)

  def renderMeasurementEndPoint(
      p: ClickablePoint,
      toCanvasPoint: Point => Point
  ): Element =
    renderMeasurementPoint(p, toCanvasPoint, isStartPoint = false)

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

    svg.line(
      lineCoords(LineSegment(point1, point2)),
      svg.stroke          := "#ffffff",
      svg.strokeWidth     := "2",
      svg.strokeDashArray := "5, 5",
      svg.className       := "measurement-line",
      svg.pointerEvents   := "none"
    )
