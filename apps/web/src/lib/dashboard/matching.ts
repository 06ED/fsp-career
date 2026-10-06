import type { Company, VerifiedAchievement } from "./types";

export const invitationStatusLabels: Record<string, string> = {
  sent: "Отправлено",
  viewed: "Просмотрено",
  accepted: "Принято",
  rejected: "Отклонено",
};

export function gradeLabel(grade?: string | null): string {
  return grade ? grade.charAt(0).toUpperCase() + grade.slice(1) : "—";
}

export function splitTags(value: string): string[] {
  const seen = new Set<string>();
  return value
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => {
      const key = tag.toLowerCase();
      if (!tag || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

/** «Призёр (2 место) · Чемпионат России … · 2025» */
export function achievementLabel(a: VerifiedAchievement): string {
  const outcome = a.resultLabel
    ? a.resultLabel.charAt(0).toUpperCase() + a.resultLabel.slice(1)
    : "Подтверждено";
  const place = a.place ? ` (${a.place} место)` : "";
  const event = a.eventName || a.title;
  const yearText = a.eventDate?.slice(0, 4) ?? "";
  // Event names often already contain the year («… 2025 (демо)»): don't repeat it.
  const year = yearText && !event.includes(yearText) ? ` · ${yearText}` : "";
  return `${outcome}${place} · ${event}${year}`;
}

export function achievementDetails(a: VerifiedAchievement): string {
  return [a.discipline, a.levelLabel, a.issuer].filter(Boolean).join(" · ");
}

export function daysAgo(iso?: string | null): string {
  if (!iso) return "нет данных";
  const days = Math.max(
    0,
    Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000),
  );
  return days === 0 ? "сегодня" : `${days} дн. назад`;
}

/** Same default as the API: the invitation's contact method is built from company contacts. */
export function companyContactLine(company: Company): string {
  return [
    company.contactEmail && `Email: ${company.contactEmail}`,
    company.contactPhone && `Телефон: ${company.contactPhone}`,
    company.contactTelegram && `Telegram: ${company.contactTelegram}`,
    company.contactNote,
  ]
    .filter(Boolean)
    .join(" · ");
}

export const fitLabels: Record<string, string> = {
  exact: "точное совпадение",
  higher: "грейд выше",
  lower: "грейд ниже",
  adjacent: "смежное направление",
  browse: "категория",
};

// Typical stacks per specialization for one-click need/filter tags.
export const stackSuggestions: Record<string, string[]> = {
  backend: [
    "Kotlin",
    "Java",
    "Go",
    "Python",
    "PostgreSQL",
    "Kafka",
    "Docker",
    "Kubernetes",
    "Redis",
  ],
  frontend: [
    "TypeScript",
    "React",
    "Vue",
    "Svelte",
    "Next.js",
    "CSS",
    "Redux",
    "Jest",
    "GraphQL",
  ],
  data: [
    "Python",
    "SQL",
    "Pandas",
    "Spark",
    "Airflow",
    "ClickHouse",
    "PyTorch",
    "scikit-learn",
    "Tableau",
  ],
  product: [
    "SQL",
    "Jira",
    "A/B-тесты",
    "Amplitude",
    "CJM",
    "CustDev",
    "Unit-экономика",
    "Miro",
  ],
  design: [
    "Figma",
    "Дизайн-системы",
    "Прототипирование",
    "UX-исследования",
    "Illustrator",
    "Photoshop",
    "Protopie",
  ],
};
