# Ingestion

The ingestion service converts source material into structured job records.

## Pipeline

1. Acquire an approved source.
2. Detect possible vacancy blocks.
3. Extract factual fields.
4. Normalize title, job family, category and location.
5. Generate a duplicate fingerprint.
6. Assign extraction confidence.
7. Send low-confidence or suspicious listings to human review.
8. Publish only approved, non-duplicate records.

## Important rule

Do not commit raw newspaper PDFs, personal applicant data, API keys or private source files to this repository.

Initial source experiments may use the Trinidad Express classifieds, but the production system should not assume that a free-to-read source automatically grants bulk republication rights.
