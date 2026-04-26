package io.github.scala_tessella.editor.utils.file

import io.github.scala_tessella.editor.utils.AsyncUtils
import io.github.scala_tessella.editor.utils.file.SvgImporter.importTilingFromSVG
import io.github.scala_tessella.editor.operations.{ErrorOperations, RecentFilesOperations}
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TemplateLoader:

  private given ExecutionContext = ExecutionContext.global

  def loadTemplate(directory: String, fileName: String): Unit =
    // Assumes templates are in the /public/templates/ directory
    val url = s"templates/$directory/$fileName"
    dom.fetch(url).toFuture
      .flatMap { response =>

        if response.ok then response.text().toFuture
        else Future.failed(new Exception(s"Failed to load template: ${response.statusText}"))
      }
      .onComplete {
        case Success(svgContent) =>
          val _ = AsyncUtils.withLoadingState(() => importTilingFromSVG(svgContent, fileName))
          // Track in the recent-files list so it shows up under File → Recent.
          RecentFilesOperations.record(url, fileName)
        case Failure(exception)  =>
          ErrorOperations.showError(
            message = s"Failed to load template '$fileName': ${exception.getMessage}",
            context = Some("Template Loader"),
            hint = Some("Check your network connection and try again."),
            asToast = true,
            severity = ErrorOperations.Severity.Error
          )
      }
