export type Role = "candidate" | "employer";

export type Me = { role: Role | null; email: string; emailVerified: boolean };

export { accountApi } from "./api/account";
export { dashboardNavigation, dashboardSections } from "./model/navigation";
