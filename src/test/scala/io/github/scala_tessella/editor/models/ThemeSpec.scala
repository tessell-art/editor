package io.github.scala_tessella.editor.models

import com.raquo.airstream.core.Signal
import com.raquo.airstream.ownership.ManualOwner
import munit.FunSuite

import scala.scalajs.js

final class ThemeSpec extends FunSuite:

  override def beforeAll(): Unit =
    // Ensure matchMedia exists before EditorState is initialized.
    installMatchMedia(matches = true)

  override def afterEach(context: AfterEach): Unit =
    // Reset to default after each test to avoid cross-test leakage.
    EditorState.themeState.update(_.copy(userThemePreference = None))

  test("effectiveTheme falls back to system when no preference is set") {
    EditorState.themeState.update(_.copy(userThemePreference = None))
    val systemTheme    = sampleSignal(EditorState.themeState.signal.map(_.systemTheme).distinct)
    val effectiveTheme = sampleSignal(EditorState.effectiveTheme)
    assertEquals(effectiveTheme, systemTheme)
  }

  test("effectiveTheme uses user preference when set") {
    EditorState.themeState.update(_.copy(userThemePreference = Some(Theme.Dark)))
    assertEquals(sampleSignal(EditorState.effectiveTheme), Theme.Dark)
  }

  private def installMatchMedia(matches: Boolean): Unit =
    val mql       = js.Dynamic.literal(
      matches = matches,
      addEventListener = (_: String, _: js.Function1[js.Dynamic, Unit]) => (),
      removeEventListener = (_: String, _: js.Function1[js.Dynamic, Unit]) => ()
    )
    val windowDyn = js.Dynamic.global.window.asInstanceOf[js.Dynamic]
    windowDyn.updateDynamic("matchMedia")((_: String) => mql)

  private def sampleSignal[A](signal: Signal[A]): A =
    var value: Option[A] = None
    val owner            = new ManualOwner
    val sub              = signal.foreach { v =>

      value = Some(v)
    }(using owner)
    sub.kill()
    owner.killSubscriptions()
    value.getOrElse(fail("Signal did not emit synchronously"))
