package art.tessell.editor.components.popup

import com.raquo.laminar.api.L._
import com.raquo.laminar.api.features.unitArrows
import com.raquo.laminar.codecs.{BooleanAsTrueFalseStringCodec, StringAsIsCodec}
import org.scalajs.dom

object PopupCommons:

  // Laminar 17.2 doesn't ship `role` or `aria-modal` out of the box, so define them once.
  private val roleAttr      = htmlAttr("role", StringAsIsCodec)
  private val ariaModalAttr = aria.ariaAttr("modal", BooleanAsTrueFalseStringCodec)

  // Simple 'X' close icon
  def closeIcon: Element =
    svg.svg(
      svg.width       := "24",
      svg.height      := "24",
      svg.viewBox     := "0 0 24 24",
      svg.fill        := "none",
      svg.stroke      := "currentColor",
      svg.strokeWidth := "2",
      svg.path(svg.d := "M 18 6 L 6 18"),
      svg.path(svg.d := "M 6 6 L 18 18")
    )

  // Factory to produce a close handler bound to a by-name close action.
  // Returned as Observer[Any] so the same observer can drive click and key handlers.
  def closePopup(close: => Unit): Observer[Any] = Observer { _ =>

    close
  }

  // Selector for elements that can receive keyboard focus inside a dialog.
  private val focusableSelector =
    "a[href], button:not([disabled]), input:not([disabled]), " +
      "select:not([disabled]), textarea:not([disabled]), " +
      "[tabindex]:not([tabindex='-1'])"

  private def focusableWithin(root: dom.Element): List[dom.html.Element] =
    val nodes = root.querySelectorAll(focusableSelector)
    val out   = scala.collection.mutable.ListBuffer.empty[dom.html.Element]
    var i     = 0
    while i < nodes.length do
      nodes.item(i) match
        case el: dom.html.Element => out += el
        case _                    => ()
      i += 1
    out.toList

  def popupOverlay(
      closeObserver: Observer[Any],
      overlayClassName: String = "popup-overlay",
      extraMods: Modifier[HtmlElement]*
  )(children: Modifier[HtmlElement]*): Element =
    val mods =
      List[Modifier[HtmlElement]](
        className := overlayClassName,
        onClick --> closeObserver
      ) ++ extraMods.toList ++ children.toList
    div(mods*)

  def popupContent(
      closeObserver: Observer[Any],
      contentClassName: String = "popup-content"
  )(children: Modifier[HtmlElement]*): Element =
    var previousFocus: Option[dom.html.Element] = None
    val mods                                    =
      List[Modifier[HtmlElement]](
        className     := contentClassName,
        roleAttr      := "dialog",
        ariaModalAttr := true,
        tabIndex      := -1,
        onClick.stopPropagation --> {},
        // Escape closes the dialog. Stop propagation so the global key handler
        // (which would clear the canvas selection) doesn't also fire.
        documentEvents(_.onKeyDown).filter(_.key == "Escape") --> { ev =>

          ev.preventDefault()
          ev.stopPropagation()
          closeObserver.onNext(ev)
        },
        // Focus trap: cycle Tab / Shift+Tab inside the dialog.
        onKeyDown.filter(_.key == "Tab") --> { ev =>

          val root      = ev.currentTarget.asInstanceOf[dom.Element]
          val focusable = focusableWithin(root)
          if focusable.nonEmpty then
            val first  = focusable.head
            val last   = focusable.last
            val active = dom.document.activeElement
            if ev.shiftKey && active == first then
              ev.preventDefault()
              last.focus()
            else if !ev.shiftKey && active == last then
              ev.preventDefault()
              first.focus()
        },
        // On open: remember the trigger so we can restore focus on close,
        // and move focus into the dialog.
        onMountCallback { ctx =>

          previousFocus = Option(dom.document.activeElement).collect {
            case el: dom.html.Element => el
          }
          val rootEl = ctx.thisNode.ref
          val target =
            focusableWithin(rootEl).headOption
              .getOrElse(rootEl.asInstanceOf[dom.html.Element])
          target.focus()
        },
        onUnmountCallback { _ =>

          previousFocus.foreach(_.focus())
        },
        button(
          className  := "popup-close-btn",
          tpe        := "button",
          aria.label := "Close",
          onClick --> closeObserver,
          closeIcon
        )
      ) ++ children.toList
    div(mods*)

  // Platform-aware primary modifier label
  val primaryModLabel: String =
    if dom.window.navigator.platform.toLowerCase.contains("mac") then "⌘" else "Ctrl"
