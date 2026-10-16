import type { Metadata } from "next";
import { EmployeeView } from "./employee-view";

export const metadata: Metadata = { title: "Employee" };

/** Client-rendered behind authentication, so navigations are not validated as instant. */
export const instant = false;

export default function Page() {
  return <EmployeeView />;
}
