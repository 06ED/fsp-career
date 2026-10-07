import { specializations } from "$lib/entities/candidate";
import type { ApplicationStatus, WorkFormat } from "..";

export const workFormats: { value: WorkFormat; label: string }[] = [
  { value: "office", label: "Офис" },
  { value: "hybrid", label: "Гибрид" },
  { value: "remote", label: "Удалённо" },
];

export const grades = [
  { value: "junior", label: "Junior" },
  { value: "middle", label: "Middle" },
  { value: "senior", label: "Senior" },
] as const;

export const applicationStatusLabels: Record<ApplicationStatus, string> = {
  sent: "Отправлен",
  viewed: "Просмотрен",
  accepted: "Принят",
  rejected: "Отказ",
};

export function specializationName(value: string): string {
  return specializations.find((item) => item.value === value)?.label ?? value;
}

export function gradeName(value?: string | null): string {
  return grades.find((item) => item.value === value)?.label ?? "—";
}

export function workFormatName(value: string): string {
  return workFormats.find((item) => item.value === value)?.label ?? value;
}

export function salaryRange(min: number, max: number): string {
  const format = (value: number) => Number(value).toLocaleString("ru-RU");
  return min === max ? `${format(min)} ₽` : `${format(min)}–${format(max)} ₽`;
}

export function shortDate(value?: string | null): string {
  return value ? new Date(value).toLocaleDateString("ru-RU") : "—";
}

export function splitList(value: string): string[] {
  return [
    ...new Set(
      value
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  ];
}
