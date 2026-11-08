import { screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { getSession } from "../auth/session";
import { pageOf } from "../test/fixtures";
import { renderApp, TEST_TOKEN } from "../test/render";
import { problem, server } from "../test/server";

describe("authentication", () => {
  it("sends signed-out users to the sign-in page, then back to where they were going", async () => {
    const { user, router } = renderApp("/claims/new", { as: null });

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Main" })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Username"), "adjudicator");
    await user.type(screen.getByLabelText("Password"), "adjudicator-pass");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("heading", { name: "Submit a claim" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/claims/new");
    expect(getSession()).toMatchObject({ username: "adjudicator", roles: ["ADJUDICATOR"] });
    expect(screen.getByText("adjudicator")).toBeInTheDocument();
  });

  it("shows an error for wrong credentials and stays on the sign-in page", async () => {
    const { user } = renderApp("/login", { as: null });

    await user.type(await screen.findByLabelText("Username"), "adjudicator");
    await user.type(screen.getByLabelText("Password"), "wrong");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("Invalid username or password")).toBeInTheDocument();
    expect(getSession()).toBeNull();
  });

  it("sends the bearer token and a correlation ID on every API call", async () => {
    const seen: Headers[] = [];
    server.use(
      http.get("/api/v1/claims", ({ request }) => {
        seen.push(request.headers);
        return HttpResponse.json(pageOf([]));
      }),
    );
    renderApp("/");

    await screen.findByText("No claims match these filters.");
    expect(seen[0]?.get("Authorization")).toBe(`Bearer ${TEST_TOKEN}`);
    expect(seen[0]?.get("X-Correlation-Id")).toMatch(/^[0-9a-f-]{36}$/);
  });

  it("signs the user out when the API says the token is no longer valid (401)", async () => {
    server.use(http.get("/api/v1/claims", () => problem(401, { title: "Unauthorized" })));
    const { router } = renderApp("/");

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(getSession()).toBeNull();
  });

  it("signs out from the header", async () => {
    const { user } = renderApp("/");

    await user.click(await screen.findByRole("button", { name: "Sign out" }));

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
  });

  it("shows the request reference on errors so support can find it in the logs", async () => {
    server.use(
      http.get("/api/v1/claims", () =>
        problem(500, { title: "Internal server error", correlationId: "abc12345-reference" }),
      ),
    );
    renderApp("/");

    expect(await screen.findByText("abc12345-reference")).toBeInTheDocument();
  });
});
