<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import { logout } from "$lib/shared/auth";
  import type { Role, Section } from "$lib/entities/account";
  let {
    role,
    section,
    identity,
    openSection,
  }: {
    role: Role;
    section: Section;
    identity: string;
    openSection: (id: Section) => Promise<void>;
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
  const candidateTabs = [
    { id: "overview", label: "Обзор", icon: "overview" },
    { id: "profile", label: "Мой профиль", icon: "profile" },
    { id: "assessment", label: "Оценка навыков", icon: "assessment" },
    { id: "invitations", label: "Приглашения", icon: "invitations" },
    { id: "vacancies", label: "Вакансии", icon: "vacancies" },
    { id: "applications", label: "Мои отклики", icon: "applications" },
    { id: "tasks", label: "Задания", icon: "tasks" },
  ] as const;
  const employerTabs = [
    { id: "overview", label: "Обзор", icon: "overview" },
    { id: "company", label: "Компания", icon: "company" },
    { id: "needs", label: "Потребность", icon: "need" },
    { id: "matches", label: "Подбор кандидатов", icon: "matches" },
    { id: "invitations", label: "Приглашения", icon: "invitations" },
    { id: "vacancies", label: "Вакансии", icon: "vacancies" },
    { id: "tasks", label: "Задания", icon: "tasks" },
  ] as const;
</script>

<aside class="d-ui-sidebar">
  <a href="/" class="d-ui-brand"
    ><img src="/fsp-mark.svg" alt="" class="d-ui-emblem" /> ФСП / КАРЬЕРА</a
  >
  <nav class="d-ui-nav" aria-label="Разделы кабинета">
    {#each role === "candidate" ? candidateTabs : employerTabs as tab}
      <button
        class={`d-ui-nav-button ${section === tab.id ? "d-ui-nav-active" : ""}`}
        aria-current={section === tab.id ? "page" : undefined}
        onclick={() => openSection(tab.id)}
        ><ActionIcon
          name={tab.icon}
          size={18}
          placement="leading"
        />{tab.label}</button
      >
    {/each}
  </nav>
  <div class="d-ui-user">
    <div class="d-ui-account">
      <span class="d-ui-avatar" aria-hidden="true"
        >{identity.trim().charAt(0).toUpperCase() || "?"}</span
      >
      <div class="d-ui-account-details">
        <span class="d-ui-account-email" title={identity}>{identity}</span>
        <span class="d-ui-role"
          >{role === "candidate" ? "Специалист" : "Работодатель"}</span
        >
      </div>
    </div>
    <button class="d-ui-logout" disabled={loggingOut} onclick={signOut}
      ><ActionIcon name="logout" placement="leading" />Выйти</button
    >
    {#if logoutError}<p role="alert" class="d-ui-error">{logoutError}</p>{/if}
  </div>
</aside>
