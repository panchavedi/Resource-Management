"use client";

import { LogOut, Menu, X } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { signOut, useCan, useSession } from "@/lib/auth";
import { cn, humanize, initials } from "@/lib/format";
import { useAllocations } from "@/lib/api/hooks";
import { Spinner } from "@/components/ui/feedback";
import { ThemeToggle } from "./theme-toggle";

const NAV = [
  { href: "/", label: "Overview" },
  { href: "/demands", label: "Demands" },
  { href: "/people", label: "People" },
  { href: "/search", label: "Find talent" },
  { href: "/approvals", label: "Approvals" },
  { href: "/assistant", label: "Assistant" },
  { href: "/reports", label: "Reports" },
];

function isActive(pathname: string, href: string) {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

/** Signed-in chrome: glass navigation bar and a guarded content area. */
export function AppShell({ children }: { children: ReactNode }) {
  const session = useSession();
  const canReviewAccounts = useCan("ADMIN", "APPROVER");
  const router = useRouter();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const pending = useAllocations({ status: ["PENDING_APPROVAL"], size: 1 }, Boolean(session));
  const pendingCount = pending.data?.totalElements ?? 0;

  useEffect(() => {
    if (session === null) router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [session, router, pathname]);

  if (!session) {
    return (
      <div className="flex min-h-screen items-center justify-center text-fg-3">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="min-h-screen">
      <header className="glass sticky top-0 z-40 border-b border-line">
        <nav className="mx-auto flex h-14 max-w-[1240px] items-center gap-6 px-4 sm:px-6" aria-label="Main">
          <Link href="/" className="flex items-center gap-2 font-semibold tracking-tight">
            <span className="ai-gradient flex size-7 items-center justify-center rounded-[9px] text-[13px] font-bold text-white">T</span>
            <span className="text-[16px]">Talent</span>
          </Link>
          <ul className="hidden flex-1 items-center gap-1 lg:flex">
            {[...NAV, ...(canReviewAccounts ? [{ href: "/admin", label: "Access requests" }] : [])].map((item) => (
              <li key={item.href}>
                <NavLink
                  href={item.href}
                  active={isActive(pathname, item.href)}
                  label={item.label}
                  badge={item.href === "/approvals" ? pendingCount : 0}
                />
              </li>
            ))}
          </ul>
          <div className="ml-auto flex items-center gap-3">
            <div className="hidden sm:block">
              <ThemeToggle />
            </div>
            <UserMenu name={session.user.displayName} roles={session.user.roles.map(humanize).join(", ")} />
            <button
              className="flex size-9 items-center justify-center rounded-full hover:bg-surface-3 lg:hidden"
              aria-label={menuOpen ? "Close menu" : "Open menu"}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((v) => !v)}
            >
              {menuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
            </button>
          </div>
        </nav>
        {menuOpen && (
          <div className="border-t border-line px-4 pb-4 lg:hidden">
            <ul className="flex flex-col py-2">
              {[...NAV, ...(canReviewAccounts ? [{ href: "/admin", label: "Access requests" }] : [])].map((item) => (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    onClick={() => setMenuOpen(false)}
                    className={cn(
                      "block rounded-xl px-3 py-3 text-[17px] font-medium",
                      isActive(pathname, item.href) ? "text-fg" : "text-fg-2",
                    )}
                  >
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
            <ThemeToggle />
          </div>
        )}
      </header>
      <main className="mx-auto max-w-[1240px] px-4 pt-8 pb-24 sm:px-6">{children}</main>
    </div>
  );
}

function NavLink({ href, active, label, badge }: { href: string; active: boolean; label: string; badge: number }) {
  return (
    <Link
      href={href}
      aria-current={active ? "page" : undefined}
      className={cn(
        "relative flex h-8 items-center gap-1.5 rounded-full px-3 text-[13px] transition-colors",
        active ? "bg-surface-3 font-medium text-fg" : "text-fg-2 hover:text-fg",
      )}
    >
      {label}
      {badge > 0 && (
        <span aria-label={`${badge} pending`} className="rounded-full bg-warn px-1.5 text-[10px] leading-4 font-bold text-black tabular">
          {badge}
        </span>
      )}
    </Link>
  );
}

function UserMenu({ name, roles }: { name: string; roles: string }) {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  return (
    <div className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-haspopup="menu"
        className="flex size-8 items-center justify-center rounded-full bg-fg text-[12px] font-semibold text-bg"
        title={name}
      >
        {initials(name)}
      </button>
      {open && (
        <>
          <button aria-hidden tabIndex={-1} className="fixed inset-0 z-40 cursor-default" onClick={() => setOpen(false)} />
          <div role="menu" className="card absolute right-0 z-50 mt-2 w-64 animate-fade-up p-2 shadow-[var(--shadow-lg)]">
            <div className="px-3 py-2">
              <div className="text-[14px] font-semibold">{name}</div>
              <div className="text-[12px] text-fg-3">{roles}</div>
            </div>
            <button
              role="menuitem"
              onClick={() => {
                signOut();
                router.replace("/login");
              }}
              className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-[14px] text-fg-2 hover:bg-surface-2 hover:text-fg"
            >
              <LogOut className="size-4" /> Sign out
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/** Large page title block, Apple-store style. */
export function PageHeader({ eyebrow, title, subtitle, actions }: { eyebrow?: string; title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between animate-fade-up">
      <div className="min-w-0">
        {eyebrow && <div className="eyebrow mb-2">{eyebrow}</div>}
        <h1 className="display text-[34px] font-semibold sm:text-[44px]">{title}</h1>
        {subtitle && <p className="mt-2 max-w-2xl text-[17px] text-fg-2">{subtitle}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
