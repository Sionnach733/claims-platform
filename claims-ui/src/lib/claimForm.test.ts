import { describe, expect, it } from "vitest";
import { claimFormSchema, todayIso, toRequest } from "./claimForm";
import { isValidNpi } from "./npi";

const valid = {
  memberId: "aet100000001",
  providerNpi: "1234567893",
  serviceDate: "2026-11-01",
  lines: [{ procedureCode: "99213", diagnosisCode: "j45.909", units: "2", billedAmount: "125.50" }],
};

describe("isValidNpi (same vectors as the Java NpiValidatorTest)", () => {
  it.each(["1234567893", "1987654328", "1456789019", "1678901236", "1321098760"])("accepts %s", (npi) => {
    expect(isValidNpi(npi)).toBe(true);
  });

  it.each(["1234567890", "123456789", "12345678933", "12345678a3", ""])("rejects %s", (npi) => {
    expect(isValidNpi(npi)).toBe(false);
  });
});

describe("claimFormSchema", () => {
  it("normalises case and converts to the API request", () => {
    const parsed = claimFormSchema.parse(valid);

    expect(toRequest(parsed)).toEqual({
      memberId: "AET100000001",
      providerNpi: "1234567893",
      serviceDate: "2026-11-01",
      lines: [{ procedureCode: "99213", diagnosisCode: "J45.909", units: 2, billedAmount: 125.5 }],
    });
  });

  it.each([
    ["memberId", { memberId: "123" }],
    ["providerNpi", { providerNpi: "1234567890" }],
    ["serviceDate", { serviceDate: "2999-01-01" }],
  ])("rejects an invalid %s", (field, override) => {
    const result = claimFormSchema.safeParse({ ...valid, ...override });

    expect(result.success).toBe(false);
    expect(result.error?.issues.map((i) => i.path[0])).toContain(field);
  });

  it.each([
    ["procedureCode", "ABC12"],
    ["diagnosisCode", "123"],
    ["units", "0"],
    ["units", "1.5"],
    ["billedAmount", "0"],
    ["billedAmount", "12.345"],
  ])("rejects line %s = %s", (field, value) => {
    const result = claimFormSchema.safeParse({ ...valid, lines: [{ ...valid.lines[0], [field]: value }] });

    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(["lines", 0, field]);
  });

  it("requires at least one line", () => {
    expect(claimFormSchema.safeParse({ ...valid, lines: [] }).success).toBe(false);
  });

  it("formats today in local time", () => {
    expect(todayIso(new Date(2026, 10, 8, 23, 30))).toBe("2026-11-08");
  });
});
