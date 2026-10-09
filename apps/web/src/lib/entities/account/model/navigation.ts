import type { Role } from "..";

export const dashboardSections = [
  {
    href: "/app",
    label: "Обзор",
    title: "Главное",
    icon: "overview",
    roles: ["candidate", "employer"],
  },
  {
    href: "/app/profile",
    label: "Мой профиль",
    title: "Мой профиль",
    icon: "profile",
    roles: ["candidate"],
  },
  {
    href: "/app/assessment",
    label: "Оценка навыков",
    title: "Оценка навыков",
    icon: "assessment",
    roles: ["candidate"],
  },
  {
    href: "/app/company",
    label: "Компания",
    title: "Моя компания",
    icon: "company",
    roles: ["employer"],
  },
  {
    href: "/app/needs",
    label: "Потребность",
    title: "Потребность команды",
    icon: "need",
    roles: ["employer"],
  },
  {
    href: "/app/matches",
    label: "Подбор кандидатов",
    title: "Подбор кандидатов",
    icon: "matches",
    roles: ["employer"],
  },
  {
    href: "/app/invitations",
    label: "Приглашения",
    title: "Приглашения",
    icon: "invitations",
    roles: ["candidate", "employer"],
  },
  {
    href: "/app/vacancies",
    label: "Вакансии",
    title: "Вакансии",
    icon: "vacancies",
    roles: ["candidate", "employer"],
  },
  {
    href: "/app/applications",
    label: "Мои отклики",
    title: "Мои отклики",
    icon: "applications",
    roles: ["candidate"],
  },
  {
    href: "/app/tasks",
    label: "Задания",
    title: "Регулярные задания",
    icon: "tasks",
    roles: ["candidate", "employer"],
  },
] as const;

export function dashboardNavigation(role: Role) {
  return dashboardSections.filter((section) =>
    section.roles.some((allowed) => allowed === role),
  );
}
