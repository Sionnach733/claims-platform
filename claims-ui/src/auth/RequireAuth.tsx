import { Navigate, Outlet, useLocation } from "react-router";
import { useSession } from "./session";

/** Route guard: signed-out users go to /login and come back to where they were heading. */
export function RequireAuth() {
  const session = useSession();
  const location = useLocation();
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <Outlet />;
}
