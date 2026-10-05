<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { onMount } from "svelte";
  import { landing as s } from "$lib/landing.stylex";

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
        gsap.from("[data-orbit]", {
          scale: 0.72,
          opacity: 0,
          duration: 1.4,
          stagger: 0.14,
          ease: "power2.out",
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

<div {...sx(s.page)}>
  <header {...sx(s.header)}>
    <a href="/" aria-label="ФСП Карьера — главная"
      ><img src="/fsp-logo.svg" alt="ФСП" {...sx(s.logo)} /></a
    >
    <nav {...sx(s.headerRight)} aria-label="Основная навигация">
      <a href="#how-it-works" {...sx(s.headerLink)}>Как это работает</a>
      <a href="/app" data-interactive {...sx(s.headerButton)}
        >Войти в платформу <ActionIcon name="login" size={16} /></a
      >
    </nav>
  </header>

  <main>
    <section {...sx(s.hero)}>
      <div data-orbit {...sx(s.orbit)}></div>
      <div data-orbit {...sx(s.orbitTwo)}></div>
      <div data-orbit {...sx(s.orbitCore)}>
        <img src="/fsp-mark.svg" alt="" {...sx(s.orbitMark)} />
      </div>
      <div data-reveal {...sx(s.eyebrow)}>
        <span {...sx(s.dot)}></span> ФЕДЕРАЦИЯ СПОРТИВНОГО ПРОГРАММИРОВАНИЯ / КАРЬЕРА
      </div>
      <h1 data-reveal {...sx(s.heroTitle)}>
        Твой уровень.<br /><span {...sx(s.violet)}>Твои возможности.</span>
      </h1>
      <p data-reveal {...sx(s.heroDescription)}>
        Создай профиль, пройди оценку, узнай свою категорию и получай
        предложения от компаний, которым нужен именно ты.
      </p>
      <div data-reveal {...sx(s.actions)}>
        <a href="/app?role=candidate" data-interactive {...sx(s.primary)}
          >Я специалист <ActionIcon
            name="arrow"
            size={18}
            placement="solo"
          /></a
        >
        <a href="/app?role=employer" data-interactive {...sx(s.secondary)}
          >Я работодатель <ActionIcon
            name="arrow"
            size={18}
            placement="solo"
          /></a
        >
      </div>
      <div {...sx(s.floating)}>
        <div {...sx(s.floatingTop)}>
          <span>ПРОВЕРЕНО НА ПРАКТИКЕ</span><ActionIcon
            name="arrow"
            size={15}
            placement="solo"
          />
        </div>
        <div {...sx(s.floatingNumber)}>01 / 03</div>
        <div>Профиль → Оценка → Предложение</div>
      </div>
    </section>

    <section id="how-it-works" {...sx(s.section)}>
      <div {...sx(s.sectionInner)}>
        <div {...sx(s.eyebrow)}>
          <span {...sx(s.dot)}></span> НОВЫЙ ПОДХОД К КАРЬЕРЕ
        </div>
        <h2 {...sx(s.sectionHeading)}>
          Не просто резюме.<br />Подтверждённый потенциал.
        </h2>
        <div {...sx(s.steps)}>
          <article {...sx(s.step)}>
            <div {...sx(s.stepNumber)}>01 / ПРОФИЛЬ</div>
            <h3 {...sx(s.stepTitle)}>Расскажи о себе</h3>
            <p {...sx(s.stepText)}>
              Создай профиль, укажи специализацию, опыт и технологии. Сам решай,
              когда твой профиль виден работодателям.
            </p>
          </article>
          <article {...sx(s.step)}>
            <div {...sx(s.stepNumber)}>02 / ОЦЕНКА</div>
            <h3 {...sx(s.stepTitle)}>Подтверди навыки</h3>
            <p {...sx(s.stepText)}>
              Пройди задания по своей специализации. Категория определяется
              проверкой знаний, а не только словами в резюме.
            </p>
          </article>
          <article {...sx(s.step)}>
            <div {...sx(s.stepNumber)}>03 / МЭТЧ</div>
            <h3 {...sx(s.stepTitle)}>Получай предложения</h3>
            <p {...sx(s.stepText)}>
              Компании видят релевантных кандидатов и объяснение совпадения.
              Контакты открываются только с твоего согласия.
            </p>
          </article>
        </div>
      </div>
    </section>
  </main>
  <footer {...sx(s.footer)}>
    <span>© ФСП КАРЬЕРА · ПЛАТФОРМА СПОРТИВНОГО ПРОГРАММИРОВАНИЯ</span><span
      >НАВЫКИ → ВОЗМОЖНОСТИ</span
    >
  </footer>
</div>
