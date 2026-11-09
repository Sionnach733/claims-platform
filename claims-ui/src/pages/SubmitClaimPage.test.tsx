import { screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import type { SubmitClaimRequest } from "../api/types";
import { makeClaim } from "../test/fixtures";
import { renderApp } from "../test/render";
import { problem, server } from "../test/server";

async function fillValidClaim(user: ReturnType<typeof renderApp>["user"]) {
  await user.type(screen.getByLabelText("Member ID"), "AET100000001");
  await user.type(screen.getByLabelText("Provider NPI"), "1234567893");
  await user.type(screen.getByLabelText("Date of service"), "2026-11-01");
  await user.selectOptions(screen.getByLabelText("CPT code (line 1)"), "99213");
  await user.selectOptions(screen.getByLabelText("Diagnosis (line 1)"), "J45.909");
  await user.type(screen.getByLabelText("Billed amount (line 1)"), "125.50");
}

describe("SubmitClaimPage", () => {
  it("offers grouped CPT and ICD-10 codes and explains the selected one", async () => {
    const { user } = renderApp("/claims/new");

    const cpt = screen.getByLabelText("CPT code (line 1)");
    expect(within(cpt).getByRole("group", { name: "Musculoskeletal" })).toBeInTheDocument();
    await user.selectOptions(cpt, "27447");
    expect(screen.getByText("Total knee replacement", { selector: ".hint" })).toBeInTheDocument();
    expect(cpt).toHaveAccessibleDescription("Total knee replacement");

    await user.selectOptions(screen.getByLabelText("Diagnosis (line 1)"), "M17.11");
    expect(screen.getByText("Osteoarthritis of right knee", { selector: ".hint" })).toBeInTheDocument();
  });

  it("validates on the client before calling the API", async () => {
    let called = false;
    server.use(
      http.post("/api/v1/claims", () => {
        called = true;
        return HttpResponse.json({});
      }),
    );
    const { user } = renderApp("/claims/new");

    await user.type(screen.getByLabelText("Provider NPI"), "1234567890");
    await user.click(screen.getByRole("button", { name: "Submit claim" }));

    expect(await screen.findByText("Not a valid 10-digit NPI")).toBeInTheDocument();
    expect(screen.getByText("Member ID looks like AET123456789")).toBeInTheDocument();
    expect(screen.getByText("Choose a procedure")).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it("submits a multi-line claim, shows the running total, then opens the new claim", async () => {
    let sent: SubmitClaimRequest | undefined;
    server.use(
      http.post("/api/v1/claims", async ({ request }) => {
        sent = (await request.json()) as SubmitClaimRequest;
        return HttpResponse.json(makeClaim({ claimNumber: "CLM-2026-00000042" }), { status: 201 });
      }),
    );
    const { user, router } = renderApp("/claims/new");

    await fillValidClaim(user);
    await user.click(screen.getByRole("button", { name: "Add line" }));
    await user.selectOptions(screen.getByLabelText("CPT code (line 2)"), "94640");
    await user.selectOptions(screen.getByLabelText("Diagnosis (line 2)"), "J45.909");
    await user.clear(screen.getByLabelText("Units (line 2)"));
    await user.type(screen.getByLabelText("Units (line 2)"), "2");
    await user.type(screen.getByLabelText("Billed amount (line 2)"), "40");

    expect(screen.getByTestId("total-billed")).toHaveTextContent("$165.50");

    await user.click(screen.getByRole("button", { name: "Submit claim" }));

    expect(await screen.findByRole("heading", { name: /CLM-2026-00000042/ })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/claims/CLM-2026-00000042");
    expect(sent).toEqual({
      memberId: "AET100000001",
      providerNpi: "1234567893",
      serviceDate: "2026-11-01",
      lines: [
        { procedureCode: "99213", diagnosisCode: "J45.909", units: 1, billedAmount: 125.5 },
        { procedureCode: "94640", diagnosisCode: "J45.909", units: 2, billedAmount: 40 },
      ],
    });
  });

  it("shows server-side business errors (422) without losing the form", async () => {
    server.use(
      http.post("/api/v1/claims", () =>
        problem(422, {
          title: "Business rule violated",
          detail: "Member 'AET100000001' does not exist",
          code: "MEMBER_NOT_FOUND",
        }),
      ),
    );
    const { user } = renderApp("/claims/new");

    await fillValidClaim(user);
    await user.click(screen.getByRole("button", { name: "Submit claim" }));

    expect(await screen.findByText("Member 'AET100000001' does not exist")).toBeInTheDocument();
    expect(screen.getByLabelText("Member ID")).toHaveValue("AET100000001");
  });

  it("maps server field errors onto the matching input", async () => {
    server.use(
      http.post("/api/v1/claims", () =>
        problem(400, {
          title: "Validation failed",
          errors: [{ field: "lines[0].procedureCode", message: "must be a valid CPT code, e.g. 99213" }],
        }),
      ),
    );
    const { user } = renderApp("/claims/new");

    await fillValidClaim(user);
    await user.click(screen.getByRole("button", { name: "Submit claim" }));

    expect(await screen.findAllByText(/must be a valid CPT code/)).toHaveLength(2); // inline + summary
    expect(screen.getByLabelText("CPT code (line 1)").closest(".field")).toHaveClass("has-error");
  });

  it("can't remove the only line", async () => {
    renderApp("/claims/new");

    expect(await screen.findByRole("button", { name: "Remove line 1" })).toBeDisabled();
  });
});
