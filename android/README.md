# OpportunityTT Android MVP — v0.2.0

Native Kotlin + Jetpack Compose Android application. Works offline, with no account, network permission, or backend dependency.

## Included
- Job seeker profile: skills, qualifications, licences, experience, preferred category and region
- Jobs feed with search and eligible-only filter
- Explainable skill/experience/location match scores and hard requirement gating
- Saved jobs persisted between app sessions
- Employer draft job creation and deletion on the same device
- Candidate match preview with one opt-in local profile only — no fictional candidates or public directory
- **97 employment positions extracted from the 17 October 2026 Trinidad Express** (pp. 22–24); source page, published date, contacts, needs-review flags and unverified vacancy status are visible. No fictional starter jobs.
- Local storage using SharedPreferences JSON

## Limitations
- Published vacancies are actual newspaper advertisements; **live openings, contact identity and applicant safety are not independently verified**.
- Posted jobs and candidate profiles do **not** synchronize between devices.
- Employer candidate directory, actual applications, messages, notifications, authentication and live listings are not implemented.
- This first build is an offline product prototype, not a live recruiting marketplace.

## Build
From `android/` run `gradle assembleDebug` with Gradle 8.9, Java 17, Android SDK platform 35.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
On the repository's GitHub Actions workflow, a successful build also publishes the APK to a prerelease called `v0.2.0`.

## Update and signing note
This debug APK is for testing. CI debug signing credentials can vary from build to build. A durable production keystore kept in a secure secret store will be required before in-place upgrade compatibility can be guaranteed. Preserve `tt.opportunity.app` as the package ID and increment `versionCode` on later releases.
