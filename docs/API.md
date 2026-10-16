# API reference

Base URL `http://localhost:8080`. Interactive OpenAPI docs: **http://localhost:8080/swagger-ui.html** (click *Authorize* and paste a token).

All endpoints except `POST /api/auth/login` and `/actuator/health` require `Authorization: Bearer <token>`.
Reads need any authenticated role; writes are role-restricted as listed below.

| Role | Can |
|---|---|
| `RESOURCE_MANAGER` | create demands, employees, skills; record skills; propose / withdraw allocations; run the AI agent |
| `APPROVER` | approve or reject pending allocations; cancel allocations |
| `ADMIN` | everything (still subject to the four-eyes rule) |

## Endpoints

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | public | Exchange username/password for a JWT (HS256, 8 h) |
| POST | `/api/auth/register` | public | Submit a manager account request; the new account stays pending until an approver/admin acts |
| GET | `/api/auth/me` | any | Current user from the token |
| GET | `/api/auth/accounts` | admin | List deployment-configured accounts and effective manager access |
| PUT | `/api/auth/accounts/{username}/manager-access` | admin | Grant or revoke manager access for a configured account; the account takes effect on next login |
| GET | `/api/auth/registrations` | approver, admin | List pending manager account requests |
| PUT | `/api/auth/registrations/{username}/approve` · `/reject` | approver, admin | Approve a manager account or reject the request |
| GET | `/api/skills` · `/api/skills/categories` | any | Skill taxonomy |
| POST | `/api/skills` | manager | Add a skill |
| POST | `/api/demands` | manager | Create a demand (role, valid dates, allocation % mandatory) |
| GET | `/api/demands?status=&priority=&skillId=&q=&from=&to=&page=&size=&sort=` | any | Filtered, paged demand list |
| GET | `/api/demands/{id}` | any | Demand with staffing counts |
| POST | `/api/demands/{id}/recommendations` | any | Ranked + excluded candidates with full score breakdown |
| PUT | `/api/demands/{id}/cancel` | manager | Cancel the demand and release its allocations |
| POST | `/api/employees` | manager | Create an employee |
| GET | `/api/employees?q=&department=&location=&status=&skillId=&minLevel=` | any | Paged employee search |
| GET | `/api/employees/{id}` · `/allocations` · `/availability?startDate=&endDate=&allocationPercent=` | any | Profile, allocation history, day-accurate availability |
| GET | `/api/employees/skills-matrix?q=&department=&category=` · `/facets` | any | Skills matrix and filter values |
| PATCH | `/api/employees/{id}/status` | manager | Change employment status |
| POST | `/api/employees/{id}/skills` | manager | Record or re-assess a skill (201 created / 200 updated) |
| DELETE | `/api/employees/{id}/skills/{skillId}` | manager | Remove a skill record |
| GET | `/api/resources/available?startDate=&endDate=&skillIds=&minProficiency=&location=&department=&allocationPercent=&includeUnavailable=` | any | Resource search ranked by availability coverage |
| POST | `/api/allocations` (+ optional `Idempotency-Key` header) | manager | Propose an allocation (`PENDING_APPROVAL`) |
| GET | `/api/allocations?status=&employeeId=&demandId=` · `/{id}` | any | List / detail with full history |
| PUT | `/api/allocations/{id}/approve` · `/reject` | approver | Decide; approval re-validates every rule |
| PUT | `/api/allocations/{id}/cancel` | manager, approver | Cancel (releases capacity, keeps history) |
| GET/POST/PATCH | `/api/training-recommendations` | any / manager | Learning actions |
| GET | `/api/reports/dashboard` · `utilization` · `open-demands` · `skill-proficiency` · `available-resources` · `unstaffable-demands` · `skill-gaps` | any | SQL reports |
| POST | `/api/ai/staffing-advice` | manager | Run the staffing agent |
| GET | `/api/ai/status` · `/api/ai/runs?demandId=` · `/api/ai/runs/{id}` | any | AI mode, audit trail, exact stored response |

## Error format

