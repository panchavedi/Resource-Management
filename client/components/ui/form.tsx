"use client";

import { ChevronDown, Star } from "lucide-react";
import { useId, type ComponentProps, type ReactNode } from "react";
import { cn, levelName } from "@/lib/format";

const control =
  "w-full rounded-xl bg-surface-2 px-3.5 text-[15px] text-fg ring-1 ring-inset ring-line transition-shadow placeholder:text-fg-3 focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-50 aria-[invalid=true]:ring-bad";

export function Field({ label, hint, error, children, className, htmlFor }: { label: ReactNode; hint?: ReactNode; error?: string; children: ReactNode; className?: string; htmlFor?: string }) {
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <label htmlFor={htmlFor} className="text-[13px] font-medium text-fg-2">
        {label}
      </label>
      {children}
      {error ? (
        <p role="alert" className="text-[12px] text-bad">
          {error}
        </p>
      ) : (
        hint && <p className="text-[12px] text-fg-3">{hint}</p>
      )}
    </div>
  );
}

export function Input({ className, ...rest }: ComponentProps<"input">) {
  return <input className={cn(control, "h-11", className)} {...rest} />;
}

export function Textarea({ className, ...rest }: ComponentProps<"textarea">) {
  return <textarea className={cn(control, "min-h-24 py-2.5 leading-relaxed", className)} {...rest} />;
}

export function Select({ className, children, ...rest }: ComponentProps<"select">) {
  return (
    <div className="relative">
      <select className={cn(control, "h-11 appearance-none pr-9", className)} {...rest}>
        {children}
      </select>
      <ChevronDown aria-hidden className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-fg-3" />
    </div>
  );
}

export function Switch({ checked, onChange, label, description, disabled = false }: { checked: boolean; onChange: (value: boolean) => void; label: ReactNode; description?: ReactNode; disabled?: boolean }) {
  const id = useId();
  return (
    <label htmlFor={id} className={cn("flex items-center justify-between gap-4", disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer")}>
      <span>
        <span className="block text-[14px] font-medium">{label}</span>
        {description && <span className="block text-[12px] text-fg-3">{description}</span>}
      </span>
      <button
        id={id}
        type="button"
        disabled={disabled}
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={cn(
          "relative h-[30px] w-[50px] shrink-0 rounded-full transition-colors duration-200",
          checked ? "bg-good" : "bg-surface-3",
        )}
      >
        <span
          className={cn(
            "absolute top-[2px] left-[2px] size-[26px] rounded-full bg-white shadow-[0_2px_6px_rgba(0,0,0,0.25)] transition-transform duration-200",
            checked && "translate-x-5",
          )}
        />
      </button>
    </label>
  );
}

/** Apple-style segmented control. */
export function Segmented<T extends string | number>({ value, options, onChange, size = "md", label }: { value: T; options: { value: T; label: ReactNode }[]; onChange: (value: T) => void; size?: "sm" | "md"; label?: string }) {
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex rounded-full bg-surface-3/70 p-0.5">
      {options.map((o) => (
        <button
          key={String(o.value)}
          type="button"
          role="radio"
          aria-checked={o.value === value}
          onClick={() => onChange(o.value)}
          className={cn(
            "rounded-full font-medium transition-all duration-200",
            size === "sm" ? "h-7 px-3 text-[12px]" : "h-8 px-4 text-[13px]",
            o.value === value ? "bg-surface text-fg shadow-[0_1px_4px_rgba(0,0,0,0.12)]" : "text-fg-2 hover:text-fg",
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

/** Pick a proficiency level 1–5. */
export function LevelPicker({ value, onChange, label = "Level" }: { value: number; onChange: (value: number) => void; label?: string }) {
  return (
    <div role="radiogroup" aria-label={label} className="flex gap-1">
      {[1, 2, 3, 4, 5].map((level) => (
        <button
          key={level}
          type="button"
          role="radio"
          aria-checked={value === level}
          title={levelName(level)}
          onClick={() => onChange(level)}
          className={cn("flex h-9 w-9 items-center justify-center rounded-lg transition-colors", level <= value ? "text-amber-500" : "text-fg-3 ring-1 ring-inset ring-line hover:text-amber-500")}
        >
          <Star className={cn("size-5", level <= value && "fill-amber-400")} />
        </button>
      ))}
    </div>
  );
}

/** Pill filter chip that toggles. */
export function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "h-8 rounded-full px-3.5 text-[13px] font-medium transition-colors",
        active ? "bg-fg text-bg" : "bg-surface text-fg-2 ring-1 ring-inset ring-line hover:text-fg",
      )}
    >
      {children}
    </button>
  );
}
