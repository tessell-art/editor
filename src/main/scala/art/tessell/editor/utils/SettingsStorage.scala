package art.tessell.editor.utils

import art.tessell.editor.models.ReduceMotionPref
import org.scalajs.dom

object SettingsStorage:

  private val defaultFillKey       = "tessella.settings.defaultFillColor"
  private val perimeterEdgeKey     = "tessella.settings.perimeterEdgeColor"
  private val boundaryEdgeWidthKey = "tessella.settings.boundaryEdgeWidth"
  private val polygonEdgeColorKey  = "tessella.settings.polygonEdgeColor"
  private val polygonEdgeWidthKey  = "tessella.settings.polygonEdgeWidth"
  private val reduceMotionKey      = "tessella.settings.reduceMotion"

  private def read(key: String): Option[String] =
    try Option(dom.window.localStorage.getItem(key))
    catch case _: Throwable => None

  private def write(key: String, value: String): Unit =
    try dom.window.localStorage.setItem(key, value)
    catch case _: Throwable => ()

  def loadDefaultStartFillColor(): Option[ColorRGB] =
    read(defaultFillKey).flatMap(ColorRGB.parseHex)

  def saveDefaultStartFillColor(color: ColorRGB): Unit =
    write(defaultFillKey, color.toHex)

  def loadPerimeterEdgeColor(): Option[ColorRGB] =
    read(perimeterEdgeKey).flatMap(ColorRGB.parseHex)

  def savePerimeterEdgeColor(color: ColorRGB): Unit =
    write(perimeterEdgeKey, color.toHex)

  def loadBoundaryEdgeWidth(): Option[Double] =
    read(boundaryEdgeWidthKey).flatMap(s => s.toDoubleOption)

  def saveBoundaryEdgeWidth(width: Double): Unit =
    write(boundaryEdgeWidthKey, width.toString)

  def loadPolygonEdgeColor(): Option[ColorRGB] =
    read(polygonEdgeColorKey).flatMap(ColorRGB.parseHex)

  def savePolygonEdgeColor(color: ColorRGB): Unit =
    write(polygonEdgeColorKey, color.toHex)

  def loadPolygonEdgeWidth(): Option[Double] =
    read(polygonEdgeWidthKey).flatMap(s => s.toDoubleOption)

  def savePolygonEdgeWidth(width: Double): Unit =
    write(polygonEdgeWidthKey, width.toString)

  def loadReduceMotion(): Option[ReduceMotionPref] =
    read(reduceMotionKey).flatMap:
      case "Auto" => Some(ReduceMotionPref.Auto)
      case "On"   => Some(ReduceMotionPref.On)
      case "Off"  => Some(ReduceMotionPref.Off)
      case _      => None

  def saveReduceMotion(pref: ReduceMotionPref): Unit =
    write(reduceMotionKey, pref.toString)