Every error is an RFC 9457 problem document with a stable `code`. Validation errors list fields in `errors`;
business-rule errors carry structured `details`.

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Missing/invalid fields, unknown sort property, bad query parameter |
| 400 | `MALFORMED_REQUEST` | Unparseable JSON, unknown enum value, non-ISO date |
| 401 | `UNAUTHENTICATED` / `INVALID_CREDENTIALS` | No/invalid token; wrong password |
| 403 | `ACCESS_DENIED` | Role not allowed |
| 404 | `RESOURCE_NOT_FOUND` | Unknown id or endpoint |
| 409 | `DUPLICATE_RESOURCE` / `ALLOCATION_ALREADY_EXISTS` / `CONCURRENT_MODIFICATION` | Unique key clash, second active allocation, stale update |
| 422 | `CAPACITY_EXCEEDED` | Requested % does not fit on every day of the period |
| 422 | `CANDIDATE_NOT_ELIGIBLE` | Status not assignable or mandatory skill missing/below level |
| 422 | `HEADCOUNT_FILLED` / `DEMAND_NOT_OPEN` / `INVALID_DATE_RANGE` / `INVALID_STATE_TRANSITION` | Workflow rules |
| 422 | `SELF_APPROVAL_NOT_ALLOWED` | Four-eyes rule |
| 429 | `ACCOUNT_TEMPORARILY_LOCKED` | 5 failed logins → 5 min lock |
| 503 | `AI_UNAVAILABLE` | `AI_MODE=LIVE` and the model is unreachable |

## Examples (captured from the running application)

### Sign in
```http
POST /api/auth/login
Content-Type: application/json

{ "username": "manager", "password": "<from .env>" }
```
```json
200 OK
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9…", "tokenType": "Bearer", "expiresAt": "2026-10-15T21:33:07Z",
  "user": { "username": "manager", "displayName": "Riya Menon (Resource Manager)", "roles": ["RESOURCE_MANAGER"] } }
```

### Create a demand
```http
POST /api/demands
Authorization: Bearer <token>
Content-Type: application/json

{
  "projectName": "Atlas Payments Platform",
  "role": "Senior Java Backend Engineer",
  "startDate": "2026-10-22",
  "endDate": "2027-02-12",
  "location": "Bengaluru",
  "remoteAllowed": true,
  "allocationPercent": 100,
  "headcount": 2,
  "priority": "CRITICAL",
  "requiredSkills": [
    { "skillId": 1, "minimumProficiency": 4, "mandatory": true },
    { "skillId": 12, "minimumProficiency": 3, "mandatory": false }
  ]
}
```
`201 Created` with `Location: /api/demands/{id}` and the demand body (shape as in *Get a demand*).

Missing role and an inverted date range:
```json
400 Bad Request
{
  "type": "urn:talent:error:validation-failed", "title": "Bad Request", "status": 400,
  "detail": "One or more fields are invalid.", "instance": "/api/demands",
  "code": "VALIDATION_FAILED", "timestamp": "2026-10-15T14:14:14.134Z",
  "errors": [
    { "field": "endDate", "message": "must be on or after startDate" },
    { "field": "role", "message": "must not be blank" }
  ]
}
```

### Get a demand
```json
200 OK
{
  "id": 2, "projectName": "Nimbus Customer Portal", "role": "Frontend Engineer",
  "description": "Customer self-service portal on Next.js.",
  "startDate": "2026-10-29", "endDate": "2027-01-23", "durationDays": 87,
  "location": "Pune", "remoteAllowed": true, "allocationPercent": 100, "headcount": 1,
  "priority": "HIGH", "status": "OPEN", "approvedCount": 0, "pendingCount": 1,
  "requiredSkills": [
    { "skillId": 7, "skillName": "React", "category": "Frontend", "minimumProficiency": 4, "mandatory": true },
    { "skillId": 22, "skillName": "UX Design", "category": "Design", "minimumProficiency": 2, "mandatory": false }
  ],
  "createdBy": "manager", "createdAt": "2026-10-15T13:15:15Z", "updatedAt": "2026-10-15T13:15:15Z"
}
```

