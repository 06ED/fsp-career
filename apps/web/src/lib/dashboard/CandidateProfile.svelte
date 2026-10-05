<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import type { Profile, ResumeDraft } from "./types";
  import { specializations } from "./specializations";
  let {
    profile = $bindable(),
    resumeDraft,
    techStackText = $bindable(),
    softSkillsText = $bindable(),
    targetRolesText = $bindable(),
    busy,
    uploadResume,
    confirmResume,
    saveProfile,
    downloadResume,
    revokeConsent,
  }: {
    profile: Profile;
    resumeDraft: ResumeDraft | null;
    techStackText: string;
    softSkillsText: string;
    targetRolesText: string;
    busy: boolean;
    uploadResume: (event: Event) => Promise<void>;
    confirmResume: () => Promise<void>;
    saveProfile: () => Promise<void>;
    downloadResume: () => Promise<void>;
    revokeConsent: () => Promise<void>;
  } = $props();
</script>

<div {...sx(s.panel)}>
  <h2 {...sx(s.panelTitle)}>Загрузить резюме PDF</h2>
  <p {...sx(s.muted)}>
    Файл до 5 МБ. Распознавание создаёт только черновик: проверьте данные перед
    сохранением; подтверждённый email и настройка публикации не меняются.
  </p>
  <input
    type="file"
    accept="application/pdf,.pdf"
    aria-label="Загрузить PDF-резюме"
    onchange={uploadResume}
    disabled={busy}
  />
  {#if resumeDraft}<div {...sx(s.note)}>
      Черновик готов к проверке. Исправьте распознанные поля ниже, затем
      подтвердите перенос.
    </div>
    <button
      type="button"
      {...sx(s.button)}
      disabled={busy}
      onclick={confirmResume}
      >Подтвердить данные из PDF <ActionIcon name="confirm" /></button
    >{/if}
</div>
<form
  {...sx(s.panel)}
  onsubmit={(event) => {
    event.preventDefault();
    saveProfile();
  }}
>
  <h2 {...sx(s.panelTitle)}>Профиль специалиста</h2>
  <p {...sx(s.muted)}>
    Категория подтверждается только после оценки. Указанный грейд — ваше
    предположение, а не результат теста.
  </p>
  <div {...sx(s.grid)}>
    <div>
      <label for="fullName" {...sx(s.label)}>Имя и фамилия</label><input
        id="fullName"
        required
        bind:value={profile.fullName}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="phone" {...sx(s.label)}>Телефон для связи</label><input
        id="phone"
        type="tel"
        bind:value={profile.phone}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="spec" {...sx(s.label)}>Специализация</label><select
        id="spec"
        required
        bind:value={profile.specialization}
        {...sx(s.field)}
        ><option value="">Выберите направление</option
        >{#each specializations as item}<option value={item.value}
            >{item.label}</option
          >{/each}</select
      >
    </div>
    <div>
      <label for="industry" {...sx(s.label)}>Отрасль / интерес</label><input
        id="industry"
        placeholder="Финтех, образование…"
        bind:value={profile.industry}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="grade" {...sx(s.label)}>Предполагаемый грейд</label><select
        id="grade"
        bind:value={profile.desiredGrade}
        {...sx(s.field)}
        ><option value="junior">Junior</option><option value="middle"
          >Middle</option
        ><option value="senior">Senior</option></select
      >
    </div>
    <div>
      <label for="email" {...sx(s.label)}>Электронная почта</label><input
        id="email"
        type="email"
        readonly
        aria-describedby="email-hint"
        bind:value={profile.email}
        {...sx(s.field)}
      /><small id="email-hint" {...sx(s.muted)}
        >Подтверждённый адрес из учётной записи; изменить его можно в настройках
        Keycloak.</small
      >
    </div>
  </div>
  <div {...sx(s.grid)}>
    <div>
      <label for="techStack" {...sx(s.label)}>Технологии · через запятую</label
      ><input
        id="techStack"
        placeholder="Kotlin, PostgreSQL, Ktor"
        bind:value={techStackText}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="targetRoles" {...sx(s.label)}
        >Желаемые роли · через запятую</label
      ><input
        id="targetRoles"
        placeholder="Backend engineer, Team lead"
        bind:value={targetRolesText}
        {...sx(s.field)}
      />
    </div>
  </div>
  <label for="softSkills" {...sx(s.label)}>Гибкие навыки · через запятую</label
  ><input
    id="softSkills"
    placeholder="Коммуникация, работа в команде"
    bind:value={softSkillsText}
    {...sx(s.field)}
  />
  <label for="experience" {...sx(s.label)}>Опыт и достижения</label><textarea
    id="experience"
    bind:value={profile.experience}
    {...sx(s.textarea)}
  ></textarea>
  <h3 {...sx(s.panelTitle)}>Опыт работы</h3>
  {#each profile.cvHistory as entry, index}
    <div {...sx(s.panel)}>
      <div {...sx(s.grid)}>
        <div>
          <label for={"employer-" + index} {...sx(s.label)}>Компания</label
          ><input
            id={"employer-" + index}
            bind:value={entry.company}
            {...sx(s.field)}
          />
        </div>
        <div>
          <label for={"role-" + index} {...sx(s.label)}>Роль</label><input
            id={"role-" + index}
            bind:value={entry.role}
            {...sx(s.field)}
          />
        </div>
        <div>
          <label for={"start-" + index} {...sx(s.label)}>Начало (ГГГГ-ММ)</label
          ><input
            id={"start-" + index}
            placeholder="2022-09"
            bind:value={entry.startDate}
            {...sx(s.field)}
          />
        </div>
        <div>
          <label for={"end-" + index} {...sx(s.label)}
            >Окончание / по настоящее время</label
          ><input
            id={"end-" + index}
            placeholder="2024-06"
            bind:value={entry.endDate}
            {...sx(s.field)}
          />
        </div>
      </div>
      <label for={"description-" + index} {...sx(s.label)}>Что делали</label
      ><textarea
        id={"description-" + index}
        bind:value={entry.description}
        {...sx(s.textarea)}
      ></textarea>
      <button
        type="button"
        {...sx(s.danger)}
        onclick={() =>
          (profile.cvHistory = profile.cvHistory.filter(
            (_, position) => position !== index,
          ))}>Удалить запись <ActionIcon name="remove" /></button
      >
    </div>
  {/each}
  <button
    type="button"
    {...sx(s.outline)}
    onclick={() =>
      (profile.cvHistory = [
        ...profile.cvHistory,
        {
          company: "",
          role: "",
          startDate: "",
          endDate: "",
          description: "",
        },
      ])}>Добавить опыт <ActionIcon name="add" /></button
  >
  <label {...sx(s.answer)}
    ><input type="checkbox" required bind:checked={profile.processingConsent} /> Я
    даю согласие на обработку персональных данных для работы платформы. Без него нельзя
    сохранять профиль и проходить оценку.</label
  >
  <label {...sx(s.answer)}
    ><input type="checkbox" bind:checked={profile.consentPublication} /> Я отдельно
    согласен(на) на публикацию профиля в подборке работодателей. Эту настройку можно
    выключить, сохранив профиль.</label
  >
  <div {...sx(s.inline)}>
    <button type="submit" disabled={busy} {...sx(s.button)}
      >Сохранить профиль <ActionIcon name="save" /></button
    >
    <button
      type="button"
      disabled={busy || !profile.processingConsent || !profile.fullName}
      {...sx(s.outline)}
      onclick={downloadResume}
      >Скачать резюме PDF <ActionIcon name="download" /></button
    >
    <button
      type="button"
      disabled={busy}
      {...sx(s.danger)}
      onclick={() => {
        if (
          !window.confirm(
            "Отозвать согласие и безвозвратно удалить профиль, оценки и приглашения?",
          )
        )
          return;
        revokeConsent();
      }}>Удалить мои данные <ActionIcon name="remove" /></button
    >
  </div>
</form>
