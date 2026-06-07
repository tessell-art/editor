package art.tessell.editor.components

import art.tessell.editor.i18n.I18n
import art.tessell.editor.models.EditorState
import com.raquo.laminar.api.L.*

/** Status row shown at the bottom edge of the canvas area (code-editor style).
  *
  * Two slots: file name on the left, measurement readout on the right. While a measurement is active the file
  * name is hidden so the measurement can use the available width without competing.
  */
object StatusRowComponent:

  private def distanceString(distance: Double): String =
    I18n.tNow("status.distanceFmt", f"$distance%.6f")

  private val fileNameDisplaySignal: Signal[String] =
    EditorState.fileState.signal.map(_.currentFileName).distinct
      .combineWith(
        EditorState.measurementState.signal.map(_.measurementResult).distinct,
        EditorState.localeState.signal
      )
      .map: (maybeName, maybeDistance, _) =>
        maybeDistance match
          case Some(_) => ""
          case None    => maybeName.getOrElse(I18n.tNow("status.untitled"))

  private val measurementDisplaySignal: Signal[Option[Element]] =
    EditorState.measurementState.signal.map(_.measurementResult).distinct
      .combineWith(
        EditorState.measurementState.signal.map(_.measurementAngle).distinct,
        EditorState.measurementState.signal.map(_.isAngleShownInRad).distinct,
        EditorState.localeState.signal
      )
      .map:
        case (None, _, _, _)                         => None
        case (Some(distance), None, _, _)            => Some(span(distanceString(distance)))
        case (Some(distance), Some(angle), isRad, _) =>
          val angleText    =
            if isRad then I18n.tNow("status.angle.radFmt", f"${angle.toDouble}%.6f")
            else I18n.tNow("status.angle.degFmt", f"${angle.toDegrees}%.2f")
          val distancePart = distanceString(distance)
          Some(span(
            span(
              onClick --> { _ =>

                EditorState.measurementState.update(s => s.copy(isAngleShownInRad = !s.isAngleShownInRad))
              },
              title <-- I18n.t("status.angle.toggle"),
              className := "angle-toggle",
              angleText
            ),
            span(s" · $distancePart")
          ))

  def element: Element =
    div(
      className := "status-row",
      div(
        className := "status-file-name",
        child.text <-- fileNameDisplaySignal
      ),
      div(
        className := "status-measurement",
        child.maybe <-- measurementDisplaySignal
      )
    )
