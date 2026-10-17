# OpportunityTT Android MVP — v0.3.0

Native Kotlin + Jetpack Compose Android application. Works offline, with no account, network permission, or backend dependency.

## Included
- Job seeker profile: skills, qualifications, licences, experience, preferred category and region
- Redesigned job-seeker home, dedicated search (keyword, regional and industry filters) and full-screen job details
- Explainable skill/experience/location match scores and hard requirement gating
- Saved jobs and application progress (interested/contacted/applied/interview/offer) stored locally
- Employer draft job creation and deletion on the same device
- Candidate privacy: no fictional users or remote applicant directory; employer mode is a transparent private-draft workspace
- **97 employment positions extracted from the 17 October 2026 Trinidad Express** (pp. 22–24); source page, published date, contacts, needs-review flags and unverified vacancy status are visible. No fictional starter jobs.
- Call, email, WhatsApp and share actions where original advertisement supplies a usable contact\n- System document-picker backup and restore of saved jobs, profile and application stages\n- Persistent local storage compatible with v0.2.0 data schema

## Limitations
- Published vacancies are actual newspaper advertisements; **live openings, contact identity and applicant safety are not independently verified**.
- Posted jobs and candidate profiles do **not** synchronize between devices.
- Employer candidate directory, submitting actual applications, online messaging, notifications, authentication and live listings are not implemented. The local tracker does not submit an application.
- This first build is an offline product prototype, not a live recruiting marketplace.

## Build
From `android/` run `gradle assembleDebug` with Gradle 8.9, Java 17, Android SDK platform 35.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
On the repository's GitHub Actions workflow, a successful build also publishes the APK to a prerelease called `v0.3.0`.

## Update and signing note
This debug APK is for testing. CI debug signing credentials can vary from build to build. A durable production keystore kept in a secure secret store will be required before in-place upgrade compatibility can be guaranteed. Preserve `tt.opportunity.app` as the package ID and increment `versionCode` on later releases.
