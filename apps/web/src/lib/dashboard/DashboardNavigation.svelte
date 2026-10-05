<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
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
  const candidateTabs = [
    { id: "overview", label: "Обзор", icon: "overview" },
    { id: "profile", label: "Мой профиль", icon: "profile" },
    { id: "assessment", label: "Оценка навыков", icon: "assessment" },
    { id: "invitations", label: "Приглашения", icon: "invitations" },
  ] as const;
  const employerTabs = [
    { id: "overview", label: "Обзор", icon: "overview" },
    { id: "company", label: "Компания", icon: "company" },
    { id: "needs", label: "Потребность", icon: "need" },
    { id: "matches", label: "Подбор кандидатов", icon: "matches" },
    { id: "invitations", label: "Приглашения", icon: "invitations" },
  ] as const;
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
        onclick={() => openSection(tab.id)}
        ><ActionIcon
          name={tab.icon}
          size={18}
          placement="leading"
        />{tab.label}</button
      >
    {/each}
  </nav>
  <div {...sx(s.navSpacer)}></div>
  <div {...sx(s.user)}>
    {identity}<br />{role === "candidate" ? "СПЕЦИАЛИСТ" : "РАБОТОДАТЕЛЬ"}<br
    /><button
      {...sx(s.navButton)}
      onclick={() => auth.logout({ redirectUri: window.location.origin })}
      ><ActionIcon name="logout" placement="leading" />Выйти</button
    >
  </div>
</aside>
