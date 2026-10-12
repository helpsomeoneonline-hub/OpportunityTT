create extension if not exists pgcrypto;

create table if not exists sources (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  source_type text not null,
  base_url text,
  created_at timestamptz not null default now()
);

create table if not exists employers (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  verified boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists jobs (
  id uuid primary key default gen_random_uuid(),
  source_id uuid references sources(id),
  employer_id uuid references employers(id),
  external_id text,
  title text not null,
  normalized_job_family text,
  primary_category text,
  subcategory text,
  employer_name_raw text,
  description text,
  region text,
  locality text,
  employment_type text,
  salary_min numeric,
  salary_max numeric,
  salary_period text,
  application_url text,
  contact_email text,
  contact_phone text,
  published_at timestamptz,
  closes_at timestamptz,
  source_reference text,
  extraction_confidence numeric check (extraction_confidence between 0 and 1),
  review_status text not null default 'pending',
  duplicate_status text not null default 'unique',
  fingerprint text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists jobs_family_idx on jobs(normalized_job_family);
create index if not exists jobs_region_idx on jobs(region);
create index if not exists jobs_fingerprint_idx on jobs(fingerprint);

create table if not exists job_skills (
  job_id uuid not null references jobs(id) on delete cascade,
  skill text not null,
  required boolean not null default false,
  primary key (job_id, skill)
);

create table if not exists job_requirements (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references jobs(id) on delete cascade,
  requirement_type text not null,
  value text not null,
  mandatory boolean not null default false
);

create table if not exists candidate_profiles (
  id uuid primary key default gen_random_uuid(),
  display_name text,
  region text,
  locality text,
  years_experience numeric not null default 0,
  available boolean not null default true,
  searchable_by_employers boolean not null default false,
  photo_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists candidate_skills (
  candidate_id uuid not null references candidate_profiles(id) on delete cascade,
  skill text not null,
  years_experience numeric,
  primary key (candidate_id, skill)
);

create table if not exists candidate_qualifications (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null references candidate_profiles(id) on delete cascade,
  qualification_type text not null,
  name text not null,
  issuer text,
  completed boolean not null default true
);

create table if not exists candidate_preferences (
  candidate_id uuid primary key references candidate_profiles(id) on delete cascade,
  preferred_categories text[] not null default '{}',
  preferred_job_families text[] not null default '{}',
  preferred_regions text[] not null default '{}',
  preferred_employment_types text[] not null default '{}',
  salary_min numeric,
  alerts_enabled boolean not null default true
);

create table if not exists matches (
  id uuid primary key default gen_random_uuid(),
  candidate_id uuid not null references candidate_profiles(id) on delete cascade,
  job_id uuid not null references jobs(id) on delete cascade,
  match_label text not null,
  score integer,
  eligible boolean not null,
  reasons jsonb not null default '[]'::jsonb,
  gaps jsonb not null default '[]'::jsonb,
  calculated_at timestamptz not null default now(),
  unique(candidate_id, job_id)
);

create table if not exists saved_jobs (
  candidate_id uuid not null references candidate_profiles(id) on delete cascade,
  job_id uuid not null references jobs(id) on delete cascade,
  saved_at timestamptz not null default now(),
  primary key (candidate_id, job_id)
);

create table if not exists notification_preferences (
  candidate_id uuid primary key references candidate_profiles(id) on delete cascade,
  minimum_match_label text not null default 'Good Match',
  push_enabled boolean not null default true,
  email_enabled boolean not null default false,
  quiet_hours_start time,
  quiet_hours_end time
);
