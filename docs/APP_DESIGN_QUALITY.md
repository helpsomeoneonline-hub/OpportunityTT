# App design & quality standards

This repository uses an evidence-informed design-and-engineering review for every user-facing change. These are review guidelines, not claims that an external design service is connected.

## Decision process
1. Read the existing product scope and real code. Identify the primary user task, current flow, constraints, and likely failure cases.
2. Challenge the requested change. Compare the existing solution against at least one simpler alternative. Reject unnecessary features or extra taps and explain the trade-off.
3. Use official Android Material 3 and Core App Quality guidance first for Android behaviour; use Mobbin for comparable real-world screen flows **only when access is connected**; use Figma files only when the user authorizes them. Context7 can help verify library API versions if available, but is not a UI evidence source.
4. Keep the app's existing architecture and visual identity unless a redesign or migration is requested. Define spacing, typography, color roles, component states, and navigation consistently.
5. Implement one coherent user flow at a time, with error/empty/loading/success states, rather than decorative mockups with dead controls.

## Non-negotiable usability checks
- The main task is recognizable immediately; key action is easy to find; secondary settings remain discoverable.
- No buttons or input fields are hidden behind system status/navigation bars, display cutouts, the on-screen keyboard, or tablet window edges. Support edge-to-edge window insets, keyboard resizing, landscape, font scaling, and long text.
- Use touch targets of approximately 48 dp or larger where feasible, sufficient text contrast, meaningful labels, logical reading/focus order, and no information expressed through color alone.
- Preserve user data, settings, login state, deep-link handling, app identity, signing strategy, and update compatibility. Never trade correct function for appearance.
- Avoid unnecessary permissions, fake loading indicators, destructive changes without confirmation, hardcoded secrets, and unbounded background work.
- Provide usable progress, cancellation, retry, network/offline, and permission-denied states as relevant.
- Performance: avoid main-thread blocking, excessive animations, duplicated work, and unnecessary image/media storage. Prefer placeholders, caching, and measured optimization.

## Implementation and proof
- Before changes: state the user problem, expected behavior, impacted files, and regression risks.
- After changes: run available build/lint/tests; add focused tests for changed behavior. Check narrow/wide phones, tablet/landscape, keyboard, system navigation, font scaling, loading/empty/error states and offline handling.
- Do not claim something was tested if no emulator/device or test run was available; report verification gaps plainly.
- Keep changes minimal and reversible. Respect project README, security, privacy, existing APIs, and user requirements.
- Use the PR checklist in `.github/pull_request_template.md`.

## Design evidence / references
- Android Material 3: https://m3.material.io/
- Android Core App Quality: https://developer.android.com/docs/quality-guidelines/core-app-quality
- Adaptive layouts: https://developer.android.com/develop/ui/compose/layouts/adaptive
- Android accessibility: https://developer.android.com/guide/topics/ui/accessibility
- Nielsen Norman Group usability heuristics: https://www.nngroup.com/articles/ten-usability-heuristics/
- Mobbin design examples: https://mobbin.com/ (requires appropriate access; do not assume connected)
- Figma: https://www.figma.com/ (editable designs are separate from implementation)
- Context7: https://context7.com/ (developer documentation, not a UX database)

## Project-specific priorities: OpportunityTT
- Keep the initial product job-seeker-first; do not build employer marketplace, in-app chat, or paid subscriptions without an explicit scope decision.
- Explain why a job matches a candidate and visibly distinguish hard disqualifiers from softer preferences.
- Candidate photos must remain optional; job-seeker privacy and employer visibility are opt-in.
- Allow clear corrections to profile information and saved jobs; show source/age of each listing, duplicate status, and uncertain/low-confidence fields.
- Follow the existing `android/`, `backend/`, `ingestion/`, and `database/` boundaries. Never commit personal candidate information or credentials.
