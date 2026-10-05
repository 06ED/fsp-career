<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import type { Match, Offer } from "./types";
  let {
    matches,
    busy,
    findMatches,
    sendInvitation,
  }: {
    matches: Match[];
    busy: boolean;
    findMatches: () => Promise<void>;
    sendInvitation: (
      candidate: Match,
      offer: Offer,
      onSent: () => void,
    ) => Promise<void>;
  } = $props();
  let search = $state("");
  let stackFilter = $state("");
  let gradeFilter = $state("");
  let achievementOnly = $state(false);
  let selectedCandidate = $state<Match | null>(null);
  let offer = $state<Offer>({
    description: "",
    salaryMinRub: 120000,
    salaryMaxRub: 200000,
  });
  let filteredMatches = $derived(
    matches.filter(
      (m) =>
        (!search ||
          `${m.fullName} ${m.specialization} ${(m.techStack ?? []).join(" ")} ${(m.achievements ?? []).map((a) => a.title).join(" ")}`
            .toLowerCase()
            .includes(search.toLowerCase())) &&
        (!stackFilter ||
          splitTags(stackFilter).every((skill) =>
            (m.techStack ?? []).some(
              (known) => known.toLowerCase() === skill.toLowerCase(),
            ),
          )) &&
        (!gradeFilter || m.grade?.toLowerCase() === gradeFilter) &&
        (!achievementOnly || (m.achievements?.length ?? 0) > 0),
    ),
  );
  function splitTags(value: string): string[] {
    return [
      ...new Set(
        value
          .split(",")
          .map((tag) => tag.trim())
          .filter(Boolean),
      ),
    ];
  }
</script>

<div {...sx(s.panel)}>
  <div {...sx(s.row)}>
    <div>
      <h2 {...sx(s.panelTitle)}>Подходящие специалисты</h2>
      <p {...sx(s.muted)}>
        Рейтинг учитывает оценку, совпадение отрасли и проверенные записи
        достижений. Официальная интеграция ФСП пока отсутствует.
      </p>
    </div>
    <button {...sx(s.outline)} onclick={findMatches}>Обновить ↻</button>
  </div>
  <div {...sx(s.grid)}>
    <div>
      <label for="search" {...sx(s.label)}
        >Поиск по имени, специализации, стеку</label
      ><input
        id="search"
        placeholder="Найти…"
        bind:value={search}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="gradeFilter" {...sx(s.label)}>Грейд</label><select
        id="gradeFilter"
        bind:value={gradeFilter}
        {...sx(s.field)}
        ><option value="">Любой грейд</option><option value="junior"
          >Junior</option
        ><option value="middle">Middle</option><option value="senior"
          >Senior</option
        ></select
      >
    </div>
  </div>
  <label for="stackFilter" {...sx(s.label)}
    >Стек технологий (все указанные, через запятую)</label
  >
  <input
    id="stackFilter"
    placeholder="Kotlin, PostgreSQL"
    bind:value={stackFilter}
    {...sx(s.field)}
  />
  <label {...sx(s.answer)}
    ><input type="checkbox" bind:checked={achievementOnly} /> Только с проверенными
    записями достижений (демо, не официальный реестр ФСП)</label
  >
</div>
{#each filteredMatches as match}<article {...sx(s.panel)}>
    <div {...sx(s.row)}>
      <div>
        <div {...sx(s.kicker)}>
          ПОДТВЕРЖДЁННАЯ КАТЕГОРИЯ / {match.specialization} · {match.grade}
        </div>
        <h2 {...sx(s.panelTitle)} style="margin-top: 12px;">
          {match.fullName}
        </h2>
      </div>
      <div {...sx(s.score)}>
        {match.totalScore ?? match.score}<span style="font-size: 14px;">
          баллов</span
        >
      </div>
    </div>
    <p {...sx(s.muted)}>{match.explanation}</p>
    <div>
      {#each match.techStack ?? [] as skill}<span {...sx(s.tag)}>{skill}</span
        >{/each}
    </div>
    <div>
      {#each match.achievements ?? [] as achievement}<span {...sx(s.tag)}
          >{achievement.title} · {achievement.issuer}</span
        >{/each}
    </div>
    <div {...sx(s.separator)}></div>
    <button
      {...sx(s.button)}
      onclick={() => {
        selectedCandidate = match;
        offer = {
          description: "",
          salaryMinRub: 120000,
          salaryMaxRub: 200000,
        };
      }}>Пригласить кандидата ↗</button
    >
  </article>{:else}<div {...sx(s.panel)}>
    <p {...sx(s.muted)}>
      Пока нет специалистов по этим условиям. Сохраните потребность или измените
      фильтры.
    </p>
  </div>{/each}
{#if selectedCandidate}<form
    {...sx(s.panel)}
    onsubmit={(event) => {
      event.preventDefault();
      if (selectedCandidate)
        sendInvitation(
          selectedCandidate,
          offer,
          () => (selectedCandidate = null),
        );
    }}
  >
    <div {...sx(s.row)}>
      <h2 {...sx(s.panelTitle)}>
        Предложение для {selectedCandidate.fullName}
      </h2>
      <button
        type="button"
        {...sx(s.outline)}
        onclick={() => (selectedCandidate = null)}>Закрыть ✕</button
      >
    </div>
    <label for="offerDescription" {...sx(s.label)}>Описание предложения</label
    ><textarea
      id="offerDescription"
      required
      bind:value={offer.description}
      {...sx(s.textarea)}
    ></textarea>
    <div {...sx(s.grid)}>
      <div>
        <label for="salaryMin" {...sx(s.label)}>Зарплата от, ₽</label><input
          id="salaryMin"
          required
          type="number"
          min="1"
          bind:value={offer.salaryMinRub}
          {...sx(s.field)}
        />
      </div>
      <div>
        <label for="salaryMax" {...sx(s.label)}>Зарплата до, ₽</label><input
          id="salaryMax"
          required
          type="number"
          min={offer.salaryMinRub}
          bind:value={offer.salaryMaxRub}
          {...sx(s.field)}
        />
      </div>
    </div>
    <p {...sx(s.muted)}>
      Контакты кандидата станут доступны только после принятия приглашения.
    </p>
    <button
      type="submit"
      disabled={busy || offer.salaryMaxRub < offer.salaryMinRub}
      {...sx(s.button)}>Отправить приглашение ↗</button
    >
  </form>{/if}
