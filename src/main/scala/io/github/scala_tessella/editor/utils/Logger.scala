package io.github.scala_tessella.editor.utils

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.UndefOr

object Logger:

  enum Level(val priority: Int):
    case Debug extends Level(10)
    case Info  extends Level(20)
    case Warn  extends Level(30)
    case Error extends Level(40)
    case Off   extends Level(1000)

  private var minLevel: Level = Level.Info

  def setLevel(level: Level): Unit =
    minLevel = level

  def getLevel: Level =
    minLevel

  /** Detect environment and set a sensible default logging level.
    *   - Debug for localhost/dev
    *   - Info for production
    *
    * Uses:
    *   - Vite's import.meta.env.MODE when available
    *   - process.env.NODE_ENV when available
    *   - hostname fallback
    */
  def initFromEnvironment(): Unit =
    val isLocalhost =
      try
        val h = dom.window.location.hostname
        h == "localhost" || h == "127.0.0.1" || h.endsWith(".local")
      catch case _: Throwable => false

    // Vite: import.meta.env.MODE (safe access)
    val viteMode: Option[String] =
      try
        val env = js.`import`.meta.selectDynamic("env")
        env.selectDynamic("MODE").asInstanceOf[UndefOr[String]].toOption
      catch case _: Throwable => None

    // Node-like: process.env.NODE_ENV (safe access; do not store global in a val)
    val nodeEnv: Option[String] =
      try
        val proc = js.Dynamic.global.selectDynamic("process")
        if js.typeOf(proc) != "undefined" && !js.isUndefined(proc) && !js.isUndefined(
            proc.selectDynamic("env")
          )
        then
          proc.selectDynamic("env").selectDynamic("NODE_ENV").asInstanceOf[UndefOr[String]].toOption
        else None
      catch case _: Throwable => None

    val mode = viteMode.orElse(nodeEnv).getOrElse(if isLocalhost then "development" else "production")
    if mode == "development" || isLocalhost then setLevel(Level.Debug)
    else setLevel(Level.Info)

  private def shouldLog(level: Level): Boolean =
    level.priority >= minLevel.priority

  private def prefix(level: Level): String =
    val ts = new js.Date().toISOString()
    s"[$ts] [${level.toString.toUpperCase}]"

  private def formatMessage(level: Level, msg: => String, data: Seq[Any]): String =
    s"${prefix(level)} $msg${if data.nonEmpty then " " + data.mkString(" ") else ""}"

  def debug(msg: => String, data: Any*): Unit =
    if shouldLog(Level.Debug) then
      dom.console.log(formatMessage(Level.Debug, msg, data))

  def info(msg: => String, data: Any*): Unit =
    if shouldLog(Level.Info) then
      dom.console.info(formatMessage(Level.Info, msg, data))

  def warn(msg: => String, data: Any*): Unit =
    if shouldLog(Level.Warn) then
      dom.console.warn(formatMessage(Level.Warn, msg, data))

  def error(msg: => String, data: Any*): Unit =
    if shouldLog(Level.Error) then
      dom.console.error(formatMessage(Level.Error, msg, data))

  def error(e: Throwable, context: => String = "Unhandled error"): Unit =
    if shouldLog(Level.Error) then
      dom.console.error(s"${prefix(Level.Error)} $context: ${e.getMessage}")
