import { auth, authRequest } from "../auth";

export async function api<T>(
  path: string,
  options: RequestInit = {},
  fetcher: typeof fetch = fetch,
): Promise<T> {
  if (!auth.authenticated) throw new Error("Необходимо войти в аккаунт.");
  const response = await authRequest(path, options, fetcher);
  return response.status === 204
    ? (undefined as T)
    : ((await response.json()) as T);
}

export function save<T>(
  path: string,
  data: unknown,
  method = "PUT",
): Promise<T> {
  return api<T>(path, { method, body: JSON.stringify(data) });
}
