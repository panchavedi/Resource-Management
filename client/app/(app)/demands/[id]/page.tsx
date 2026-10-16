import type { Metadata } from "next";
import { DemandDetailView } from "./demand-detail-view";

export const metadata: Metadata = { title: "Demand" };

/** Client-rendered behind authentication, so navigations are not validated as instant. */
export const instant = false;

export default function Page() {
  return <DemandDetailView />;
}
