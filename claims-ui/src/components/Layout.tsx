import { NavLink, Outlet } from "react-router";
import { clearSession, useSession } from "../auth/session";

export function Layout() {
  const session = useSession();
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="app-header">
        <div className="container header-inner">
          <span className="brand">Claims Workbench</span>
          {session && (
            <nav aria-label="Main">
              <NavLink to="/" end>
                Claims
              </NavLink>
              <NavLink to="/claims/new">Submit claim</NavLink>
            </nav>
          )}
          {session && (
            <div className="user">
              <span>
                {session.username} <span className="muted">({session.roles.join(", ").toLowerCase()})</span>
              </span>
              <button type="button" className="button link" onClick={clearSession}>
                Sign out
              </button>
            </div>
          )}
        </div>
      </header>
      <main id="main" className="container">
        <Outlet />
      </main>
    </>
  );
}
