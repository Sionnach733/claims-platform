import { NavLink, Outlet } from "react-router";

export function Layout() {
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="app-header">
        <div className="container header-inner">
          <span className="brand">Claims Workbench</span>
          <nav aria-label="Main">
            <NavLink to="/" end>
              Claims
            </NavLink>
            <NavLink to="/claims/new">Submit claim</NavLink>
          </nav>
        </div>
      </header>
      <main id="main" className="container">
        <Outlet />
      </main>
    </>
  );
}
