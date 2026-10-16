import type { Metadata } from "next";
import { CompareView } from "./compare-view";

export const metadata: Metadata = { title: "Compare candidates" };

/** Client-rendered behind authentication, so navigations are not validated as instant. */
export const instant = false;

export default function Page() {
  return <CompareView />;
}
