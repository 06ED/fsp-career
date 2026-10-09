import type { Content, TDocumentDefinitions } from "pdfmake/interfaces";
import type { Profile } from "$lib/entities/candidate";
import { industryLabel, specializations } from "$lib/entities/candidate";
import { gradeLabel } from "$lib/entities/assessment";

export function resumeDocument(
  profile: Profile,
  theme: Pick<CSSStyleDeclaration, "getPropertyValue">,
): TDocumentDefinitions {
  if (!profile.processingConsent)
    throw new Error("Для экспорта резюме нужно согласие на обработку данных.");
  if (!profile.fullName.trim())
    throw new Error("Укажите имя и фамилию перед скачиванием резюме.");

  const color = (name: string) => theme.getPropertyValue(name).trim();
  const accent = color("--accent");
  const muted = color("--text-muted");
  const specialization =
    specializations.find((entry) => entry.value === profile.specialization)
      ?.label ||
    profile.specialization ||
    "Не указана";
  const section = (title: string): Content => ({
    text: title,
    style: "section",
    headlineLevel: 1,
  });

  return {
    pageSize: "A4",
    pageMargins: [44, 82, 44, 58],
    info: {
      title: `Резюме — ${profile.fullName.trim()}`,
      author: profile.fullName.trim(),
      creator: "ФСП Карьера",
    },
    defaultStyle: {
      font: "Montserrat",
      fontSize: 10,
      lineHeight: 1.35,
      color: color("--text"),
    },
    styles: {
      section: {
        fontSize: 11,
        bold: true,
        color: accent,
        margin: [0, 20, 0, 8],
      },
      muted: { color: muted, fontSize: 9 },
    },
    background: (_page, size) => ({
      canvas: [
        {
          type: "rect",
          x: 0,
          y: 0,
          w: size.width,
          h: size.height,
          color: color("--surface"),
        },
        {
          type: "rect",
          x: 0,
          y: 0,
          w: size.width,
          h: 54,
          color: accent,
        },
        {
          type: "rect",
          x: 0,
          y: 54,
          w: size.width,
          h: 3,
          color: color("--accent-highlight"),
        },
      ],
    }),
    header: {
      text: "ФСП / КАРЬЕРА · РЕЗЮМЕ",
      color: color("--on-accent"),
      bold: true,
      fontSize: 10,
      margin: [44, 20, 44, 0],
    },
    footer: (page, pages) => ({
      columns: [
        { text: "ФСП Карьера", color: accent },
        { text: `${page} / ${pages}`, alignment: "right" },
      ],
      style: "muted",
      margin: [44, 20, 44, 0],
    }),
    pageBreakBefore: (node, queries) =>
      node.headlineLevel === 1 && !queries.getFollowingNodesOnPage().length,
    content: [
      {
        text: profile.fullName.trim(),
        fontSize: 26,
        bold: true,
        color: accent,
      },
      { text: specialization, color: muted, margin: [0, 6, 0, 12] },
      {
        text:
          [profile.email, profile.phone].filter(Boolean).join(" · ") ||
          "Контакты не указаны",
        margin: [0, 0, 0, 16],
      },
      {
        table: {
          widths: ["*"],
          body: [
            [
              {
                stack: [
                  {
                    text: "ПОДТВЕРЖДЁННАЯ КАТЕГОРИЯ",
                    fontSize: 8,
                    color: accent,
                  },
                  {
                    text: profile.grade
                      ? `${gradeLabel(profile.grade)}${profile.score != null ? ` · ${profile.score}/100` : ""}`
                      : "Оценка ещё не пройдена",
                    fontSize: 16,
                    bold: true,
                    color: accent,
                    margin: [0, 6, 0, 0],
                  },
                ],
                fillColor: color("--accent-soft"),
                margin: [12, 10, 12, 10],
              },
            ],
          ],
          dontBreakRows: true,
        },
        layout: "noBorders",
      },
      section("КАРЬЕРНЫЙ ПРОФИЛЬ"),
      {
        table: {
          widths: [145, "*"],
          body: [
            ["Специализация", specialization],
            ["Отрасль", industryLabel(profile.industry) || "Не указана"],
            ["Желаемые роли", profile.targetRoles.join(", ") || "Не указаны"],
            ["Предполагаемый грейд", gradeLabel(profile.desiredGrade)],
          ].map(([label, value]) => [
            { text: label, color: muted },
            { text: value },
          ]),
        },
        layout: "noBorders",
      },
      section("ТЕХНОЛОГИИ"),
      { text: profile.techStack.join(" · ") || "Не указаны" },
      section("ОПЫТ И ДОСТИЖЕНИЯ"),
      { text: profile.experience || "Не указаны" },
      section("ИСТОРИЯ РАБОТЫ"),
      ...(profile.cvHistory.length
        ? profile.cvHistory.flatMap((entry): Content[] => [
            {
              unbreakable: true,
              stack: [
                {
                  text:
                    [entry.role, entry.company].filter(Boolean).join(" · ") ||
                    "Место работы",
                  bold: true,
                  color: accent,
                },
                {
                  text: [entry.startDate, entry.endDate]
                    .filter(Boolean)
                    .join(" — "),
                  style: "muted",
                  margin: [0, 4, 0, 6],
                },
              ],
            },
            { text: entry.description, margin: [0, 0, 0, 12] },
          ])
        : [{ text: "Не указана" }]),
      section("ГИБКИЕ НАВЫКИ"),
      { text: profile.softSkills.join(" · ") || "Не указаны" },
    ],
  };
}

export async function downloadResumePdf(profile: Profile): Promise<void> {
  const definition = resumeDocument(
    profile,
    getComputedStyle(document.documentElement),
  );
  const { default: pdfMake } = await import("pdfmake/build/pdfmake");
  // Static faces preserve real bold weights in the embedded PDF font subsets.
  pdfMake.addFonts({
    Montserrat: {
      normal: new URL("/fonts/Montserrat-Regular.ttf", window.location.origin)
        .href,
      bold: new URL("/fonts/Montserrat-Bold.ttf", window.location.origin).href,
    },
  });
  await pdfMake.createPdf(definition).download("fsp-resume.pdf");
}
