package io.github.scala_tessella.editor.utils

import org.scalajs.dom

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success}

object TemplateLoader:

  private given ExecutionContext = ExecutionContext.global

  def loadTemplate(fileName: String): Unit =
    // Assumes templates are in the /public/templates/ directory
    val url = s"templates/$fileName"
    dom.fetch(url).toFuture
      .flatMap { response =>
        if response.ok then response.text().toFuture
        else Future.failed(new Exception(s"Failed to load template: ${response.statusText}"))
      }
      .onComplete {
        case Success(svgContent) =>
          // This function will contain the logic to parse the SVG
          // and update the application state.
          SvgImporter.importTilingFromSVG(svgContent, fileName)
        case Failure(exception) =>
          // You could implement a more user-friendly error display here
          println(s"Error loading template '$fileName': $exception")
          dom.window.alert(s"Failed to load template: $fileName")
      }
