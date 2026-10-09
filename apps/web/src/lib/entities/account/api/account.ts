import { api, save } from "$lib/shared/api";
import type { Me, Role } from "..";

export const accountApi = {
  me: (fetcher?: typeof fetch) => api<Me>("/api/me", {}, fetcher),
  chooseRole: (role: Role) => save<Me>("/api/me/role", { role }, "POST"),
};
