import type { VerifiedAchievement } from "..";

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
