<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import { candidateApi } from "$lib/entities/candidate";
  import { auth, providerUrl } from "$lib/shared/auth";
  import type { CandidatePrivacy } from "$lib/entities/candidate";
  import type { RunAction } from "$lib/shared/api";
  let {
    processingConsent,
    busy,
    run,
  }: {
    processingConsent: boolean;
    busy: boolean;
    run: RunAction;
  } = $props();

  let privacy = $state<CandidatePrivacy>({
    fullName: true,
    techStack: true,
    experience: true,
    cvHistory: true,
    softSkills: true,
    fspAchievements: true,
  });
  let privacyReady = $state(false);
  let loadError = $state("");
  const providers = [
    { id: "fsp", label: "ФСП ID", icon: "/fsp-mark.svg" },
    { id: "vk", label: "VK ID", icon: "/vk-mark.svg" },
  ] as const;

  const sections: {
    key: keyof CandidatePrivacy;
    label: string;
    hint: string;
  }[] = [
    {
      key: "fullName",
      label: "Имя и фамилия",
      hint: "Если скрыть — работодатель видит «Кандидат №…» до принятия приглашения.",
    },
    {
      key: "techStack",
      label: "Стек технологий",
      hint: "Скрытый стек не участвует в фильтрах и в факторе «Стек».",
    },
    {
      key: "experience",
      label: "Опыт и желаемые роли",
      hint: "Используется для текстового сходства с описанием потребности.",
    },
    {
      key: "cvHistory",
      label: "История работы",
      hint: "Компании, роли и описание задач.",
    },
    {
      key: "softSkills",
      label: "Гибкие навыки",
      hint: "Коммуникация, работа в команде и т. п.",
    },
    {
      key: "fspAchievements",
      label: "Достижения ФСП",
      hint: "Если скрыть — достижения не видны и не повышают позицию в выдаче.",
    },
  ];

  async function load() {
    if (!processingConsent) return;
    privacyReady = false;
    loadError = "";
    try {
      privacy = await candidateApi.privacy();
      privacyReady = true;
    } catch (error) {
      loadError =
        error instanceof Error ? error.message : "Не удалось загрузить настройки приватности.";
    }
  }

  async function savePrivacy() {
    await run(async () => {
      privacy = await candidateApi.savePrivacy(privacy);
    }, "Настройки приватности сохранены.");
  }

  // Load once consent exists (also right after the candidate first saves the profile with consent).
  $effect(() => {
    if (processingConsent) load();
  });
</script>

<div class="d-ui-panel">
  <h2 class="d-ui-panel-title">Связанные аккаунты</h2>
  <div class="d-ui-inline">
    {#each providers as provider}
      {@const linked = auth.linkedProviders[provider.id]}
      {@const enabled = auth.providers[provider.id]}
      <form method="POST" action={providerUrl(provider.id, "link")}>
        <input type="hidden" name="csrfToken" value={auth.csrfToken} />
        <button
          type="submit"
          class="d-ui-outline provider-button"
          disabled={busy || !enabled || linked}
          title={!enabled ? `${provider.label} пока не настроен` : undefined}
        >
          <img src={provider.icon} alt="" width="24" height="24" />
          {linked ? `${provider.label} привязан` : `Привязать ${provider.label}`}
          <ActionIcon name={linked ? "accept" : "signup"} placement="solo" />
        </button>
      </form>
    {/each}
  </div>
</div>

{#if processingConsent}
  <form
    class="d-ui-panel"
    onsubmit={(event) => {
      event.preventDefault();
      savePrivacy();
    }}
  >
    <h2 class="d-ui-panel-title">Приватность: что видит работодатель</h2>
    {#if loadError}
      <div class="d-ui-error" role="alert">{loadError}</div>
      <button type="button" class="d-ui-outline" onclick={load}>
        Повторить загрузку <ActionIcon name="refresh" />
      </button>
    {/if}
    <p class="d-ui-muted">
      Специализация, подтверждённый грейд и балл теста видны всегда — на них строится подбор.
      Скрытые разделы не показываются работодателю и не участвуют в ранжировании. Контакты
      открываются только после того, как вы примете приглашение.
    </p>
    {#each sections as section (section.key)}
      <label class="d-ui-answer">
        <input type="checkbox" bind:checked={privacy[section.key]} />
        <span>
          <strong>{section.label}</strong>
          <br />
          <span class="d-ui-muted">{section.hint}</span>
        </span>
      </label>
    {/each}
    <button type="submit" disabled={busy || !privacyReady} class="d-ui-button">
      Сохранить настройки приватности <ActionIcon name="save" />
    </button>
  </form>
{/if}

<style>
  .provider-button {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    min-height: 48px;
  }
  .provider-button img {
    object-fit: contain;
  }
</style>
