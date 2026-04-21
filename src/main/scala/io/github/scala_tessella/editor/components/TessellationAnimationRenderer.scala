package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.models.{DoublingAnimation, FanAnimation, MirrorAnimation}
import io.github.scala_tessella.editor.utils.geo.Point
import org.scalajs.dom

import scala.scalajs.js

object TessellationAnimationRenderer:

  private def renderAnimationPolygons(
      facePoints: List[(FaceId, String)],
      renderPolygon: (String, FaceId) => Element
  ): List[Element] =
    facePoints.map: (faceId, pointsStr) =>
      renderPolygon(pointsStr, faceId)

  private def renderAnimatedPolygonGroup(
      facePoints: List[(FaceId, String)],
      durationMs: Int,
      renderPolygon: (String, FaceId) => Element,
      initialTransform: String = "",
      delayMs: Double = 0.0
  )(transformAtEasedProgress: Double => String): Element =
    var cancel: () => Unit = () => ()

    svg.g(
      svg.style := "pointer-events: none;",
      renderAnimationPolygons(facePoints, renderPolygon),
      onMountCallback: ctx =>

        val node      = ctx.thisNode.ref
        var rafId     = 0
        var startTime = -1.0

        lazy val step: js.Function1[Double, Unit] = (ts: Double) =>

          if startTime < 0 then startTime = ts
          val elapsed  = ts - startTime
          val progress =
            if durationMs <= 0 then 1.0
            else Math.min(1.0, (elapsed - delayMs) / durationMs.toDouble)
          val eased    = easeOutCubic(progress)
          node.setAttribute("transform", transformAtEasedProgress(eased))
          if progress < 1.0 then
            rafId = dom.window.requestAnimationFrame(step)

        cancel = () =>
          if rafId != 0 then dom.window.cancelAnimationFrame(rafId)

        node.setAttribute("transform", initialTransform)
        rafId = dom.window.requestAnimationFrame(step)
      ,
      onUnmountCallback: _ =>
        cancel()
    )

  def renderFanAnimation(
      animation: FanAnimation,
      renderPolygon: (String, FaceId) => Element,
      toCanvasPoint: Point => Point
  ): Element =
    val pivot       = toCanvasPoint(animation.pivot)
    val stepDegrees = animation.stepAngle.toDegrees
    val copyGroups  = (0 until animation.copies).map: copyIndex =>

      val targetDegrees = stepDegrees * copyIndex
      val delayMs       = animation.staggerMs.toDouble * copyIndex
      renderAnimatedPolygonGroup(
        facePoints = animation.facePoints,
        durationMs = animation.durationMs,
        renderPolygon = renderPolygon,
        initialTransform = s"rotate(0 ${pivot.x} ${pivot.y})",
        delayMs = delayMs
      ): eased =>

        val angle = targetDegrees * eased
        s"rotate($angle ${pivot.x} ${pivot.y})"

    svg.g(
      svg.className := "fan-animation",
      copyGroups
    )

  def renderDoublingAnimation(
      animation: DoublingAnimation,
      renderPolygon: (String, FaceId) => Element
  ): Element =
    svg.g(
      svg.className := "doubling-animation",
      svg.g(
        svg.style := "pointer-events: none;",
        renderAnimationPolygons(animation.facePoints, renderPolygon)
      ),
      renderAnimatedPolygonGroup(
        facePoints = animation.facePoints,
        durationMs = animation.durationMs,
        renderPolygon = renderPolygon,
        initialTransform = "translate(0 0)"
      ): eased =>

        val dx = animation.delta.x * eased
        val dy = animation.delta.y * eased
        s"translate($dx $dy)"
    )

  def renderMirrorAnimation(
      animation: MirrorAnimation,
      renderPolygon: (String, FaceId) => Element
  ): Element =
    svg.g(
      svg.className := "mirror-animation",
      renderAnimatedPolygonGroup(
        facePoints = animation.facePoints,
        durationMs = animation.durationMs,
        renderPolygon = renderPolygon,
        initialTransform = "matrix(1 0 0 1 0 0)"
      ): eased =>

        val sy = 1.0 - (2.0 * eased)
        val ty = animation.axisY * (1.0 - sy)
        s"matrix(1 0 0 $sy 0 $ty)"
    )

  private def easeOutCubic(t: Double): Double =
    val clamped = t.max(0.0).min(1.0)
    1.0 - Math.pow(1.0 - clamped, 3)
