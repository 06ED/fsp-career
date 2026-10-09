import { error, redirect } from "@sveltejs/kit";
import {
  accountApi,
  dashboardNavigation,
  dashboardSections,
} from "$lib/entities/account";
import { initAuth } from "$lib/shared/auth";
import type { LayoutLoad } from "./$types";
import type { Role } from "$lib/entities/account";

// The API session cookie belongs to the API origin and is sent by the browser.
export const ssr = false;

export const load: LayoutLoad = async ({ url, fetch }) => {
  const desiredRole: Role =
    url.searchParams.get("role") === "employer" ? "employer" : "candidate";

  if (
    url.pathname === "/app" &&
    url.searchParams.get("section") === "profile"
  ) {
    const query = new URLSearchParams(url.searchParams);
    query.delete("section");
    redirect(307, `/app/profile${query.size ? `?${query}` : ""}`);
  }

  if (!(await initAuth(fetch))) {
    const query = new URLSearchParams({
      role: desiredRole,
      next: url.pathname + url.search,
    });
    if (url.searchParams.has("authError")) query.set("authError", "failed");
    redirect(307, `/login?${query}`);
  }

  const account = await accountApi.me(fetch);
  if (!account.role && url.pathname !== "/app/role")
    redirect(307, `/app/role?role=${desiredRole}`);
  if (account.role && url.pathname === "/app/role") redirect(307, "/app");
  const section = dashboardSections.find(
    (section) => section.href === url.pathname,
  );
  if (url.pathname !== "/app/role" && !section)
    error(404, "Страница не найдена");
  if (
    account.role &&
    !dashboardNavigation(account.role).some(
      (section) => section.href === url.pathname,
    )
  )
    redirect(307, "/app");

  return { account, desiredRole, title: section?.title ?? "Выберите роль" };
};
