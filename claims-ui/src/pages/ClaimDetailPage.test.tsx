import { screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import type { Claim } from "../api/types";
import { makeClaim } from "../test/fixtures";
import { renderApp } from "../test/render";
import { problem, server } from "../test/server";

const URL = "/claims/CLM-2026-00000001";

function serveClaim(claim: Claim) {
  server.use(http.get("/api/v1/claims/:n", () => HttpResponse.json(claim)));
}

describe("ClaimDetailPage", () => {
  it("shows the claim summary, lines and history", async () => {
    renderApp(URL);

    expect(await screen.findByRole("heading", { name: /CLM-2026-00000001/ })).toBeInTheDocument();
    expect(screen.getByText("Not scored")).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "99213" })).toBeInTheDocument();
    expect(
      within(screen.getByRole("list", { name: "Status history" })).getByText("Claim submitted"),
    ).toBeVisible();
  });

  it("adjudicates a submitted claim and shows the outcome", async () => {
    const approved = makeClaim({
      status: "APPROVED",
      totalAllowed: 100.4,
      riskScore: 12,
      history: [
        ...makeClaim().history,
        {
          fromStatus: "SUBMITTED",
          toStatus: "APPROVED",
          reason: "Auto-approved: all 5 rules passed; priced at 80% of billed",
          changedBy: "system:adjudication-engine",
          changedAt: "2026-11-08T10:01:00Z",
        },
      ],
    });
    server.use(http.post("/api/v1/claims/:n/adjudicate", () => HttpResponse.json(approved)));
    const { user } = renderApp(URL);

    await user.click(await screen.findByRole("button", { name: "Adjudicate" }));

    expect(await screen.findByText(/Auto-approved: all 5 rules passed/)).toBeInTheDocument();
    expect(screen.getByText("12 · LOW")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mark as paid ($100.40)" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Adjudicate" })).not.toBeInTheDocument();
  });

  it("requires review notes before a claim in review can be denied", async () => {
    serveClaim(makeClaim({ status: "IN_REVIEW", riskScore: 88 }));
    let body: unknown;
    server.use(
      http.patch("/api/v1/claims/:n/status", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(makeClaim({ status: "DENIED", denialReason: "Upcoded visit" }));
      }),
    );
    const { user } = renderApp(URL);

    const deny = await screen.findByRole("button", { name: "Deny" });
    expect(deny).toBeDisabled();
    expect(screen.getByText("88 · HIGH")).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Review notes/), "Upcoded visit");
    await user.click(deny);

    expect(await screen.findByText("Upcoded visit")).toBeInTheDocument();
    expect(body).toEqual({ status: "DENIED", reason: "Upcoded visit" });
    expect(screen.getByText(/No further actions/)).toBeInTheDocument();
  });

  it("explains a rejected transition (409) with the allowed moves", async () => {
    serveClaim(makeClaim({ status: "APPROVED", totalAllowed: 100 }));
    server.use(
      http.patch("/api/v1/claims/:n/status", () =>
        problem(409, {
          title: "Invalid status transition",
          detail: "Cannot move a claim from PAID to PAID",
          currentStatus: "PAID",
          allowedTransitions: [],
        }),
      ),
    );
    const { user } = renderApp(URL);

    await user.click(await screen.findByRole("button", { name: /Mark as paid/ }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Cannot move a claim from PAID to PAID");
    expect(alert).toHaveTextContent("Allowed from PAID: none (final status)");
  });

  it("does not offer adjudication actions to a submitter", async () => {
    renderApp(URL, { as: "submitter" });

    expect(await screen.findByText("Adjudication actions require the Adjudicator role.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Adjudicate" })).not.toBeInTheDocument();
  });

  it("shows a not-found message for an unknown claim", async () => {
    server.use(
      http.get("/api/v1/claims/:n", () =>
        problem(404, { title: "Resource not found", detail: "Claim 'CLM-NOPE' was not found" }),
      ),
    );
    renderApp("/claims/CLM-NOPE");

    expect(await screen.findByText("Claim 'CLM-NOPE' was not found")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to claims" })).toBeInTheDocument();
  });

  it("shows a network error when the API is unreachable", async () => {
    server.use(http.get("/api/v1/claims/:n", () => HttpResponse.error()));
    renderApp(URL);

    expect(await screen.findByText("Could not reach the claims service.")).toBeInTheDocument();
  });
});
