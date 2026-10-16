"use client";

import { Monitor, Moon, Sun } from "lucide-react";
import { useTheme, type ThemePreference } from "@/lib/theme";
import { cn } from "@/lib/format";

const options: { value: ThemePreference; label: string; Icon: typeof Sun }[] = [
  { value: "light", label: "Light", Icon: Sun },
  { value: "dark", label: "Dark", Icon: Moon },
  { value: "system", label: "System", Icon: Monitor },
];

export function ThemeToggle() {
  const { preference, setPreference } = useTheme();
  return (
    <div role="radiogroup" aria-label="Appearance" className="inline-flex rounded-full bg-surface-3/70 p-0.5">
      {options.map(({ value, label, Icon }) => (
        <button
          key={value}
          role="radio"
          aria-checked={preference === value}
          aria-label={`${label} appearance`}
          title={label}
          onClick={() => setPreference(value)}
          className={cn(
            "flex size-7 items-center justify-center rounded-full transition-all",
            preference === value ? "bg-surface text-fg shadow-[0_1px_4px_rgba(0,0,0,0.15)]" : "text-fg-3 hover:text-fg",
          )}
        >
          <Icon className="size-3.5" strokeWidth={2} />
        </button>
      ))}
    </div>
  );
}
