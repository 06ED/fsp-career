<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { invitationStatusLabels } from "./matching";
  import type { Invitation, InvitationStatusAction, Role } from "./types";
  let {
    invitations,
    role,
    busy,
    updateInvitation,
  }: {
    invitations: Invitation[];
    role: Role;
    busy: boolean;
    updateInvitation: (
      invitation: Invitation,
      status: InvitationStatusAction,
    ) => Promise<void>;
  } = $props();
  const contactsOf = (invitation: Invitation) =>
    [
      invitation.contactEmail || invitation.email,
      invitation.contactPhone || invitation.phone,
    ].filter(Boolean);
</script>

{#each invitations as invitation}<article {...sx(s.panel)}>
    <div {...sx(s.row)}>
      <div>
        <div {...sx(s.kicker)}>
          {role === "candidate"
            ? invitation.companyName || "ПРЕДЛОЖЕНИЕ КОМПАНИИ"
            : invitation.candidateName || "КАНДИДАТ"}
        </div>
        <h2 {...sx(s.panelTitle)} style="margin-top: 11px;">
          {invitation.description}
        </h2>
      </div>
      <span {...sx(s.status)}
        >{invitationStatusLabels[invitation.status] ?? invitation.status}</span
      >
    </div>
    <p>
      <strong
        >{Number(invitation.salaryMinRub).toLocaleString("ru-RU")}–{Number(
          invitation.salaryMaxRub,
        ).toLocaleString("ru-RU")} ₽</strong
      > / месяц
    </p>
    {#if invitation.contactMethod}<p {...sx(s.muted)}>
        {role === "candidate"
          ? `Как связаться с компанией «${invitation.companyName || "—"}»:`
          : "Способ связи, указанный кандидату:"}
        <strong>{invitation.contactMethod}</strong>
      </p>{/if}
    {#if invitation.createdAt}<p {...sx(s.muted)} style="margin-top: 0;">
        Отправлено {new Date(invitation.createdAt).toLocaleDateString("ru-RU")}
      </p>{/if}
    {#if role === "candidate" && (invitation.status === "sent" || invitation.status === "viewed")}<div
        {...sx(s.inline)}
      >
        <button
          disabled={busy}
          {...sx(s.button)}
          onclick={() => updateInvitation(invitation, "accept")}
          >Принять <ActionIcon name="accept" /></button
        ><button
          disabled={busy}
          {...sx(s.danger)}
          onclick={() => updateInvitation(invitation, "reject")}
          >Отклонить <ActionIcon name="reject" /></button
        >{#if invitation.status === "sent"}<button
            disabled={busy}
            {...sx(s.outline)}
            onclick={() => updateInvitation(invitation, "view")}
            >Отметить просмотренным <ActionIcon name="view" /></button
          >{/if}
      </div>
      <p {...sx(s.muted)}>
        Приняв приглашение, вы откроете компании свои email и телефон.
      </p>{/if}{#if role === "employer"}{#if contactsOf(invitation).length}<div
          {...sx(s.note)}
        >
          Контакты кандидата: {contactsOf(invitation).join(" · ")}
        </div>{:else}<p {...sx(s.muted)}>
          Контакты кандидата откроются, когда он примет приглашение.
        </p>{/if}{/if}
  </article>{:else}<div {...sx(s.panel)}>
    <p {...sx(s.muted)}>
      Пока нет приглашений. {role === "candidate"
        ? "Заполните профиль и подтвердите категорию, чтобы вас находили компании."
        : "Откройте подборку и отправьте первое предложение."}
    </p>
  </div>{/each}
