import { afterAll, expect, test } from "bun:test";
import { auth, authRequest, initAuth, signIn } from "../src/lib/shared/auth";
import { save } from "../src/lib/shared/api/client";

const originalFetch = globalThis.fetch;
afterAll(() => {
  globalThis.fetch = originalFetch;
});

test("cookie login rotates CSRF; mutations use it; expired sessions can reconnect", async () => {
  const requests: { path: string; options: RequestInit }[] = [];
  let fail = true;
  globalThis.fetch = (async (
    url: string | URL | Request,
    options: RequestInit = {},
  ) => {
    const path = new URL(String(url)).pathname;
    requests.push({ path, options });
    if (fail) throw new Error("network");
    if (path === "/expired")
      return Response.json({ message: "Session expired" }, { status: 401 });
    if (path === "/api/candidates/me") return Response.json({ saved: true });
    return Response.json({
      authenticated: path === "/api/auth/login",
      email: path === "/api/auth/login" ? "user@example.com" : null,
      csrfToken: path === "/api/auth/login" ? "rotated" : "anonymous",
      providers: { fsp: true, vk: true },
    });
  }) as typeof fetch;

  await expect(initAuth()).rejects.toThrow("network");
  fail = false;
  const first = initAuth();
  expect(initAuth()).toBe(first);
  expect(await first).toBe(false);
  expect(auth.providers).toEqual({ fsp: true, vk: true });
  await signIn("user@example.com", "long safe password");
  expect(auth.authenticated).toBe(true);
  expect(auth.email).toBe("user@example.com");
  const login = requests.find((r) => r.path === "/api/auth/login")!;
  expect(new Headers(login.options.headers).get("X-CSRF-Token")).toBe(
    "anonymous",
  );
  expect(JSON.parse(login.options.body as string)).toEqual({
    email: "user@example.com",
    password: "long safe password",
  });
  expect(await save("/api/candidates/me", { fullName: "Test" })).toEqual({
    saved: true,
  });
  const mutation = requests.at(-1)!;
  expect(new Headers(mutation.options.headers).get("X-CSRF-Token")).toBe(
    "rotated",
  );
  expect(new Headers(mutation.options.headers).has("Authorization")).toBe(
    false,
  );
  expect(requests.every((r) => r.options.credentials === "include")).toBe(true);
  await expect(authRequest("/expired")).rejects.toThrow("Session expired");
  expect(auth.authenticated).toBe(false);
  expect(await initAuth()).toBe(false);
  expect(auth.csrfToken).toBe("anonymous");
});
