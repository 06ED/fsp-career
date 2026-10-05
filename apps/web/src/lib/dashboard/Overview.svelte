<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import type {
    Role,
    Profile,
    Company,
    Need,
    Invitation,
    Section,
  } from "./types";
  let {
    role,
    profile,
    company,
    need,
    invitations,
    openSection,
  }: {
    role: Role;
    profile: Profile;
    company: Company;
    need: Need;
    invitations: Invitation[];
    openSection: (id: Section) => Promise<void>;
  } = $props();
</script>

{#if role === "candidate"}
  <div {...sx(s.cards)}>
    <div {...sx(s.stat)}>
      <div {...sx(s.statLabel)}>ПОДТВЕРЖДЁННАЯ КАТЕГОРИЯ</div>
      <div {...sx(s.statNumber)}>{profile.grade || "—"}</div>
    </div>
    <div {...sx(s.stat, s.statLight)}>
      <div {...sx(s.statLabel)}>СПЕЦИАЛИЗАЦИЯ</div>
      <div
        {...sx(s.statNumber)}
        style="font-size: 22px; overflow-wrap: anywhere;"
      >
        {profile.specialization || "Не указана"}
      </div>
    </div>
    <div {...sx(s.stat, s.statLight)}>
      <div {...sx(s.statLabel)}>ПРИГЛАШЕНИЯ</div>
      <div {...sx(s.statNumber)}>{invitations.length}</div>
    </div>
  </div>
  <div {...sx(s.panel)}>
    <h2 {...sx(s.panelTitle)}>Ваш маршрут</h2>
    <p {...sx(s.muted)}>
      01 — Заполните профиль и дайте согласие на публикацию. 02 — Выберите
      специализацию и предполагаемый грейд. 03 — Пройдите оценку: категория
      определяется проверкой навыков. 04 — Рассмотрите приглашения.
    </p>
    <div {...sx(s.inline)}>
      <button {...sx(s.button)} onclick={() => openSection("profile")}
        >Заполнить профиль <ActionIcon name="arrow" /></button
      ><button {...sx(s.outline)} onclick={() => openSection("assessment")}
        >Перейти к оценке <ActionIcon name="assessment" /></button
      >
    </div>
  </div>
{:else}
  <div {...sx(s.cards)}>
    <div {...sx(s.stat)}>
      <div {...sx(s.statLabel)}>СПЕЦИАЛИЗАЦИЯ ПОИСКА</div>
      <div
        {...sx(s.statNumber)}
        style="font-size: 22px; overflow-wrap: anywhere;"
      >
        {need.specialization || "—"}
      </div>
    </div>
    <div {...sx(s.stat, s.statLight)}>
      <div {...sx(s.statLabel)}>ПРИГЛАШЕНИЯ</div>
      <div {...sx(s.statNumber)}>{invitations.length}</div>
    </div>
    <div {...sx(s.stat, s.statLight)}>
      <div {...sx(s.statLabel)}>КОМПАНИЯ</div>
      <div
        {...sx(s.statNumber)}
        style="font-size: 22px; overflow-wrap: anywhere;"
      >
        {company.name || "—"}
      </div>
    </div>
  </div>
  <div {...sx(s.panel)}>
    <h2 {...sx(s.panelTitle)}>Подбор по подтверждённым навыкам</h2>
    <p {...sx(s.muted)}>
      Опишите команду и задачу. Платформа покажет специалистов с проверенной
      категорией, рейтингом и объяснением совпадения. Контакты откроются только
      если кандидат примет приглашение.
    </p>
    <div {...sx(s.inline)}>
      <button {...sx(s.button)} onclick={() => openSection("needs")}
        >Описать потребность <ActionIcon name="need" /></button
      ><button {...sx(s.outline)} onclick={() => openSection("matches")}
        >Смотреть подборку <ActionIcon name="matches" /></button
      >
    </div>
  </div>
{/if}
