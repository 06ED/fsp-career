import { auth, authRequest } from "../auth";

async function request(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  if (!auth.authenticated) throw new Error("Необходимо войти в аккаунт.");
  return authRequest(path, options);
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
