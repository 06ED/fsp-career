import Keycloak from "keycloak-js";

export const auth = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8081",
  realm: "fsp-career",
  clientId: "fsp-web",
});

const base = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

async function request(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  if (!auth.authenticated) throw new Error("Необходимо войти в аккаунт.");
  try {
    await auth.updateToken(30);
  } catch {
    await auth.login();
    throw new Error("Сессия истекла. Пожалуйста, войдите снова.");
  }
  const response = await fetch(`${base}${path}`, {
    ...options,
    headers: {
      ...(options.body && !(options.body instanceof FormData)
        ? { "Content-Type": "application/json" }
        : {}),
      Authorization: `Bearer ${auth.token}`,
      ...options.headers,
    },
  });
  if (!response.ok) {
    let detail = `Ошибка запроса (${response.status})`;
    try {
      const payload = await response.json();
      detail = payload.message ?? payload.error ?? detail;
    } catch {
      /* non-JSON error */
    }
    throw new Error(detail);
  }
  return response;
}

export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await request(path, options);
  return response.status === 204
    ? (undefined as T)
    : ((await response.json()) as T);
}

export async function apiBlob(path: string): Promise<Blob> {
  return (await request(path)).blob();
}

export function save<T>(
  path: string,
  data: unknown,
  method = "PUT",
): Promise<T> {
  return api<T>(path, { method, body: JSON.stringify(data) });
}
