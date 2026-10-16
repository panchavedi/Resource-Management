import type { Metadata } from "next";
import { NewDemandView } from "./new-demand-view";

export const metadata: Metadata = { title: "New demand" };

/** Client-rendered behind authentication, so navigations are not validated as instant. */
export const instant = false;

export default function Page() {
  return <NewDemandView />;
}
