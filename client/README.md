# Talent — web client

Next.js 16 front end for the Talent resource-management platform. See the [root README](../README.md) for
architecture, setup and the full feature tour.

```bash
cp .env.example .env.local   # NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
npm install
npm run dev                  # http://localhost:3000
```

Checks: `npx tsc --noEmit`, `npm run lint`, `npm run build`.

- `app/(app)/*/page.tsx` — thin server pages (metadata, `instant = false`) rendering client `*-view.tsx` screens
- `lib/api` — typed DTOs, problem-aware fetch client, React Query hooks
- `components/ui` — design system (tokens for light and dark in `app/globals.css`)
- `components/domain` — staffing visuals: score ring and breakdown, availability timeline, level bars, candidate cards
