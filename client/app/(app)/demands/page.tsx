import type { Metadata } from "next";
import { DemandsView } from "./demands-view";

export const metadata: Metadata = { title: "Demands" };

/** Client-rendered behind authentication, so navigations are not validated as instant. */
export const instant = false;

export default function Page() {
  return <DemandsView />;
}
