// Only tracks with specialty questions seeded in the API's assessment bank.
export const specializations = [
  { value: "backend", label: "Backend-разработка" },
  { value: "frontend", label: "Frontend-разработка" },
  { value: "data", label: "Данные и аналитика" },
  { value: "product", label: "Продукт" },
  { value: "design", label: "Дизайн" },
] as const;
