import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import pdfMake from "pdfmake/build/pdfmake";
import { resumeDocument } from "../src/lib/features/edit-candidate-profile/lib/resume-pdf";
import type { Profile } from "../src/lib/entities/candidate";

test("browser PDF preserves profile text, theme colors, embedded Cyrillic fonts and pagination", async () => {
  const css = readFileSync(
    new URL("../src/lib/shared/styles/theme.css", import.meta.url),
    "utf8",
  );
  const theme = {
    getPropertyValue: (name: string) =>
      name === "--accent"
        ? "#123456"
        : css.match(new RegExp(`${name}:\\s*(#[a-f0-9]+)`, "i"))![1],
  };
  const profile: Profile = {
    fullName: "Анна Ёлкина",
    email: "anna@example.com",
    phone: "+7 900 123-45-67",
    processingConsent: true,
    consentPublication: false,
    specialization: "frontend",
    industry: "edtech",
    desiredGrade: "senior",
    grade: "middle",
    score: 0,
    techStack: ["TypeScript", "Svelte"],
    experience: "Разрабатывала доступные интерфейсы.\n".repeat(150),
    softSkills: ["Работа в команде"],
    targetRoles: ["Frontend-разработчик"],
    cvHistory: [
      {
        company: "Компания Пример",
        role: "Разработчик",
        startDate: "2021-09",
        endDate: "2025-06",
        description: "Создала интерфейс личного кабинета.",
      },
    ],
  };
  expect(() =>
    resumeDocument({ ...profile, processingConsent: false }, theme),
  ).toThrow("согласие");
  expect(() => resumeDocument({ ...profile, fullName: "   " }, theme)).toThrow(
    "имя и фамилию",
  );
  const definition = resumeDocument(profile, theme);
  expect(definition.styles?.section.color).toBe("#123456");
  expect(definition.defaultStyle?.font).toBe("Montserrat");
  const content = JSON.stringify(definition.content);
  for (const text of [
    profile.fullName,
    profile.email,
    profile.phone,
    profile.experience,
    "Frontend-разработка",
    "Образование (EdTech)",
    "Middle · 0/100",
    "Senior",
    "Компания Пример",
    "Создала интерфейс личного кабинета.",
    "Работа в команде",
  ]) {
    expect(content).toContain(JSON.stringify(text).slice(1, -1));
  }
  expect(
    JSON.stringify(
      resumeDocument({ ...profile, grade: undefined }, theme).content,
    ),
  ).toContain("Оценка ещё не пройдена");

  pdfMake.addVirtualFileSystem(
    Object.fromEntries(
      ["Regular", "Bold"].map((weight) => [
        `Montserrat-${weight}.ttf`,
        readFileSync(
          new URL(`../static/fonts/Montserrat-${weight}.ttf`, import.meta.url),
        ).toString("base64"),
      ]),
    ),
  );
  pdfMake.addFonts({
    Montserrat: {
      normal: "Montserrat-Regular.ttf",
      bold: "Montserrat-Bold.ttf",
    },
  });
  const buffer = await pdfMake.createPdf(definition).getBuffer();
  const pdf = buffer.toString("latin1");
  expect(pdf.startsWith("%PDF-")).toBe(true);
  expect(pdf).toContain("Montserrat-Regular");
  expect(pdf).toContain("Montserrat-Bold");
  expect(pdf).toContain("/ToUnicode");
  expect([...pdf.matchAll(/\/Type \/Page\b/g)].length).toBeGreaterThan(1);
});
