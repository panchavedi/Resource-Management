"use client";

import { ArrowRight, ShieldCheck, Sparkles, Target } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState, type FormEvent } from "react";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Button } from "@/components/ui/button";
import { Notice } from "@/components/ui/feedback";
import { Field, Input } from "@/components/ui/form";
import { ApiError, http } from "@/lib/api/client";
import { signIn, useSession } from "@/lib/auth";

export default function LoginPage() {
  return (
    <div className="relative flex min-h-screen flex-col">
      <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="ai-gradient absolute -top-40 left-1/2 h-[520px] w-[920px] -translate-x-1/2 rounded-full opacity-[0.18] blur-[120px]" />
      </div>
      <header className="relative mx-auto flex w-full max-w-[1100px] items-center justify-between px-6 py-5">
        <div className="flex items-center gap-2 font-semibold">
          <span className="ai-gradient flex size-7 items-center justify-center rounded-[9px] text-[13px] font-bold text-white">T</span>
          Talent
        </div>
        <ThemeToggle />
      </header>
      <main className="relative mx-auto grid w-full max-w-[1100px] flex-1 items-center gap-12 px-6 pb-16 lg:grid-cols-[1.15fr_1fr]">
        <section className="animate-fade-up">
          <div className="eyebrow mb-4">Resource management · AI staffing</div>
          <h1 className="display text-[44px] font-semibold sm:text-[64px]">
            Staffing,
            <br />
            <span className="ai-text">made precise.</span>
          </h1>
          <p className="mt-5 max-w-lg text-[19px] leading-relaxed text-fg-2">
            Match people to project demand by verified skills, day-accurate availability and location — then let an
            evidence-grounded assistant explain every recommendation.
          </p>
          <ul className="mt-8 grid gap-4 sm:grid-cols-3">
            {[
              { Icon: Target, title: "Transparent scoring", text: "Every rank has a breakdown." },
              { Icon: ShieldCheck, title: "Safe allocations", text: "Approval and re-validation at commit." },
              { Icon: Sparkles, title: "Grounded AI", text: "Fact-checked, never invented." },
            ].map(({ Icon, title, text }) => (
              <li key={title} className="text-[14px]">
                <Icon className="mb-2 size-5 text-accent" strokeWidth={1.8} />
                <div className="font-semibold">{title}</div>
                <div className="text-fg-2">{text}</div>
              </li>
            ))}
          </ul>
        </section>
        <Suspense>
          <SignInCard />
        </Suspense>
      </main>
    </div>
  );
}

function SignInCard() {
  const router = useRouter();
  const params = useSearchParams();
  const session = useSession();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [displayName, setDisplayName] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const next = params.get("next");
  const destination = next && next.startsWith("/") && !next.startsWith("//") ? next : "/";

  useEffect(() => {
    if (session) router.replace(destination);
  }, [session, router, destination]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await signIn(username.trim(), password);
      router.replace(destination);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Sign-in failed. Try again.");
    } finally {
      setLoading(false);
    }
  }

  async function submitRegistration(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setNotice(null);
    try {
      const created = await http.post<{ username: string }>("/api/auth/register", {
        displayName: displayName.trim(), username: username.trim(), password,
      });
      setNotice(`Your request for @${created.username} was submitted. An approver or administrator must approve it before you can sign in.`);
      setRegistering(false);
      setPassword("");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Account request failed. Try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="card animate-fade-up p-8 shadow-[var(--shadow-lg)] [animation-delay:120ms]">
      <h2 className="display text-[28px] font-semibold">{registering ? "Request an account" : "Sign in"}</h2>
      <p className="mt-1 text-[15px] text-fg-2">{registering ? "Create a manager account request. Access is enabled after review." : "Sign in with an account configured for this deployment."}</p>
      {notice && <Notice tone="good" className="mt-4" title="Request submitted">{notice}</Notice>}
      <form onSubmit={registering ? submitRegistration : submit} className="mt-6 space-y-4">
        {registering && <Field label="Full name" htmlFor="display-name"><Input id="display-name" autoComplete="name" maxLength={120} value={displayName} onChange={(e) => setDisplayName(e.target.value)} required /></Field>}
        <Field label="Username" htmlFor="username">
          <Input id="username" autoComplete="username" minLength={registering ? 3 : undefined} maxLength={registering ? 40 : 80} pattern={registering ? "[A-Za-z0-9._-]{3,40}" : undefined} value={username} onChange={(e) => setUsername(e.target.value)} required />
        </Field>
        <Field label="Password" htmlFor="password" hint={registering ? "Use at least 10 characters. Your password is stored as a secure hash." : "If you need access, contact your platform administrator."}>
          <Input
            id="password"
            type="password"
            autoComplete={registering ? "new-password" : "current-password"}
            minLength={registering ? 10 : undefined}
            maxLength={200}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </Field>
        {error && <Notice tone="bad" title={error} />}
        <Button type="submit" size="lg" className="w-full" loading={loading}>
          {registering ? "Submit account request" : "Continue"} <ArrowRight className="size-4" />
        </Button>
      </form>
      <button type="button" className="mt-5 w-full text-center text-[14px] font-medium text-accent hover:underline" onClick={() => { setRegistering((value) => !value); setError(null); setNotice(null); }}>
        {registering ? "Already have an account? Sign in" : "New here? Request a manager account"}
      </button>
    </div>
  );
}
