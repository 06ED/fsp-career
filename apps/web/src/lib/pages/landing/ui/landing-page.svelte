<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import { onMount } from "svelte";
  import BlinkingSquares from "./blinking-squares.svelte";

  onMount(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let context: { revert: () => void } | undefined;
    import("gsap").then(({ gsap }) => {
      context = gsap.context(() => {
        gsap.from("[data-reveal]", {
          y: 28,
          opacity: 0,
          duration: 0.9,
          stagger: 0.12,
          ease: "power3.out",
        });
      });
    });
    return () => context?.revert();
  });
</script>

<svelte:head>
  <title>ФСП Карьера — оценка навыков и подбор</title>
  <meta
    name="description"
    content="Прототип платформы ФСП: оценка навыков, прозрачный подбор и контакт после принятия приглашения."
  />
</svelte:head>

<div class="l-page">
  <header class="l-header">
    <a href="/" aria-label="ФСП Карьера — главная">
      <img src="/fsp-logo.svg" alt="ФСП" class="l-logo" />
    </a>
    <nav class="l-header-right" aria-label="Основная навигация">
      <a href="#how-it-works" class="l-header-link">Как это работает</a>
      <a href="/login" data-interactive class="l-header-button d-ui-outline">
        Войти в платформу <ActionIcon name="login" size={16} />
      </a>
    </nav>
  </header>

  <main>
    <section class="l-hero">
      <BlinkingSquares />
      <h1 data-reveal class="l-hero-title">
        Твой уровень.
        <br />
        <span class="l-violet">Твои возможности.</span>
      </h1>
      <p data-reveal class="l-hero-description">
        Создай профиль, пройди оценку, узнай свою категорию и получай предложения от компаний,
        которым нужен именно ты.
      </p>
      <div data-reveal class="l-actions">
        <a href="/app?role=candidate" data-interactive class="l-primary d-ui-button">
          Я специалист <ActionIcon name="arrow" size={18} placement="solo" />
        </a>
        <a href="/app?role=employer" data-interactive class="l-secondary d-ui-outline">
          Я работодатель <ActionIcon name="arrow" size={18} placement="solo" />
        </a>
      </div>
    </section>

    <section id="how-it-works" class="l-section">
      <div class="l-section-inner">
        <h2 class="l-section-heading">
          Не просто резюме.
          <br />
          Подтверждённый потенциал.
        </h2>
        <div class="l-steps">
          <article class="l-step">
            <div class="l-step-number">01 / ПРОФИЛЬ</div>
            <h3 class="l-step-title">Расскажи о себе</h3>
            <p class="l-step-text">
              Создай профиль, укажи специализацию, опыт и технологии. Сам решай, когда твой профиль
              виден работодателям.
            </p>
          </article>
          <article class="l-step">
            <div class="l-step-number">02 / ОЦЕНКА</div>
            <h3 class="l-step-title">Подтверди навыки</h3>
            <p class="l-step-text">
              Пройди задания по своей специализации. Категория определяется проверкой знаний, а не
              только словами в резюме.
            </p>
          </article>
          <article class="l-step">
            <div class="l-step-number">03 / МЭТЧ</div>
            <h3 class="l-step-title">Получай предложения</h3>
            <p class="l-step-text">
              Компании видят релевантных кандидатов и объяснение совпадения. Контакты открываются
              только с твоего согласия.
            </p>
          </article>
        </div>
      </div>
    </section>
  </main>
  <footer class="l-footer">
    <span>© ФСП КАРЬЕРА · ПЛАТФОРМА СПОРТИВНОГО ПРОГРАММИРОВАНИЯ</span>
    <span>НАВЫКИ → ВОЗМОЖНОСТИ</span>
  </footer>
</div>
