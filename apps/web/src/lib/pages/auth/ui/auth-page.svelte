<script lang="ts">
  import { onMount } from "svelte";
  import { goto } from "$app/navigation";
  import { page } from "$app/state";
  import {
    auth,
    initAuth,
    signIn,
    providerUrl,
    appReturnPath,
    verifyEmail,
    resendVerification,
    logout,
  } from "$lib/shared/auth";
  import { ActionIcon } from "$lib/shared/ui";

  const registering = $derived(page.url.searchParams.get("mode") === "register");
  const role = $derived(
    page.url.searchParams.get("role") === "employer" ? "employer" : "candidate",
  );
  const destination = $derived(
    appReturnPath(page.url.searchParams.get("next")) ?? `/app?role=${role}`,
  );
  let ready = $state(false);
  let busy = $state(false);
  let error = $state("");
  let email = $state("");
  let password = $state("");
  let providers = $state({ fsp: false, vk: false });
  let verificationRequired = $state(false);
  let verificationToken = $state("");
  let notice = $state("");

  onMount(() => {
    verificationToken = new URLSearchParams(window.location.hash.slice(1)).get("verify") ?? "";
    if (verificationToken)
      window.history.replaceState(null, "", window.location.pathname + window.location.search);
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
      verificationRequired = auth.emailVerificationRequired;
      if (auth.authenticated && !verificationRequired && !verificationToken)
        await goto(destination, { replaceState: true });
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
      verificationRequired = auth.emailVerificationRequired;
      if (verificationRequired)
        notice = registering
          ? "Письмо отправлено. Откройте ссылку для подтверждения почты."
          : "Подтвердите почту по ссылке из письма.";
      else await goto(destination, { replaceState: true });
    } catch (e) {
      error = e instanceof Error ? e.message : "Не удалось войти в аккаунт.";
      await initAuth().catch(() => {});
      verificationRequired = auth.emailVerificationRequired;
    } finally {
      busy = false;
    }
  }

  function enterProvider(provider: "fsp" | "vk") {
    window.location.assign(providerUrl(provider));
  }

  async function confirmEmail() {
    busy = true;
    error = "";
    try {
      await verifyEmail(verificationToken);
      verificationToken = "";
      verificationRequired = auth.emailVerificationRequired;
      notice = "Почта подтверждена. Войдите с паролем, если вы открыли ссылку в другом браузере.";
      if (auth.authenticated && !verificationRequired)
        await goto(destination, { replaceState: true });
    } catch (e) {
      error = e instanceof Error ? e.message : "Не удалось подтвердить почту.";
    } finally {
      busy = false;
    }
  }

  async function resend() {
    busy = true;
    error = "";
    try {
      await resendVerification();
      notice = "Новое письмо отправлено. Ссылка действует 24 часа.";
    } catch (e) {
      error = e instanceof Error ? e.message : "Не удалось отправить письмо.";
    } finally {
      busy = false;
    }
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
    {#if notice}<p role="status" class="d-ui-note">{notice}</p>{/if}
    {#if !ready}
      {#if error}
        <button type="button" class="d-ui-outline" onclick={connect}>
          Повторить подключение <ActionIcon name="refresh" />
        </button>
      {:else}
        <p class="d-ui-muted" role="status">Подключаем авторизацию…</p>
      {/if}
    {/if}
    {#if verificationToken}
      <p class="d-ui-muted">Подтвердите адрес, на который пришло письмо.</p>
      <button class="d-ui-button" disabled={!ready || busy} onclick={confirmEmail}>
        Подтвердить почту
      </button>
      <a class="auth-home" href="/login">Вернуться ко входу</a>
    {:else if verificationRequired}
      <p class="d-ui-muted">
        Почта {auth.email} ещё не подтверждена. До подтверждения кабинет недоступен.
      </p>
      <button class="d-ui-button" disabled={!ready || busy} onclick={resend}>
        Отправить письмо ещё раз
      </button>
      <button class="d-ui-outline" disabled={busy} onclick={logout}>Выйти</button>
    {:else}
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
          href={`/login?role=${role}${registering ? "" : "&mode=register"}&next=${encodeURIComponent(destination)}`}
          onclick={() => {
            error = "";
            password = "";
          }}
        >
          {registering ? "Войти" : "Зарегистрироваться"}
        </a>
      </p>
    {/if}
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
