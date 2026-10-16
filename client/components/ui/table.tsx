import type { ReactNode } from "react";
import { cn } from "@/lib/format";

export interface Column<T> {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  align?: "left" | "right";
  className?: string;
}

/** Plain, accessible data table with tabular figures for numeric columns. */
export function DataTable<T>({ columns, rows, rowKey, empty = "No rows" }: { columns: Column<T>[]; rows: T[]; rowKey: (row: T) => string | number; empty?: string }) {
  if (rows.length === 0) return <p className="px-6 py-10 text-center text-[14px] text-fg-3">{empty}</p>;
  return (
    <div className="scrollbar-thin overflow-x-auto">
      <table className="w-full text-[14px]">
        <thead>
          <tr className="border-b border-line text-[12px] text-fg-3">
            {columns.map((c) => (
              <th key={c.key} scope="col" className={cn("px-4 py-3 font-medium whitespace-nowrap first:pl-6 last:pr-6", c.align === "right" ? "text-right" : "text-left")}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)} className="border-b border-line last:border-0 hover:bg-surface-2/60">
              {columns.map((c) => (
                <td key={c.key} className={cn("px-4 py-3 first:pl-6 last:pr-6", c.align === "right" && "text-right tabular", c.className)}>
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
