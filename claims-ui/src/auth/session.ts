import { useSyncExternalStore } from "react";

export type Role = "SUBMITTER" | "ADJUDICATOR";

export interface Session {
  token: string;
  username: string;
  roles: Role[];
  expiresAt: number; // epoch millis
}

/**
 * The signed-in session. Kept in sessionStorage (per tab, gone when the tab closes) so a page refresh doesn't
 * log the user out.
 *
 * Trade-off: any script on the page can read sessionStorage, so XSS would expose the token. That's acceptable
 * for this demo with short-lived tokens and no third-party scripts. In production use an OIDC provider with a
 * backend-for-frontend holding tokens in an HttpOnly cookie.
 */
const KEY = "claims.session";
const listeners = new Set<() => void>();
let current: Session | null = load();

function load(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    const parsed = raw ? (JSON.parse(raw) as Session) : null;
    return parsed && parsed.expiresAt > Date.now() ? parsed : null;
  } catch {
    return null;
  }
}

function emit() {
  listeners.forEach((l) => l());
}

export function getSession(): Session | null {
  if (current && current.expiresAt <= Date.now()) {
    clearSession();
  }
  return current;
}

export function setSession(session: Session): void {
  current = session;
  try {
    sessionStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    /* storage unavailable (private mode): keep the in-memory session */
  }
  emit();
}

export function clearSession(): void {
  if (current === null) return;
  current = null;
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
  emit();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useSession(): Session | null {
  return useSyncExternalStore(subscribe, getSession, getSession);
}

export const hasRole = (session: Session | null, role: Role) => !!session?.roles.includes(role);
