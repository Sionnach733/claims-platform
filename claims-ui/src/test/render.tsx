import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../App";
import { setSession, type Role } from "../auth/session";

export const TEST_TOKEN = "test-token";

/**
 * Renders the real app routes at `path`, with a fresh query cache and no retries.
 * Signed in as an adjudicator unless `as` says otherwise (`null` = signed out).
 */
export function renderApp(
  path: string,
  { as = "adjudicator" }: { as?: "adjudicator" | "submitter" | null } = {},
) {
  if (as) {
    const roles: Role[] = [as === "adjudicator" ? "ADJUDICATOR" : "SUBMITTER"];
    setSession({ token: TEST_TOKEN, username: as, roles, expiresAt: Date.now() + 3_600_000 });
  }
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const user = userEvent.setup();
  const view = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { ...view, user, router, queryClient };
}
