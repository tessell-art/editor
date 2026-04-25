package io.github.scala_tessella.editor.components.popup

import com.raquo.laminar.api.L._
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.editor.components.PolygonPaletteComponent
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.ring_seq.RingSeq.{reflectAt, rotateLeft, rotateRight}

object IrregularPolygonPopup:

  import PopupCommons._

  private val closeIrregular: Observer[org.scalajs.dom.MouseEvent] =
    closePopup(EditorState.popupState.update(_.copy(showIrregularPolygonPopup = false)))

  // Controls
  private def modify(f: Vector[AngleDegree] => Vector[AngleDegree]): Observer[org.scalajs.dom.MouseEvent] =
    Observer { e =>

      e.stopPropagation()
      EditorState.irregularState.update(_.updateHead(f))
    }

  private val shiftLeft: Observer[org.scalajs.dom.MouseEvent]  = modify(_.rotateLeft(1))
  private val shiftRight: Observer[org.scalajs.dom.MouseEvent] = modify(_.rotateRight(1))
  private val flip: Observer[org.scalajs.dom.MouseEvent]       = modify(_.reflectAt(1))

  def element: Element =
    popupOverlay(closeIrregular)(
      popupContent(closeIrregular)(
        h2("Adjust attaching edge"),
        div(
          className := "popup-text-scrollable",
          child.maybe <-- EditorState.irregularState.signal.map(_.headOption).distinct.map {
            case None         => Some(div("No irregular polygon"))
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
                      title     := "Move head left",
                      onClick --> shiftLeft,
                      "◀"
                    ),
                    button(
                      tpe       := "button",
                      className := "btn-right",
                      title     := "Move head right",
                      onClick --> shiftRight,
                      "▶"
                    ),
                    button(
                      tpe       := "button",
                      className := "btn-flip",
                      title     := "Flip",
                      onClick --> flip,
                      "Flip ⧎"
                    )
                  )
                )
              )
          }
        )
      )
    )
