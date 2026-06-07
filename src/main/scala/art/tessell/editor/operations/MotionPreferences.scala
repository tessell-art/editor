package art.tessell.editor.operations

import art.tessell.editor.models.{EditorConfig, EditorState, ReduceMotionPref}
import com.raquo.laminar.api.L.*

/** Resolves the user's reduce-motion preference into effective animation timings. `Auto` honours the OS-level
  * `prefers-reduced-motion` media query; `On` / `Off` override it explicitly.
  *
  * Animation operations (TransformOperations, etc.) read these instead of the raw `EditorConfig` constants so
  * user preferences propagate without each call site having to reason about them.
  *
  * The OS-level state lives in `EditorState.osPrefersReducedMotion`, which `Editor.scala` keeps in sync with
  * the `matchMedia("(prefers-reduced-motion: reduce)")` listener.
  */
object MotionPreferences:

  /** Snapshot helper used by the dynamic animation paths (Fan, Doubling, Mirror) to decide whether to skip
    * the animation timeline entirely.
    */
  def reducedMotion: Boolean =
    EditorState.settingsState.now().reduceMotion match
      case ReduceMotionPref.On   => true
      case ReduceMotionPref.Off  => false
      case ReduceMotionPref.Auto => EditorState.osPrefersReducedMotion.now()

  /** Reactive form of [[reducedMotion]]. Subscribed to from `Editor.scala` to toggle the `body.reduce-motion`
    * class so CSS hover transforms / transitions can opt out.
    */
  val reducedMotionSignal: Signal[Boolean] =
    EditorState.settingsState.signal.map(_.reduceMotion).distinct
      .combineWith(EditorState.osPrefersReducedMotion.signal)
      .map:
        case (ReduceMotionPref.On, _)      => true
        case (ReduceMotionPref.Off, _)     => false
        case (ReduceMotionPref.Auto, osOn) => osOn

  /** Effective animation duration in milliseconds. Returns 0 when motion is reduced (animation effectively
    * skipped — the visual transition is instant; the underlying state mutation still happens).
    */
  def effectiveAnimationDurationMs: Int =
    if reducedMotion then 0 else EditorConfig.fanAnimationDurationMs

  /** Effective stagger between fan copies in milliseconds. Returns 0 when motion is reduced. */
  def effectiveStaggerMs: Int =
    if reducedMotion then 0 else EditorConfig.fanAnimationStaggerMs
