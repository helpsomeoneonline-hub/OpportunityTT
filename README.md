# OpportunityTT

OpportunityTT is a Trinidad & Tobago opportunity discovery and job-matching platform.

## MVP goal

Prove one end-to-end flow:

**source document/feed → clean job record → candidate profile → relevant match → notification-ready result**

The first release is intentionally focused on job seekers. Employer candidate-search and direct recruitment are planned for a later stage, after there is enough active candidate demand.

## MVP scope

### Included
- Import jobs from approved/public sources
- Normalize job titles, categories, locations, requirements and contacts
- Detect likely duplicate listings
- Candidate profile with skills, qualifications, licences, experience, location and job preferences
- Rules-based job matching with clear reasons
- Saved jobs
- Notification preferences
- Human review for low-confidence extracted listings

### Not in the first release
- Employer candidate marketplace
- In-app chat
- Interview scheduling
- Candidate ratings
- AI-only matching
- Mandatory candidate photos
- Paid subscriptions

## Repository layout

- `android/` — Android client
- `backend/` — API and matching services
- `ingestion/` — source extraction, normalization and deduplication
- `database/` — PostgreSQL/Supabase schema and migrations
- `admin/` — future review/publishing dashboard
- `docs/` — product and technical decisions
- `sample-data/` — synthetic test data only

## Product principles

1. The app must be useful before employers join.
2. Matching must explain *why* a job fits.
3. Hard requirements must not be hidden by a high score.
4. Candidate privacy is opt-in for employer visibility.
5. Real applicant data, secrets and API keys must never be committed.
6. Newspaper/source content should be stored as structured facts, not wholesale copied layouts.

## Milestone 1

1. Store a batch of cleaned jobs in PostgreSQL.
2. Create a synthetic candidate profile.
3. Match that candidate against the jobs.
4. Return ranked matches with reasons and disqualifiers.
5. Prepare matching results for future push notifications.

