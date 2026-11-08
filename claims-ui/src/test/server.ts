import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { makeClaim, pageOf, providers, summaryOf } from "./fixtures";

/** Default happy-path API. Individual tests override handlers with server.use(...). */
export const handlers = [
  http.get("/api/v1/providers", () => HttpResponse.json(providers)),
  http.get("/api/v1/claims", () => HttpResponse.json(pageOf([summaryOf(makeClaim())]))),
  http.get("/api/v1/claims/:claimNumber", ({ params }) =>
    HttpResponse.json(makeClaim({ claimNumber: String(params.claimNumber) })),
  ),
];

export const server = setupServer(...handlers);

export const problem = (status: number, body: Record<string, unknown>) =>
  HttpResponse.json({ status, ...body }, { status, headers: { "Content-Type": "application/problem+json" } });
