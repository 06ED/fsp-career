import type { Company } from "..";
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
