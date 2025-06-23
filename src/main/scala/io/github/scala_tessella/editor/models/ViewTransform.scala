package io.github.scala_tessella.editor.models

case class ViewTransform(
                          scale: Double = 1.0,
                          rotationDegrees: Double = 0.0,
                          panX: Double = 0.0,
                          panY: Double = 0.0
                        ):
  // Helper method to normalize rotation to 0-359 degrees
  def normalizeRotation(degrees: Double): Double =
    val normalized = degrees % 360
    if normalized < 0 then normalized + 360 else normalized

  // Method to update rotation and keep it normalized
  def withRotation(newRotationDegrees: Double): ViewTransform =
    this.copy(rotationDegrees = normalizeRotation(newRotationDegrees))