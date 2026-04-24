import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";

export default defineConfig({
    base: './',
    plugins: [scalaJSPlugin()],
    optimizeDeps: {
        include: [
            '@ui5/webcomponents/dist/ColorPicker.js',
        ]
    }
});