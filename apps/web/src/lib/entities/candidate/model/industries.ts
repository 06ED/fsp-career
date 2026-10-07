// Same ids and labels as AssessmentCatalog.industries on the API: the survey stores the id, matching
// normalizes free text to it, so employer needs and profiles pick from this list too.
export const industries = [
  { id: "fintech", label: "Финтех и банки" },
  { id: "ecommerce", label: "E-commerce и ритейл" },
  { id: "edtech", label: "Образование (EdTech)" },
  { id: "healthtech", label: "Медицина (HealthTech)" },
  { id: "gamedev", label: "Игры (GameDev)" },
  { id: "telecom", label: "Телеком" },
  { id: "govtech", label: "Госсектор (GovTech)" },
  { id: "logistics", label: "Логистика и транспорт" },
  { id: "media", label: "Медиа и развлечения" },
  { id: "industrial", label: "Промышленность и энергетика" },
  { id: "other", label: "Другая отрасль" },
] as const;

/** Label for a catalog id; free text from older records is shown as is. */
export function industryLabel(value: string | null | undefined): string {
  if (!value) return "";
  return industries.find((i) => i.id === value)?.label ?? value;
}
