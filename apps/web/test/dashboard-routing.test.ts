import { expect, test } from "bun:test";
import { load } from "../src/routes/app/+layout";
import { dashboardNavigation } from "../src/lib/entities/account/model/navigation";
import { appReturnPath } from "../src/lib/shared/auth";
import type { Role } from "../src/lib/entities/account";

test("dashboard routes preserve login destinations and enforce the account role", async () => {
  const originalFetch = globalThis.fetch;
  let authenticated = false;
  let role: Role | null = "candidate";
  globalThis.fetch = (async (input: string | URL | Request) => {
    if (new URL(String(input)).pathname === "/api/me")
      return Response.json({
        role,
        email: "user@example.com",
        emailVerified: true,
      });
    return Response.json({
      authenticated,
      email: "user@example.com",
      csrfToken: "test",
      providers: { fsp: false, vk: false },
      linkedProviders: { fsp: false, vk: false },
    });
  }) as typeof fetch;
  const open = (path: string) =>
    load({
      url: new URL(path, "http://localhost"),
      fetch: globalThis.fetch,
    } as Parameters<typeof load>[0]);

  try {
    await expect(open("/app/tasks")).rejects.toMatchObject({
      status: 307,
      location: "/login?role=candidate&next=%2Fapp%2Ftasks",
    });
    authenticated = true;
    await expect(open("/app/tasks")).resolves.toMatchObject({
      title: "Регулярные задания",
      account: { role: "candidate" },
    });
    await expect(open("/app/company")).rejects.toMatchObject({
      status: 307,
      location: "/app",
    });
    role = "employer";
    await expect(open("/app/company")).resolves.toMatchObject({
      title: "Моя компания",
    });
    await expect(open("/app/profile")).rejects.toMatchObject({
      status: 307,
      location: "/app",
    });
    expect(
      dashboardNavigation(role).some(
        (section) => section.href === "/app/profile",
      ),
    ).toBe(false);
    expect(
      dashboardNavigation(role).some(
        (section) => section.href === "/app/matches",
      ),
    ).toBe(true);
    role = null;
    await expect(open("/app?role=employer")).rejects.toMatchObject({
      status: 307,
      location: "/app/role?role=employer",
    });
    await expect(open("/app/role?role=employer")).resolves.toMatchObject({
      desiredRole: "employer",
      account: { role: null },
    });
    await expect(
      open("/app?section=profile&providerLinkError=failed"),
    ).rejects.toMatchObject({
      status: 307,
      location: "/app/profile?providerLinkError=failed",
    });
    role = "candidate";
    await expect(open("/app/missing")).rejects.toMatchObject({ status: 404 });
    expect(appReturnPath("/app/tasks?view=history")).toBe(
      "/app/tasks?view=history",
    );
    for (const target of [
      "https://evil.example/app",
      "//evil.example/app",
      "/app/../../login",
      "javascript:alert(1)",
    ])
      expect(appReturnPath(target)).toBeNull();
  } finally {
    globalThis.fetch = originalFetch;
  }
});
