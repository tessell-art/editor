package art.tessell.editor.components.popup

import art.tessell.editor.components.PolygonPaletteComponent
import art.tessell.editor.i18n.I18n
import art.tessell.editor.models.EditorState
import com.raquo.laminar.api.L._
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.ring_seq.RingSeq.{reflectAt, rotateLeft, rotateRight}

object IrregularPolygonPopup:

  import PopupCommons._

  private val closeIrregular: Observer[Any] =
    closePopup(EditorState.popupState.update(_.copy(showIrregularPolygonPopup = false)))

  // Controls
  private def modify(f: Vector[AngleDegree] => Vector[AngleDegree]): Observer[org.scalajs.dom.MouseEvent] =
    Observer { e =>

      e.stopPropagation()
      EditorState.irregularState.update(_.updateSelected(f))
    }

  private val shiftLeft: Observer[org.scalajs.dom.MouseEvent]  = modify(_.rotateLeft(1))
  private val shiftRight: Observer[org.scalajs.dom.MouseEvent] = modify(_.rotateRight(1))
  private val flip: Observer[org.scalajs.dom.MouseEvent]       = modify(_.reflectAt(1))

  def element: Element =
    popupOverlay(closeIrregular)(
      popupContent(closeIrregular)(
        h2(child.text <-- I18n.t("popup.irregular.title")),
        div(
          className := "popup-text-scrollable",
          child.maybe <-- EditorState.irregularState.signal.map(_.selectedShape).distinct.map {
            case None         => Some(div(child.text <-- I18n.t("popup.irregular.empty")))
            case Some(angles) =>
              Some(
                div(
                  className := "irregular-head-editor",
                  div(className := "big-preview", PolygonPaletteComponent.bigIrregularWithHead(angles)),
                  div(
                    className   := "controls",
                    button(
                      tpe       := "button",
                      className := "btn-left",
                      title <-- I18n.t("popup.irregular.shiftLeft"),
                      onClick --> shiftLeft,
                      "◀"
                    ),
                    button(
                      tpe       := "button",
                      className := "btn-right",
                      title <-- I18n.t("popup.irregular.shiftRight"),
                      onClick --> shiftRight,
                      "▶"
                    ),
                    button(
                      tpe       := "button",
                      className := "btn-flip",
                      onClick --> flip,
                      child.text <-- I18n.t("popup.irregular.flip")
                    )
                  )
                )
              )
          }
        )
      )
    )
