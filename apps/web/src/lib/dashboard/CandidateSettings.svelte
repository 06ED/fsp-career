<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import { mx } from "./matching.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { dashboardApi } from "./service";
  import { achievementDetails, achievementLabel } from "./matching";
  import type { CandidatePrivacy, FspStatus, RunAction } from "./types";
  let {
    processingConsent,
    email,
    busy,
    run,
  }: {
    processingConsent: boolean;
    email: string;
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
  let fsp = $state<FspStatus | null>(null);
  let fspInput = $state("");

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
    await run(async () => {
      [privacy, fsp] = await Promise.all([
        dashboardApi.privacy(),
        dashboardApi.fsp(),
      ]);
      fspInput = fsp.fspId ?? "";
    }, "");
  }

  async function savePrivacy() {
    await run(async () => {
      privacy = await dashboardApi.savePrivacy(privacy);
    }, "Настройки приватности сохранены.");
  }

  async function link(id: string) {
    await run(async () => {
      try {
        fsp = await dashboardApi.linkFsp(id.trim());
      } catch (error) {
        const text = error instanceof Error ? error.message : "";
        if (/not found/i.test(text))
          throw new Error(
            "Участник с таким ФСП ID и вашей подтверждённой почтой не найден в реестре. Проверьте номер и почту учётной записи.",
          );
        if (/already linked/i.test(text))
          throw new Error("Этот ФСП ID уже привязан к другой учётной записи.");
        if (/must look like/i.test(text))
          throw new Error("ФСП ID имеет вид FSP-123456.");
        throw error;
      }
    }, "Профиль связан с ФСП ID, результаты импортированы.");
  }

  async function unlink() {
    if (
      !window.confirm(
        "Отвязать ФСП ID? Импортированные достижения будут удалены из профиля.",
      )
    )
      return;
    await run(async () => {
      fsp = await dashboardApi.unlinkFsp();
      fspInput = "";
    }, "ФСП ID отвязан.");
  }

  // Load once consent exists (also right after the candidate first saves the profile with consent).
  $effect(() => {
    if (processingConsent && !fsp) load();
  });
</script>

<div {...sx(s.panel)}>
  <h2 {...sx(s.panelTitle)}>Связь с ID участника ФСП</h2>
  {#if !processingConsent}
    <p {...sx(s.muted)}>
      Сначала сохраните профиль с согласием на обработку данных — после этого
      можно связать профиль с ФСП ID и настроить приватность.
    </p>
  {:else if fsp?.linked}
    <div {...sx(s.success)}>
      Профиль связан с <strong>{fsp.fspId}</strong>{fsp.linkedAt
        ? ` · ${new Date(fsp.linkedAt).toLocaleDateString("ru-RU")}`
        : ""}. {fsp.message}
    </div>
    {#if fsp.achievements.length}
      {#each fsp.achievements as achievement}
        <div {...sx(mx.achievement)}>
          <div>
            <strong>{achievementLabel(achievement)}</strong><br />
            <span {...sx(s.muted)}>{achievementDetails(achievement)}</span>
          </div>
          <span {...sx(mx.points)}>+{achievement.points}</span>
        </div>
      {/each}
      <p {...sx(s.muted)}>
        Сумма баллов: {fsp.totalPoints}. Баллы по правилу «уровень × результат»
        (всероссийский призёр — 9, победитель — 11, региональный участник — 2);
        в выдаче учитывается давность результата.
      </p>
    {/if}
    <div {...sx(s.inline)}>
      <button
        type="button"
        disabled={busy}
        {...sx(s.outline)}
        onclick={() => link(fsp?.fspId ?? "")}
        >Обновить из реестра <ActionIcon name="refresh" /></button
      >
      <button type="button" disabled={busy} {...sx(s.danger)} onclick={unlink}
        >Отвязать <ActionIcon name="remove" /></button
      >
    </div>
  {:else}
    <div {...sx(s.note)}>
      <strong>Истории ФСП нет?</strong> Это нормально: профиль участвует в подборе
      без неё, категория и грейд определяются тестом. Привязка лишь добавляет подтверждённые
      достижения.
    </div>
    <p {...sx(s.muted)}>
      Номер участника сам по себе не доказывает, что он ваш — он печатается в
      протоколах и дипломах. Поэтому мы сверяем подтверждённую почту вашей
      учётной записи ({email || "не подтверждена"}) с почтой участника в реестре
      ФСП. Сейчас подключён {fsp?.registry ?? "демо-реестр ФСП"}: для проверки
      войдите учётной записью с почтой вида <code>имя@demo.fsp.local</code>.
    </p>
    <form
      {...sx(s.inline)}
      onsubmit={(event) => {
        event.preventDefault();
        link(fspInput);
      }}
    >
      <input
        aria-label="ФСП ID"
        placeholder="FSP-100001"
        required
        maxlength="20"
        bind:value={fspInput}
        {...sx(s.field)}
        style="max-width: 260px; margin: 0;"
      />
      <button
        type="submit"
        disabled={busy || !fspInput.trim()}
        {...sx(s.button)}>Привязать ФСП ID <ActionIcon name="confirm" /></button
      >
    </form>
  {/if}
</div>

{#if processingConsent}
  <form
    {...sx(s.panel)}
    onsubmit={(event) => {
      event.preventDefault();
      savePrivacy();
    }}
  >
    <h2 {...sx(s.panelTitle)}>Приватность: что видит работодатель</h2>
    <p {...sx(s.muted)}>
      Специализация, подтверждённый грейд и балл теста видны всегда — на них
      строится подбор. Скрытые разделы не показываются работодателю и не
      участвуют в ранжировании. Контакты открываются только после того, как вы
      примете приглашение.
    </p>
    {#each sections as section (section.key)}
      <label {...sx(s.answer)}>
        <input type="checkbox" bind:checked={privacy[section.key]} />
        <span
          ><strong>{section.label}</strong><br /><span {...sx(s.muted)}
            >{section.hint}</span
          ></span
        >
      </label>
    {/each}
    <button type="submit" disabled={busy} {...sx(s.button)}
      >Сохранить настройки приватности <ActionIcon name="save" /></button
    >
  </form>
{/if}
