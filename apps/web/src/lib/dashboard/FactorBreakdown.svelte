<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { mx } from "./matching.stylex";
  import type { MatchFactor } from "./types";
  let { factors }: { factors: MatchFactor[] } = $props();
  const number = (value: number) =>
    value.toLocaleString("ru-RU", { maximumFractionDigits: 1 });
</script>

<div {...sx(mx.factors)}>
  {#each factors as factor (factor.code)}
    <div {...sx(mx.factor)}>
      <div {...sx(mx.factorHead)}>
        <span>{factor.label}</span>
        <span>
          {#if factor.code === "grade"}×{factor.value.toLocaleString("ru-RU", {
              minimumFractionDigits: 2,
            })}{:else if factor.weight > 0}{number(factor.points)} из {number(
              factor.weight,
            )}{:else}справочно{/if}
        </span>
      </div>
      {#if factor.code !== "grade" && factor.weight > 0}
        <div
          {...sx(mx.track)}
          role="meter"
          aria-label={factor.label}
          aria-valuemin="0"
          aria-valuemax={factor.weight}
          aria-valuenow={factor.points}
        >
          <div
            {...sx(mx.fill)}
            style="width: {Math.round(factor.value * 100)}%"
          ></div>
        </div>
      {/if}
      <div>{factor.text}</div>
    </div>
  {/each}
</div>
