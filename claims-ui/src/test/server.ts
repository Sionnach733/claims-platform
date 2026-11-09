import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { makeClaim, members, pageOf, providers, summaryOf } from "./fixtures";

/** Default happy-path API. Individual tests override handlers with server.use(...). */
export const handlers = [
  http.post("/api/v1/auth/token", async ({ request }) => {
    const { username, password } = (await request.json()) as { username: string; password: string };
    if (password !== `${username}-pass`) {
      return problem(401, { title: "Unauthorized", detail: "Invalid username or password" });
    }
    return HttpResponse.json({
      accessToken: `token-for-${username}`,
      tokenType: "Bearer",
      expiresIn: 3600,
      username,
      roles: [username.toUpperCase()],
    });
  }),
  http.get("/api/v1/members", () => HttpResponse.json(members)),
  http.get("/api/v1/providers", () => HttpResponse.json(providers)),
  http.get("/api/v1/claims", () => HttpResponse.json(pageOf([summaryOf(makeClaim())]))),
  http.get("/api/v1/claims/:claimNumber", ({ params }) =>
    HttpResponse.json(makeClaim({ claimNumber: String(params.claimNumber) })),
  ),
];

export const server = setupServer(...handlers);

export const problem = (status: number, body: Record<string, unknown>) =>
  HttpResponse.json({ status, ...body }, { status, headers: { "Content-Type": "application/problem+json" } });
