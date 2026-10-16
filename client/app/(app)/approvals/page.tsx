import type { Metadata } from "next";
import { ApprovalsView } from "./approvals-view";

export const metadata: Metadata = { title: "Approvals" };

/** Client-rendered behind authentication, so navigations are not validated as instant. */
export const instant = false;

export default function Page() {
  return <ApprovalsView />;
}
