import { z } from "zod";
import type { SubmitClaimRequest } from "../api/types";
import { isValidNpi } from "./npi";

// Client-side checks mirror the server's Bean Validation rules for fast feedback.
// The server stays the source of truth: its 400 field errors are shown too.

export const CPT_PATTERN = /^\d{4}[0-9FTU]$/;
export const ICD10_PATTERN = /^[A-TV-Z]\d[0-9A-Z](\.[0-9A-Z]{1,4})?$/;
const AMOUNT_PATTERN = /^\d{1,10}(\.\d{1,2})?$/;

export function todayIso(now: Date = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

const lineSchema = z.object({
  procedureCode: z.string().trim().regex(CPT_PATTERN, { error: "Choose a procedure" }),
  diagnosisCode: z.string().trim().toUpperCase().regex(ICD10_PATTERN, { error: "Choose a diagnosis" }),
  units: z
    .string()
    .regex(/^\d+$/, { error: "Whole number" })
    .refine((u) => Number(u) >= 1 && Number(u) <= 999, {
      error: "1 to 999",
    }),
  billedAmount: z
    .string()
    .trim()
    .regex(AMOUNT_PATTERN, { error: "Amount with up to 2 decimals" })
    .refine((a) => Number(a) > 0, { error: "Must be more than 0" }),
});

export const claimFormSchema = z.object({
  memberId: z
    .string()
    .trim()
    .toUpperCase()
    .regex(/^AET\d{9}$/, { error: "Choose a member" }),
  providerNpi: z.string().trim().refine(isValidNpi, { error: "Not a valid 10-digit NPI" }),
  serviceDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, { error: "Pick the date of service" })
    .refine((d) => d <= todayIso(), { error: "Date of service can't be in the future" }),
  lines: z.array(lineSchema).min(1, { error: "Add at least one line" }).max(50),
});

export type ClaimFormInput = z.input<typeof claimFormSchema>;
export type ClaimFormValues = z.output<typeof claimFormSchema>;

export const emptyLine = { procedureCode: "", diagnosisCode: "", units: "1", billedAmount: "" };

export function toRequest(values: ClaimFormValues): SubmitClaimRequest {
  return {
    memberId: values.memberId,
    providerNpi: values.providerNpi,
    serviceDate: values.serviceDate,
    lines: values.lines.map((l) => ({
      procedureCode: l.procedureCode,
      diagnosisCode: l.diagnosisCode,
      units: Number(l.units),
      billedAmount: Number(l.billedAmount),
    })),
  };
}