### Rank candidates (`POST /api/demands/2/recommendations`, abbreviated)
```json
200 OK
{
  "criteria": {
    "mandatorySkillPolicy": "EXCLUDE",
    "weights": { "skill": 0.5, "availability": 0.3, "location": 0.15, "evidence": 0.05 },
    "evidenceFreshnessMonths": 18, "capacityLimitPercent": 100,
    "rules": ["Employment status must be ACTIVE or BENCH", "…"]
  },
  "candidatePoolSize": 5, "notConsideredCount": 17,
  "rankedCandidates": [{
    "employeeId": 4, "name": "Sofia Martins", "rank": 1, "eligible": true, "totalScore": 87.1,
    "scoreBreakdown": [
      { "factor": "SKILL_FIT", "weight": 0.5, "score": 82.5, "weightedScore": 41.3,
        "explanation": "4 of 4 required skills met; weighted mean of per-skill points (mandatory ×2)" },
      { "factor": "AVAILABILITY", "weight": 0.3, "score": 100.0, "weightedScore": 30.0,
        "explanation": "Available at 100% for all 87 days (2026-10-29 to 2027-01-23); peak existing load 0%." },
      { "factor": "LOCATION", "weight": 0.15, "score": 80.0, "weightedScore": 12.0,
        "explanation": "Based in Hyderabad; demand in Pune allows remote work and employee is remote-eligible" },
      { "factor": "EVIDENCE_QUALITY", "…": "…" }
    ],
    "skillMatches": [{ "skillName": "React", "requiredLevel": 4, "actualLevel": 5, "meetsRequirement": true,
                       "points": 1.0, "evidenceStatus": "VALIDATED", "evidence": "Design-system component library author" }],
    "availability": { "status": "FULL", "availableDays": 87, "totalDays": 87, "coveragePercent": 100.0, "windows": ["…"] },
    "dataGaps": ["UX Design level 2 has never been validated (source: SELF_DECLARED)"],
    "exclusionReasons": []
  }],
  "excludedCandidates": [{ "name": "Hannah Lee", "exclusionReasons": ["Not available: no free capacity on any day …"] }]
}
```

### Day-accurate availability (`GET /api/employees/1/availability?startDate=2026-10-22&endDate=2027-02-12&allocationPercent=100`)
```json
200 OK
{
  "status": "PARTIAL", "capacityPercent": 100, "requestedPercent": 100,
  "peakLoadPercent": 50, "minFreePercent": 50, "totalDays": 114, "availableDays": 100, "coveragePercent": 93.9,
  "windows": [
    { "startDate": "2026-10-22", "endDate": "2026-11-04", "days": 14, "loadPercent": 50, "confirmedLoadPercent": 50, "freePercent": 50, "meetsRequest": false },
    { "startDate": "2026-11-05", "endDate": "2027-02-12", "days": 100, "loadPercent": 0, "confirmedLoadPercent": 0, "freePercent": 100, "meetsRequest": true }
  ],
  "bookings": [{ "allocationId": 7, "projectName": "Phoenix Core Banking", "startDate": "2026-08-16", "endDate": "2026-11-04", "allocationPercent": 50, "status": "APPROVED" }]
}
```

### Propose an allocation
```http
POST /api/allocations
Authorization: Bearer <token>
Idempotency-Key: 6f1c2d9e-3b0a-4c55-9b51-0c3cfe0f8a10
Content-Type: application/json

{ "employeeId": 3, "demandId": 1, "note": "Best full-period fit" }
```
`201 Created` → `{ "id": 8, "status": "PENDING_APPROVAL", "history": [{ "eventType": "REQUESTED", … }], … }`.
Replaying the same `Idempotency-Key` returns `200 OK` with the same allocation.

