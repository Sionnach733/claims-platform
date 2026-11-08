import { expect, test, type Page } from "@playwright/test";

// Seeded members with active coverage (see V2__seed_reference_data.sql).
const ACTIVE_MEMBERS = ["AET100000001", "AET100000002", "AET100000003", "AET100000005"];
const IN_NETWORK_NPI = "1234567893";

/**
 * Random member + date of service, so repeated runs against the same database don't trip the
 * duplicate-claim rule (same member, provider, date and CPT).
 */
function uniqueClaimKey() {
  const member = ACTIVE_MEMBERS[Math.floor(Math.random() * ACTIVE_MEMBERS.length)]!;
  const date = new Date();
  date.setUTCDate(date.getUTCDate() - (1 + Math.floor(Math.random() * 300)));
  return { member, serviceDate: date.toISOString().slice(0, 10) };
}

async function submitClaim(
  page: Page,
  line: { cpt: string; icd: string; units: string; amount: string },
): Promise<{ claimNumber: string; member: string }> {
  const { member, serviceDate } = uniqueClaimKey();
  await page.goto("/claims/new");
  await page.getByLabel("Member ID").fill(member);
  await page.getByLabel("Provider NPI").fill(IN_NETWORK_NPI);
  await page.getByLabel("Date of service").fill(serviceDate);
  await page.getByLabel("CPT code (line 1)").fill(line.cpt);
  await page.getByLabel("Diagnosis (line 1)").fill(line.icd);
  await page.getByLabel("Units (line 1)").fill(line.units);
  await page.getByLabel("Billed amount (line 1)").fill(line.amount);
  await page.getByRole("button", { name: "Submit claim" }).click();

  await expect(page).toHaveURL(/\/claims\/CLM-\d{4}-\d{8}$/);
  const claimNumber = page.url().split("/").pop()!;
  await expect(page.getByRole("heading", { name: new RegExp(claimNumber) })).toContainText("Submitted");
  return { claimNumber, member };
}

const heading = (page: Page) => page.getByRole("heading", { level: 1 });

async function signIn(page: Page, username: "adjudicator" | "submitter") {
  await page.goto("/login");
  await page.getByLabel("Username").fill(username);
  await page.getByLabel("Password").fill(`${username}-pass`);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible();
}

test("a routine claim is submitted, auto-approved at the in-network rate, and paid", async ({ page }) => {
  await signIn(page, "adjudicator");
  const { claimNumber, member } = await submitClaim(page, {
    cpt: "99213",
    icd: "J45.909",
    units: "1",
    amount: "125.50",
  });

  await page.getByRole("button", { name: "Adjudicate" }).click();

  await expect(heading(page)).toContainText("Approved");
  await expect(page.getByText(/Auto-approved: all \d+ rules passed; priced at 80% of billed/)).toBeVisible();
  // Scored by the real fraud-service. The exact score depends on recent activity already in the database
  // (velocity signals), so assert the outcome, not the number: below the review threshold.
  await expect(page.getByText(/^\d+ · (LOW|MEDIUM)$/)).toBeVisible();

  await page.getByRole("button", { name: "Mark as paid ($100.40)" }).click();

  await expect(heading(page)).toContainText("Paid");
  await expect(page.getByText("No further actions.", { exact: false })).toBeVisible();

  // The claim appears in the list when filtering by its member.
  await page.getByRole("link", { name: "All claims" }).click();
  await page.getByLabel("Member ID").fill(member);
  await page.getByLabel("Status").selectOption("PAID");
  await page.getByRole("button", { name: "Search" }).click();
  await expect(page.getByRole("link", { name: claimNumber })).toBeVisible();
});

test("a suspicious claim is routed to manual review by the fraud score, then denied with notes", async ({
  page,
}) => {
  await signIn(page, "adjudicator");
  // Knee replacement billed with an asthma diagnosis and 12 units: fraud-service scores this at least 60
  // (higher if the member/provider have recent activity), at or above the review threshold of 50.
  await submitClaim(page, { cpt: "27447", icd: "J45.909", units: "12", amount: "4900.00" });

  await page.getByRole("button", { name: "Adjudicate" }).click();

  await expect(heading(page)).toContainText("In review");
  await expect(page.getByText(/Fraud risk score \d+ \((MEDIUM|HIGH)\) at or above 50/)).toBeVisible();
  await expect(page.getByText("PROCEDURE_DIAGNOSIS_MISMATCH", { exact: false })).toBeVisible();

  const deny = page.getByRole("button", { name: "Deny" });
  await expect(deny).toBeDisabled();
  await page.getByLabel(/Review notes/).fill("Diagnosis does not support the procedure");
  await deny.click();

  await expect(heading(page)).toContainText("Denied");
  await expect(page.getByRole("status")).toContainText("Denied: Diagnosis does not support the procedure");
});

test("invalid input is caught in the browser before reaching the API", async ({ page }) => {
  await signIn(page, "submitter");
  await page.goto("/claims/new");
  await page.getByLabel("Provider NPI").fill("1234567890");
  await page.getByRole("button", { name: "Submit claim" }).click();

  await expect(page.getByText("Not a valid 10-digit NPI")).toBeVisible();
  await expect(page).toHaveURL(/\/claims\/new$/);
});

test("a submitter can submit but not adjudicate, and the API enforces it too", async ({ page, request }) => {
  await signIn(page, "submitter");
  const { claimNumber } = await submitClaim(page, { cpt: "99213", icd: "I10", units: "1", amount: "90.00" });

  await expect(page.getByText("Adjudication actions require the Adjudicator role.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Adjudicate" })).toHaveCount(0);

  // Hidden buttons are UX, not security: call the API directly with the submitter's token.
  const token = await page.evaluate(
    () => JSON.parse(sessionStorage.getItem("claims.session")!).token as string,
  );
  const res = await request.post(`/api/v1/claims/${claimNumber}/adjudicate`, {
    headers: { Authorization: `Bearer ${token}`, "X-Correlation-Id": "e2e-forbidden-check-01" },
  });
  expect(res.status()).toBe(403);
  expect(res.headers()["x-correlation-id"]).toBe("e2e-forbidden-check-01");
  expect((await res.json()).title).toBe("Forbidden");
});

test("signed-out users are sent to sign in", async ({ page }) => {
  await page.goto("/claims/new");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
});
