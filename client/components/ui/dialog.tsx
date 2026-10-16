"use client";

import { X } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";
import { cn } from "@/lib/format";
import { IconButton } from "./button";

/**
 * Modal dialog on the native <dialog> element: focus trapping, Escape to close and the top layer
 * come from the browser, so it stays accessible without extra libraries.
 */
export function Dialog({ open, onClose, title, description, children, footer, size = "md" }: { open: boolean; onClose: () => void; title: ReactNode; description?: ReactNode; children: ReactNode; footer?: ReactNode; size?: "md" | "lg" }) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
      className={cn(
        "m-auto w-[calc(100%-2rem)] rounded-[24px] bg-surface p-0 text-fg shadow-[var(--shadow-lg)] backdrop:bg-black/40 backdrop:backdrop-blur-sm open:animate-fade-up",
        size === "md" ? "max-w-lg" : "max-w-3xl",
      )}
    >
      {open && (
        <div className="flex max-h-[85vh] flex-col">
          <div className="flex items-start justify-between gap-4 px-6 pt-6 pb-2">
            <div>
              <h2 className="display text-[22px] font-semibold">{title}</h2>
              {description && <p className="mt-1 text-[14px] text-fg-2">{description}</p>}
            </div>
            <IconButton label="Close" onClick={onClose}>
              <X className="size-5" />
            </IconButton>
          </div>
          <div className="scrollbar-thin overflow-y-auto px-6 py-4">{children}</div>
          {footer && <div className="flex justify-end gap-2 border-t border-line px-6 py-4">{footer}</div>}
        </div>
      )}
    </dialog>
  );
}
