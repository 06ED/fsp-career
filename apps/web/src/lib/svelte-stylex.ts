import * as stylex from "@stylexjs/stylex";
import type { StyleXStyles } from "@stylexjs/stylex";

/** Adapt StyleX's React-shaped props to Svelte's HTML class/style attributes. */
export function sx(...styles: (StyleXStyles | false | null | undefined)[]): {
  class: string;
  style?: string;
} {
  const result = stylex.props(...styles);
  const inline = result.style as Record<string, string | number> | undefined;
  return {
    class: result.className ?? "",
    ...(inline
      ? {
          style: Object.entries(inline)
            .map(
              ([key, value]) =>
                `${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}:${value}`,
            )
            .join(";"),
        }
      : {}),
  };
}
