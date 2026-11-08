// Mirrors the claims-service v1 API DTOs.

export const CLAIM_STATUSES = ["SUBMITTED", "IN_REVIEW", "APPROVED", "DENIED", "PAID"] as const;
export type ClaimStatus = (typeof CLAIM_STATUSES)[number];

export interface ClaimLine {
  lineNumber: number;
  procedureCode: string;
  diagnosisCode: string;
  units: number;
  billedAmount: number;
}

export interface StatusChange {
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export interface Claim {
  claimNumber: string;
  memberId: string;
  providerNpi: string;
  serviceDate: string;
  submittedAt: string;
  status: ClaimStatus;
  totalBilled: number;
  totalAllowed: number | null;
  totalPaid: number | null;
  denialReason: string | null;
  riskScore: number | null;
  lines: ClaimLine[];
  history: StatusChange[];
}

export interface ClaimSummary {
  claimNumber: string;
  memberId: string;
  providerNpi: string;
  serviceDate: string;
  submittedAt: string;
  status: ClaimStatus;
  totalBilled: number;
  totalPaid: number | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Provider {
  npi: string;
  name: string;
  specialty: string;
  inNetwork: boolean;
}

export interface SubmitClaimRequest {
  memberId: string;
  providerNpi: string;
  serviceDate: string;
  lines: { procedureCode: string; diagnosisCode: string; units: number; billedAmount: number }[];
}

export interface ClaimSearch {
  memberId?: string;
  status?: ClaimStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/** RFC 7807 problem detail as returned by claims-service. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  errors?: { field: string; message: string }[];
  code?: string;
  currentStatus?: ClaimStatus;
  allowedTransitions?: ClaimStatus[];
  correlationId?: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  username: string;
  roles: string[];
}
