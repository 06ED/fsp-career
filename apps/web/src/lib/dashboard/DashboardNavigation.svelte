<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import { auth } from "$lib/client";
  import type { Role, Section } from "./types";
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
  const candidateTabs: { id: Section; label: string }[] = [
    { id: "overview", label: "⌂  Обзор" },
    { id: "profile", label: "◫  Мой профиль" },
    { id: "assessment", label: "◇  Оценка навыков" },
    { id: "invitations", label: "↗  Приглашения" },
  ];
  const employerTabs: { id: Section; label: string }[] = [
    { id: "overview", label: "⌂  Обзор" },
    { id: "company", label: "◫  Компания" },
    { id: "needs", label: "◎  Потребность" },
    { id: "matches", label: "◇  Подбор кандидатов" },
    { id: "invitations", label: "↗  Приглашения" },
  ];
</script>

<aside {...sx(s.sidebar)}>
  <a href="/" {...sx(s.brand)}
    ><img src="/fsp-mark.svg" alt="" {...sx(s.emblem)} /> ФСП / КАРЬЕРА</a
  >
  <nav {...sx(s.nav)} aria-label="Разделы кабинета">
    {#each role === "candidate" ? candidateTabs : employerTabs as tab}
      <button
        {...sx(s.navButton, section === tab.id && s.navActive)}
        aria-current={section === tab.id ? "page" : undefined}
        onclick={() => openSection(tab.id)}>{tab.label}</button
      >
    {/each}
  </nav>
  <div {...sx(s.navSpacer)}></div>
  <div {...sx(s.user)}>
    {identity}<br />{role === "candidate" ? "СПЕЦИАЛИСТ" : "РАБОТОДАТЕЛЬ"}<br
    /><button
      {...sx(s.navButton)}
      onclick={() => auth.logout({ redirectUri: window.location.origin })}
      >Выйти ↗</button
    >
  </div>
</aside>
