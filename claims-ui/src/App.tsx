import { QueryClient } from "@tanstack/react-query";
import type { RouteObject } from "react-router";
import { Link } from "react-router";
import { ApiError } from "./api/client";
import { RequireAuth } from "./auth/RequireAuth";
import { Layout } from "./components/Layout";
import { ClaimDetailPage } from "./pages/ClaimDetailPage";
import { ClaimsListPage } from "./pages/ClaimsListPage";
import { LoginPage } from "./pages/LoginPage";
import { SubmitClaimPage } from "./pages/SubmitClaimPage";

function NotFound() {
  return (
    <section>
      <h1>Page not found</h1>
      <Link to="/">Back to claims</Link>
    </section>
  );
}

export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { path: "login", element: <LoginPage /> },
      {
        element: <RequireAuth />,
        children: [
          { index: true, element: <ClaimsListPage /> },
          { path: "claims/new", element: <SubmitClaimPage /> },
          { path: "claims/:claimNumber", element: <ClaimDetailPage /> },
          { path: "*", element: <NotFound /> },
        ],
      },
    ],
  },
];

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 10_000,
        // Retry network blips and 5xx, never 4xx: a 404 or 400 won't fix itself.
        retry: (failureCount, error) =>
          failureCount < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
    },
  });
}
