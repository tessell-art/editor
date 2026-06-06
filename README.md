[![Scala 3](https://img.shields.io/badge/scala-3.8.4-red.svg)](https://scala-lang.org)
[![Scala.js](https://www.scala-js.org/assets/badges/scalajs-1.16.0.svg)](https://www.scala-js.org)
[![CI](https://github.com/tessell-art/editor/actions/workflows/build.yml/badge.svg)](https://github.com/tessell-art/editor/actions/workflows/build.yml)

# Tessella Editor

![Tessella Logo](public/tessella-logo.svg)

**Tessella Editor** is a simple polygon tessellation editor built with **Scala.js** and **Laminar**. It allows you to interactively create, view, and manipulate tessellations of the plane made of simple (regular and irregular) polygons.

If you want to try it live, you can [open the web editor](https://tessell.art/editor).

The editor is part of the [scala-tessella](https://github.com/scala-tessella) project.

## Key Features

- **Interactive Tiling Creation:**
  - Select polygon shapes from a palette and add them to the canvas.
  - Add new polygons by clicking on any boundary edge.
  - Automatic validation ensures added polygons don't cross boundaries or other polygons, maintaining a proper edge-to-edge finite tessellation.

- **Advanced Editing Tools:**
  - **Eraser Tool:** Delete vertices, edges, or entire polygons while maintaining tiling integrity.
  - **Insertion Tool:** Add regular polygons to the interior of existing ones.
  - **Add Copy:** Grow the tiling by welding on a copy of itself under a plane isometry, snapping to exact tiling anchors. Four direct-manipulation tools: **Translate**, **Rotate**, **Reflect**, and **Glide reflect**.
  - **Mirroring:** Switch the entire tiling to its mirror image.

- **Selection & Styling:**
  - Select polygons by clicking, by color, or by shape.
  - Change polygon colors using a built-in color picker.
  - Shape and color picker tool to clone properties of existing polygons.

- **Visualization Options:**
  - Pan, zoom, and rotate the canvas.
  - Show node labels, uniformity dots, and rotational or reflectional symmetry axes.
  - "Fit to Canvas" to automatically adjust the view to the entire tiling.

- **Measurement:**
  - Calculate unit distances between key points (vertices, mid-sides, centers).
  - Measure angles between points.

- **Saving & Loading:**
  - Save and load your work as SVG files.
  - Export the tiling's topological structure as a DOT graph (.gv).

## Technology Stack

- **Language:** [Scala 3](https://www.scala-lang.org/) (via [Scala.js](https://www.scala-js.org/))
- **UI Framework:** [Laminar](https://laminar.dev/)
- **Web Components:** [UI5 Web Components](https://sap.github.io/ui5-webcomponents/)
- **Build Tools:** [SBT](https://www.scala-sbt.org/), [Vite](https://vitejs.dev/)

## Architecture

Source under `src/main/scala/.../editor/` is organised as a one-directional
dependency graph, enforced at compile time by the `checkLayering` sbt task:

```
  components / interactions   (UI elements + DOM event handlers)
            │
            ▼
        AppState               (thin façade composing operations)
            │
            ▼
        operations             (business logic; sole writer of Vars)
            │
            ▼
          models               (data, Vars, derived Signals)
            │
            ▼
          utils                (no project imports)
```

State is global by necessity (Laminar `Var`s live at module scope) but flow
is one-way: `components`/`interactions` subscribe to `models` signals and call
into `operations` via `AppState`; `operations` is the only layer allowed to
mutate state. `EditorState` groups that state into thirteen aggregate case
classes, each wrapped in its own `Var`; updates use `update(_.copy(...))` to
keep mutations atomic. The desktop shell under `desktop/src-tauri/` and the
Playwright smoke suite under `e2e/` are sibling projects that consume the
same `dist/` bundle Vite produces.

## Getting Started

### Prerequisites

- [JDK 11+](https://adoptium.net/)
- [SBT](https://www.scala-sbt.org/download.html)
- [Node.js & npm](https://nodejs.org/)

### Installation

1.  Clone the repository:
    ```bash
    git clone https://github.com/tessell-art/editor.git
    cd editor
    ```
2.  Install dependencies:
    ```bash
    npm install
    ```

### Development

To run the editor in development mode with hot-reloading:

```bash
npm run dev
```

The Vite plugin will automatically handle the Scala.js compilation. Open `http://localhost:5173` in your browser.

### Building for Production

To create a production build:

```bash
npm run build
```

The output will be in the `dist` folder.

### Tests

Unit tests (MUnit + ScalaCheck) and Laminar-in-JSDOM component mount specs both
run under `sbt`:

```bash
sbt test
```

The Playwright smoke suite is a sibling project with its own `package.json`.
See [`e2e/README.md`](e2e/README.md) for setup and commands.

## Contributing

For more information and to contribute, please visit the [tessell.art site](https://www.tessell.art).

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the release history.

## License

Tessella Editor is released under the [Apache License 2.0](LICENSE).

Built with Scala.js and Laminar.
