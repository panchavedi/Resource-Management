import type { Metadata } from "next";
import { PeopleView } from "./people-view";

export const metadata: Metadata = { title: "Skills matrix" };

/** Client-rendered behind authentication, so navigations are not validated as instant. */
export const instant = false;

export default function Page() {
  return <PeopleView />;
}
