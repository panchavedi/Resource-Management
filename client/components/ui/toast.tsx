"use client";

import { CheckCircle2, AlertCircle } from "lucide-react";
import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import { cn } from "@/lib/format";

interface Toast {
  id: number;
  tone: "good" | "bad";
  title: string;
  description?: string;
}

const ToastContext = createContext<(toast: Omit<Toast, "id">) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((toast: Omit<Toast, "id">) => {
    const id = Date.now() + Math.random();
    setToasts((current) => [...current.slice(-2), { ...toast, id }]);
    setTimeout(() => setToasts((current) => current.filter((t) => t.id !== id)), 5000);
  }, []);

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-6 z-[60] flex flex-col items-center gap-2 px-4">
        {toasts.map((t) => (
          <div
            key={t.id}
            className="glass pointer-events-auto flex w-full max-w-md animate-fade-up items-start gap-3 rounded-2xl px-4 py-3 shadow-[var(--shadow-lg)] ring-1 ring-line"
          >
            {t.tone === "good" ? (
              <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-good" />
            ) : (
              <AlertCircle className="mt-0.5 size-5 shrink-0 text-bad" />
            )}
            <div className="min-w-0">
              <div className={cn("text-[14px] font-semibold")}>{t.title}</div>
              {t.description && <div className="text-[13px] text-fg-2">{t.description}</div>}
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
