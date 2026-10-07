export function gradeLabel(grade?: string | null): string {
  return grade ? grade.charAt(0).toUpperCase() + grade.slice(1) : "—";
}

export function daysAgo(iso?: string | null): string {
  if (!iso) return "нет данных";
  const days = Math.max(
    0,
    Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000),
  );
  return days === 0 ? "сегодня" : `${days} дн. назад`;
}

export const fitLabels: Record<string, string> = {
  exact: "точное совпадение",
  higher: "грейд выше",
  lower: "грейд ниже",
  adjacent: "смежное направление",
  browse: "категория",
};
