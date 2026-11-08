import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { api } from "../api/client";
import { setSession, useSession, type Role } from "../auth/session";
import { ProblemAlert } from "../components/ProblemAlert";

export function LoginPage() {
  const session = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  if (session) return <Navigate to={from} replace />;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    try {
      const token = await api.login(String(form.get("username")), String(form.get("password")));
      setSession({
        token: token.accessToken,
        username: token.username,
        roles: token.roles as Role[],
        expiresAt: Date.now() + token.expiresIn * 1000,
      });
      navigate(from, { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="login">
      <h1>Sign in</h1>
      <form onSubmit={onSubmit} aria-label="Sign in">
        <label>
          Username
          <input name="username" autoComplete="username" required />
        </label>
        <label>
          Password
          <input name="password" type="password" autoComplete="current-password" required />
        </label>
        <ProblemAlert error={error} title="Sign-in failed" />
        <button type="submit" className="button primary" disabled={submitting}>
          {submitting ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <p className="muted">
        Demo users: <code>submitter</code> / <code>submitter-pass</code> and <code>adjudicator</code> /{" "}
        <code>adjudicator-pass</code>
      </p>
    </section>
  );
}
