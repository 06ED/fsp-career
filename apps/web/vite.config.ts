import { sveltekit } from "@sveltejs/kit/vite";
import stylex from "@stylexjs/unplugin";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [
    stylex.vite({ useCSSLayers: true, enableMediaQueryOrder: false }),
    sveltekit(),
  ],
  server: { host: "0.0.0.0" },
});
