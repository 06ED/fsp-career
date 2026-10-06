<script lang="ts">
  import { industryLabel } from "./industries";
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import { mx } from "./matching.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import FactorBreakdown from "./FactorBreakdown.svelte";
  import {
    achievementDetails,
    achievementLabel,
    daysAgo,
    gradeLabel,
    invitationStatusLabels,
  } from "./matching";
  import type { CandidateCard } from "./types";
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

<svelte:window onkeydown={(event) => event.key === "Escape" && onClose()} />

<div {...sx(mx.backdrop)}>
  <div
    {...sx(mx.modal)}
    role="dialog"
    aria-modal="true"
    aria-labelledby="candidate-card-title"
  >
    <div {...sx(s.row)}>
      <div>
        <div {...sx(s.kicker)}>
          КАРТОЧКА КАНДИДАТА / {(
            card.category ?? card.specialization
          ).toUpperCase()}
        </div>
        <h2
          id="candidate-card-title"
          {...sx(s.panelTitle)}
          style="margin-top: 12px;"
        >
          {card.fullName}
        </h2>
      </div>
      <button type="button" {...sx(s.outline)} onclick={onClose}
        >Закрыть <ActionIcon name="close" /></button
      >
    </div>
    <div {...sx(s.cards)} style="margin-top: 14px;">
      <div {...sx(s.stat)}>
        <div {...sx(s.statLabel)}>ПОДТВЕРЖДЁННЫЙ ГРЕЙД</div>
        <div {...sx(s.statNumber)}>{gradeLabel(card.grade)}</div>
      </div>
      <div {...sx(s.stat, s.statLight)} style="background: #f7f7f9;">
        <div {...sx(s.statLabel)}>ТЕСТ</div>
        <div {...sx(s.statNumber)}>{card.score ?? "—"}<small>/100</small></div>
      </div>
      <div {...sx(s.stat, s.statLight)} style="background: #f7f7f9;">
        <div {...sx(s.statLabel)}>АКТИВНОСТЬ</div>
        <div {...sx(s.statNumber)} style="font-size: 22px;">
          {daysAgo(card.lastActiveAt)}
        </div>
      </div>
    </div>
    {#if card.match}
      <h3 {...sx(s.panelTitle)}>
        Соответствие вашей потребности: {card.match.totalScore} / 100
      </h3>
      <p {...sx(mx.summaryLine)}>{card.match.explanation}</p>
      <FactorBreakdown factors={card.match.factors} />
    {/if}
    {#if card.contactsDisclosed}
      <div {...sx(s.success)}>
        Контакты открыты (кандидат принял ваше приглашение): {card.contactEmail ||
          "—"} · {card.contactPhone || "—"}
      </div>
    {:else}
      <div {...sx(s.note)}>
        Контакты скрыты. Они откроются, когда кандидат примет приглашение.
      </div>
    {/if}
    {#if card.hiddenSections.length}
      <p {...sx(s.muted)}>
        Кандидат скрыл: {card.hiddenSections.join(", ")}. Скрытые разделы не
        участвуют в подборе.
      </p>
    {/if}
    <p {...sx(s.muted)}>
      Отрасль: {industryLabel(card.industry) || "не указана"}{card.assessedAt
        ? ` · тест пройден ${new Date(card.assessedAt).toLocaleDateString("ru-RU")}`
        : ""}
    </p>

    <h3 {...sx(s.panelTitle)}>Достижения ФСП</h3>
    {#if card.achievements == null}
      <p {...sx(s.muted)}>Раздел скрыт кандидатом.</p>
    {:else if card.achievements.length === 0}
      <p {...sx(s.muted)}>
        {card.fspLinked
          ? "ФСП ID привязан, но результатов соревнований в реестре нет."
          : "Нет истории ФСП — это не снижает категорию кандидата."}
      </p>
    {:else}
      {#each card.achievements as achievement}
        <div {...sx(mx.achievement)}>
          <div>
            <strong>{achievementLabel(achievement)}</strong><br />
            <span {...sx(s.muted)}>{achievementDetails(achievement)}</span>
          </div>
          <span {...sx(mx.points)}>+{achievement.points}</span>
        </div>
      {/each}
    {/if}

    {#if card.techStack}
      <h3 {...sx(s.panelTitle)}>Стек (заявлен кандидатом)</h3>
      <div>
        {#each card.techStack as skill}<span {...sx(s.tag)}>{skill}</span
          >{/each}
      </div>
    {/if}
    {#if card.experience}
      <h3 {...sx(s.panelTitle)}>Опыт</h3>
      <p style="white-space: pre-line; font-size: 14px; line-height: 1.55;">
        {card.experience}
      </p>
    {/if}
    {#if card.targetRoles?.length}
      <p {...sx(s.muted)}>Желаемые роли: {card.targetRoles.join(", ")}</p>
    {/if}
    {#if card.cvHistory?.length}
      <h3 {...sx(s.panelTitle)}>История работы</h3>
      {#each card.cvHistory as entry}
        <div {...sx(mx.factor)} style="margin-bottom: 8px;">
          <strong>{entry.role}</strong> · {entry.company}
          <span {...sx(s.muted)}>
            {entry.startDate}{entry.endDate ? `–${entry.endDate}` : ""}</span
          >
          {#if entry.description}<div>{entry.description}</div>{/if}
        </div>
      {/each}
    {/if}
    {#if card.softSkills?.length}
      <h3 {...sx(s.panelTitle)}>Гибкие навыки</h3>
      <div>
        {#each card.softSkills as skill}<span {...sx(s.tag)}>{skill}</span
          >{/each}
      </div>
    {/if}

    <div {...sx(s.separator)}></div>
    {#if card.invitations.length}
      <p {...sx(s.muted)}>
        Ваши приглашения: {card.invitations
          .map(
            (item) =>
              `${invitationStatusLabels[item.status] ?? item.status} (${rub(item.salaryMinRub)}–${rub(item.salaryMaxRub)} ₽)`,
          )
          .join("; ")}
      </p>
    {/if}
    <div {...sx(s.inline)}>
      <button
        type="button"
        {...sx(s.button)}
        onclick={() => onInvite(card.candidateId, card.fullName)}
        >Пригласить <ActionIcon name="send" /></button
      >
      <button type="button" {...sx(s.outline)} onclick={onClose}
        >Закрыть <ActionIcon name="close" /></button
      >
    </div>
  </div>
</div>
