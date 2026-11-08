import { screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { makeClaim, pageOf, summaryOf } from "../test/fixtures";
import { renderApp } from "../test/render";
import { problem, server } from "../test/server";

describe("ClaimsListPage", () => {
  it("lists claims with status and formatted amounts", async () => {
    renderApp("/");

    const row = (await screen.findByRole("link", { name: "CLM-2026-00000001" })).closest("tr")!;
    expect(within(row).getByText("Submitted")).toBeInTheDocument();
    expect(within(row).getByText("$125.50")).toBeInTheDocument();
    expect(within(row).getByText("Nov 1, 2026")).toBeInTheDocument();
  });

  it("sends filters to the API and keeps them in the URL", async () => {
    const requests: URLSearchParams[] = [];
    server.use(
      http.get("/api/v1/claims", ({ request }) => {
        requests.push(new URL(request.url).searchParams);
        return HttpResponse.json(pageOf([]));
      }),
    );
    const { user, router } = renderApp("/");

    await user.type(screen.getByLabelText("Member ID"), "aet100000005");
    await user.selectOptions(screen.getByLabelText("Status"), "IN_REVIEW");
    await user.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByText("No claims match these filters.")).toBeInTheDocument();
    const last = requests.at(-1)!;
    expect(last.get("memberId")).toBe("AET100000005");
    expect(last.get("status")).toBe("IN_REVIEW");
    expect(router.state.location.search).toBe("?memberId=AET100000005&status=IN_REVIEW");
  });

  it("pages through results", async () => {
    server.use(
      http.get("/api/v1/claims", ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page") ?? 0);
        const claim = makeClaim({ claimNumber: `CLM-2026-0000000${page + 1}` });
        return HttpResponse.json(pageOf([summaryOf(claim)], page, 10, 12));
      }),
    );
    const { user } = renderApp("/");

    expect(await screen.findByText(/Page 1 of 2/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByRole("link", { name: "CLM-2026-00000002" })).toBeInTheDocument();
    expect(screen.getByText(/Page 2 of 2/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("shows the server's problem detail when the search fails", async () => {
    server.use(
      http.get("/api/v1/claims", () =>
        problem(400, { title: "Invalid request", detail: "'from' must be on or before 'to'" }),
      ),
    );
    renderApp("/?from=2026-05-01&to=2026-04-01");

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Couldn't load claims");
    expect(alert).toHaveTextContent("'from' must be on or before 'to'");
  });
});
