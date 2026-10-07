export type Role = "candidate" | "employer";
export type Section =
  | "overview"
  | "profile"
  | "assessment"
  | "company"
  | "needs"
  | "matches"
  | "invitations"
  | "vacancies"
  | "applications"
  | "tasks";
export type Me = { role: Role | null; email: string; emailVerified: boolean };
export { accountApi } from "./api/account";
