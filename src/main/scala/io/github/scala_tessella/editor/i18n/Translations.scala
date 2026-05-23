package io.github.scala_tessella.editor.i18n

/** UI string catalogs, keyed by stable identifiers in dotted form (`menu.file.new`, `popup.about.title`, …).
  * English is the source-of-truth; missing keys in any other locale fall back to English (and finally to a
  * visible `!key!` marker so untranslated strings surface in QA).
  *
  * Conventions:
  *   - keys are lowercase, dot-separated, scoped by surface (menu / popup / palette / status …).
  *   - format strings use `{0}`, `{1}` placeholders resolved by `I18n.t(key, args*)`.
  *   - long-form prose with rich inline content (Guide body, About paragraphs) uses templated strings with
  *     `{tokenName}` placeholders, resolved by `I18n.tFragments(key, tokens)` at the call site. Tokens map
  *     each name to a Laminar element factory (icon, kbd, em, link, …).
  */
object Translations:

  // ----- English (source) ----------------------------------------------------

  val en: Map[String, String] = Map(
    // App shell
    "ui.language.toggleTitle" -> "Switch language",
    "ui.theme.toLight"        -> "Switch to light mode",
    "ui.theme.toDark"         -> "Switch to dark mode",
    "ui.menu.toggle"          -> "Toggle navigation menu",

    // Top menu
    "menu.file"                  -> "File",
    "menu.file.new"              -> "New",
    "menu.file.newFromTemplate"  -> "New from Template…",
    "menu.file.recent"           -> "Recent…",
    "menu.file.loadSvg"          -> "Load SVG…",
    "menu.file.saveSvg"          -> "Save SVG",
    "menu.file.saveSvgAs"        -> "Save SVG as…",
    "menu.file.exportDot"        -> "Export to DOT…",
    "menu.file.printPdf"         -> "Print to PDF…",
    "menu.file.settings"         -> "Settings…",
    "menu.edit"                  -> "Edit",
    "menu.edit.undo"             -> "↶ Undo",
    "menu.edit.redo"             -> "↷ Redo",
    "menu.edit.clearTiling"      -> "Clear Tiling",
    "menu.edit.doubleToInfinite" -> "Double (to infinite)",
    "menu.edit.mirror"           -> "Mirror",
    "menu.edit.fan"              -> "Fan",
    "menu.edit.measurement"      -> "Measurement",
    "menu.edit.selectAll"        -> "Select All",
    "menu.edit.deselectAll"      -> "Deselect All",
    "menu.edit.fillColor"        -> "Fill Color…",
    "menu.view"                  -> "View",
    "menu.view.showLabels"       -> "Show Node Labels",
    "menu.view.hideLabels"       -> "Hide Node Labels",
    "menu.view.showUniformity"   -> "Show Uniformity",
    "menu.view.hideUniformity"   -> "Hide Uniformity",
    "menu.view.showRotation"     -> "Show Rotational Symmetry",
    "menu.view.hideRotation"     -> "Hide Rotational Symmetry",
    "menu.view.showReflection"   -> "Show Reflectional Symmetry",
    "menu.view.hideReflection"   -> "Hide Reflectional Symmetry",
    "menu.view.showTilingInfo"   -> "Show Tiling Info",
    "menu.view.hideTilingInfo"   -> "Hide Tiling Info",
    "menu.view.fitToCanvas"      -> "Fit to Canvas",
    "menu.view.resetView"        -> "Reset View",
    "menu.view.zoomIn"           -> "Zoom In",
    "menu.view.zoomOut"          -> "Zoom Out",
    "menu.view.rotateLeft"       -> "Rotate Left",
    "menu.view.rotateRight"      -> "Rotate Right",
    "menu.help"                  -> "Help",
    "menu.help.guide"            -> "Guide…",
    "menu.help.shortcuts"        -> "Keyboard Shortcuts…",
    "menu.help.about"            -> "About…",

    // Tool strip
    "tool.addPolygon"                  -> "Add",
    "tool.addPolygon.outside"          -> "outside",
    "tool.addPolygon.inside"           -> "inside",
    "tool.addPolygon.activeFmt"        -> "Add ({0}) ▾",
    "tool.eraser"                      -> "Eraser",
    "tool.eraser.title"                -> "Activate deletion mode to delete polygons",
    "tool.colorPicker"                 -> "Color",
    "tool.colorPicker.title"           -> "Pick a color from an existing polygon",
    "tool.shapeColor"                  -> "Shape+Color",
    "tool.shapeColor.title"            -> "Pick the shape and color from an existing polygon",
    "tool.selectByColor"               -> "SelByCol",
    "tool.selectByColor.title"         -> "Select all polygons with the same color",
    "tool.measurement"                 -> "Measure",
    "tool.measurement.title"           -> "Measure distances and angles",
    "tool.fill.title"                  -> "Fill color — click to change",
    "tool.labels.on"                   -> "Labels: ON",
    "tool.labels.off"                  -> "Labels: OFF",
    "tool.labels.show"                 -> "Click to show the node labels",
    "tool.labels.hide"                 -> "Click to hide the node labels",
    "tool.info.show"                   -> "Show tiling info panel",
    "tool.info.hide"                   -> "Hide tiling info panel",
    "tool.addPolygon.tooltip.active"   -> "Click to switch between adding outside / inside",
    "tool.addPolygon.tooltip.inactive" -> "Activate Add Polygon mode (click again to cycle outside/inside)",

    // Polygon palette (CSS `text-transform: uppercase` capitalizes section titles)
    "palette.section.shapes"           -> "Recent shapes",
    "palette.section.addShape"         -> "Add shape",
    "palette.handle.title"             -> "Tap to expand or collapse the palette",
    "palette.handle.label"             -> "Shapes",
    "palette.queue.empty.title"        -> "Empty palette slot — add a shape with the factories below",
    "palette.irregular.label"          -> "Irregular",
    "palette.regular.label"            -> "Regular polygon",
    "palette.regular.title"            -> "Regular polygon by sides count (3–100)",
    "palette.rhombus.label"            -> "Rhombus",
    "palette.rhombus.title"            -> "Rhombus by acute angle (1°–90°)",
    "palette.adjustHead.title"         -> "Adjust attaching edge",
    "palette.tooltip.polygonFmt"       -> "{0}-sided polygon ({1})",
    "palette.tooltip.regularRecentFmt" -> "Regular {0}-gon (in palette)",
    "palette.tooltip.irregularFmt"     -> "Irregular polygon — {0} sides",
    "palette.action.selectAllFmt"      -> "Select all {0}s",
    "palette.action.fillAllFmt"        -> "Fill all {0}s with…",
    "palette.action.selectAllShape"    -> "Select all of this shape",
    "palette.action.fillAllShape"      -> "Fill all of this shape with…",

    // Empty-state card
    "emptyState.title"        -> "Start a tessellation",
    "emptyState.hint"         -> "Pick / drag a shape from the Shapes palette",
    "emptyState.divider"      -> "or",
    "emptyState.openTemplate" -> "Open template…",
    "emptyState.loadSvg"      -> "Load SVG…",

    // First-run overlay
    "firstRun.title"             -> "Welcome to Tessella",
    "firstRun.subtitle"          ->
      "A simple polygon tessellation editor. Pick a starting point — you can change your mind anytime.",
    "firstRun.blank.headline"    -> "Blank canvas",
    "firstRun.blank.body"        -> "Start from nothing and build up shape by shape.",
    "firstRun.template.headline" -> "From a template",
    "firstRun.template.body"     -> "Browse regular, semi-regular, and aperiodic tilings.",
    "firstRun.openSvg.headline"  -> "Open SVG",
    "firstRun.openSvg.body"      -> "Load a tessellation you've previously saved.",

    // Unsaved-changes confirm
    "unsaved.title"   -> "Unsaved changes",
    "unsaved.body"    -> "Your tessellation has changes that haven't been saved. What would you like to do?",
    "unsaved.cancel"  -> "Cancel",
    "unsaved.discard" -> "Discard",
    "unsaved.save"    -> "Save",

    // Online update banner
    "update.available.message"          -> "A newer version is available: ",
    "update.available.reload"           -> "Reload",
    "update.available.dismissAriaLabel" -> "Dismiss update notification",

    // Common buttons
    "common.cancel" -> "Cancel",
    "common.ok"     -> "OK",
    "common.print"  -> "Print",
    "common.close"  -> "Close",

    // Status row
    "status.idle"         -> "Ready",
    "status.processing"   -> "Processing…",
    "status.untitled"     -> "untitled",
    "status.distanceFmt"  -> "Distance: {0} units",
    "status.angle.radFmt" -> "Angle: {0} rad",
    "status.angle.degFmt" -> "Angle: {0}°",
    "status.angle.toggle" -> "Click to toggle radians/degrees",

    // Mode badge
    "modeBadge.label"         -> "Mode:",
    "modeBadge.reset.title"   -> "Click to return to Add Polygon mode",
    "modeBadge.add.outside"   -> "Add Polygon (outside)",
    "modeBadge.add.inside"    -> "Add Polygon (inside)",
    "modeBadge.eraser"        -> "Eraser",
    "modeBadge.colorPicker"   -> "Color Picker",
    "modeBadge.shapeColor"    -> "Shape & Color Picker",
    "modeBadge.selectByColor" -> "Select by Color",
    "modeBadge.measurement"   -> "Measurement",
    "modeBadge.fan"           -> "Fan",

    // Tiling info panel
    "info.title"               -> "Tiling info",
    "info.close"               -> "Close info panel",
    "info.vertices"            -> "Vertices",
    "info.faces"               -> "Faces",
    "info.edges"               -> "Edges",
    "info.vertexTypes"         -> "Vertex types",
    "info.vertexConfigs"       -> "Vertex configurations",
    "info.vertexConfigs.empty" -> "No full vertices yet",

    // Popups (titles only; long body content remains English)
    "popup.about.title"                   -> "Tessella",
    "popup.about.tagline"                 -> "Simple polygon tessellation editor",
    "popup.about.versionFmt"              -> "Editor v{0}",
    "popup.about.imageAlt"                -> "Tessella Logo",
    "popup.about.bodyAriaLabel"           -> "About content",
    "popup.about.body.intro"              ->
      "Interactively create, view, and manipulate tessellations of the plane made of simple (regular and irregular) polygons.",
    "popup.about.body.project"            ->
      "The editor is part of the {projectName} project. For more information, and to contribute, please visit the {ghLink}.",
    "popup.about.body.builtWith"          -> "Built with Scala.js and Laminar.",
    "popup.about.body.license"            ->
      "© 2026 Mario Callisto. Released under the {licenseLink}. {sourceLink}.",
    "popup.about.link.github"             -> "GitHub organization",
    "popup.about.link.license"            -> "Apache License 2.0",
    "popup.about.link.source"             -> "View source on GitHub",
    "popup.guide.title"                   -> "Guide",
    "popup.guide.bodyAriaLabel"           -> "Guide content",
    // Guide popup — section headings
    "popup.guide.section.creating"        -> "Creating a tiling",
    "popup.guide.section.addOutside"      -> "Adding regular polygons (outside)",
    "popup.guide.section.addInside"       -> "Adding regular polygons (inside)",
    "popup.guide.section.addIrregular"    -> "Adding irregular polygons",
    "popup.guide.section.fanning"         -> "Fanning",
    "popup.guide.section.selecting"       -> "Selecting",
    "popup.guide.section.deleting"        -> "Deleting",
    "popup.guide.section.doubleMirror"    -> "Doubling and mirroring",
    "popup.guide.section.styling"         -> "Styling",
    "popup.guide.section.visual"          -> "Visual options",
    "popup.guide.section.measurement"     -> "Measurement",
    "popup.guide.section.navigating"      -> "Navigating the canvas",
    "popup.guide.section.savingLoading"   -> "Saving and loading",
    "popup.guide.section.validation"      -> "Validation rules",
    // Guide popup — mode + tool labels (used as inline tokens in the templates below)
    "popup.guide.mode.outside"            -> "Add (outside)",
    "popup.guide.mode.inside"             -> "Add (inside)",
    "popup.guide.tool.selectAll"          -> "Select all",
    "popup.guide.tool.deselectAll"        -> "Deselect all",
    "popup.guide.tool.selectButton"       -> "Select",
    "popup.guide.tool.selectByColor"      -> "Select by color",
    "popup.guide.tool.shapeAndColor"      -> "Shape and color picker",
    "popup.guide.tool.colorPicker"        -> "Color picker",
    "popup.guide.tool.fan"                -> "Fan",
    "popup.guide.tool.eraser"             -> "Eraser",
    "popup.guide.tool.measurement"        -> "Measurement",
    "popup.guide.tool.fit"                -> "Fit",
    // Guide popup — body items (use {tokenName} placeholders resolved in GuidePopup.tokens)
    "popup.guide.creating.modes"          ->
      "The editor has two adding modes. {addOutside} grows the tiling by attaching polygons to its boundary; {addInside} fills an existing polygon with smaller regular ones.",
    "popup.guide.creating.start"          ->
      "To start a new tiling, pick a polygon shape from the palette. It is placed on the canvas.",
    "popup.guide.addOutside.howTo"        ->
      "Pick a shape from the palette and drag it onto the canvas, or click any boundary edge to attach the shape there.",
    "popup.guide.addInside.toggle"        ->
      "Click the {iconPlus} icon to enter {addInside} mode.",
    "popup.guide.addInside.howTo"         ->
      "Drag a shape from the palette onto an existing polygon, or click a polygon to highlight its edges, then click an edge to attach the chosen shape.",
    "popup.guide.addIrregular.pick"       ->
      "Pick the irregular shape from the palette, just like a regular one.",
    "popup.guide.addIrregular.shift"      ->
      "Click the ↺ button on the top-right corner of the palette item to shift the attaching edge.",
    "popup.guide.addIrregular.copy"       ->
      "Use the {iconShapeAndColor} {shapeAndColor} tool to copy the shape (and fill colour) of an existing irregular polygon.",
    "popup.guide.fanning.tool"            ->
      "Use the {iconFan} {fan} tool to add rotated copies of the tiling around a boundary vertex.",
    "popup.guide.fanning.howTo"           ->
      "Click a polygon to highlight its boundary vertices, then click one. As many copies as fit are added, up to a full circle.",
    "popup.guide.selecting.click"         ->
      "In {addOutside} mode, click any polygon to select it.",
    "popup.guide.selecting.all"           ->
      "Use the {iconSelectAll} {selectAll} button to select every polygon.",
    "popup.guide.selecting.deselectAll"   ->
      "Use the {iconDeselectAll} {deselectAll} button — or press {kbdEsc} — to clear the selection.",
    "popup.guide.selecting.sameShape"     ->
      "Use the {selectButton} button at the bottom of the palette to select all regular polygons of the same shape.",
    "popup.guide.selecting.byColor"       ->
      "Use the {iconSelectByColor} {selectByColor} tool to select all polygons sharing the same fill colour.",
    "popup.guide.deleting.tool"           ->
      "Use the {iconEraser} {eraser} tool to remove a vertex, an edge, or a whole polygon.",
    "popup.guide.deleting.howTo"          ->
      "Click a polygon to highlight its vertices, mid-edge points, and centre, then click the item you want to remove.",
    "popup.guide.doubleMirror.double"     ->
      "Use {menuDouble} or press {kbdD} to double the entire tiling. This works only when the boundary is a parallelogon (a polygon whose opposite sides are equal and parallel), so the doubled copies could tile the infinite plane.",
    "popup.guide.doubleMirror.mirror"     ->
      "Use {menuMirror} to switch to a mirror image of the tiling.",
    "popup.guide.styling.fill"            ->
      "In {addOutside} mode, select one or more polygons, then click the colour button or use {menuFillColor} to open the colour picker.",
    "popup.guide.styling.applyTo"         ->
      "The chosen colour applies to the current selection and to any polygon you add next.",
    "popup.guide.styling.copy"            ->
      "Use the {iconColorPicker} {colorPicker} tool to copy the fill colour from an existing polygon.",
    "popup.guide.styling.copyShape"       ->
      "Use the {iconShapeAndColor} {shapeAndColor} tool to copy both the shape and the fill colour.",
    "popup.guide.visual.labels"           ->
      "{menuShowLabels} shows the node label (unique number) of each vertex of the underlying graph.",
    "popup.guide.visual.uniformity"       ->
      "{menuShowUniformity} marks nodes that share the same adjacent pattern.",
    "popup.guide.visual.rotation"         ->
      "{menuShowRotation} shows the rotation axes that divide the tiling into identical rotated parts.",
    "popup.guide.visual.reflection"       ->
      "{menuShowReflection} shows the reflection axes that divide the tiling into mirrored halves.",
    "popup.guide.measurement.unitLength"  ->
      "By design, every polygon side has unit length 1 (or an integer multiple of it).",
    "popup.guide.measurement.tool"        ->
      "Use the {iconRuler} {measurement} tool to measure unit distances and angles between key points (vertex, mid-edge, centre).",
    "popup.guide.measurement.startEnd"    ->
      "Click a polygon to highlight its key points; click one to set the (green) start, and click another to set the (red) end. The unit distance is displayed above the top-right corner of the canvas.",
    "popup.guide.measurement.angle"       ->
      "Click another key point to choose a new (red) end. The angle between the previous and current end points is shown as an arc, with its measure in radians.",
    "popup.guide.navigating.pan"          ->
      "Pan: click and drag the canvas background (or drag with one finger on touch devices).",
    "popup.guide.navigating.zoom"         ->
      "Zoom: scroll the mouse wheel, pinch on a touch device, or press {kbdPlus} / {kbdMinus}.",
    "popup.guide.navigating.rotate"       ->
      "Rotate: press {kbdE} (left) or {kbdR} (right).",
    "popup.guide.navigating.fit"          ->
      "Fit: use the {iconMaximize} {fit} button or press {kbdF} to fit the entire tiling in view.",
    "popup.guide.navigating.reset"        ->
      "Reset: {menuResetView} returns to the default position, zoom, and rotation.",
    "popup.guide.savingLoading.svg"       ->
      "Use the {menuFile} menu to save your work as an SVG ({menuSaveSvg} or {menuSaveSvgAs}) or to load a previously saved SVG tiling.",
    "popup.guide.savingLoading.dot"       ->
      "The tiling's topological structure can also be exported as a DOT graph, in the Graphviz .gv format.",
    "popup.guide.validation.add"          ->
      "An added polygon must not cross the boundary or another polygon, so the result stays a proper edge-to-edge finite tessellation (every edge is shared by exactly one or two polygons, with no overlaps).",
    "popup.guide.validation.remove"       ->
      "When you remove a polygon, the editor checks that the remaining tiling still has a single, non-self-intersecting boundary.",
    "popup.shortcuts.title"               -> "Keyboard Shortcuts",
    "popup.shortcuts.action"              -> "Action",
    "popup.shortcuts.shortcut"            -> "Shortcut",
    "popup.shortcuts.undo"                -> "Undo",
    "popup.shortcuts.redo"                -> "Redo",
    "popup.shortcuts.save"                -> "Save",
    "popup.shortcuts.deselectAll"         -> "Deselect All",
    "popup.shortcuts.zoomIn"              -> "Zoom In",
    "popup.shortcuts.zoomOut"             -> "Zoom Out",
    "popup.shortcuts.rotateLeft"          -> "Rotate Left",
    "popup.shortcuts.rotateRight"         -> "Rotate Right",
    "popup.shortcuts.fitToCanvas"         -> "Fit to Canvas",
    "popup.irregular.title"               -> "Adjust attaching edge",
    "popup.irregular.empty"               -> "No irregular polygon",
    "popup.irregular.shiftLeft"           -> "Move head left",
    "popup.irregular.shiftRight"          -> "Move head right",
    "popup.irregular.flip"                -> "Flip ⧎",
    "popup.colorPicker.title"             -> "Select Color",
    "popup.colorPicker.fillSelectedTitle" -> "Pick color and fill selected",
    "popup.templates.title"               -> "New from template",
    "popup.templates.regular"             -> "Regular",
    "popup.templates.semiRegular"         -> "Semi-regular",
    "popup.templates.aperiodic"           -> "Aperiodic",
    "popup.recent.title"                  -> "Recent files",
    "popup.recent.empty"                  -> "No recent files yet. Templates loaded from the gallery will appear here.",
    "popup.recent.footnote"               ->
      "References only — files are not embedded. If a template moves or 404s, the row will fail to load.",
    "popup.recent.justNow"                -> "just now",
    "popup.recent.minAgoFmt"              -> "{0} min ago",
    "popup.recent.hoursAgoFmt"            -> "{0} hours ago",
    "popup.recent.yesterday"              -> "yesterday",
    "popup.recent.daysAgoFmt"             -> "{0} days ago",
    "popup.recent.monthsAgoFmt"           -> "{0} months ago",
    "popup.settings.title"                -> "Settings",
    "popup.settings.fillColor"            -> "Default start fill color",
    "popup.settings.boundaryColor"        -> "Boundary edge color",
    "popup.settings.boundaryWidth"        -> "Boundary edge width",
    "popup.settings.polygonEdgeColor"     -> "Edge color",
    "popup.settings.polygonEdgeWidth"     -> "Edge width",
    "popup.settings.resetDefaults"        -> "Reset to defaults",
    "popup.settings.reduceMotion"         -> "Reduce motion",
    "popup.settings.reduceMotion.auto"    -> "Auto",
    "popup.settings.reduceMotion.on"      -> "On",
    "popup.settings.reduceMotion.off"     -> "Off",
    "popup.settings.language"             -> "Language",
    "popup.settings.apply"                -> "Apply",
    "popup.settings.reset"                -> "Reset",
    "popup.print.title"                   -> "Print to PDF",
    "popup.print.paper"                   -> "Paper",
    "popup.print.paper.a4"                -> "A4",
    "popup.print.paper.letter"            -> "Letter",
    "popup.print.orientation"             -> "Orientation",
    "popup.print.orientation.portrait"    -> "Portrait",
    "popup.print.orientation.landscape"   -> "Landscape",
    "popup.print.fit"                     -> "Fit",
    "popup.print.fit.toPage"              -> "Fit canvas to page",

    // Loading / async
    "loading.default"      -> "Processing tessellation…",
    "loading.importingSvg" -> "Importing SVG…",
    "loading.parsingSvg"   -> "Parsing SVG metadata…",
    "loading.validating"   -> "Validating tessellation…",

    // File operations
    "file.saveAs.prompt"      -> "Enter file name for the SVG:",
    "file.saveAs.defaultName" -> "tessellation.svg"
  )

  // ----- Spanish ------------------------------------------------------------

  val es: Map[String, String] = Map(
    // App shell
    "ui.language.toggleTitle" -> "Cambiar idioma",
    "ui.theme.toLight"        -> "Cambiar a modo claro",
    "ui.theme.toDark"         -> "Cambiar a modo oscuro",
    "ui.menu.toggle"          -> "Mostrar / ocultar el menú",

    // Top menu
    "menu.file"                  -> "Archivo",
    "menu.file.new"              -> "Nuevo",
    "menu.file.newFromTemplate"  -> "Nuevo desde plantilla…",
    "menu.file.recent"           -> "Reciente…",
    "menu.file.loadSvg"          -> "Cargar SVG…",
    "menu.file.saveSvg"          -> "Guardar SVG",
    "menu.file.saveSvgAs"        -> "Guardar SVG como…",
    "menu.file.exportDot"        -> "Exportar a DOT…",
    "menu.file.printPdf"         -> "Imprimir a PDF…",
    "menu.file.settings"         -> "Ajustes…",
    "menu.edit"                  -> "Editar",
    "menu.edit.undo"             -> "↶ Deshacer",
    "menu.edit.redo"             -> "↷ Rehacer",
    "menu.edit.clearTiling"      -> "Vaciar la teselación",
    "menu.edit.doubleToInfinite" -> "Duplicar (al infinito)",
    "menu.edit.mirror"           -> "Reflejar",
    "menu.edit.fan"              -> "Abanico",
    "menu.edit.measurement"      -> "Medir",
    "menu.edit.selectAll"        -> "Seleccionar todo",
    "menu.edit.deselectAll"      -> "Deseleccionar todo",
    "menu.edit.fillColor"        -> "Color de relleno…",
    "menu.view"                  -> "Ver",
    "menu.view.showLabels"       -> "Mostrar etiquetas de nodo",
    "menu.view.hideLabels"       -> "Ocultar etiquetas de nodo",
    "menu.view.showUniformity"   -> "Mostrar uniformidad",
    "menu.view.hideUniformity"   -> "Ocultar uniformidad",
    "menu.view.showRotation"     -> "Mostrar simetría rotacional",
    "menu.view.hideRotation"     -> "Ocultar simetría rotacional",
    "menu.view.showReflection"   -> "Mostrar simetría reflexiva",
    "menu.view.hideReflection"   -> "Ocultar simetría reflexiva",
    "menu.view.showTilingInfo"   -> "Mostrar información de la teselación",
    "menu.view.hideTilingInfo"   -> "Ocultar información de la teselación",
    "menu.view.fitToCanvas"      -> "Ajustar al lienzo",
    "menu.view.resetView"        -> "Restablecer vista",
    "menu.view.zoomIn"           -> "Acercar",
    "menu.view.zoomOut"          -> "Alejar",
    "menu.view.rotateLeft"       -> "Girar a la izquierda",
    "menu.view.rotateRight"      -> "Girar a la derecha",
    "menu.help"                  -> "Ayuda",
    "menu.help.guide"            -> "Guía…",
    "menu.help.shortcuts"        -> "Atajos de teclado…",
    "menu.help.about"            -> "Acerca de…",

    // Tool strip
    "tool.addPolygon"                  -> "Añadir",
    "tool.addPolygon.outside"          -> "exterior",
    "tool.addPolygon.inside"           -> "interior",
    "tool.addPolygon.activeFmt"        -> "Añadir ({0}) ▾",
    "tool.eraser"                      -> "Borrador",
    "tool.eraser.title"                -> "Activar el modo de borrado para eliminar polígonos",
    "tool.colorPicker"                 -> "Color",
    "tool.colorPicker.title"           -> "Tomar el color de un polígono existente",
    "tool.shapeColor"                  -> "Forma+color",
    "tool.shapeColor.title"            -> "Tomar la forma y el color de un polígono existente",
    "tool.selectByColor"               -> "PorColor",
    "tool.selectByColor.title"         -> "Seleccionar todos los polígonos del mismo color",
    "tool.measurement"                 -> "Medir",
    "tool.measurement.title"           -> "Medir distancias y ángulos",
    "tool.fill.title"                  -> "Color de relleno — pulsar para cambiar",
    "tool.labels.on"                   -> "Etiquetas: SÍ",
    "tool.labels.off"                  -> "Etiquetas: NO",
    "tool.labels.show"                 -> "Pulsar para mostrar las etiquetas de nodo",
    "tool.labels.hide"                 -> "Pulsar para ocultar las etiquetas de nodo",
    "tool.info.show"                   -> "Mostrar el panel de información",
    "tool.info.hide"                   -> "Ocultar el panel de información",
    "tool.addPolygon.tooltip.active"   -> "Pulsar para alternar entre añadir exterior / interior",
    "tool.addPolygon.tooltip.inactive" ->
      "Activar el modo Añadir polígono (pulsar de nuevo para alternar exterior/interior)",

    // Polygon palette (CSS `text-transform: uppercase` capitalizes section titles)
    "palette.section.shapes"           -> "Formas recientes",
    "palette.section.addShape"         -> "Añadir forma",
    "palette.handle.title"             -> "Pulsar para expandir o contraer la paleta",
    "palette.handle.label"             -> "Formas",
    "palette.queue.empty.title"        -> "Espacio vacío — añade una forma con las opciones de abajo",
    "palette.irregular.label"          -> "Irregular",
    "palette.regular.label"            -> "Polígono regular",
    "palette.regular.title"            -> "Polígono regular por número de lados (3–100)",
    "palette.rhombus.label"            -> "Rombo",
    "palette.rhombus.title"            -> "Rombo por ángulo agudo (1°–90°)",
    "palette.adjustHead.title"         -> "Ajustar arista de unión",
    "palette.tooltip.polygonFmt"       -> "Polígono de {0} lados ({1})",
    "palette.tooltip.regularRecentFmt" -> "{0}-gono regular (en la paleta)",
    "palette.tooltip.irregularFmt"     -> "Polígono irregular — {0} lados",
    "palette.action.selectAllFmt"      -> "Seleccionar todos los {0}",
    "palette.action.fillAllFmt"        -> "Rellenar todos los {0} con…",
    "palette.action.selectAllShape"    -> "Seleccionar todos los de esta forma",
    "palette.action.fillAllShape"      -> "Rellenar todos los de esta forma con…",

    // Empty-state card
    "emptyState.title"        -> "Empezar una teselación",
    "emptyState.hint"         -> "Elige / arrastra una forma de la paleta de Formas",
    "emptyState.divider"      -> "o",
    "emptyState.openTemplate" -> "Abrir plantilla…",
    "emptyState.loadSvg"      -> "Cargar SVG…",

    // First-run overlay
    "firstRun.title"             -> "Bienvenido a Tessella",
    "firstRun.subtitle"          ->
      "Un editor de teselación de polígonos simples. Elige por dónde empezar — siempre puedes cambiar de idea.",
    "firstRun.blank.headline"    -> "Lienzo en blanco",
    "firstRun.blank.body"        -> "Empieza desde cero y construye forma a forma.",
    "firstRun.template.headline" -> "Desde una plantilla",
    "firstRun.template.body"     -> "Explora teselaciones regulares, semi-regulares y aperiódicas.",
    "firstRun.openSvg.headline"  -> "Abrir SVG",
    "firstRun.openSvg.body"      -> "Carga una teselación que hayas guardado antes.",

    // Unsaved-changes confirm
    "unsaved.title"   -> "Cambios sin guardar",
    "unsaved.body"    -> "Tu teselación tiene cambios que no se han guardado. ¿Qué deseas hacer?",
    "unsaved.cancel"  -> "Cancelar",
    "unsaved.discard" -> "Descartar",
    "unsaved.save"    -> "Guardar",

    // Online update banner
    "update.available.message"          -> "Hay una versión más reciente disponible: ",
    "update.available.reload"           -> "Recargar",
    "update.available.dismissAriaLabel" -> "Descartar la notificación de actualización",

    // Common buttons
    "common.cancel" -> "Cancelar",
    "common.ok"     -> "Aceptar",
    "common.print"  -> "Imprimir",
    "common.close"  -> "Cerrar",

    // Status row
    "status.idle"         -> "Listo",
    "status.processing"   -> "Procesando…",
    "status.untitled"     -> "sin título",
    "status.distanceFmt"  -> "Distancia: {0} unidades",
    "status.angle.radFmt" -> "Ángulo: {0} rad",
    "status.angle.degFmt" -> "Ángulo: {0}°",
    "status.angle.toggle" -> "Pulsar para alternar entre radianes y grados",

    // Mode badge
    "modeBadge.label"         -> "Modo:",
    "modeBadge.reset.title"   -> "Pulsar para volver al modo Añadir polígono",
    "modeBadge.add.outside"   -> "Añadir polígono (exterior)",
    "modeBadge.add.inside"    -> "Añadir polígono (interior)",
    "modeBadge.eraser"        -> "Borrador",
    "modeBadge.colorPicker"   -> "Selector de color",
    "modeBadge.shapeColor"    -> "Selector de forma y color",
    "modeBadge.selectByColor" -> "Seleccionar por color",
    "modeBadge.measurement"   -> "Medición",
    "modeBadge.fan"           -> "Abanico",

    // Tiling info panel
    "info.title"               -> "Información de la teselación",
    "info.close"               -> "Cerrar el panel de información",
    "info.vertices"            -> "Vértices",
    "info.faces"               -> "Caras",
    "info.edges"               -> "Aristas",
    "info.vertexTypes"         -> "Tipos de vértice",
    "info.vertexConfigs"       -> "Configuraciones de vértice",
    "info.vertexConfigs.empty" -> "Aún no hay vértices completos",

    // Popups (titles only; long body content remains English)
    "popup.about.title"                   -> "Tessella",
    "popup.about.tagline"                 -> "Editor de teselación de polígonos simples",
    "popup.about.versionFmt"              -> "Editor v{0}",
    "popup.about.imageAlt"                -> "Logotipo de Tessella",
    "popup.about.bodyAriaLabel"           -> "Contenido «Acerca de»",
    "popup.about.body.intro"              ->
      "Crea, visualiza y manipula interactivamente teselaciones del plano hechas con polígonos simples (regulares e irregulares).",
    "popup.about.body.project"            ->
      "El editor forma parte del proyecto {projectName}. Para más información y para contribuir, visita la {ghLink}.",
    "popup.about.body.builtWith"          -> "Hecho con Scala.js y Laminar.",
    "popup.about.body.license"            ->
      "© 2026 Mario Callisto. Publicado bajo la {licenseLink}. {sourceLink}.",
    "popup.about.link.github"             -> "organización de GitHub",
    "popup.about.link.license"            -> "Licencia Apache 2.0",
    "popup.about.link.source"             -> "Ver el código en GitHub",
    "popup.guide.title"                   -> "Guía",
    "popup.guide.bodyAriaLabel"           -> "Contenido de la guía",
    // Guide popup — section headings
    "popup.guide.section.creating"        -> "Crear una teselación",
    "popup.guide.section.addOutside"      -> "Añadir polígonos regulares (exterior)",
    "popup.guide.section.addInside"       -> "Añadir polígonos regulares (interior)",
    "popup.guide.section.addIrregular"    -> "Añadir polígonos irregulares",
    "popup.guide.section.fanning"         -> "Abanico",
    "popup.guide.section.selecting"       -> "Selección",
    "popup.guide.section.deleting"        -> "Borrado",
    "popup.guide.section.doubleMirror"    -> "Duplicar y reflejar",
    "popup.guide.section.styling"         -> "Estilo",
    "popup.guide.section.visual"          -> "Opciones visuales",
    "popup.guide.section.measurement"     -> "Medición",
    "popup.guide.section.navigating"      -> "Navegar por el lienzo",
    "popup.guide.section.savingLoading"   -> "Guardar y cargar",
    "popup.guide.section.validation"      -> "Reglas de validación",
    // Guide popup — mode + tool labels
    "popup.guide.mode.outside"            -> "Añadir (exterior)",
    "popup.guide.mode.inside"             -> "Añadir (interior)",
    "popup.guide.tool.selectAll"          -> "Seleccionar todo",
    "popup.guide.tool.deselectAll"        -> "Deseleccionar todo",
    "popup.guide.tool.selectButton"       -> "Seleccionar",
    "popup.guide.tool.selectByColor"      -> "Seleccionar por color",
    "popup.guide.tool.shapeAndColor"      -> "Selector de forma y color",
    "popup.guide.tool.colorPicker"        -> "Selector de color",
    "popup.guide.tool.fan"                -> "Abanico",
    "popup.guide.tool.eraser"             -> "Borrador",
    "popup.guide.tool.measurement"        -> "Medición",
    "popup.guide.tool.fit"                -> "Ajustar",
    // Guide popup — body items
    "popup.guide.creating.modes"          ->
      "El editor tiene dos modos para añadir. {addOutside} extiende la teselación adjuntando polígonos a su contorno; {addInside} rellena un polígono existente con polígonos regulares más pequeños.",
    "popup.guide.creating.start"          ->
      "Para empezar una nueva teselación, elige una forma poligonal de la paleta. Se coloca en el lienzo.",
    "popup.guide.addOutside.howTo"        ->
      "Elige una forma de la paleta y arrástrala al lienzo, o pulsa cualquier arista del contorno para adjuntar la forma allí.",
    "popup.guide.addInside.toggle"        ->
      "Pulsa el icono {iconPlus} para entrar en el modo {addInside}.",
    "popup.guide.addInside.howTo"         ->
      "Arrastra una forma de la paleta sobre un polígono existente, o pulsa un polígono para resaltar sus aristas y luego pulsa una arista para adjuntar la forma elegida.",
    "popup.guide.addIrregular.pick"       ->
      "Elige la forma irregular en la paleta, igual que una regular.",
    "popup.guide.addIrregular.shift"      ->
      "Pulsa el botón ↺ en la esquina superior derecha del elemento de la paleta para desplazar la arista de unión.",
    "popup.guide.addIrregular.copy"       ->
      "Usa la herramienta {iconShapeAndColor} {shapeAndColor} para copiar la forma (y el color de relleno) de un polígono irregular existente.",
    "popup.guide.fanning.tool"            ->
      "Usa la herramienta {iconFan} {fan} para añadir copias rotadas de la teselación alrededor de un vértice del contorno.",
    "popup.guide.fanning.howTo"           ->
      "Pulsa un polígono para resaltar sus vértices del contorno y luego pulsa uno. Se añaden tantas copias como quepan, hasta una circunferencia completa.",
    "popup.guide.selecting.click"         ->
      "En el modo {addOutside}, pulsa cualquier polígono para seleccionarlo.",
    "popup.guide.selecting.all"           ->
      "Usa el botón {iconSelectAll} {selectAll} para seleccionar todos los polígonos.",
    "popup.guide.selecting.deselectAll"   ->
      "Usa el botón {iconDeselectAll} {deselectAll} — o pulsa {kbdEsc} — para limpiar la selección.",
    "popup.guide.selecting.sameShape"     ->
      "Usa el botón {selectButton} en la parte inferior de la paleta para seleccionar todos los polígonos regulares con la misma forma.",
    "popup.guide.selecting.byColor"       ->
      "Usa la herramienta {iconSelectByColor} {selectByColor} para seleccionar todos los polígonos con el mismo color de relleno.",
    "popup.guide.deleting.tool"           ->
      "Usa la herramienta {iconEraser} {eraser} para borrar un vértice, una arista o un polígono entero.",
    "popup.guide.deleting.howTo"          ->
      "Pulsa un polígono para resaltar sus vértices, puntos medios de arista y centro, y luego pulsa el elemento que quieras borrar.",
    "popup.guide.doubleMirror.double"     ->
      "Usa {menuDouble} o pulsa {kbdD} para duplicar la teselación entera. Solo funciona cuando el contorno es un paralelogon (un polígono cuyos lados opuestos son iguales y paralelos), de modo que las copias duplicadas podrían cubrir el plano infinito.",
    "popup.guide.doubleMirror.mirror"     ->
      "Usa {menuMirror} para cambiar a una imagen reflejada de la teselación.",
    "popup.guide.styling.fill"            ->
      "En el modo {addOutside}, selecciona uno o más polígonos y luego pulsa el botón de color o usa {menuFillColor} para abrir el selector de color.",
    "popup.guide.styling.applyTo"         ->
      "El color elegido se aplica a la selección actual y a cualquier polígono que añadas a continuación.",
    "popup.guide.styling.copy"            ->
      "Usa la herramienta {iconColorPicker} {colorPicker} para copiar el color de relleno de un polígono existente.",
    "popup.guide.styling.copyShape"       ->
      "Usa la herramienta {iconShapeAndColor} {shapeAndColor} para copiar tanto la forma como el color de relleno.",
    "popup.guide.visual.labels"           ->
      "{menuShowLabels} muestra la etiqueta del nodo (número único) de cada vértice del grafo subyacente.",
    "popup.guide.visual.uniformity"       ->
      "{menuShowUniformity} marca los nodos que comparten el mismo patrón de vecindad.",
    "popup.guide.visual.rotation"         ->
      "{menuShowRotation} muestra los ejes de rotación que dividen la teselación en partes rotadas idénticas.",
    "popup.guide.visual.reflection"       ->
      "{menuShowReflection} muestra los ejes de reflexión que dividen la teselación en mitades reflejadas.",
    "popup.guide.measurement.unitLength"  ->
      "Por diseño, cada lado de polígono tiene longitud unidad 1 (o un múltiplo entero de ella).",
    "popup.guide.measurement.tool"        ->
      "Usa la herramienta {iconRuler} {measurement} para medir distancias unitarias y ángulos entre puntos clave (vértice, punto medio de arista, centro).",
    "popup.guide.measurement.startEnd"    ->
      "Pulsa un polígono para resaltar sus puntos clave; pulsa uno para fijar el inicio (verde) y pulsa otro para fijar el final (rojo). La distancia unitaria se muestra sobre la esquina superior derecha del lienzo.",
    "popup.guide.measurement.angle"       ->
      "Pulsa otro punto clave para elegir un nuevo final (rojo). El ángulo entre los puntos final anterior y actual se muestra como un arco, con su medida en radianes.",
    "popup.guide.navigating.pan"          ->
      "Mover: pulsa y arrastra el fondo del lienzo (o arrastra con un dedo en pantallas táctiles).",
    "popup.guide.navigating.zoom"         ->
      "Zoom: gira la rueda del ratón, pellizca en pantalla táctil o pulsa {kbdPlus} / {kbdMinus}.",
    "popup.guide.navigating.rotate"       ->
      "Girar: pulsa {kbdE} (izquierda) o {kbdR} (derecha).",
    "popup.guide.navigating.fit"          ->
      "Ajustar: usa el botón {iconMaximize} {fit} o pulsa {kbdF} para encajar toda la teselación en la vista.",
    "popup.guide.navigating.reset"        ->
      "Restablecer: {menuResetView} vuelve a la posición, zoom y rotación predeterminados.",
    "popup.guide.savingLoading.svg"       ->
      "Usa el menú {menuFile} para guardar tu trabajo como SVG ({menuSaveSvg} o {menuSaveSvgAs}) o para cargar una teselación SVG guardada anteriormente.",
    "popup.guide.savingLoading.dot"       ->
      "La estructura topológica de la teselación también puede exportarse como un grafo DOT en formato Graphviz .gv.",
    "popup.guide.validation.add"          ->
      "Un polígono añadido no puede cruzar el contorno ni otro polígono, de modo que el resultado siga siendo una teselación finita arista-con-arista válida (cada arista la comparten exactamente uno o dos polígonos, sin superposiciones).",
    "popup.guide.validation.remove"       ->
      "Cuando borras un polígono, el editor comprueba que la teselación restante sigue teniendo un único contorno sin auto-intersecciones.",
    "popup.shortcuts.title"               -> "Atajos de teclado",
    "popup.shortcuts.action"              -> "Acción",
    "popup.shortcuts.shortcut"            -> "Atajo",
    "popup.shortcuts.undo"                -> "Deshacer",
    "popup.shortcuts.redo"                -> "Rehacer",
    "popup.shortcuts.save"                -> "Guardar",
    "popup.shortcuts.deselectAll"         -> "Deseleccionar todo",
    "popup.shortcuts.zoomIn"              -> "Acercar",
    "popup.shortcuts.zoomOut"             -> "Alejar",
    "popup.shortcuts.rotateLeft"          -> "Girar a la izquierda",
    "popup.shortcuts.rotateRight"         -> "Girar a la derecha",
    "popup.shortcuts.fitToCanvas"         -> "Ajustar al lienzo",
    "popup.irregular.title"               -> "Ajustar arista de unión",
    "popup.irregular.empty"               -> "Sin polígono irregular",
    "popup.irregular.shiftLeft"           -> "Mover el inicio a la izquierda",
    "popup.irregular.shiftRight"          -> "Mover el inicio a la derecha",
    "popup.irregular.flip"                -> "Voltear ⧎",
    "popup.colorPicker.title"             -> "Elegir color",
    "popup.colorPicker.fillSelectedTitle" -> "Elegir color y rellenar la selección",
    "popup.templates.title"               -> "Nuevo desde plantilla",
    "popup.templates.regular"             -> "Regulares",
    "popup.templates.semiRegular"         -> "Semi-regulares",
    "popup.templates.aperiodic"           -> "Aperiódicas",
    "popup.recent.title"                  -> "Archivos recientes",
    "popup.recent.empty"                  ->
      "Aún no hay archivos recientes. Las plantillas que cargues desde la galería aparecerán aquí.",
    "popup.recent.footnote"               ->
      "Solo referencias — los archivos no se incrustan. Si una plantilla se mueve o devuelve 404, la fila fallará al cargar.",
    "popup.recent.justNow"                -> "ahora mismo",
    "popup.recent.minAgoFmt"              -> "hace {0} min",
    "popup.recent.hoursAgoFmt"            -> "hace {0} horas",
    "popup.recent.yesterday"              -> "ayer",
    "popup.recent.daysAgoFmt"             -> "hace {0} días",
    "popup.recent.monthsAgoFmt"           -> "hace {0} meses",
    "popup.settings.title"                -> "Ajustes",
    "popup.settings.fillColor"            -> "Color inicial de relleno",
    "popup.settings.boundaryColor"        -> "Color de la arista del borde",
    "popup.settings.boundaryWidth"        -> "Ancho de la arista del borde",
    "popup.settings.polygonEdgeColor"     -> "Color de la arista",
    "popup.settings.polygonEdgeWidth"     -> "Ancho de la arista",
    "popup.settings.resetDefaults"        -> "Restablecer valores predeterminados",
    "popup.settings.reduceMotion"         -> "Reducir movimiento",
    "popup.settings.reduceMotion.auto"    -> "Auto",
    "popup.settings.reduceMotion.on"      -> "Activado",
    "popup.settings.reduceMotion.off"     -> "Desactivado",
    "popup.settings.language"             -> "Idioma",
    "popup.settings.apply"                -> "Aplicar",
    "popup.settings.reset"                -> "Restablecer",
    "popup.print.title"                   -> "Imprimir a PDF",
    "popup.print.paper"                   -> "Papel",
    "popup.print.paper.a4"                -> "A4",
    "popup.print.paper.letter"            -> "Carta",
    "popup.print.orientation"             -> "Orientación",
    "popup.print.orientation.portrait"    -> "Vertical",
    "popup.print.orientation.landscape"   -> "Horizontal",
    "popup.print.fit"                     -> "Ajuste",
    "popup.print.fit.toPage"              -> "Ajustar lienzo a la página",

    // Loading / async
    "loading.default"      -> "Procesando teselación…",
    "loading.importingSvg" -> "Importando SVG…",
    "loading.parsingSvg"   -> "Analizando metadatos del SVG…",
    "loading.validating"   -> "Validando teselación…",

    // File operations
    "file.saveAs.prompt"      -> "Introduce el nombre del archivo SVG:",
    "file.saveAs.defaultName" -> "teselacion.svg"
  )

  /** Catalogs in lookup order. The English catalog is also the fallback for missing keys. */
  val catalogs: Map[Locale, Map[String, String]] = Map(
    Locale.En -> en,
    Locale.Es -> es
  )
