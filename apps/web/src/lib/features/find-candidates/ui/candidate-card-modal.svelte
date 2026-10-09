<script lang="ts">
  import { achievementDetails, achievementLabel, industryLabel } from "$lib/entities/candidate";
  import { ActionIcon, modal } from "$lib/shared/ui";
  import FactorBreakdown from "./factor-breakdown.svelte";
  import { invitationStatusLabels } from "$lib/entities/invitation";
  import { daysAgo, gradeLabel } from "../model/matching";
  import type { CandidateCard } from "../model/types";
  let {
    card,
    onClose,
    onInvite,
  }: {
    card: CandidateCard;
    onClose: () => void;
    onInvite: (candidateId: string, name: string) => void;
  } = $props();
  const rub = (value: number) => Number(value).toLocaleString("ru-RU");
</script>

<dialog class="d-mx-modal" aria-labelledby="candidate-card-title" use:modal={onClose}>
  <div class="d-ui-row">
    <div>
      <div class="d-ui-kicker">
        КАРТОЧКА КАНДИДАТА / {(card.category ?? card.specialization).toUpperCase()}
      </div>
      <h2 id="candidate-card-title" class="d-ui-panel-title" style="margin-top: 12px;">
        {card.fullName}
      </h2>
    </div>
    <button type="button" class="d-ui-outline" onclick={onClose}>
      Закрыть <ActionIcon name="close" />
    </button>
  </div>
  <div class="d-ui-cards" style="margin-top: 14px;">
    <div class="d-ui-stat">
      <div class="d-ui-stat-label">ПОДТВЕРЖДЁННЫЙ ГРЕЙД</div>
      <div class="d-ui-stat-number">{gradeLabel(card.grade)}</div>
    </div>
    <div class="d-ui-stat d-ui-stat-light" style="background: var(--surface-muted);">
      <div class="d-ui-stat-label">ТЕСТ</div>
      <div class="d-ui-stat-number">
        {card.score ?? "—"}
        <small>/100</small>
      </div>
    </div>
    <div class="d-ui-stat d-ui-stat-light" style="background: var(--surface-muted);">
      <div class="d-ui-stat-label">АКТИВНОСТЬ</div>
      <div class="d-ui-stat-number" style="font-size: 22px;">
        {daysAgo(card.lastActiveAt)}
      </div>
    </div>
  </div>
  {#if card.match}
    <h3 class="d-ui-panel-title">
      Соответствие вашей потребности: {card.match.totalScore} / 100
    </h3>
    <p class="d-mx-summary-line">{card.match.explanation}</p>
    <FactorBreakdown factors={card.match.factors} />
  {/if}
  {#if card.contactsDisclosed}
    <div class="d-ui-success">
      Контакты открыты (кандидат принял ваше приглашение): {card.contactEmail || "—"} · {card.contactPhone ||
        "—"}
    </div>
  {:else}
    <div class="d-ui-note">Контакты скрыты. Они откроются, когда кандидат примет приглашение.</div>
  {/if}
  {#if card.hiddenSections.length}
    <p class="d-ui-muted">
      Кандидат скрыл: {card.hiddenSections.join(", ")}. Скрытые разделы не участвуют в подборе.
    </p>
  {/if}
  <p class="d-ui-muted">
    Отрасль: {industryLabel(card.industry) || "не указана"}{card.assessedAt
      ? ` · тест пройден ${new Date(card.assessedAt).toLocaleDateString("ru-RU")}`
      : ""}
  </p>

  <h3 class="d-ui-panel-title">Достижения ФСП</h3>
  {#if card.achievements == null}
    <p class="d-ui-muted">Раздел скрыт кандидатом.</p>
  {:else if card.achievements.length === 0}
    <p class="d-ui-muted">
      {card.fspLinked
        ? "ФСП ID привязан, но результатов соревнований в реестре нет."
        : "Нет истории ФСП — это не снижает категорию кандидата."}
    </p>
  {:else}
    {#each card.achievements as achievement (achievement)}
      <div class="d-mx-achievement">
        <div>
          <strong>{achievementLabel(achievement)}</strong>
          <br />
          <span class="d-ui-muted">{achievementDetails(achievement)}</span>
        </div>
        <span class="d-mx-points">+{achievement.points}</span>
      </div>
    {/each}
  {/if}

  {#if card.techStack}
    <h3 class="d-ui-panel-title">Стек (заявлен кандидатом)</h3>
    <div>
      {#each card.techStack as skill, index (index)}
        <span class="d-ui-tag">{skill}</span>
      {/each}
    </div>
  {/if}
  {#if card.experience}
    <h3 class="d-ui-panel-title">Опыт</h3>
    <p style="white-space: pre-line; font-size: 14px; line-height: 1.55;">
      {card.experience}
    </p>
  {/if}
  {#if card.targetRoles?.length}
    <p class="d-ui-muted">Желаемые роли: {card.targetRoles.join(", ")}</p>
  {/if}
  {#if card.cvHistory?.length}
    <h3 class="d-ui-panel-title">История работы</h3>
    {#each card.cvHistory as entry (entry)}
      <div class="d-mx-factor" style="margin-bottom: 8px;">
        <strong>{entry.role}</strong>
        · {entry.company}
        <span class="d-ui-muted">
          {entry.startDate}{entry.endDate ? `–${entry.endDate}` : ""}
        </span>
        {#if entry.description}
          <div>{entry.description}</div>
        {/if}
      </div>
    {/each}
  {/if}
  {#if card.softSkills?.length}
    <h3 class="d-ui-panel-title">Гибкие навыки</h3>
    <div>
      {#each card.softSkills as skill, index (index)}
        <span class="d-ui-tag">{skill}</span>
      {/each}
    </div>
  {/if}

  <div class="d-ui-separator"></div>
  {#if card.invitations.length}
    <p class="d-ui-muted">
      Ваши приглашения: {card.invitations
        .map(
          (item) =>
            `${invitationStatusLabels[item.status] ?? item.status} (${rub(item.salaryMinRub)}–${rub(item.salaryMaxRub)} ₽)`,
        )
        .join("; ")}
    </p>
  {/if}
  <div class="d-ui-inline">
    <button
      type="button"
      class="d-ui-button"
      onclick={() => onInvite(card.candidateId, card.fullName)}
    >
      Пригласить <ActionIcon name="send" />
    </button>
    <button type="button" class="d-ui-outline" onclick={onClose}>
      Закрыть <ActionIcon name="close" />
    </button>
  </div>
</dialog>
