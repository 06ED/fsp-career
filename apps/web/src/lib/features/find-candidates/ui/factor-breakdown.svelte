<script lang="ts">
  import type { MatchFactor } from "../model/types";
  let { factors }: { factors: MatchFactor[] } = $props();
  const number = (value: number) => value.toLocaleString("ru-RU", { maximumFractionDigits: 1 });
</script>

<div class="d-mx-factors">
  {#each factors as factor (factor.code)}
    <div class="d-mx-factor">
      <div class="d-mx-factor-head">
        <span>{factor.label}</span>
        <span>
          {#if factor.code === "grade"}
            ×{factor.value.toLocaleString("ru-RU", {
              minimumFractionDigits: 2,
            })}
          {:else if factor.weight > 0}
            {number(factor.points)} из {number(factor.weight)}
          {:else}
            справочно
          {/if}
        </span>
      </div>
      {#if factor.code !== "grade" && factor.weight > 0}
        <div
          class="d-mx-track"
          role="meter"
          aria-label={factor.label}
          aria-valuemin="0"
          aria-valuemax={factor.weight}
          aria-valuenow={factor.points}
        >
          <div class="d-mx-fill" style="width: {Math.round(factor.value * 100)}%"></div>
        </div>
      {/if}
      <div>{factor.text}</div>
    </div>
  {/each}
</div>
