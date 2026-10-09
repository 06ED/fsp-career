<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import { logout } from "$lib/shared/auth";
  import { page } from "$app/state";
  import { dashboardNavigation } from "$lib/entities/account";
  import type { Role } from "$lib/entities/account";

  let {
    role,
    identity,
  }: {
    role: Role;
    identity: string;
  } = $props();

  let logoutError = $state("");
  let loggingOut = $state(false);

  async function signOut() {
    loggingOut = true;
    logoutError = "";
    try {
      await logout();
    } catch (e) {
      logoutError = e instanceof Error ? e.message : "Не удалось выйти";
    } finally {
      loggingOut = false;
    }
  }

</script>

<aside class="d-ui-sidebar">
  <a href="/" class="d-ui-brand">
    <img src="/fsp-mark.svg" alt="" class="d-ui-emblem" />
    ФСП / КАРЬЕРА
  </a>
  <nav class="d-ui-nav" aria-label="Разделы кабинета">
    {#each dashboardNavigation(role) as tab (tab.href)}
      <a
        href={tab.href}
        data-interactive
        class={`d-ui-nav-button ${page.url.pathname === tab.href ? "d-ui-nav-active" : ""}`}
        aria-current={page.url.pathname === tab.href ? "page" : undefined}
      >
        <ActionIcon name={tab.icon} size={18} placement="leading" />{tab.label}
      </a>
    {/each}
  </nav>
  <div class="d-ui-user">
    <div class="d-ui-account">
      <span class="d-ui-avatar" aria-hidden="true">
        {identity.trim().charAt(0).toUpperCase() || "?"}
      </span>
      <div class="d-ui-account-details">
        <span class="d-ui-account-email" title={identity}>{identity}</span>
        <span class="d-ui-role">
          {role === "candidate" ? "Специалист" : "Работодатель"}
        </span>
      </div>
    </div>
    <button class="d-ui-logout" disabled={loggingOut} onclick={signOut}>
      <ActionIcon name="logout" placement="leading" />Выйти
    </button>
    {#if logoutError}
      <p role="alert" class="d-ui-error">{logoutError}</p>
    {/if}
  </div>
</aside>
