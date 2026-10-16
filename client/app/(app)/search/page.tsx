import type { Metadata } from "next";
import { SearchView } from "./search-view";

export const metadata: Metadata = { title: "Find talent" };

/** Client-rendered behind authentication, so navigations are not validated as instant. */
export const instant = false;

export default function Page() {
  return <SearchView />;
}
