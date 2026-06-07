package art.tessell.editor.operations

import art.tessell.editor.models.EditorState

/** Operations over the measurement/eraser/inserter tool state (clickable points, start/end points,
  * measurement result, highlighted polygon, angle result).
  */
object MeasurementOperations:

  /** Clears all measurement-related state (clickable points, start/end/previous points, highlighted polygon,
    * distance and angle results). Atomic — one update, one signal emission. `isAngleShownInRad` is preserved
    * (it's a user preference, not per-measurement state).
    */
  def clearAll(): Unit =
    EditorState.measurementState.update(
      _.copy(
        clickablePoints = Nil,
        measurementStartPoint = None,
        measurementEndPoint = None,
        measurementPreviousEndPoint = None,
        highlightedPolygonId = None,
        measurementResult = None,
        measurementAngle = None
      )
    )
