package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.DirtyTracker
import io.github.scala_tessella.editor.utils.PolygonNameGenerator
import io.github.scala_tessella.editor.utils.PolygonNameGenerator.Template
import io.github.scala_tessella.editor.utils.file.TemplateLoader

/** Template gallery — visual replacement for the File → New from Template submenu.
  *
  * Layout:
  *   - Title bar with close (✕)
  *   - Left: category sidebar (Regular / Semi-regular / Aperiodic)
  *   - Right: thumbnail grid for the selected category
  *
  * Each thumbnail renders the template SVG via `<img>` (browser handles SVG natively) plus the template name
  * and pattern label. Click loads the template through `TemplateLoader` and closes the gallery.
  */
object TemplateGalleryPopup:

  import PopupCommons._

  private enum Category:
    case Regular, SemiRegular, Aperiodic

  private object Category:
    def labelKey(c: Category): String = c match
      case Regular     => "popup.templates.regular"
      case SemiRegular => "popup.templates.semiRegular"
      case Aperiodic   => "popup.templates.aperiodic"

    def directory(c: Category): String = c match
      case Regular     => "regular"
      case SemiRegular => "semiregular"
      case Aperiodic   => "aperiodic"

    def templates(c: Category): List[Template] = c match
      case Regular     => PolygonNameGenerator.regularNames
      case SemiRegular => PolygonNameGenerator.semiRegularNames
      case Aperiodic   => PolygonNameGenerator.irregularNames

  private val close: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.popupState.update(_.copy(showTemplateGallery = false)))

  def element: Element =
    val selected = Var(Category.Regular)

    popupOverlay(close, overlayClassName = "popup-overlay template-gallery-overlay")(
      popupContent(close, contentClassName = "popup-content template-gallery")(
        h2(child.text <-- I18n.t("popup.templates.title")),
        div(
          className := "template-gallery-body",
          categorySidebar(selected),
          thumbnailGrid(selected.signal)
        )
      )
    )

  private def categorySidebar(selected: Var[Category]): Element =
    div(
      className := "template-gallery-sidebar",
      List(Category.Regular, Category.SemiRegular, Category.Aperiodic).map: c =>
        button(
          tpe       := "button",
          className := "template-gallery-category",
          cls("active") <-- selected.signal.map(_ == c),
          onClick --> { _ =>

            selected.set(c)
          },
          child.text <-- I18n.t(Category.labelKey(c))
        )
    )

  private def thumbnailGrid(selected: Signal[Category]): Element =
    div(
      className := "template-gallery-grid",
      children <-- selected.map { c =>

        Category.templates(c).map(t => thumbnail(c, t))
      }
    )

  private def thumbnail(category: Category, template: Template): Element =
    val dir      = Category.directory(category)
    val imgUrl   = s"templates/$dir/${template.filename}"
    val patternF = template.pattern.trim
    div(
      className := "template-gallery-thumbnail",
      title     := s"${template.name} ${patternF}".trim,
      onClick --> { _ =>

        // Close the gallery immediately; the dirty-confirm popup (if any) takes over.
        EditorState.popupState.update(_.copy(showTemplateGallery = false))
        DirtyTracker.confirmIfDirty(() => TemplateLoader.loadTemplate(dir, template.filename))
      },
      img(
        className := "template-gallery-image",
        src       := imgUrl,
        alt       := template.name
      ),
      div(
        className := "template-gallery-caption",
        div(className := "template-gallery-name", template.name),
        if patternF.nonEmpty then
          div(className := "template-gallery-pattern", patternF)
        else emptyNode
      )
    )
