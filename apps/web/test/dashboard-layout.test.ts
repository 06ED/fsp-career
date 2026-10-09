import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";

test("desktop dashboard owns the viewport while short screens keep document scrolling", () => {
  const css = readFileSync(
    new URL("../src/lib/shared/styles/dashboard.css", import.meta.url),
    "utf8",
  );
  const desktop = css.split(
    "@media (min-width: 980px) and (min-height: 760px)",
  )[1];
  expect(desktop).toBeDefined();
  expect(desktop).toContain("height: 100dvh");
  expect(desktop).toContain("grid-template-columns: 245px minmax(0, 1fr)");
  expect(desktop).toContain("overflow-y: auto");
  expect(desktop).toContain("margin-top: auto");
  expect(
    css.split("@media (min-width: 980px) and (min-height: 760px)")[0],
  ).toContain("min-height: 100vh");
  const dashboard = readFileSync(
    new URL(
      "../src/routes/app/+layout.svelte",
      import.meta.url,
    ),
    "utf8",
  );
  expect(dashboard).toContain(
    '<main bind:this={mainElement} class="d-ui-main" id="dashboard-main">',
  );
  expect(dashboard).toMatch(
    /afterNavigate\([\s\S]*?if \(from\?\.url\?\.pathname !== to\?\.url\?\.pathname\)[\s\S]*?mainElement\.scrollTop = 0/,
  );
});
