<script lang="ts">
  import { onMount } from "svelte";
  import { Invitations } from "$lib/features/manage-invitations";
  import { getDashboard } from "$lib/pages/dashboard";
  import { ActionIcon } from "$lib/shared/ui";

  const dashboard = getDashboard();

  onMount(() => {
    dashboard.loadInvitations();
  });
</script>

{#if dashboard.busy && !dashboard.invitationsLoaded}
  <p class="d-ui-muted" role="status">Загружаем приглашения…</p>
{:else if !dashboard.invitationsLoaded}
  <div class="d-ui-panel">
    <p class="d-ui-muted">Не удалось загрузить приглашения.</p>
    <button class="d-ui-button" disabled={dashboard.busy} onclick={dashboard.loadInvitations}>
      Повторить загрузку <ActionIcon name="refresh" />
    </button>
  </div>
{:else if dashboard.role}
  <Invitations
    invitations={dashboard.invitations}
    role={dashboard.role}
    busy={dashboard.busy}
    updateInvitation={dashboard.updateInvitation}
  />
{/if}
