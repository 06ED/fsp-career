import { expect, test } from "bun:test";
import type { Profile } from "../src/lib/entities/candidate";
import type { Invitation } from "../src/lib/entities/invitation";
import { routeProgress } from "../src/lib/widgets/overview/model/route";

test("route tracks consent, chosen direction, confirmed assessment and reviewed invitations", () => {
  const profile: Profile = {
    fullName: "",
    email: "",
    phone: "",
    processingConsent: false,
    consentPublication: false,
    specialization: "",
    industry: "",
    desiredGrade: "junior",
    techStack: [],
    experience: "",
    softSkills: [],
    targetRoles: [],
    cvHistory: [],
  };
  const invitation: Invitation = {
    id: "offer-1",
    candidateId: "candidate-1",
    description: "Предложение",
    salaryMinRub: 100000,
    salaryMaxRub: 150000,
    status: "sent",
  };
  expect(routeProgress(profile, [])).toEqual([
    "current",
    "upcoming",
    "upcoming",
    "upcoming",
  ]);
  profile.fullName = "Анна Иванова";
  profile.processingConsent = true;
  expect(routeProgress(profile, [invitation])[0]).toBe("current");
  profile.consentPublication = true;
  expect(routeProgress(profile, [])).toEqual([
    "done",
    "current",
    "upcoming",
    "upcoming",
  ]);
  profile.specialization = "backend";
  expect(routeProgress(profile, [])).toEqual([
    "done",
    "done",
    "current",
    "upcoming",
  ]);
  profile.grade = "junior";
  profile.score = 0;
  expect(routeProgress(profile, [invitation])).toEqual([
    "done",
    "done",
    "done",
    "current",
  ]);
  for (const status of ["viewed", "accepted", "rejected"]) {
    expect(routeProgress(profile, [{ ...invitation, status }])).toEqual([
      "done",
      "done",
      "done",
      "done",
    ]);
  }
  profile.consentPublication = false;
  expect(routeProgress(profile, [invitation])[0]).toBe("current");
  profile.specialization = "   ";
  profile.grade = undefined;
  expect(routeProgress(profile, [])).toEqual([
    "current",
    "upcoming",
    "upcoming",
    "upcoming",
  ]);
});
