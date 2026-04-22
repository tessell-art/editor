package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingSymmetry.{BoundaryEdge, BoundaryLocation, BoundaryVertex}
import io.github.scala_tessella.dcel.geometry.{BigLineSegment, BigPoint}
import io.github.scala_tessella.dcel.geometry.BigPoint.centroid
import io.github.scala_tessella.dcel.structure.VertexId
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.SvgDsl.{
  circleCoordsRadius,
  lineCoords,
  textCoords,
  uniformColorMap
}
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.toPoint
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}

object TessellationOverlayRenderer:

  def renderNodeLabels(
      coordinates: Map[VertexId, BigPoint],
      toCanvasPoint: Point => Point
  ): List[Element] =
    coordinates.toList.map: (vertexId, bigPoint) =>

      val vertex = bigPoint.toPoint
      val point  = toCanvasPoint(vertex)
      val offset = point + Point(4, -4)

      svg.text(
        textCoords(offset),
        svg.fontSize <-- EditorState.viewState.signal.map(_.viewTransform).distinct.map: transform =>

          val baseFontSize = 12
          val scaledSize   = (baseFontSize / transform.scale).max(8).min(20)
          scaledSize.toString
        ,
        svg.transform <-- EditorState.viewState.signal.map(_.viewTransform).distinct.map: transform =>
          s"rotate(${-transform.rotationDegrees} ${offset.x} ${offset.y})",
        svg.fill             := "#ffeb3b",
        svg.fontFamily       := "monospace",
        svg.fontWeight       := "bold",
        svg.textAnchor       := "start",
        svg.dominantBaseline := "middle",
        svg.className        := "node-label",
        svg.stroke           := "#000",
        svg.strokeWidth <-- EditorState.viewState.signal.map(_.viewTransform).distinct.map: transform =>
          (0.5 / transform.scale).max(0.2).min(1.0).toString,
        svg.paintOrder       := "stroke fill",
        vertexId.value
      )

  def renderUniformity(
      coordinates: Map[VertexId, BigPoint],
      uniMap: Map[VertexId, Int],
      toCanvasPoint: Point => Point
  ): List[Element] =
    coordinates.toList
      .filter: (vertexId, _) =>
        uniMap.contains(vertexId)
      .map: (vertexId, bigPoint) =>

        val point = toCanvasPoint(bigPoint.toPoint)
        val color = uniformColorMap.getOrElse(uniMap(vertexId), "black")

        svg.circle(
          circleCoordsRadius(point, 16),
          svg.fill        := color,
          svg.stroke      := color,
          svg.strokeWidth := "1"
        )

  def renderRotation(
      coordinates: Map[VertexId, BigPoint],
      rotList: List[BoundaryLocation],
      toCanvasPoint: Point => Point,
      durationSeconds: Int = 30
  ): List[Element] =
    val rotCoords = rotList.map:
      case BoundaryVertex(i)  => i -> coordinates(i)
      case BoundaryEdge(i, j) => i -> BigLineSegment(coordinates(i), coordinates(j)).midPoint

    if rotCoords.isEmpty then Nil
    else
      val center   = toCanvasPoint(rotCoords.map(_._2).centroid.toPoint)
      val elements = rotCoords.map: (id, coords) =>

        val vertex     = toCanvasPoint(coords.toPoint)
        val segment    = LineSegment(center, vertex).extendFromOrigin
        val p1         = segment.p1
        val p2         = segment.p2
        val width      = segment.length / 10
        val vector     = segment.unitVector * width
        val p3         = p2 + Point(-vector.y, vector.x)
        val gradientId = s"rot-grad-${id.value}"

        svg.g(
          svg.defs(
            svg.linearGradient(
              svg.idAttr        := gradientId,
              svg.gradientUnits := "userSpaceOnUse",
              svg.x1            := p2.x.toString,
              svg.y1            := p2.y.toString,
              svg.x2            := p3.x.toString,
              svg.y2            := p3.y.toString,
              svg.stop(svg.offsetAttr := "0%", svg.stopColor   := "Gold", svg.stopOpacity := "0.8"),
              svg.stop(svg.offsetAttr := "100%", svg.stopColor := "Blue", svg.stopOpacity := "0.0")
            )
          ),
          svg.polygon(
            svg.points        := s"${p1.x},${p1.y} ${p2.x},${p2.y} ${p3.x},${p3.y}",
            svg.fill          := s"url(#$gradientId)",
            svg.stroke        := "none",
            svg.pointerEvents := "none"
          )
        )

      List(
        svg.g(
          elements,
          svg.animateTransform(
            svg.attributeName := "transform",
            svg.attributeType := "XML",
            svg.tpe           := "rotate",
            svg.from          := s"360 ${center.x} ${center.y}",
            svg.to            := s"0 ${center.x} ${center.y}",
            svg.dur           := s"${durationSeconds}s",
            svg.repeatCount   := "indefinite"
          )
        )
      )

  def renderReflection(
      coordinates: Map[VertexId, BigPoint],
      refList: List[(BoundaryLocation, BoundaryLocation)],
      toCanvasPoint: Point => Point
  ): List[Element] =

    def locationToPoint(loc: BoundaryLocation): Point =
      toCanvasPoint(loc match
        case BoundaryVertex(i)  => coordinates(i).toPoint
        case BoundaryEdge(i, j) => LineSegment(coordinates(i).toPoint, coordinates(j).toPoint).midPoint)

    refList.map: (loc1, loc2) =>

      val vertex1          = locationToPoint(loc1)
      val vertex2          = locationToPoint(loc2)
      svg.line(
        lineCoords(LineSegment(vertex1, vertex2).extendFromMidPoint),
        svg.stroke          := "DarkOrange",
        svg.strokeWidth     := "1",
        svg.strokeDashArray := "5, 5",
        svg.className       := "previous-measurement-line",
        svg.pointerEvents   := "none"
      )
