<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import type { Role } from "$lib/entities/account";
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

<div class="d-ui-panel">
  <h2 class="d-ui-panel-title">Кто вы на платформе?</h2>
  <p class="d-ui-muted">
    Роль выбирается один раз для этого аккаунта: профиль, тесты и приглашения
    привязаны к ней. Для второй роли используйте отдельный аккаунт.
  </p>
</div>
<div class="d-ui-grid">
  {#each options as option}
    <div class="d-ui-panel">
      <h2 class="d-ui-panel-title">{option.title}</h2>
      <p class="d-ui-muted">{option.text}</p>
      <button
        disabled={busy}
        class={option.role === suggested ? "d-ui-button" : "d-ui-outline"}
        onclick={() => chooseRole(option.role)}
        >Продолжить как {option.role === "candidate"
          ? "специалист"
          : "работодатель"}
        <ActionIcon name={option.icon} /></button
      >
    </div>
  {/each}
</div>
