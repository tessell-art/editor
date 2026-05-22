package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.TilingSymmetry.{reflectionalVertexIds, rotationalVertexIds}
import io.github.scala_tessella.editor.models.{EditorState, ViewState}
import io.github.scala_tessella.editor.operations.OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.utils.{AsyncUtils, Logger}

import scala.concurrent.ExecutionContext.Implicits.global

/** Operations over the symmetry-overlay state (uniformity, rotation, reflection visibility flags and their
  * cached computations).
  */
object SymmetryOperations:

  /** Hides all symmetry overlays and clears their cached data. Atomic — one update, one emission. */
  def clearOverlays(): Unit =
    EditorState.viewState.update(
      _.copy(
        showUniformity = false,
        uniformityMap = None,
        showRotation = false,
        rotationVertexIds = None,
        showReflection = false,
        reflectionVertexIds = None
      )
    )

  private def displaySizeInfo(size: Int, dataType: String): Unit =
    ErrorOperations.info(
      s"$dataType data computed: $size ${if size == 1 then "class" else "classes"} found"
    )

  /** Toggles a visibility flag backed by a cache of computed data.
    *   - If currently shown, hides.
    *   - Else, if cache is populated, shows.
    *   - Else, computes asynchronously, caches, then shows (unless the user toggled off meanwhile).
    *
    * Callers pass lens-style getters/setters over the aggregate `ViewState` so this helper stays agnostic to
    * which specific overlay it is toggling.
    */
  private def toggleComputedOverlay[T](
      getShow: ViewState => Boolean,
      setShow: (ViewState, Boolean) => ViewState,
      getCache: ViewState => Option[T],
      setCache: (ViewState, Option[T]) => ViewState,
      dataType: String,
      sizeOf: Option[T] => Int
  )(compute: TilingDCEL => Option[T]): Unit =
    val currentlyShown = getShow(EditorState.viewState.now())
    if currentlyShown then
      EditorState.viewState.update(setShow(_, false))
    else if getCache(EditorState.viewState.now()).nonEmpty then
      EditorState.viewState.update(setShow(_, true))
    else
      val tiling = EditorState.tessellationState.now().currentTiling
      AsyncUtils
        .withLoadingState { () =>

          if tiling.isEmpty then None
          else compute(tiling)
        }
        .foreach { computed =>

          // Only apply if the user still intends to show (hasn't toggled off meanwhile)
          if !getShow(EditorState.viewState.now()) then
            EditorState.viewState.update: s =>
              setShow(setCache(s, computed), true)
            displaySizeInfo(sizeOf(computed), dataType)
        }

  /** Toggles the visibility of the uniformity data. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowUniformity(): Unit =
    ifNotProcessing:
      toggleComputedOverlay(
        getShow = _.showUniformity,
        setShow = (s, b) => s.copy(showUniformity = b),
        getCache = _.uniformityMap,
        setCache = (s, c) => s.copy(uniformityMap = c),
        dataType = "Uniformity",
        sizeOf = _.map(_.values.toSet.size).getOrElse(0)
      ): tiling =>

        val classes  = tiling.uniformityTree.flattenLeaves
        val indexMap = classes.zipWithIndex.flatMap((vertexIds, index) => vertexIds.map((_, index))).toMap
        Some(indexMap)

  /** Toggles the visibility of the rotation axes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowRotation(): Unit =
    ifNotProcessing:
      toggleComputedOverlay(
        getShow = _.showRotation,
        setShow = (s, b) => s.copy(showRotation = b),
        getCache = _.rotationVertexIds,
        setCache = (s, c) => s.copy(rotationVertexIds = c),
        dataType = "Rotation",
        sizeOf = _.map(_.size).getOrElse(1)
      ): tiling =>

        val vs = tiling.rotationalVertexIds
        if vs.size > 1 then
          Logger.debug(s"Rotation vertices: $vs")
          Some(vs)
        else
          None

  /** Toggles the visibility of the reflection axes. Does nothing if the editor is currently processing an
    * operation.
    */
  def toggleShowReflection(): Unit =
    ifNotProcessing:
      toggleComputedOverlay(
        getShow = _.showReflection,
        setShow = (s, b) => s.copy(showReflection = b),
        getCache = _.reflectionVertexIds,
        setCache = (s, c) => s.copy(reflectionVertexIds = c),
        dataType = "Reflection",
        sizeOf = _.map(_.size).getOrElse(0)
      ): tiling =>

        val vs = tiling.reflectionalVertexIds
        if vs.nonEmpty then
          Logger.debug(s"Reflection vertices: $vs")
          Some(vs)
        else
          None
