import { clearSession, getSession } from "../auth/session";
import type {
  Claim,
  ClaimSearch,
  ClaimStatus,
  ClaimSummary,
  Page,
  ProblemDetail,
  Provider,
  SubmitClaimRequest,
  TokenResponse,
} from "./types";

const BASE = "/api/v1";
export const CORRELATION_HEADER = "X-Correlation-Id";

/** An HTTP error from the API, carrying the parsed RFC 7807 body so the UI can show useful detail. */
export class ApiError extends Error {
  readonly problem: ProblemDetail;
  /** Quote this to support: it finds the request in claims-service and fraud-service logs. */
  readonly correlationId?: string;

  constructor(problem: ProblemDetail, correlationId?: string) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`);
    this.name = "ApiError";
    this.problem = problem;
    this.correlationId = problem.correlationId ?? correlationId;
  }

  get status(): number {
    return this.problem.status;
  }
}

async function request<T>(path: string, init?: RequestInit & { anonymous?: boolean }): Promise<T> {
  const correlationId = crypto.randomUUID();
  const session = init?.anonymous ? null : getSession();
  const headers: Record<string, string> = { Accept: "application/json", [CORRELATION_HEADER]: correlationId };
  if (init?.body) headers["Content-Type"] = "application/json";
  if (session) headers.Authorization = `Bearer ${session.token}`;

  let response: Response;
  try {
    // Absolute URL against the page origin: identical in the browser, and also works with Node's fetch in tests.
    response = await fetch(new URL(BASE + path, window.location.origin), { ...init, headers });
  } catch {
    throw new ApiError(
      { status: 0, title: "Network error", detail: "Could not reach the claims service." },
      correlationId,
    );
  }
  if (!response.ok) {
    const problem = await readProblem(response);
    if (response.status === 401 && !init?.anonymous) {
      clearSession(); // token expired or revoked: the route guard sends the user to sign in again
    }
    throw new ApiError(problem, response.headers.get(CORRELATION_HEADER) ?? correlationId);
  }
  return (await response.json()) as T;
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    const body = (await response.json()) as Partial<ProblemDetail>;
    return { ...body, status: body.status ?? response.status };
  } catch {
    return { status: response.status, title: response.statusText || "Request failed" };
  }
}

function query(params: ClaimSearch): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  const s = search.toString();
  return s ? `?${s}` : "";
}

export const api = {
  login: (username: string, password: string) =>
    request<TokenResponse>("/auth/token", {
      method: "POST",
      body: JSON.stringify({ username, password }),
      anonymous: true,
    }),
  searchClaims: (params: ClaimSearch) => request<Page<ClaimSummary>>(`/claims${query(params)}`),
  getClaim: (claimNumber: string) => request<Claim>(`/claims/${encodeURIComponent(claimNumber)}`),
  submitClaim: (body: SubmitClaimRequest) =>
    request<Claim>("/claims", { method: "POST", body: JSON.stringify(body) }),
  adjudicate: (claimNumber: string) =>
    request<Claim>(`/claims/${encodeURIComponent(claimNumber)}/adjudicate`, { method: "POST" }),
  changeStatus: (claimNumber: string, status: ClaimStatus, reason?: string) =>
    request<Claim>(`/claims/${encodeURIComponent(claimNumber)}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, reason: reason || undefined }),
    }),
  providers: () => request<Provider[]>("/providers"),
};
