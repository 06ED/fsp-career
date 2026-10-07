<script lang="ts">
  import { onMount } from "svelte";

  let canvas: HTMLCanvasElement;

  onMount(() => {
    const context = canvas.getContext("2d");
    if (!context) return;

    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    let width = 0;
    let height = 0;
    let frame = 0;
    let start = performance.now();
    const accent = getComputedStyle(canvas).getPropertyValue("--accent").trim();

    function draw(now: number) {
      if (!context || !width || !height) return;
      context.clearRect(0, 0, width, height);
      const cellSize = Math.max(width, height) / 48;
      const columns = Math.ceil(width / cellSize);
      const rows = Math.ceil(height / cellSize);
      const time = (now - start) / 1000;

      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < columns; x++) {
          const i = y * columns + x;
          const seed = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
          const phase = (seed - Math.floor(seed)) * Math.PI * 2;
          const twinkle = 0.5 + 0.5 * Math.sin(time * 4 + phase);
          const right = x / Math.max(1, columns - 1);
          context.fillStyle = accent;
          context.globalAlpha = 0.55 * right * right * twinkle;
          context.fillRect(
            (x + 0.15) * cellSize,
            (y + 0.15) * cellSize,
            cellSize * 0.7,
            cellSize * 0.7,
          );
        }
      }
      context.globalAlpha = 1;
    }

    function loop(now: number) {
      draw(now);
      frame = requestAnimationFrame(loop);
    }

    function motionChanged() {
      cancelAnimationFrame(frame);
      if (media.matches) draw(start);
      else {
        start = performance.now();
        frame = requestAnimationFrame(loop);
      }
    }

    const observer = new ResizeObserver(([entry]) => {
      width = entry.contentRect.width;
      height = entry.contentRect.height;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.round(width * dpr);
      canvas.height = Math.round(height * dpr);
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      if (media.matches) draw(start);
    });
    observer.observe(canvas);
    media.addEventListener("change", motionChanged);
    motionChanged();

    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      media.removeEventListener("change", motionChanged);
    };
  });
</script>

<canvas bind:this={canvas} aria-hidden="true" class="squares"></canvas>

<style>
  .squares {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
  }
</style>
