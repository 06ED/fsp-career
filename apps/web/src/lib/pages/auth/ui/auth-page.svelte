<script lang="ts">
  import { onMount } from "svelte";
  import { goto } from "$app/navigation";
  import { page } from "$app/state";
  import { auth, initAuth, signIn, providerUrl } from "$lib/shared/auth";
  import { ActionIcon } from "$lib/shared/ui";

  const registering = $derived(page.url.searchParams.get("mode") === "register");
  const role = $derived(
    page.url.searchParams.get("role") === "employer" ? "employer" : "candidate",
  );
  let ready = $state(false);
  let busy = $state(false);
  let error = $state("");
  let email = $state("");
  let password = $state("");
  let providers = $state({ fsp: false, vk: false });

  onMount(() => {
    if (page.url.searchParams.has("authError")) {
      error = "Не удалось войти через внешний сервис. Попробуйте ещё раз.";
    }
    connect();
  });

  async function connect() {
    ready = false;
    try {
      await initAuth();
      providers = { ...auth.providers };
      if (auth.authenticated) await goto(`/app?role=${role}`, { replaceState: true });
      ready = true;
    } catch (e) {
      error = e instanceof Error ? e.message : "Не удалось подключить авторизацию.";
    }
  }

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    busy = true;
    error = "";
    try {
      await signIn(email, password, registering);
      password = "";
      await goto(`/app?role=${role}`, { replaceState: true });
    } catch (e) {
      error = e instanceof Error ? e.message : "Не удалось войти в аккаунт.";
    } finally {
      busy = false;
    }
  }

  function enterProvider(provider: "fsp" | "vk") {
    window.location.assign(providerUrl(provider));
  }
</script>

<svelte:head><title>{registering ? "Регистрация" : "Вход"} — ФСП Карьера</title></svelte:head>

<main class="auth-page">
  <a href="/" class="auth-brand" aria-label="ФСП Карьера — на главную">
    <img src="/fsp-mark.svg" alt="" width="36" height="36" />
    <span>ФСП / КАРЬЕРА</span>
  </a>
  <section class="auth-card" aria-labelledby="auth-title" aria-busy={busy}>
    <p class="auth-kicker">ВАШ СЛЕДУЮЩИЙ КАРЬЕРНЫЙ ШАГ</p>
    <h1 id="auth-title">
      {registering ? "Создать аккаунт" : "С возвращением"}
    </h1>
    <p class="auth-description">
      {registering
        ? "Зарегистрируйтесь по почте или через ФСП ID / VK ID."
        : "Войдите по почте или используйте ФСП ID / VK ID."}
    </p>
    {#if error}
      <p role="alert" class="d-ui-error">{error}</p>
    {/if}
    {#if !ready}
      {#if error}
        <button type="button" class="d-ui-outline" onclick={connect}>
          Повторить подключение <ActionIcon name="refresh" />
        </button>
      {:else}
        <p class="d-ui-muted" role="status">Подключаем авторизацию…</p>
      {/if}
    {/if}
    <form onsubmit={submit}>
      <label class="d-ui-label" for="auth-email">Электронная почта</label>
      <input
        id="auth-email"
        class="d-ui-field"
        type="email"
        bind:value={email}
        autocomplete="username"
        maxlength="320"
        required
        disabled={!ready || busy}
      />
      <label class="d-ui-label" for="auth-password">Пароль</label>
      <input
        id="auth-password"
        class="d-ui-field"
        type="password"
        bind:value={password}
        autocomplete={registering ? "new-password" : "current-password"}
        minlength={registering ? 12 : undefined}
        maxlength="128"
        aria-describedby={registering ? "password-hint" : undefined}
        required
        disabled={!ready || busy}
      />
      {#if registering}
        <p id="password-hint" class="auth-hint">От 12 до 128 символов.</p>
      {/if}
      <button class="d-ui-button auth-submit" type="submit" disabled={!ready || busy}>
        {busy ? "Подождите…" : registering ? "Создать аккаунт" : "Войти"}
        <ActionIcon name={registering ? "signup" : "login"} />
      </button>
    </form>
    <p class="auth-divider">
      или {registering ? "зарегистрироваться" : "войти"} через
    </p>
    <div class="auth-providers">
      <button
        type="button"
        class="auth-provider d-ui-outline"
        disabled={!ready || busy || !providers.fsp}
        onclick={() => enterProvider("fsp")}
      >
        <span class="auth-provider-label">
          <img src="/fsp-mark.svg" alt="" width="24" height="24" />
          ФСП ID
        </span>
        {#if ready && !providers.fsp}
          <small>Не подключён</small>
        {/if}
      </button>
      <button
        type="button"
        class="auth-provider d-ui-outline"
        disabled={!ready || busy || !providers.vk}
        onclick={() => enterProvider("vk")}
      >
        <span class="auth-provider-label">
          <img src="/vk-mark.svg" alt="" width="24" height="24" />
          VK ID
        </span>
        {#if ready && !providers.vk}
          <small>Не подключён</small>
        {/if}
      </button>
    </div>
    <p class="auth-switch">
      {registering ? "Уже есть аккаунт?" : "Нет аккаунта?"}
      <a
        data-interactive
        href={`/login?role=${role}${registering ? "" : "&mode=register"}`}
        onclick={() => {
          error = "";
          password = "";
        }}
      >
        {registering ? "Войти" : "Зарегистрироваться"}
      </a>
    </p>
  </section>
  <a class="auth-home" data-interactive href="/">Вернуться на главную</a>
</main>

<style>
  .auth-page {
    min-height: 100svh;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 32px 20px;
    gap: 24px;
    background:
      radial-gradient(ellipse at top, var(--accent-soft), transparent 65%), var(--background);
  }
  .auth-brand {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 14px;
    font-weight: 700;
  }
  .auth-card {
    width: 100%;
    max-width: 440px;
    padding: clamp(24px, 5vw, 36px);
    border-radius: var(--radius-ui);
    background: var(--surface);
    border: 1px solid var(--border);
  }
  .auth-kicker {
    margin: 0 0 12px;
    color: var(--accent);
    font-size: 10px;
    letter-spacing: 0.1em;
  }
  h1 {
    margin: 0;
    font-size: 30px;
    letter-spacing: -0.03em;
  }
  .auth-description {
    margin: 12px 0 28px;
    color: var(--text-muted);
    font-size: 14px;
    line-height: 1.5;
  }
  .auth-submit {
    width: 100%;
  }
  .auth-hint {
    margin: -8px 0 20px;
    color: var(--text-muted);
    font-size: 12px;
  }
  .auth-divider {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 24px 0;
    color: var(--text-muted);
    font-size: 12px;
  }
  .auth-divider::before,
  .auth-divider::after {
    content: "";
    flex: 1;
    height: 1px;
    background: var(--border);
  }
  .auth-providers {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }
  .auth-provider {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    min-height: 56px;
  }
  .auth-provider small {
    font-size: 10px;
    font-weight: 400;
  }
  .auth-provider-label {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
  .auth-provider-label img {
    object-fit: contain;
  }
  .auth-switch {
    margin: 24px 0 0;
    text-align: center;
    font-size: 13px;
    color: var(--text-muted);
  }
  .auth-switch a {
    color: var(--accent);
    font-weight: 700;
  }
  .auth-home {
    color: var(--text-muted);
    font-size: 13px;
  }
</style>
