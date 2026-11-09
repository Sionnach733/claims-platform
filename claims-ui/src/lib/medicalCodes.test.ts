import { describe, expect, it } from "vitest";
import { CPT_PATTERN, ICD10_PATTERN } from "./claimForm";
import { CPT_CODES, describeCpt, describeIcd, groupCodes, ICD10_CODES } from "./medicalCodes";

// Mirrors PRICE_BENCHMARKS in fraud-service scoring.py, so every price signal can be demoed from the UI.
const FRAUD_BENCHMARKED_CPTS = ["99213", "99214", "99285", "71046", "94640", "20610", "27447"];

describe("medical code catalogue", () => {
  it("only contains codes the API accepts", () => {
    CPT_CODES.forEach((c) => expect(c.code).toMatch(CPT_PATTERN));
    ICD10_CODES.forEach((c) => expect(c.code).toMatch(ICD10_PATTERN));
  });

  it("has no duplicate codes", () => {
    for (const codes of [CPT_CODES, ICD10_CODES]) {
      expect(new Set(codes.map((c) => c.code)).size).toBe(codes.length);
    }
  });

  it("covers every fraud-service signal", () => {
    FRAUD_BENCHMARKED_CPTS.forEach((code) => expect(describeCpt(code)).toBeDefined());
    // Procedure/diagnosis mismatch: musculoskeletal procedures plus related and unrelated diagnoses.
    expect(CPT_CODES.some((c) => Number(c.code) >= 20000 && Number(c.code) <= 29999)).toBe(true);
    expect(ICD10_CODES.some((c) => /^[MS]/.test(c.code))).toBe(true);
    expect(ICD10_CODES.some((c) => !/^[MS]/.test(c.code))).toBe(true);
  });

  it("describes known codes and returns undefined for others", () => {
    expect(describeCpt("99213")).toBe("Office visit, established patient, low complexity");
    expect(describeIcd("j45.909")).toBe("Asthma, uncomplicated");
    expect(describeCpt("12345")).toBeUndefined();
  });

  it("groups codes in catalogue order", () => {
    expect(groupCodes(CPT_CODES)[0]![0]).toBe("Office visits");
    expect(groupCodes(CPT_CODES).flatMap(([, cs]) => cs)).toEqual(CPT_CODES);
  });
});