Capacity conflict:
```json
422 Unprocessable Content
{
  "type": "urn:talent:error:capacity-exceeded", "status": 422, "code": "CAPACITY_EXCEEDED",
  "detail": "Allocating 100% would exceed Divya Krishnan's capacity of 100%: existing load peaks at 100% between 2026-10-22 and 2027-02-12",
  "details": { "capacityPercent": 100, "peakLoadPercent": 100, "requestedPercent": 100, "minFreePercent": 0, "conflictingAllocationIds": [6] }
}
```
Not eligible:
```json
422 Unprocessable Content
{
  "code": "CANDIDATE_NOT_ELIGIBLE",
  "detail": "Daniel Brooks is not eligible for this demand: Missing mandatory skill Python (requires level 4, no stored record); …",
  "details": { "reasons": ["Missing mandatory skill Python (requires level 4, no stored record)", "Missing mandatory skill Machine Learning (requires level 4, no stored record)"] }
}
```

### Approve / cancel
```http
PUT /api/allocations/8/approve        { "note": "Approved - strong fit" }
PUT /api/allocations/8/cancel         { "reason": "Client paused workstream" }
```
`200 OK` with `history`: `REQUESTED → APPROVED → CANCELLED`. The requester approving their own request returns
`422 SELF_APPROVAL_NOT_ALLOWED`; a manager calling approve returns `403 ACCESS_DENIED`.

### Staffing agent
```http
POST /api/ai/staffing-advice
Authorization: Bearer <token>
Content-Type: application/json

{ "demandId": 1, "question": "Assume Meera Iyer is an expert in Kafka and fully available. Is she the best choice?",
  "topN": 3, "recordTrainingRecommendations": false }
```
```json
200 OK (abbreviated, live run on qwen2.5-coder:7b)
{
  "runId": 3, "mode": "LIVE", "model": "qwen2.5-coder:7b",
  "status": "COMPLETED_WITH_FALLBACK", "narrativeSource": "LLM_WITH_REPAIRS",
  "simulationNotice": "PARTIALLY REPAIRED: statements that failed fact-checking were replaced with deterministic text (summary, answer, candidate:1).",
  "answer": "I can only report what is stored; I cannot assume, invent or estimate proficiency or availability. Meera Iyer: no stored Kafka record. …",
  "recommendations": [{ "rank": 1, "candidate": { "name": "Aarav Kulkarni", "totalScore": 92.5, "scoreBreakdown": ["…"] },
                        "explanation": "…", "strengths": ["…"], "considerations": ["…"] }],
  "dataGaps": [{ "employeeName": "Meera Iyer", "subject": "question:skill", "detail": "no stored proficiency for Kafka; it cannot be stated or assumed" }],
  "validation": { "passed": true, "llmAttempts": 1, "repairedFields": ["summary", "answer", "candidate:1"],
    "issues": [
      { "attempt": 1, "field": "summary", "code": "UNRECORDED_SKILL", "message": "Meera Iyer is credited with Kafka, which is not stored for them" },
      { "attempt": 1, "field": "candidate", "code": "AVAILABILITY_OVERCLAIM", "message": "Aarav Kulkarni is described as fully available but computed availability is PARTIAL" }
    ] },
  "toolCalls": [
    { "sequence": 1, "tool": "get_demand", "summary": "Atlas Payments Platform / Senior Java Backend Engineer, 100% …", "success": true, "durationMs": 19 },
    { "sequence": 2, "tool": "rank_eligible_candidates", "summary": "Evaluated 8 employee(s): 3 ranked, 5 excluded, …" },
    { "sequence": 9, "tool": "llm_narrate", "durationMs": 91068 },
    { "sequence": 10, "tool": "validate_narrative", "success": false },
    { "sequence": 11, "tool": "repair_narrative" },
    { "sequence": 12, "tool": "validate_narrative", "success": true }
  ],
  "guardrails": ["Eligibility, scores and availability are computed by deterministic backend rules, never by the language model.", "…"]
}
```

### AI status
```json
200 OK
{ "configuredMode": "AUTO", "effectiveMode": "LIVE", "model": "qwen2.5-coder:7b", "baseUrl": "http://localhost:11434",
  "reachable": true, "modelInstalled": true, "detail": "Model qwen2.5-coder:7b is available" }
```
