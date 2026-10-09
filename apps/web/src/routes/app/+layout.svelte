<script lang="ts">
  import { onMount, untrack } from "svelte";
  import { afterNavigate } from "$app/navigation";
  import { page } from "$app/state";
  import { createDashboard, setDashboard } from "$lib/pages/dashboard";
  import { DashboardNavigation } from "$lib/widgets/dashboard-navigation";
  import { ActionIcon } from "$lib/shared/ui";

  let { data, children } = $props();
  const dashboard = setDashboard(createDashboard(untrack(() => data.account)));
  let loaded = $state(false);
  let loadError = $state("");
  let mainElement = $state<HTMLElement>();

  $effect(() => {
    dashboard.role = data.account.role;
    dashboard.identity = data.account.email || "Аккаунт VK ID";
  });

  async function connect() {
    loadError = "";
    try {
      await dashboard.refresh();
      loaded = true;
    } catch (error) {
      loadError = error instanceof Error ? error.message : "Не удалось загрузить кабинет.";
    }
  }

  onMount(() => {
    if (dashboard.role) connect();
    else loaded = true;
  });

  afterNavigate(({ from, to }) => {
    if (from?.url?.pathname !== to?.url?.pathname) {
      dashboard.error = "";
      dashboard.success = "";
      if (mainElement) mainElement.scrollTop = 0;
    }
    if (page.url.searchParams.has("providerLinkError"))
      dashboard.error =
        "Не удалось привязать аккаунт. Возможно, он уже связан с другим профилем. Попробуйте ещё раз.";
    else if (page.url.searchParams.has("authError"))
      dashboard.error = "Не удалось войти через внешний сервис. Попробуйте ещё раз.";
  });
</script>

<svelte:head><title>{data.title} — ФСП Карьера</title></svelte:head>

<div class="d-ui-shell">
  {#if dashboard.role}
    <DashboardNavigation role={dashboard.role} identity={dashboard.identity} />
  {:else}
    <aside class="d-ui-sidebar">
      <a href="/" class="d-ui-brand">
        <img src="/fsp-mark.svg" alt="" class="d-ui-emblem" />
        ФСП / КАРЬЕРА
      </a>
    </aside>
  {/if}
  <main bind:this={mainElement} class="d-ui-main" id="dashboard-main">
    <div class="d-ui-top">
      <div>
        <div class="d-ui-kicker">
          {dashboard.role
            ? `ФСП КАРЬЕРА / ${dashboard.role === "candidate" ? "СПЕЦИАЛИСТ" : "РАБОТОДАТЕЛЬ"}`
            : `ПЕРВЫЙ ВХОД / ${dashboard.identity}`}
        </div>
        <h1 class="d-ui-title">{data.title}</h1>
        {#if dashboard.role}
          <p class="d-ui-subtitle">
            {dashboard.role === "candidate"
              ? "Ваши навыки превращаются в новые возможности."
              : "Найдите специалистов по направлению и результату оценки."}
          </p>
        {/if}
      </div>
    </div>
    {#if dashboard.error}
      <div role="alert" class="d-ui-error">{dashboard.error}</div>
    {/if}
    {#if dashboard.success}
      <div role="status" class="d-ui-success">{dashboard.success}</div>
    {/if}
    {#if loadError}
      <div role="alert" class="d-ui-error">{loadError}</div>
      <button class="d-ui-button" onclick={connect}>
        Повторить загрузку <ActionIcon name="refresh" />
      </button>
    {:else if !loaded}
      <p class="d-ui-muted" role="status">Подключаем платформу…</p>
    {:else}
      {@render children()}
    {/if}
  </main>
</div>
