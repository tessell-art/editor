package art.tessell.editor.operations

import art.tessell.editor.buildinfo.BuildInfo
import art.tessell.editor.utils.Logger
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Signal
import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

/** Polls a static `version.json` published alongside the bundle and exposes a signal the banner watches.
  * Strict-greater semantic comparison only: an equal or older response does nothing, so a CDN POP briefly
  * serving a stale `version.json` won't false-alarm.
  *
  * Three trigger sources, all racing into the same fetch:
  *   - Initial check 5 s after startup (avoid blocking first paint).
  *   - `visibilitychange → visible` (highest-signal trigger — users return to a stale tab).
  *   - 30-minute interval for tabs that genuinely stay foregrounded.
  *
  * Skipped entirely inside native shells (Tauri / `file://`); those update via their own packaging channel.
  */
object UpdateChecker:

  private given ExecutionContext = ExecutionContext.global

  private val versionUrl     = "version.json"
  private val initialDelayMs = 5_000
  private val intervalMs     = 30 * 60 * 1_000

  private val newVersion: Var[Option[String]] = Var(None)
  private val dismissed: mutable.Set[String]  = mutable.Set.empty

  /** Latest published version detected as strictly newer than `BuildInfo.version`, with session-dismissed
    * versions filtered out. `None` means "no banner."
    */
  val latestVersion: Signal[Option[String]] =
    newVersion.signal.map(_.filterNot(dismissed.contains))

  /** Installs the three trigger sources. No-op inside native shells. Safe to call multiple times — the
    * `installed` latch keeps the listeners single-shot.
    */
  private var installed: Boolean = false

  def install(): Unit =
    if installed then ()
    else
      installed = true
      if !shouldCheckOnline then Logger.debug("UpdateChecker: skipping (native shell or file:// load)")
      else
        dom.window.setTimeout(() => check(), initialDelayMs.toDouble): Unit
        dom.window.setInterval(() => check(), intervalMs.toDouble): Unit
        dom.document.addEventListener(
          "visibilitychange",
          (_: dom.Event) => if dom.document.visibilityState == "visible" then check()
        )

  /** True only when the runtime is a real web tab. Tauri exposes `window.__TAURI__`; any local-loaded shell
    * (Tauri, Android WebView via `file://`) reports `location.protocol == "file:"`. Either case skips the
    * fetch entirely.
    */
  def shouldCheckOnline: Boolean =
    val isTauri   =
      val t = dom.window.asInstanceOf[js.Dynamic].selectDynamic("__TAURI__")
      !js.isUndefined(t) && t != null
    val isFileUrl = dom.window.location.protocol == "file:"
    !isTauri && !isFileUrl

  /** Fetches `version.json`, parses the `version` field, and updates `newVersion` only if it is strictly
    * newer than the running bundle. Network / parse failures are logged at debug and dropped — this feature
    * is decorative; a flaky CDN should not produce user-visible noise.
    */
  def check(): Unit =
    val init = new dom.RequestInit {}
    init.cache = dom.RequestCache.`no-store`
    dom.fetch(versionUrl, init).toFuture
      .flatMap: r =>
        if r.ok then r.text().toFuture
        else Future.failed[String](new Exception(s"HTTP ${r.status}"))
      .onComplete:
        case Success(body) =>
          parseVersion(body) match
            case Some(remote) if isNewer(BuildInfo.version, remote) =>
              if !newVersion.now().contains(remote) then
                Logger.info(s"UpdateChecker: newer version detected ($remote, running ${BuildInfo.version})")
                newVersion.set(Some(remote))
            case _                                                  => ()
        case Failure(e)    =>
          Logger.debug(s"UpdateChecker: fetch failed (${e.getMessage})")

  private def parseVersion(body: String): Option[String] =
    Try {
      val parsed = js.JSON.parse(body)
      val v      = parsed.version
      if js.typeOf(v) == "string" then Some(v.asInstanceOf[String]) else None
    }.toOption.flatten

  /** Strict-greater semantic version comparison: split on `.`, compare integers left-to-right. Non-numeric
    * segments demote to 0 — a malformed live version never wins, so a typo on the server can't force a
    * banner. Equal-length tail of zeros is treated as equal (`1.2` == `1.2.0`).
    */
  def isNewer(current: String, candidate: String): Boolean =
    // SemVer pre-release identifiers (e.g. `-rc.1`) trail the numeric core. Strip them before splitting
    // so `0.3.7-rc.1` reads as the same release as `0.3.7` (equal, not newer) — we don't want to nag
    // users into reloading just because a release-candidate was published with the same eventual version.
    def parts(s: String): List[Int] =
      s.takeWhile(_ != '-').split('.').toList.map(seg => Try(seg.takeWhile(_.isDigit).toInt).getOrElse(0))
    val a                           = parts(current)
    val b                           = parts(candidate)
    val n                           = math.max(a.length, b.length)
    a.padTo(n, 0).zip(b.padTo(n, 0)).iterator
      .map { case (x, y) =>
        if x < y then -1 else if x > y then 1 else 0
      }
      .find(_ != 0)
      .exists(_ < 0)

  /** Hides the banner for the current session; the next genuinely-newer announcement re-shows. */
  def dismiss(): Unit =
    newVersion.now().foreach { v =>

      dismissed.add(v): Unit
    }
    newVersion.set(None)

  /** Reload via the dirty-state confirm flow when the tiling has unsaved edits, otherwise reload outright.
    * The `UnsavedChangesPopup` Save / Discard / Cancel branches map naturally onto save-then-reload /
    * reload-now / remind-me-later (the banner stays for the latter).
    */
  def reloadNow(): Unit =
    DirtyTracker.confirmIfDirty(() => dom.window.location.reload())
