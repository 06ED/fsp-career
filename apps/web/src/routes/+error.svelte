<script lang="ts">
  import { page } from "$app/state";
  import { invalidateAll } from "$app/navigation";
  import { ActionIcon } from "$lib/shared/ui";
</script>

<svelte:head><title>Ошибка — ФСП Карьера</title></svelte:head>

<main class="d-ui-main">
  <div class="d-ui-panel">
    <h1 class="d-ui-panel-title">Не удалось открыть страницу</h1>
    <p role="alert" class="d-ui-error">{page.error?.message}</p>
    <div class="d-ui-inline">
      <button class="d-ui-button" onclick={() => invalidateAll()}>
        Повторить загрузку <ActionIcon name="refresh" />
      </button>
      <a href="/app" data-interactive class="d-ui-outline">В кабинет</a>
    </div>
  </div>
</main>
