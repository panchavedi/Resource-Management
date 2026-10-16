"use client";

import { useSyncExternalStore } from "react";
import { http } from "./api/client";
import type { AuthResponse, Role } from "./api/types";
import { hasRole, sessionStore } from "./session";

/** Current session: `undefined` while hydrating, `null` when signed out. */
export function useSession() {
  return useSyncExternalStore(sessionStore.subscribe, sessionStore.getSnapshot, sessionStore.getServerSnapshot);
}

export function useCan(...roles: Role[]) {
  return hasRole(useSession(), ...roles);
}

export async function signIn(username: string, password: string) {
  const auth = await http.post<AuthResponse>("/api/auth/login", { username, password });
  sessionStore.set(auth);
  return auth;
}

export function signOut() {
  sessionStore.clear();
}
