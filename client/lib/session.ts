import type { AuthResponse, Role, UserInfo } from "./api/types";

/**
 * Authenticated session kept in sessionStorage (cleared when the tab closes) and exposed through a
 * tiny subscribe/getSnapshot store so React can read it with useSyncExternalStore.
 */
export interface Session {
  token: string;
  expiresAt: string;
  user: UserInfo;
}

const KEY = "tm.session";
const listeners = new Set<() => void>();
let cached: Session | null | undefined;

function read(): Session | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.sessionStorage.getItem(KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    return new Date(session.expiresAt).getTime() > Date.now() ? session : null;
  } catch {
    return null;
  }
}

export const sessionStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  getSnapshot(): Session | null {
    if (cached === undefined) cached = read();
    return cached;
  },
  getServerSnapshot(): Session | null | undefined {
    return undefined;
  },
  set(auth: AuthResponse) {
    const session: Session = { token: auth.accessToken, expiresAt: auth.expiresAt, user: auth.user };
    try {
      window.sessionStorage.setItem(KEY, JSON.stringify(session));
    } catch {
      /* storage unavailable: keep the session in memory only */
    }
    cached = session;
    listeners.forEach((l) => l());
  },
  clear() {
    try {
      window.sessionStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
    cached = null;
    listeners.forEach((l) => l());
  },
};

export function hasRole(session: Session | null | undefined, ...roles: Role[]): boolean {
  if (!session) return false;
  return session.user.roles.some((r) => r === "ADMIN" || roles.includes(r));
}
