<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import type { Role } from "./types";
  let {
    suggested,
    busy,
    chooseRole,
  }: {
    suggested: Role;
    busy: boolean;
    chooseRole: (role: Role) => Promise<void>;
  } = $props();

  const options: {
    role: Role;
    title: string;
    text: string;
    icon: "profile" | "company";
  }[] = [
    {
      role: "candidate",
      title: "Я специалист",
      text: "Пройдите опрос и тест на свой грейд, получите подтверждённую категорию — работодатели сами пришлют приглашения с зарплатной вилкой.",
      icon: "profile",
    },
    {
      role: "employer",
      title: "Я работодатель",
      text: "Опишите потребность команды, получите ранжированную подборку категоризированных кандидатов и пригласите подходящих.",
      icon: "company",
    },
  ];
</script>

<div {...sx(s.panel)}>
  <h2 {...sx(s.panelTitle)}>Кто вы на платформе?</h2>
  <p {...sx(s.muted)}>
    Роль выбирается один раз для этого аккаунта: профиль, тесты и приглашения
    привязаны к ней. Для второй роли используйте отдельный аккаунт.
  </p>
</div>
<div {...sx(s.grid)}>
  {#each options as option}
    <div {...sx(s.panel)}>
      <h2 {...sx(s.panelTitle)}>{option.title}</h2>
      <p {...sx(s.muted)}>{option.text}</p>
      <button
        disabled={busy}
        {...sx(option.role === suggested ? s.button : s.outline)}
        onclick={() => chooseRole(option.role)}
        >Продолжить как {option.role === "candidate"
          ? "специалист"
          : "работодатель"}
        <ActionIcon name={option.icon} /></button
      >
    </div>
  {/each}
</div>
