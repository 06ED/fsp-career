export const apiOrigin =
  import.meta.env.VITE_API_URL ?? "http://localhost:8080";

type Session = {
  authenticated: boolean;
  email: string | null;
  csrfToken: string;
  providers: { fsp: boolean; vk: boolean };
  linkedProviders: { fsp: boolean; vk: boolean };
};

export const auth: Session = {
  authenticated: false,
  email: null,
  csrfToken: "",
  providers: { fsp: false, vk: false },
  linkedProviders: { fsp: false, vk: false },
};
let initialization: Promise<boolean> | undefined;

export async function authRequest(
  path: string,
  options: RequestInit = {},
  fetcher: typeof fetch = fetch,
): Promise<Response> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(options.method ?? "GET"))
    headers.set("X-CSRF-Token", auth.csrfToken);
  const response = await fetcher(`${apiOrigin}${path}`, {
    ...options,
    headers,
    credentials: "include",
  });
  if (!response.ok) {
    if (response.status === 401) {
      auth.authenticated = false;
      initialization = undefined;
    }
    const payload = await response.json().catch(() => ({}));
    throw new Error(
      payload.message ?? payload.error ?? `Ошибка запроса (${response.status})`,
    );
  }
  return response;
}

export function initAuth(fetcher: typeof fetch = fetch): Promise<boolean> {
  if (!initialization) {
    initialization = authRequest("/api/auth/session", {}, fetcher)
      .then((response) => response.json())
      .then((session: Session) => {
        Object.assign(auth, session);
        return auth.authenticated;
      })
      .finally(() => {
        initialization = undefined;
      });
  }
  return initialization;
}

export async function signIn(
  email: string,
  password: string,
  register = false,
): Promise<void> {
  await initAuth();
  const response = await authRequest(
    `/api/auth/${register ? "register" : "login"}`,
    {
      method: "POST",
      body: JSON.stringify({ email, password }),
    },
  );
  Object.assign(auth, await response.json());
}

export async function logout(): Promise<void> {
  await initAuth();
  Object.assign(
    auth,
    await (await authRequest("/api/auth/logout", { method: "POST" })).json(),
  );
  window.location.assign("/");
}

export function providerUrl(
  provider: "fsp" | "vk",
  action: "start" | "link" = "start",
): string {
  return `${apiOrigin}/api/auth/${provider}/${action}`;
}

export function appReturnPath(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value, "http://localhost");
    if (
      url.origin !== "http://localhost" ||
      (url.pathname !== "/app" && !url.pathname.startsWith("/app/"))
    )
      return null;
    return url.pathname + url.search + url.hash;
  } catch {
    return null;
  }
}
