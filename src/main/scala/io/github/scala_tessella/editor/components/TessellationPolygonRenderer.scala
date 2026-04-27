package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{EditorConfig, EditorMode, EditorState}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.utils.ColorRGB.*
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.toPoint
import io.github.scala_tessella.editor.utils.geo.Point

object TessellationPolygonRenderer:

  val selectionPattern: Element = svg.defs(
    svg.pattern(
      svg.idAttr       := "selection-pattern",
      svg.patternUnits := "userSpaceOnUse",
      svg.width        := "8",
      svg.height       := "8",
      svg.path(
        svg.d           := "M-2,2 l4,-4 M0,8 l8,-8 M6,10 l4,-4",
        svg.stroke      := "rgba(40, 40, 40, 0.6)",
        svg.strokeWidth := "1.5"
      )
    )
  )

  def renderTilingPolygons(
      tiling: TilingDCEL,
      toCanvasPoint: Point => Point
  ): List[Element] =
    val facesData: List[(FaceId, String)] =
      tiling.innerFacesVertices.map: (faceId, faceVertices) =>

        val pointStrings =
          faceVertices.map: vertex =>

            val point = toCanvasPoint(vertex.coords.toPoint)
            s"${point.x},${point.y}"
        (faceId, pointStrings.mkString(" "))

    facesData.map: (faceId, pointsStr) =>
      renderTilingPolygonFromPoints(pointsStr, faceId)

  def renderTilingPolygonFromPoints(pointsStr: String, faceId: FaceId): Element =
    val isSelected =
      EditorState.tessellationState.signal.map(_.selectedTilingPolygons).distinct.map:
        _.contains(faceId)

    val rgbSignal =
      EditorState.colorState.signal.map(_.polygonColors).distinct.map:
        _.getOrElse(faceId, EditorConfig.defaultPolygonColor).toRgb

    val opacity =
      EditorState.viewState.signal.map(_.showUniformity).distinct.map: showUni =>
        if showUni then "0.0" else "1.0"

    // Check if this polygon should be hidden due to failed deletion
    val shouldHideForDeletion =
      EditorState.errorState.signal.map(_.failedDeletion).distinct.map:
        case Some(failedDel) => failedDel.faceId == faceId
        case None            => false

    // Update stroke and styling based on editor mode. The Select-mode default colour and width
    // come from user-overridable Settings; Selected and Delete-mode overrides stay hardcoded as
    // they signal interaction state, not styling.
    val strokeColorSignal =
      isSelected
        .combineWith(EditorState.toolState.signal.map(_.editorMode).distinct)
        .combineWith(EditorState.colorState.signal.map(_.polygonEdgeColor).distinct)
        .map: (selected, mode, edgeColor) =>
          if selected then "#ff6b6b"
          else
            mode match
              case EditorMode.Select => edgeColor.toHex
              case EditorMode.Delete => "#ff4444"

    val strokeWidthSignal =
      isSelected
        .combineWith(EditorState.toolState.signal.map(_.editorMode).distinct)
        .combineWith(EditorState.settingsState.signal.map(_.polygonEdgeWidth).distinct)
        .map: (selected, mode, edgeWidth) =>
          if selected then "3.5"
          else
            mode match
              case EditorMode.Select => edgeWidth.toString
              case EditorMode.Delete => "2.0"

    val basePolygon = svg.polygon(
      svg.points := pointsStr, // static, precomputed
      svg.fill <-- rgbSignal, // reactive (color changes)
      svg.fillOpacity <-- opacity, // reactive (uniformity)
      svg.stroke <-- strokeColorSignal, // reactive
      svg.strokeWidth <-- strokeWidthSignal, // reactive
      svg.className <-- EditorState.toolState.signal.map(_.editorMode).distinct.map:
        case EditorMode.Select => "tiling-polygon"
        case EditorMode.Delete => "tiling-polygon delete-mode"
      ,
      // Cursor style and conditional opacity
      svg.style <--
        shouldHideForDeletion
          .combineWith(EditorState.toolState.signal.map(_.editorMode).distinct)
          .combineWith(EditorState.toolState.signal.map(_.activeTool).distinct)
          .combineWith(EditorState.toolState.signal.map(_.addSubmode).distinct)
          .map:
            case (hidden, mode, tool, sub) =>
              val cursor  = TessellationCursorStyles.polygonCursorCss(mode, tool, sub)
              val opacity = if hidden then "opacity: 0;" else "opacity: 1;"
              cursor + opacity
      ,
      onClick.compose(gate) --> { _ =>

        AppState.handleTilingPolygonClick(faceId)
      }
    )

    val patternOverlay = svg.polygon(
      svg.points        := pointsStr, // static, precomputed
      svg.fill          := "url(#selection-pattern)",
      svg.pointerEvents := "none",
      svg.style <-- shouldHideForDeletion.map: hidden =>
        if hidden then "opacity: 0;" else "opacity: 1;"
    )

    svg.g(
      basePolygon,
      child.maybe <--
        isSelected
          .combineWith(shouldHideForDeletion)
          .map:
            case (selected, hidden) => if selected && !hidden then Some(patternOverlay) else None
    )
