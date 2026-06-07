package art.tessell.editor.components

import com.raquo.laminar.api.L.*
import munit.FunSuite
import org.scalajs.dom

/** Minimal scaffolding for Laminar-in-JSDOM component tests.
  *
  * Mounts a Laminar element into a fresh `<div>` attached to `document.body`, records the resulting
  * [[RootNode]] and the container, and tears both down in `afterEach`. Can be mixed in alongside
  * [[art.tessell.editor.EditorStateFixture]] — put this trait *last* in the mix-in list so its `afterEach`
  * runs before the state-fixture one (unmount before state restore).
  *
  * Extends `FunSuite` (rather than using a `self: FunSuite =>` self-type) so `super.afterEach` resolves
  * through the linearization chain into the next trait's override. A self-type leaves `super` pointing at
  * `Object` and the chain never reaches `EditorStateFixture`.
  *
  * {{{
  *   class MyPopupSpec extends FunSuite with EditorStateFixture with LaminarTestSupport:
  *     test("renders and wires close"):
  *       mount(MyPopup.element)
  *       assertEquals(querySelector("h1").map(_.textContent), Some("Title"))
  *       clickOn(".popup-close-btn")
  *       assert(!EditorState.popupState.now().showMyPopup)
  * }}}
  */
trait LaminarTestSupport extends FunSuite:

  private var rootNode: Option[RootNode]       = None
  private var containerEl: Option[dom.Element] = None

  /** Mount the element into a new container attached to `document.body`. Returns the container.
    *
    * Accepts `L.Element` (= `ReactiveElement[dom.Element]`) — the widest Laminar element type and the one
    * `render` itself takes. Popup/component `element` methods typically return this wider type because they
    * build from `popupOverlay`/`popupContent` helpers, not raw HTML tags.
    */
  protected def mount(element: Element): dom.Element =
    val c    = dom.document.createElement("div")
    dom.document.body.appendChild(c): Unit
    val root = render(c, element)
    containerEl = Some(c)
    rootNode = Some(root)
    c

  /** The container element created by the most recent [[mount]] call. */
  protected def container: dom.Element =
    containerEl.getOrElse(fail("Call mount(...) before accessing the container"))

  /** CSS-selector lookup scoped to the mounted container. */
  protected def querySelector(selector: String): Option[dom.Element] =
    Option(container.querySelector(selector))

  /** All elements matching the CSS selector, scoped to the mounted container. */
  protected def querySelectorAll(selector: String): List[dom.Element] =
    val nodes = container.querySelectorAll(selector)
    (0 until nodes.length).iterator.map(i => nodes.item(i).asInstanceOf[dom.Element]).toList

  /** Dispatch a synthetic click on the first element matching `selector`. Fails the test if no match. */
  protected def clickOn(selector: String): Unit =
    val el = querySelector(selector).getOrElse(fail(s"No element matches selector: $selector"))
    el.asInstanceOf[dom.HTMLElement].click()

  override def afterEach(context: AfterEach): Unit =
    // Unmount first so any teardown signal work doesn't run against a half-removed container.
    rootNode.foreach(_.unmount())
    containerEl.foreach(c => Option(c.parentNode).foreach(_.removeChild(c): Unit))
    rootNode = None
    containerEl = None
    super.afterEach(context)
