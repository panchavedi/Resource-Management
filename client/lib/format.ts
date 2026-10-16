import type {
  AllocationStatus,
  AvailabilityStatus,
  DemandPriority,
  DemandStatus,
  EmploymentStatus,
  EvidenceStatus,
  LocationFit,
} from "./api/types";

export function cn(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(" ");
}

const dateFmt = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });
const shortDateFmt = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const dateTimeFmt = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

/** ISO dates (yyyy-MM-dd) are parsed as local calendar dates, never shifted by time zone. */
function parse(value: string): Date {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
}

export const formatDate = (value: string | null | undefined) => (value ? dateFmt.format(parse(value)) : "—");
export const formatShortDate = (value: string | null | undefined) => (value ? shortDateFmt.format(parse(value)) : "—");
export const formatDateTime = (value: string | null | undefined) => (value ? dateTimeFmt.format(parse(value)) : "—");
export const formatRange = (start: string, end: string) => `${formatShortDate(start)} – ${formatDate(end)}`;

export function formatScore(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

export function relativeTime(value: string): string {
  const diff = (parse(value).getTime() - Date.now()) / 1000;
  const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
  const abs = Math.abs(diff);
  if (abs < 60) return rtf.format(Math.round(diff), "second");
  if (abs < 3600) return rtf.format(Math.round(diff / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), "hour");
  return rtf.format(Math.round(diff / 86400), "day");
}

/** Today in local time as yyyy-MM-dd. */
export function isoToday(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join("");
}

export type Tone = "neutral" | "accent" | "good" | "warn" | "bad" | "ai";

export const demandStatusMeta: Record<DemandStatus, { label: string; tone: Tone }> = {
  OPEN: { label: "Open", tone: "accent" },
  PARTIALLY_FILLED: { label: "Partially filled", tone: "warn" },
  FILLED: { label: "Filled", tone: "good" },
  CANCELLED: { label: "Cancelled", tone: "neutral" },
};

export const priorityMeta: Record<DemandPriority, { label: string; tone: Tone; rank: number }> = {
  CRITICAL: { label: "Critical", tone: "bad", rank: 1 },
  HIGH: { label: "High", tone: "warn", rank: 2 },
  MEDIUM: { label: "Medium", tone: "accent", rank: 3 },
  LOW: { label: "Low", tone: "neutral", rank: 4 },
};

export const allocationStatusMeta: Record<AllocationStatus, { label: string; tone: Tone }> = {
  PENDING_APPROVAL: { label: "Pending approval", tone: "warn" },
  APPROVED: { label: "Approved", tone: "good" },
  REJECTED: { label: "Rejected", tone: "bad" },
  CANCELLED: { label: "Cancelled", tone: "neutral" },
};

export const employmentMeta: Record<EmploymentStatus, { label: string; tone: Tone }> = {
  ACTIVE: { label: "Active", tone: "good" },
  BENCH: { label: "Bench", tone: "accent" },
  ON_LEAVE: { label: "On leave", tone: "warn" },
  INACTIVE: { label: "Inactive", tone: "neutral" },
};

export const availabilityMeta: Record<AvailabilityStatus, { label: string; tone: Tone }> = {
  FULL: { label: "Fully available", tone: "good" },
  PARTIAL: { label: "Partially available", tone: "warn" },
  NONE: { label: "Unavailable", tone: "bad" },
};

export const evidenceMeta: Record<EvidenceStatus, { label: string; tone: Tone; hint: string }> = {
  VALIDATED: { label: "Validated", tone: "good", hint: "Validated within the freshness window" },
  STALE: { label: "Stale", tone: "warn", hint: "Validated, but longer ago than the freshness window" },
  UNVALIDATED: { label: "Unvalidated", tone: "neutral", hint: "Never validated (e.g. self-declared)" },
  MISSING: { label: "No record", tone: "bad", hint: "No stored proficiency for this skill" },
};

export const locationFitMeta: Record<LocationFit, string> = {
  ANY_LOCATION: "Any location",
  SAME_LOCATION: "Same location",
  REMOTE: "Remote",
  DIFFERENT_LOCATION_REMOTE_ALLOWED: "Different city · remote allowed",
  DIFFERENT_LOCATION: "Different city · onsite",
  UNKNOWN: "Location unknown",
};

export function humanize(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, " ");
}

const LEVEL_NAMES = ["", "Awareness", "Beginner", "Practitioner", "Advanced", "Expert"];

export function levelName(level: number): string {
  return LEVEL_NAMES[level] ?? String(level);
}
