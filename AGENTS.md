# AGENTS.md — OpportunityTT

This repository uses `docs/APP_DESIGN_QUALITY.md` as its app design, UX and engineering review standard. **Read it before making UI, navigation, onboarding, state or data-flow changes.** The file provides concrete acceptance criteria and design references.

- Product: job-seeker-first Trinidad & Tobago job-discovery and matching platform; preserve scope in `README.md`.
- Follow existing architecture: `android/`, `backend/`, `ingestion/`, `database/` and `admin/`.
- Prefer the simplest usable design that correctly solves the task. Critique feature requests rather than agreeing automatically; explain rejected alternatives.
- Check the actual code before declaring a feature present. Never substitute screenshots, mockups or appearance-only edits for working features.
- Compare patterns to Android Material 3 and Core App Quality. Consult Mobbin/Figma/Context7 only when actually available; do not claim they are linked merely because they are listed in documentation.
- Every feature needs working loading, empty, error, permission and recovery states as applicable; protect user data, privacy and app update compatibility.
- After code changes, run relevant available build/tests and say exactly what was verified; do not claim emulator/device testing without it.
- Use `.github/pull_request_template.md` when reviewing changes. Never modify unrelated app flows without authorization.
