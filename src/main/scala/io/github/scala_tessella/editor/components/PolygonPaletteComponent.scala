package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import io.github.scala_tessella.dcel.geometry.{AngleDegree, RegularPolygon}
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.i18n.I18n
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState, PaletteSheetDetent}
import io.github.scala_tessella.editor.operations.OperationGuard.gate
import io.github.scala_tessella.editor.operations.TessellationOperations.*
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}
import io.github.scala_tessella.editor.utils.{PolygonNameGenerator, PolygonSvg}
import io.github.scala_tessella.editor.utils.SvgDsl.*
import io.github.scala_tessella.editor.components.PaletteDragGesture.DragShape

object PolygonPaletteComponent:

  extension (i: Int)
    private def clamp(min: Int, max: Int): Int =
      Math.max(min, Math.min(i, max))

  // --- Pure helpers (testable without Laminar) ---

  /** Parse a user-supplied sides count, falling back to 3 on bad input and clamping to [3, 100]. */
  private[components] def validateSides(input: String): Int =
    input.toIntOption.getOrElse(3).clamp(3, 100)

  /** Parse a user-supplied rhombus acute angle in degrees, falling back to 45 on bad input and clamping to
    * [1, 90]. (90° is a degenerate square; 91°+ would produce a rhombus with the obtuse angle on the wrong
    * vertex pair.)
    */
  private[components] def validateRhombusAngle(input: String): Int =
    input.toIntOption.getOrElse(45).clamp(1, 90)

  /** Build the angle vector for a rhombus parameterized by its acute angle (in degrees). */
  private[components] def rhombusAngles(acuteDegrees: Int): Vector[AngleDegree] =
    val a = AngleDegree(acuteDegrees)
    val b = AngleDegree(180 - acuteDegrees)
    Vector(a, b, a, b)

  /** True when an angle vector represents a regular polygon (all interior angles equal). */
  private[components] def isRegularShape(angles: Vector[AngleDegree]): Boolean =
    angles.size >= 3 && angles.forall(_ == angles.head)

  /** Tooltip for any palette button: "$sides-sided polygon (name)". The polygon name still comes from
    * `PolygonNameGenerator` (English-only canonical names like "triangle"/"square"); a follow-up can localize
    * that catalog if needed.
    */
  private[components] def polygonTooltip(sides: Int): String =
    I18n.tNow("palette.tooltip.polygonFmt", sides.toString, PolygonNameGenerator.polygonName(sides))

  /** Label for an irregular-polygon queue slot. Localized "Irregular" when no shape is recorded yet, "{n}≠"
    * otherwise. The "≠" suffix marks the entry as not-equal-angled (i.e. genuinely irregular), distinguishing
    * it from regular queue entries that just show the sides count.
    */
  private[components] def irregularPolygonLabel(angles: Option[Vector[AngleDegree]]): String =
    angles match
      case None    => I18n.tNow("palette.irregular.label")
      case Some(a) => s"${a.size}≠"

  /** Compose the class string for a palette button from its baseClasses, selection, and processing flags. */
  private[components] def polygonButtonClasses(
      baseClasses: String,
      selected: Boolean,
      processing: Boolean
  ): String =
    val withSelection = if selected then s"$baseClasses selected" else baseClasses
    if processing then s"$withSelection disabled" else withSelection

  private def polygonButtonClass(baseClasses: String, isSelectedSignal: Signal[Boolean]): Signal[String] =
    isSelectedSignal.combineWith(EditorState.uiState.signal.map(_.isProcessing).distinct).map {
      (selected, processing) =>

        polygonButtonClasses(baseClasses, selected, processing)
    }

  // ---------- Top-level layout ----------
  //
  // On desktop / tablet the palette is a fixed left column; CSS treats `.polygon-palette` as
  // a normal block. At phone widths it's restyled as a bottom sheet with two detents
  // (Peek / Full); the sheet handle becomes visible and `palette-sheet--peek/full` controls
  // the height. The sheet auto-retracts to Peek whenever a shape is selected so the canvas
  // is visible during placement.

  def element: Element =
    div(
      className <-- detentClassSignal,
      sheetHandle(),
      div(
        className := "polygon-palette-content",
        shapesSection(),
        addShapeSection(),
        selectedInfoFooter()
      )
    )

  private val detentClassSignal: Signal[String] =
    EditorState.uiState.signal.map(_.paletteSheetDetent).distinct
      .combineWith(EditorState.uiState.signal.map(_.isPaletteDragActive).distinct)
      .map: (detent, isDragging) =>

        val base = detent match
          case PaletteSheetDetent.Peek => "polygon-palette palette-sheet--peek"
          case PaletteSheetDetent.Full => "polygon-palette palette-sheet--full"
        if isDragging then s"$base is-palette-dragging" else base

  private def sheetHandle(): Element =
    div(
      className := "palette-sheet-handle",
      title <-- I18n.t("palette.handle.title"),
      onClick --> { _ =>

        togglePaletteDetent()
      },
      div(className  := "palette-sheet-grip"),
      span(className := "palette-sheet-handle-label", child.text <-- I18n.t("palette.handle.label"))
    )

  private def togglePaletteDetent(): Unit =
    EditorState.uiState.update: s =>

      val next = s.paletteSheetDetent match
        case PaletteSheetDetent.Peek => PaletteSheetDetent.Full
        case PaletteSheetDetent.Full => PaletteSheetDetent.Peek
      s.copy(paletteSheetDetent = next)

  /** Retract the palette sheet to Peek. Called after any palette selection so the canvas is visible during
    * placement on phone widths. No-op visually on desktop because the palette renders as a left column there.
    */
  private def retractSheet(): Unit =
    EditorState.uiState.update(_.copy(paletteSheetDetent = PaletteSheetDetent.Peek))

  // ---------- Section: shape queue (regular + irregular, mixed) ----------
  //
  // The queue is the single home for both fixed regulars (initially seeded with 3, 4, 5, 6, 8, 12)
  // and irregulars produced by the factories below. Clicks route by shape kind: a regular slot still
  // calls `selectPolygon(sides)` so the footer can offer "Select all triangles", while an irregular
  // slot uses `selectIrregularInPalette(index)` for "Select all of this shape".

  private def shapesSection(): Element =
    div(
      className := "palette-section palette-section--queue",
      div(className := "palette-section-title", child.text <-- I18n.t("palette.section.shapes")),
      div(
        className   := "palette-grid",
        children <-- queueEntriesSignal
      )
    )

  private val queueEntriesSignal: Signal[List[Element]] =
    EditorState.irregularState.signal.distinct
      .combineWith(EditorState.toolState.signal.map(_.selectedPolygon).distinct)
      .map: (irregular, regularSelectedSidesOpt) =>

        val entries                 = irregular.recentIrregularPolygons
        val irregularSelectedIdxOpt = irregular.selectedIndex
        val total                   = EditorConfig.paletteShapeQueueSize
        val filledSlots             = entries.zipWithIndex.toList.map { (angles, idx) =>

          val regular  = isRegularShape(angles)
          val selected =
            if regular then regularSelectedSidesOpt.contains(angles.size)
            else irregularSelectedIdxOpt.contains(idx)
          queueSlot(angles, idx, isSelected = selected)
        }
        val emptySlots = (entries.size until total).toList.map(_ => emptySlot())
        filledSlots ++ emptySlots

  private def emptySlot(): Element =
    div(
      className := "polygon-btn palette-queue-slot palette-queue-slot--empty",
      title <-- I18n.t("palette.queue.empty.title"),
      svg.svg(
        viewBoxCoords(Point(40, 40)),
        svg.rect(
          rectCoords(LineSegment(Point(8, 8), Point(24, 24))),
          svg.fill            := "none",
          svg.stroke          := "currentColor",
          svg.strokeDashArray := "2,2"
        )
      )
    )

  private def queueSlot(
      angles: Vector[AngleDegree],
      index: Int,
      isSelected: Boolean
  ): Element =
    val regular               = isRegularShape(angles)
    val baseClasses           =
      polygonButtonClasses(
        "polygon-btn palette-queue-slot",
        selected = isSelected,
        processing = false
      )
    val tipText               =
      if regular then I18n.tNow("palette.tooltip.regularRecentFmt", angles.size.toString)
      else I18n.tNow("palette.tooltip.irregularFmt", angles.size.toString)
    val labelText             =
      if regular then angles.size.toString else irregularPolygonLabel(Some(angles))
    val tapSelect: () => Unit =
      if regular then
        () => {
          // selectPolygon clears irregular-state selection internally; just delegate.
          selectPolygon(angles.size)
          retractSheet()
        }
      else
        () => {
          selectIrregularInPalette(index)
          initializeWithIrregularIfEmpty()
          retractSheet()
        }
    button(
      className := baseClasses,
      tpe       := "button",
      title     := tipText,
      disabled <-- EditorState.uiState.signal.map(_.isProcessing).distinct,
      inContext { thisBtn =>

        gate(thisBtn.events(onClick)) --> { _ =>

          tapSelect()
        }
      },
      PaletteDragGesture.modifiers(() => DragShape(angles, tapSelect)),
      // Adjust-attaching-edge corner is meaningful only for irregular shapes;
      // a regular n-gon is symmetric under both rotation and reflection.
      if regular then emptyNode
      else
        div(
          className := "corner-button",
          title <-- I18n.t("palette.adjustHead.title"),
          onClick.stopPropagation --> Observer.empty,
          // Stop the drag gesture from picking up a pointerdown on the corner — that's a separate
          // command (open the irregular-shape popup), not a place-shape drag.
          onPointerDown.stopPropagation --> Observer.empty,
          inContext { cornerDiv =>

            gate(cornerDiv.events(onClick)) --> { _ =>

              EditorState.irregularState.update(_.selectAt(index))
              EditorState.popupState.update(_.copy(showIrregularPolygonPopup = true))
            }
          },
          "⟲"
        )
      ,
      if regular then PolygonSvg.regularPreview(angles.size)
      else PolygonSvg.irregularPreview(angles),
      div(className := "polygon-label", labelText)
    )

  // ---------- Section: Add Shape (parametric factories) ----------

  private def addShapeSection(): Element =
    div(
      className := "palette-section palette-section--add-shape",
      div(className := "palette-section-title", child.text <-- I18n.t("palette.section.addShape")),
      regularPolygonSelector(),
      rhombusSelector()
    )

  private def regularPolygonSelector(): Element =
    val customSides = Var(15)
    val inputValue  = Var("15")

    def updateSides(sides: Int): Unit =
      customSides.set(sides)
      inputValue.set(sides.toString)

    val syncInputToSource                               = customSides.signal.changes.map(_.toString) --> inputValue
    val displaySides                                    = inputValue.signal.map(validateSides)
    def applyCustomSelection(validatedSides: Int): Unit =
      updateSides(validatedSides)
      EditorState.toolState.update(_.copy(selectedPolygon = None))
      EditorState.irregularState.update(
        _.withShape(RegularPolygon(validatedSides).angles, selectIt = true)
      )
      initializeWithIrregularIfEmpty()
      retractSheet()
    div(
      syncInputToSource,
      // Like rhombus: clicking spins a regular n-gon into the queue (deduped) and selects it.
      // The button itself never highlights as "selected" — the queue entry is the source of truth.
      className <-- polygonButtonClass("polygon-btn regular-creator", Val(false)),
      title <-- I18n.t("palette.regular.title"),
      inContext { thisDiv =>

        gate(thisDiv.events(onClick))
          .withCurrentValueOf(displaySides)
          .map { case (_, validatedSides) =>
            validatedSides
          } --> { validatedSides =>

          applyCustomSelection(validatedSides)
        }
      },
      PaletteDragGesture.modifiers { () =>

        val validated = validateSides(inputValue.now())
        DragShape(RegularPolygon(validated).angles, () => applyCustomSelection(validated))
      },
      div(
        className   := "regular-preview",
        child <-- displaySides.map(sides => PolygonSvg.regularPreview(sides))
      ),
      div(className := "polygon-label", child.text <-- I18n.t("palette.regular.label")),
      div(
        className   := "regular-sides-wrap",
        input(
          tpe       := "number",
          className := "polygon-label-input regular-sides-input",
          minAttr   := "3",
          maxAttr   := "100",
          controlled(
            value <-- inputValue,
            onInput.mapToValue --> inputValue
          ),
          onBlur.compose(_.withCurrentValueOf(inputValue.signal)) --> { case (_, value) =>
            updateSides(validateSides(value))
          },
          onKeyPress.filter(_.key == "Enter").preventDefault
            .compose(_.withCurrentValueOf(inputValue.signal)) --> { case (_, value) =>
            updateSides(validateSides(value))
          },
          onClick.stopPropagation --> {},
          // Editing the number input must not be interpreted as the start of a drag gesture on the
          // surrounding palette button.
          onPointerDown.stopPropagation --> Observer.empty,
          disabled <-- EditorState.uiState.signal.map(_.isProcessing).distinct
        )
      )
    )

  private def rhombusSelector(): Element =
    val angleVar   = Var(60)
    val inputValue = Var("60")

    def updateAngle(α: Int): Unit =
      angleVar.set(α)
      inputValue.set(α.toString)

    val syncInputToSource = angleVar.signal.changes.map(_.toString) --> inputValue
    val displayAngle      = inputValue.signal.map(validateRhombusAngle)

    def applyRhombusSelection(α: Int): Unit =
      updateAngle(α)
      val angles = rhombusAngles(α)
      EditorState.irregularState.update(_.withShape(angles, selectIt = true))
      EditorState.toolState.update(_.copy(selectedPolygon = None))
      initializeWithIrregularIfEmpty()
      retractSheet()

    div(
      syncInputToSource,
      className <-- polygonButtonClass("polygon-btn rhombus-creator", Val(false)),
      title <-- I18n.t("palette.rhombus.title"),
      inContext { thisDiv =>

        gate(thisDiv.events(onClick))
          .withCurrentValueOf(displayAngle)
          .map { case (_, α) =>
            α
          } --> { α =>

          applyRhombusSelection(α)
        }
      },
      PaletteDragGesture.modifiers { () =>

        val α = validateRhombusAngle(inputValue.now())
        DragShape(rhombusAngles(α), () => applyRhombusSelection(α))
      },
      div(
        className   := "rhombus-preview",
        child <-- displayAngle.map(α =>
          PolygonSvg.irregularPreview(rhombusAngles(α))
        )
      ),
      div(className := "polygon-label", child.text <-- I18n.t("palette.rhombus.label")),
      // Wrap input + degree suffix so the "°" sits flush against the number without shifting the
      // input's centered alignment. `<input type="number">` can't carry the unit inside its value
      // (non-numeric chars get stripped), so the unit is rendered as a separate static sibling.
      div(
        className   := "rhombus-angle-wrap",
        input(
          tpe          := "number",
          className    := "polygon-label-input rhombus-angle-input",
          minAttr      := "1",
          maxAttr      := "90",
          controlled(
            value <-- inputValue,
            onInput.mapToValue --> inputValue
          ),
          onBlur.compose(_.withCurrentValueOf(inputValue.signal)) --> { case (_, value) =>
            updateAngle(validateRhombusAngle(value))
          },
          onKeyPress.filter(_.key == "Enter").preventDefault
            .compose(_.withCurrentValueOf(inputValue.signal)) --> { case (_, value) =>
            updateAngle(validateRhombusAngle(value))
          },
          onClick.stopPropagation --> {},
          onPointerDown.stopPropagation --> Observer.empty,
          disabled <-- EditorState.uiState.signal.map(_.isProcessing).distinct
        ),
        span(className := "rhombus-angle-suffix", "°")
      )
    )

  // ---------- Selected-info footer (shape-aware actions) ----------

  private def selectedInfoFooter(): Element =
    div(
      className := "selected-info",
      child.maybe <--
        EditorState.toolState.signal.map(_.selectedPolygon).distinct
          .combineWith(EditorState.irregularState.signal.map(_.selectedShape).distinct)
          .map { (maybeSides, maybeIrregular) =>

            maybeIrregular match
              case Some(angles) => Some(irregularSelectedActions(angles))
              case None         => maybeSides.map(regularSelectedActions)
          }
    )

  private def regularSelectedActions(sides: Int): Element =
    // PolygonNameGenerator returns canonical English names ("triangle", "square", "pentagon", …);
    // not localized in v1 — the button labels still flip with locale via I18n.t / EditorState.localeState.
    val polygonName = PolygonNameGenerator.polygonName(sides)
    div(
      className := "selected-info-actions",
      button(
        className := "selected-info-action",
        child.text <-- EditorState.localeState.signal.map(_ =>
          I18n.tNow("palette.action.selectAllFmt", polygonName)
        ),
        onClick.preventDefault.map(_ => sides) --> { s =>

          AppState.selectPolygonsBySides(s)
        },
        disabled <-- EditorState.isTilingEmptySignal
      ),
      button(
        className := "selected-info-action",
        child.text <-- EditorState.localeState.signal.map(_ =>
          I18n.tNow("palette.action.fillAllFmt", polygonName)
        ),
        onClick.preventDefault.compose(stream =>
          stream
            .withCurrentValueOf(EditorState.colorState.signal.map(_.fillColor).distinct)
            .map { case (_, color) =>
              (sides, color)
            }
        ) --> { case (s, color) =>

          AppState.selectPolygonsBySides(s)
          EditorState.colorState.update(_.copy(tempColor = color))
          EditorState.colorState.update(_.copy(showColorPicker = true))
        },
        disabled <-- EditorState.isTilingEmptySignal
      )
    )

  private def irregularSelectedActions(angles: Vector[AngleDegree]): Element =
    div(
      className := "selected-info-actions",
      button(
        className := "selected-info-action",
        child.text <-- I18n.t("palette.action.selectAllShape"),
        onClick.preventDefault --> { _ =>

          AppState.selectPolygonsByShape(angles)
        },
        disabled <-- EditorState.isTilingEmptySignal
      ),
      button(
        className := "selected-info-action",
        child.text <-- I18n.t("palette.action.fillAllShape"),
        onClick.preventDefault.compose(stream =>
          stream
            .withCurrentValueOf(EditorState.colorState.signal.map(_.fillColor).distinct)
            .map { case (_, color) =>
              color
            }
        ) --> { color =>

          AppState.selectPolygonsByShape(angles)
          EditorState.colorState.update(_.copy(tempColor = color))
          EditorState.colorState.update(_.copy(showColorPicker = true))
        },
        disabled <-- EditorState.isTilingEmptySignal
      )
    )

  // ---------- Big preview used by the IrregularPolygonPopup ----------

  private[components] def bigIrregularWithHead(angles: Vector[AngleDegree]): Element =
    val size = 220
    val pad  = 30.0
    PolygonSvg.irregularBigWithHead(angles, size, pad)
