<script lang="ts">
  import { ActionIcon, EmptyState } from "$lib/shared/ui";
  import { invitationStatusLabels } from "$lib/entities/invitation";
  import type { Invitation, InvitationStatusAction } from "$lib/entities/invitation";
  import type { Role } from "$lib/entities/account";
  let {
    invitations,
    role,
    busy,
    updateInvitation,
  }: {
    invitations: Invitation[];
    role: Role;
    busy: boolean;
    updateInvitation: (invitation: Invitation, status: InvitationStatusAction) => Promise<void>;
  } = $props();
  const contactsOf = (invitation: Invitation) =>
    [
      invitation.contactEmail || invitation.email,
      invitation.contactPhone || invitation.phone,
    ].filter(Boolean);
</script>

{#each invitations as invitation}
  <article class="d-ui-panel">
    <div class="d-ui-row">
      <div>
        <div class="d-ui-kicker">
          {role === "candidate"
            ? invitation.companyName || "ПРЕДЛОЖЕНИЕ КОМПАНИИ"
            : invitation.candidateName || "КАНДИДАТ"}
        </div>
        <h2 class="d-ui-panel-title" style="margin-top: 11px;">
          {invitation.description}
        </h2>
      </div>
      <span class="d-ui-status">
        {invitationStatusLabels[invitation.status] ?? invitation.status}
      </span>
    </div>
    <p>
      <strong>
        {Number(invitation.salaryMinRub).toLocaleString("ru-RU")}–{Number(
          invitation.salaryMaxRub,
        ).toLocaleString("ru-RU")} ₽
      </strong>
      / месяц
    </p>
    {#if invitation.contactMethod}
      <p class="d-ui-muted">
        {role === "candidate"
          ? `Как связаться с компанией «${invitation.companyName || "—"}»:`
          : "Способ связи, указанный кандидату:"}
        <strong>{invitation.contactMethod}</strong>
      </p>
    {/if}
    {#if invitation.createdAt}
      <p class="d-ui-muted" style="margin-top: 0;">
        Отправлено {new Date(invitation.createdAt).toLocaleDateString("ru-RU")}
      </p>
    {/if}
    {#if role === "candidate" && (invitation.status === "sent" || invitation.status === "viewed")}
      <div class="d-ui-inline">
        <button
          disabled={busy}
          class="d-ui-button"
          onclick={() => updateInvitation(invitation, "accept")}
        >
          Принять <ActionIcon name="accept" />
        </button>
        <button
          disabled={busy}
          class="d-ui-danger"
          onclick={() => updateInvitation(invitation, "reject")}
        >
          Отклонить <ActionIcon name="reject" />
        </button>
        {#if invitation.status === "sent"}
          <button
            disabled={busy}
            class="d-ui-outline"
            onclick={() => updateInvitation(invitation, "view")}
          >
            Отметить просмотренным <ActionIcon name="view" />
          </button>
        {/if}
      </div>
      <p class="d-ui-muted">Приняв приглашение, вы откроете компании свои email и телефон.</p>
    {/if}
    {#if role === "employer"}
      {#if contactsOf(invitation).length}
        <div class="d-ui-note">
          Контакты кандидата: {contactsOf(invitation).join(" · ")}
        </div>
      {:else}
        <p class="d-ui-muted">Контакты кандидата откроются, когда он примет приглашение.</p>
      {/if}
    {/if}
  </article>
{:else}
  <EmptyState
    icon="invitations"
    title="Приглашений пока нет"
    description={role === "candidate"
      ? "Заполните профиль и подтвердите категорию, чтобы вас находили компании."
      : "Откройте подборку и отправьте первое предложение."}
  />
{/each}
