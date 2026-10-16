"use client";

import { KeyRound, UserRoundCog } from "lucide-react";
import { PageHeader } from "@/components/layout/app-shell";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader } from "@/components/ui/card";
import { ErrorState, Notice, Skeleton } from "@/components/ui/feedback";
import { Switch } from "@/components/ui/form";
import { useManagedAccounts, usePendingRegistrations, useReviewRegistration, useSetManagerAccess } from "@/lib/api/hooks";
import { useToast } from "@/components/ui/toast";
import { humanize } from "@/lib/format";
import { useCan } from "@/lib/auth";
import { Button } from "@/components/ui/button";

export function AdminAccessView() {
  const isAdmin = useCan("ADMIN");
  const canReview = useCan("ADMIN", "APPROVER");
  const accounts = useManagedAccounts(isAdmin);
  const registrations = usePendingRegistrations(canReview);
  const review = useReviewRegistration();
  const setAccess = useSetManagerAccess();
  const toast = useToast();

  if (!canReview) return <Notice tone="bad" title="Approver access required">Account requests can only be reviewed by an approver or administrator.</Notice>;

  return <>
    <PageHeader eyebrow="Accounts" title="Access requests" subtitle="Review new manager account requests and manage configured access." />
    <Card>
      <CardHeader title="Manager account requests" subtitle="Approved accounts can sign in and manage staffing. Rejected requests cannot sign in." />
      {registrations.isPending ? <div className="space-y-3 px-6 pb-6"><Skeleton className="h-20" /></div> : registrations.isError ? <div className="px-6 pb-6"><ErrorState error={registrations.error} onRetry={() => registrations.refetch()} /></div> : registrations.data?.length ? (
        <ul className="divide-y divide-line px-3 pb-3">
          {registrations.data.map((request) => <li key={request.username} className="flex flex-wrap items-center justify-between gap-4 px-3 py-4">
            <div><div className="font-semibold">{request.displayName}</div><div className="text-[13px] text-fg-3">@{request.username} · Requested {new Date(request.requestedAt).toLocaleDateString()}</div></div>
            <div className="flex gap-2"><Button variant="secondary" disabled={review.isPending} onClick={() => review.mutate({ username: request.username, decision: "reject" }, { onSuccess: () => toast({ tone: "good", title: `Request for ${request.username} rejected` }) })}>Reject</Button><Button disabled={review.isPending} onClick={() => review.mutate({ username: request.username, decision: "approve" }, { onSuccess: () => toast({ tone: "good", title: `${request.displayName} approved`, description: "They can now sign in as a resource manager." }) })}>Approve manager</Button></div>
          </li>)}
        </ul>
      ) : <p className="px-6 pb-6 text-[14px] text-fg-3">There are no account requests waiting for review.</p>}
      {review.isError && <div className="px-6 pb-5"><Notice tone="bad" title="Could not review account request">{review.error.message}</Notice></div>}
    </Card>
    {isAdmin && <>
    <Notice tone="info" className="mt-6" title="Access changes apply on next sign-in">An active session keeps its current token until it expires or the user signs out. Account credentials are managed through server environment configuration.</Notice>
    <Card className="mt-6">
      <CardHeader title="Configured accounts" subtitle="Only accounts configured by the deployment operator appear here." />
      {accounts.isPending ? <div className="space-y-3 px-6 pb-6"><Skeleton className="h-20" /><Skeleton className="h-20" /></div> : accounts.isError ? <div className="px-6 pb-6"><ErrorState error={accounts.error} onRetry={() => accounts.refetch()} /></div> : (
        <ul className="divide-y divide-line px-3 pb-3">
          {accounts.data?.map((account) => {
            const fixedManager = account.managerAccessFixed;
            const canChange = !account.managerAccessFixed;
            const pending = setAccess.isPending && setAccess.variables?.username === account.username;
            return <li key={account.username} className="flex flex-wrap items-center justify-between gap-4 rounded-xl px-3 py-4">
              <div className="flex min-w-0 items-center gap-3">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-accent"><UserRoundCog className="size-5" /></span>
                <div className="min-w-0"><div className="font-semibold">{account.displayName}</div><div className="text-[13px] text-fg-3">{account.username}</div><div className="mt-1 flex flex-wrap gap-1">{account.roles.map((role) => <Badge key={role} tone={role === "ADMIN" ? "warn" : "neutral"}>{humanize(role)}</Badge>)}</div></div>
              </div>
              <Switch checked={account.managerAccess} onChange={(enabled) => setAccess.mutate({ username: account.username, enabled }, { onSuccess: (result) => toast({ tone: "good", title: `Manager access ${result.managerAccess ? "granted" : "revoked"}`, description: `${result.displayName} must sign in again for the change to take effect.` }) })} label="Resource manager" description={fixedManager ? "Configured as a manager by the operator" : "Can manage demands, people and staffing proposals"} disabled={!canChange || pending} />
            </li>;
          })}
        </ul>
      )}
      {setAccess.isError && <div className="px-6 pb-5"><Notice tone="bad" title="Could not update access">{setAccess.error.message}</Notice></div>}
    </Card>
    <Card className="mt-5 p-5">
      <div className="flex gap-3"><KeyRound className="mt-0.5 size-5 text-accent" /><div><h2 className="font-semibold">Account provisioning</h2><p className="mt-1 text-[13px] text-fg-2">The deployment operator controls account names, display names and passwords in the server environment. This page grants or revokes manager access for those accounts; it never creates passwords or exposes credentials.</p></div></div>
    </Card>
    </>}
  </>;
}
