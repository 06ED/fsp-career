<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
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
      <span {...sx(s.status)}>{invitation.status}</span>
    </div>
    <p>
      <strong
        >{Number(invitation.salaryMinRub).toLocaleString("ru-RU")}–{Number(
          invitation.salaryMaxRub,
        ).toLocaleString("ru-RU")} ₽</strong
      > / месяц
    </p>
    {#if role === "candidate" && (invitation.status === "sent" || invitation.status === "viewed")}<div
        {...sx(s.inline)}
      >
        <button
          disabled={busy}
          {...sx(s.button)}
          onclick={() => updateInvitation(invitation, "accept")}
          >Принять ↗</button
        ><button
          disabled={busy}
          {...sx(s.danger)}
          onclick={() => updateInvitation(invitation, "reject")}
          >Отклонить</button
        >{#if invitation.status === "sent"}<button
            disabled={busy}
            {...sx(s.outline)}
            onclick={() => updateInvitation(invitation, "view")}
            >Отметить просмотренным</button
          >{/if}
      </div>{/if}{#if role === "employer" && invitation.status === "accepted"}<div
        {...sx(s.note)}
      >
        Контакты кандидата: {invitation.contactEmail || invitation.email || "—"} ·
        {invitation.contactPhone || invitation.phone || "—"}
      </div>{/if}
  </article>{:else}<div {...sx(s.panel)}>
    <p {...sx(s.muted)}>
      Пока нет приглашений. {role === "candidate"
        ? "Заполните профиль и подтвердите категорию, чтобы вас находили компании."
        : "Откройте подборку и отправьте первое предложение."}
    </p>
  </div>{/each}
