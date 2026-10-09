import { api, save } from "$lib/shared/api";
import type { Me, Role } from "..";

export const accountApi = {
  me: () => api<Me>("/api/me"),
  chooseRole: (role: Role) => save<Me>("/api/me/role", { role }, "POST"),
};
