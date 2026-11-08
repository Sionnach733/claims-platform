import type { Claim, ClaimSummary, Page, Provider } from "../api/types";

export const providers: Provider[] = [
  { npi: "1234567893", name: "Galway Family Practice", specialty: "Family Medicine", inNetwork: true },
  { npi: "1678901236", name: "Atlantic Imaging Centre", specialty: "Radiology", inNetwork: false },
];

export function makeClaim(overrides: Partial<Claim> = {}): Claim {
  return {
    claimNumber: "CLM-2026-00000001",
    memberId: "AET100000001",
    providerNpi: "1234567893",
    serviceDate: "2026-11-01",
    submittedAt: "2026-11-08T10:00:00Z",
    status: "SUBMITTED",
    totalBilled: 125.5,
    totalAllowed: null,
    totalPaid: null,
    denialReason: null,
    riskScore: null,
    lines: [
      { lineNumber: 1, procedureCode: "99213", diagnosisCode: "J45.909", units: 1, billedAmount: 125.5 },
    ],
    history: [
      {
        fromStatus: null,
        toStatus: "SUBMITTED",
        reason: "Claim submitted",
        changedBy: "anonymous",
        changedAt: "2026-11-08T10:00:00Z",
      },
    ],
    ...overrides,
  };
}

export function summaryOf(c: Claim): ClaimSummary {
  const { claimNumber, memberId, providerNpi, serviceDate, submittedAt, status, totalBilled, totalPaid } = c;
  return { claimNumber, memberId, providerNpi, serviceDate, submittedAt, status, totalBilled, totalPaid };
}

export function pageOf<T>(content: T[], page = 0, size = 10, totalElements = content.length): Page<T> {
  return { content, page, size, totalElements, totalPages: Math.ceil(totalElements / size) };
}
